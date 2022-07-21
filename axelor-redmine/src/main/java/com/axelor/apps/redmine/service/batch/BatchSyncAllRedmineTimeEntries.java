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
import com.axelor.apps.redmine.service.sync.timeentries.RedmineExportTimeSpentService;
import com.axelor.apps.redmine.service.sync.timeentries.RedmineImportTimeSpentService;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.RedmineManager;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;

public class BatchSyncAllRedmineTimeEntries extends AbstractBatch {

  protected AppRedmineRepository appRedmineRepo;
  protected RedmineImportIssueService redmineImportIssueService;
  protected RedmineImportTimeSpentService redmineImportTimeSpentService;
  protected RedmineExportTimeSpentService redmineExportTimeSpentService;
  protected RedmineBatchCommonService redmineBatchCommonService;

  @Inject
  public BatchSyncAllRedmineTimeEntries(
      AppRedmineRepository appRedmineRepo,
      RedmineImportIssueService redmineImportIssueService,
      RedmineImportTimeSpentService redmineImportTimeSpentService,
      RedmineExportTimeSpentService redmineExportTimeSpentService,
      RedmineBatchCommonService redmineBatchCommonService) {

    this.appRedmineRepo = appRedmineRepo;
    this.redmineImportIssueService = redmineImportIssueService;
    this.redmineImportTimeSpentService = redmineImportTimeSpentService;
    this.redmineBatchCommonService = redmineBatchCommonService;
    this.redmineExportTimeSpentService = redmineExportTimeSpentService;
  }

  protected List<Object[]> errorObjList = new ArrayList<>();
  protected String timeSpentResultStr;
  protected String timeSpentImportResultStr;
  protected String timeSpentExportResultStr;

  @Override
  protected void process() {

    AppRedmine appRedmine = appRedmineRepo.all().fetchOne();

    RedmineManager redmineManager = redmineBatchCommonService.getRedmineManager(appRedmine);

    if (redmineManager == null) {
      return;
    }

    // Validate different custom fields names filled in redmine app config
    redmineBatchCommonService.validateCustomFieldConfigTimeEntry(
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

    Map<String, String> redmineUserLoginMap =
        redmineBatchCommonService.getRedmineUserLoginMap(redmineManager.getUserManager());

    LOG.debug("Start process for AOS timesheetlines export..");

    timeSpentExportResultStr =
        redmineExportTimeSpentService.redmineTimeEntryExportProcess(
            redmineManager,
            lastBatchEndDate,
            appRedmineRepo.all().fetchOne(),
            redmineUserMap,
            redmineUserLoginMap,
            batchRepo.find(batch.getId()),
            success -> incrementDone(),
            error -> incrementAnomaly(),
            errorObjList);

    LOG.debug("Start process for redmine time entries import..");

    timeSpentImportResultStr =
        redmineImportTimeSpentService.redmineTimeEntriesImportProcess(
            redmineManager,
            lastBatchEndDate,
            appRedmineRepo.all().fetchOne(),
            redmineUserMap,
            batchRepo.find(batch.getId()),
            success -> incrementDone(),
            error -> incrementAnomaly(),
            errorObjList);

    timeSpentResultStr = timeSpentExportResultStr + "\n" + timeSpentImportResultStr;

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
    addComment(timeSpentResultStr);
  }
}
