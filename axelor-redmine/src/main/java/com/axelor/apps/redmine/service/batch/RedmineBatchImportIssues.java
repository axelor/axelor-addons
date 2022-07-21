/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.redmine.service.batch;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.redmine.service.imports.issues.RedmineImportIssueService;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.RedmineManager;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;

public class RedmineBatchImportIssues extends AbstractBatch {

  protected AppRedmineRepository appRedmineRepo;
  protected RedmineImportIssueService redmineImportIssueService;
  protected RedmineBatchCommonService redmineBatchCommonService;

  @Inject
  public RedmineBatchImportIssues(
      AppRedmineRepository appRedmineRepo,
      RedmineImportIssueService redmineImportIssueService,
      RedmineBatchCommonService redmineBatchCommonService) {

    this.appRedmineRepo = appRedmineRepo;
    this.redmineImportIssueService = redmineImportIssueService;
    this.redmineBatchCommonService = redmineBatchCommonService;
  }

  protected List<Object[]> errorObjList = new ArrayList<>();
  protected String issuesResultStr;

  @Override
  protected void process() {

    AppRedmine appRedmine = appRedmineRepo.all().fetchOne();

    RedmineManager redmineManager = redmineBatchCommonService.getRedmineManager(appRedmine);

    if (redmineManager == null) {
      return;
    }

    // Validate different custom fields names filled in redmine app config
    redmineBatchCommonService.validateCustomFieldConfigIssue(
        redmineManager.getCustomFieldManager(), appRedmine);

    Batch lastBatch =
        batchRepo
            .all()
            .filter(
                "self.id != ?1 and self.redmineBatch.id = ?2 and self.endDate != null",
                batch.getId(),
                batch.getRedmineBatch().getId())
            .order("-updatedOn")
            .fetchOne();

    ZonedDateTime lastBatchEndDate = lastBatch != null ? lastBatch.getEndDate() : null;

    Map<Integer, String> redmineUserMap =
        redmineBatchCommonService.getRedmineUserMap(redmineManager.getUserManager());

    LOG.debug("Start process for redmine issues import..");

    issuesResultStr =
        redmineImportIssueService.redmineIssuesImportProcess(
            redmineManager,
            lastBatchEndDate,
            appRedmine,
            redmineUserMap,
            batch,
            success -> incrementDone(),
            error -> incrementAnomaly(),
            errorObjList);

    if (CollectionUtils.isNotEmpty(errorObjList)) {
      MetaFile errorLogFile = redmineBatchCommonService.generateErrorLog(errorObjList);

      if (errorLogFile != null) {
        batchRepo.find(batch.getId()).setErrorLogFile(errorLogFile);
      }
    }
  }

  @Override
  protected void stop() {
    super.stop();
    addComment(issuesResultStr);
  }
}
