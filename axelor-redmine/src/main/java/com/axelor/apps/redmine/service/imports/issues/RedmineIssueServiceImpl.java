/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2022 Axelor (<http://axelor.com>).
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
package com.axelor.apps.redmine.service.imports.issues;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.apps.redmine.service.common.RedmineCommonService;
import com.axelor.apps.redmine.service.common.RedmineErrorLogService;
import com.axelor.apps.redmine.service.imports.fetch.RedmineFetchDataService;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.User;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineIssueServiceImpl implements RedmineIssueService {

  protected RedmineImportIssueService redmineImportIssueService;
  protected RedmineFetchDataService redmineFetchDataService;
  protected RedmineErrorLogService redmineErrorLogService;
  protected BatchRepository batchRepo;
  protected RedmineBatchRepository redmineBatchRepo;

  @Inject
  public RedmineIssueServiceImpl(
      RedmineImportIssueService redmineImportIssueService,
      RedmineFetchDataService redmineFetchDataService,
      RedmineErrorLogService redmineErrorLogService,
      BatchRepository batchRepo,
      RedmineBatchRepository redmineBatchRepo) {

    this.redmineImportIssueService = redmineImportIssueService;
    this.redmineFetchDataService = redmineFetchDataService;
    this.redmineErrorLogService = redmineErrorLogService;
    this.batchRepo = batchRepo;
    this.redmineBatchRepo = redmineBatchRepo;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());

  @Override
  @Transactional
  public void redmineImportIssue(
      Batch batch,
      RedmineManager redmineManager,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {

    RedmineCommonService.setResult("");

    // LOGGER FOR REDMINE IMPORT ERROR DATA

    List<Object[]> errorObjList = new ArrayList<Object[]>();

    // FETCH IMPORT DATA

    RedmineBatch redmineBatch = batch.getRedmineBatch();

    Batch lastBatch =
        batchRepo
            .all()
            .filter(
                "self.id != ?1 and self.redmineBatch.id = ?2 and self.endDate != null",
                batch.getId(),
                redmineBatch.getId())
            .order("-updatedOn")
            .fetchOne();

    ZonedDateTime lastBatchEndDate = lastBatch != null ? lastBatch.getEndDate() : null;
    LocalDateTime lastBatchUpdatedOn = lastBatch != null ? lastBatch.getUpdatedOn() : null;
    String failedRedmineIssuesIds = redmineBatch.getFailedRedmineIssuesIds();

    HashMap<Integer, String> redmineUserMap = new HashMap<Integer, String>();

    LOG.debug("Fetching issues from redmine..");

    try {
      List<User> redmineUserList = redmineManager.getUserManager().getUsers();

      for (User user : redmineUserList) {
        redmineUserMap.put(user.getId(), user.getMail());
      }

      // CREATE MAP FOR PASS TO THE METHODS

      HashMap<String, Object> paramsMap = new HashMap<String, Object>();

      paramsMap.put("onError", onError);
      paramsMap.put("onSuccess", onSuccess);
      paramsMap.put("batch", batch);
      paramsMap.put("errorObjList", errorObjList);
      paramsMap.put("lastBatchUpdatedOn", lastBatchUpdatedOn);
      paramsMap.put("redmineUserMap", redmineUserMap);
      paramsMap.put("redmineManager", redmineManager);

      // IMPORT PROCESS

      redmineImportIssueService.importIssue(
          redmineFetchDataService.fetchIssueImportData(
              redmineManager, lastBatchEndDate, failedRedmineIssuesIds),
          paramsMap);
      failedRedmineIssuesIds = batch.getRedmineBatch().getFailedRedmineIssuesIds();

      // ATTACH ERROR LOG WITH BATCH

      if (errorObjList != null && errorObjList.size() > 0) {
        MetaFile errorMetaFile = redmineErrorLogService.redmineErrorLogService(errorObjList);

        redmineBatch = redmineBatchRepo.find(redmineBatch.getId());
        redmineBatch.setFailedRedmineIssuesIds(failedRedmineIssuesIds);
        redmineBatchRepo.save(redmineBatch);

        if (errorMetaFile != null) {
          batch = batchRepo.find(batch.getId());
          batch.setErrorLogFile(errorMetaFile);
          batchRepo.save(batch);
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }
}
