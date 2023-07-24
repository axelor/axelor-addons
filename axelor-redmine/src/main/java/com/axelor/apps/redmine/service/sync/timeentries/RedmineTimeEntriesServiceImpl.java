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
package com.axelor.apps.redmine.service.sync.timeentries;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.apps.redmine.service.common.RedmineCommonService;
import com.axelor.apps.redmine.service.common.RedmineErrorLogService;
import com.axelor.apps.redmine.service.imports.fetch.RedmineFetchDataService;
import com.axelor.apps.redmine.service.imports.projects.pojo.MethodParameters;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.TimeEntry;
import com.taskadapter.redmineapi.bean.User;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineTimeEntriesServiceImpl implements RedmineTimeEntriesService {

  protected RedmineImportTimeSpentService redmineImportTimeSpentService;
  protected RedmineExportTimeSpentService redmineExportTimeSpentService;
  protected RedmineFetchDataService redmineFetchDataService;
  protected RedmineErrorLogService redmineErrorLogService;
  protected BatchRepository batchRepo;
  protected RedmineBatchRepository redmineBatchRepo;

  @Inject
  public RedmineTimeEntriesServiceImpl(
      RedmineImportTimeSpentService redmineImportTimeSpentService,
      RedmineExportTimeSpentService redmineExportTimeSpentService,
      RedmineFetchDataService redmineFetchDataService,
      RedmineErrorLogService redmineErrorLogService,
      BatchRepository batchRepo,
      RedmineBatchRepository redmineBatchRepo) {

    this.redmineImportTimeSpentService = redmineImportTimeSpentService;
    this.redmineExportTimeSpentService = redmineExportTimeSpentService;
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
    String failedRedmineTimeEntriesIds = redmineBatch.getFailedRedmineTimeEntriesIds();
    String failedAosTimesheetLineIds = redmineBatch.getFailedAosTimesheetLineIds();

    HashMap<Integer, String> redmineUserMap = new HashMap<>();
    HashMap<String, String> redmineUserLoginMap = new HashMap<>();

    LOG.debug("Fetching time entries from redmine..");

    try {
      List<User> redmineUserList = redmineManager.getUserManager().getUsers();

      for (User user : redmineUserList) {
        redmineUserMap.put(user.getId(), user.getMail());
        redmineUserLoginMap.put(user.getMail(), user.getLogin());
      }

      MethodParameters methodParameters =
          new MethodParameters(
              onError,
              onSuccess,
              batch,
              errorObjList,
              lastBatchUpdatedOn,
              redmineUserMap,
              redmineManager);

      // FETCH RECORDS TO IMPORT

      LOG.debug("Start timeentries fetching process from Redmine..");

      List<TimeEntry> redmineTimeEntryList =
          redmineFetchDataService.fetchTimeEntryImportData(
              redmineManager, lastBatchEndDate, failedRedmineTimeEntriesIds);

      // EXPORT PROCESS
      if (isDirectionExport(redmineBatch)) {
        LOG.debug("Start timesheetlines export process from AOS to Redmine..");

        failedAosTimesheetLineIds =
            redmineExportTimeSpentService.exportTimesheetLines(
                methodParameters, redmineUserLoginMap);
      }

      methodParameters.setBatch(batchRepo.find(batch.getId()));

      // IMPORT PROCESS
      if (isDirectionImport(redmineBatch)) {
        LOG.debug("Start timesheetlines import process from Redmine to AOS..");

        failedRedmineTimeEntriesIds =
            redmineImportTimeSpentService.importTimeSpent(redmineTimeEntryList, methodParameters);
      }

      // ATTACH ERROR LOG WITH BATCH

      redmineBatch = redmineBatchRepo.find(redmineBatch.getId());
      redmineBatch.setFailedRedmineTimeEntriesIds(failedRedmineTimeEntriesIds);
      redmineBatch.setFailedAosTimesheetLineIds(failedAosTimesheetLineIds);
      redmineBatchRepo.save(redmineBatch);

      LOG.debug("Prepare error log file to attach with batch..");

      if (CollectionUtils.isNotEmpty(errorObjList)) {
        MetaFile errorMetaFile = redmineErrorLogService.redmineErrorLogService(errorObjList);

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

  protected boolean isDirectionImport(RedmineBatch redmineBatch) {
    return getDirection(
        redmineBatch.getRedmineSynchronizationDirectionSelect(),
        RedmineBatchRepository.SYNCHRONIZATION_DIRECTION_IMPORT,
        RedmineBatchRepository.SYNCHRONIZATION_DIRECTION_BOTH);
  }

  protected boolean isDirectionExport(RedmineBatch redmineBatch) {
    return getDirection(
        redmineBatch.getRedmineSynchronizationDirectionSelect(),
        RedmineBatchRepository.SYNCHRONIZATION_DIRECTION_EXPORT,
        RedmineBatchRepository.SYNCHRONIZATION_DIRECTION_BOTH);
  }

  private boolean getDirection(Integer redmineBatchDirection, Integer... directionArray) {
    if (redmineBatchDirection == null || directionArray == null) {
      return false;
    }

    return Arrays.asList(directionArray).contains(redmineBatchDirection);
  }
}
