/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.redmine.service.imports.timeentries;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.apps.redmine.service.imports.common.RedmineFetchDataService;
import com.axelor.apps.redmine.service.imports.common.RedmineImportCommonService;
import com.axelor.apps.redmine.service.imports.log.RedmineErrorLogService;
import com.axelor.exception.service.TraceBackService;
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

public class RedmineTimeEntriesServiceImpl implements RedmineTimeEntriesService {

  protected RedmineImportTimeSpentService redmineImportTimeSpentService;
  protected RedmineFetchDataService redmineFetchDataService;
  protected RedmineErrorLogService redmineErrorLogService;
  protected BatchRepository batchRepo;
  protected RedmineBatchRepository redmineBatchRepo;

  @Inject
  public RedmineTimeEntriesServiceImpl(
      RedmineImportTimeSpentService redmineImportTimeSpentService,
      RedmineFetchDataService redmineFetchDataService,
      RedmineErrorLogService redmineErrorLogService,
      BatchRepository batchRepo,
      RedmineBatchRepository redmineBatchRepo) {

    this.redmineImportTimeSpentService = redmineImportTimeSpentService;
    this.redmineFetchDataService = redmineFetchDataService;
    this.redmineErrorLogService = redmineErrorLogService;
    this.batchRepo = batchRepo;
    this.redmineBatchRepo = redmineBatchRepo;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());

  @Override
  @Transactional
  public void redmineImportTimeEntries(
      Batch batch,
      RedmineManager redmineManager,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {

    RedmineImportCommonService.result = "";

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
    String failedRedmineTimeEntriesIds = redmineBatch.getFailedRedmineTimeEntriesIds();

    HashMap<Integer, String> redmineUserMap = new HashMap<Integer, String>();

    LOG.debug("Fetching time entries from redmine..");

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

      redmineImportTimeSpentService.importTimeSpent(
          redmineFetchDataService.fetchTimeEntryImportData(
              redmineManager, lastBatchEndDate, failedRedmineTimeEntriesIds),
          paramsMap);
      failedRedmineTimeEntriesIds = batch.getRedmineBatch().getFailedRedmineTimeEntriesIds();

      // ATTACH ERROR LOG WITH BATCH

      if (errorObjList != null && errorObjList.size() > 0) {
        MetaFile errorMetaFile = redmineErrorLogService.redmineErrorLogService(errorObjList);

        redmineBatch = redmineBatchRepo.find(redmineBatch.getId());
        redmineBatch.setFailedRedmineTimeEntriesIds(failedRedmineTimeEntriesIds);
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
