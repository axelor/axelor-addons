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

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.apps.redmine.service.common.RedmineCommonService;
import com.axelor.apps.redmine.service.common.RedmineErrorLogService;
import com.axelor.apps.redmine.service.imports.fetch.RedmineFetchDataService;
import com.axelor.apps.redmine.service.imports.projects.pojo.MethodParameters;
import com.axelor.apps.redmine.service.imports.utils.FetchRedmineInfo;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.TimeEntry;
import com.taskadapter.redmineapi.bean.User;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Consumer;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
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
  protected AppRedmineRepository appRedmineRepository;

  @Inject
  public RedmineTimeEntriesServiceImpl(
      RedmineImportTimeSpentService redmineImportTimeSpentService,
      RedmineExportTimeSpentService redmineExportTimeSpentService,
      RedmineFetchDataService redmineFetchDataService,
      RedmineErrorLogService redmineErrorLogService,
      BatchRepository batchRepo,
      RedmineBatchRepository redmineBatchRepo,
      AppRedmineRepository appRedmineRepository) {

    this.redmineImportTimeSpentService = redmineImportTimeSpentService;
    this.redmineExportTimeSpentService = redmineExportTimeSpentService;
    this.redmineFetchDataService = redmineFetchDataService;
    this.redmineErrorLogService = redmineErrorLogService;
    this.batchRepo = batchRepo;
    this.redmineBatchRepo = redmineBatchRepo;
    this.appRedmineRepository = appRedmineRepository;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());

  @Override
  @Transactional
  public void redmineImportTimeEntries(
      Batch batch,
      RedmineManager redmineManager,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError,
      AppRedmine appRedmine) {

    RedmineCommonService.setResult("");

    // LOGGER FOR REDMINE IMPORT ERROR DATA
    List<Object[]> errorObjList = new ArrayList<>();
    // FETCH IMPORT DATA
    RedmineBatch redmineBatch = batch.getRedmineBatch();
    Batch lastBatch = getLastBatch(batch);

    ZonedDateTime lastBatchEndDate = lastBatch != null ? lastBatch.getEndDate() : null;
    LocalDateTime lastBatchUpdatedOn = lastBatch != null ? lastBatch.getUpdatedOn() : null;
    String failedRedmineTimeEntriesIds = batch.getRedmineBatch().getFailedRedmineTimeEntriesIds();
    String failedAosTimesheetLineIds = batch.getRedmineBatch().getFailedAosTimesheetLineIds();

    HashMap<Integer, String> redmineUserMap = new HashMap<>();
    HashMap<String, String> redmineUserLoginMap = new HashMap<>();

    LOG.debug("Fetching time entries from redmine..");

    try {
      List<User> redmineUserList = new ArrayList<>();
      Map<Integer, Boolean> includedIdsMap = new HashMap<>();

      LOG.debug("Fetching Axelor users from Redmine...");

      FetchRedmineInfo.fillUsersList(
          redmineManager,
          includedIdsMap,
          redmineUserList,
          FetchRedmineInfo.getFillUsersListParams(appRedmine));

      for (User user : redmineUserList) {
        fillRedmineUserInfo(user, redmineUserMap, redmineUserLoginMap);
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
      if (isDirectionExport(batch.getRedmineBatch())) {
        failedAosTimesheetLineIds =
            getFailedAosTimesheetLineIdsWhenExporting(methodParameters, redmineUserLoginMap);
      }

      methodParameters.setBatch(batchRepo.find(batch.getId()));

      // IMPORT PROCESS
      if (isDirectionImport(batch.getRedmineBatch())) {
        failedRedmineTimeEntriesIds =
            getFailedAosTimesheetLineIdsWhenImporting(redmineTimeEntryList, methodParameters);
      }

      // ATTACH ERROR LOG WITH BATCH

      redmineBatch = redmineBatchRepo.find(redmineBatch.getId());
      redmineBatch.setFailedRedmineTimeEntriesIds(failedRedmineTimeEntriesIds);
      redmineBatch.setFailedAosTimesheetLineIds(failedAosTimesheetLineIds);
      redmineBatchRepo.save(redmineBatch);

      LOG.debug("Prepare error log file to attach with batch..");

      if (CollectionUtils.isNotEmpty(errorObjList)) {
        setBatchError(batch, errorObjList);
      }

    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  protected String getFailedAosTimesheetLineIdsWhenImporting(
      List<TimeEntry> redmineTimeEntryList, MethodParameters methodParameters) {
    String failedRedmineTimeEntriesIds;
    LOG.debug("Start timesheetlines import process from Redmine to AOS..");

    failedRedmineTimeEntriesIds =
        redmineImportTimeSpentService.importTimeSpent(redmineTimeEntryList, methodParameters);
    return failedRedmineTimeEntriesIds;
  }

  protected String getFailedAosTimesheetLineIdsWhenExporting(
      MethodParameters methodParameters, HashMap<String, String> redmineUserLoginMap) {
    String failedAosTimesheetLineIds;
    LOG.debug("Start timesheetlines export process from AOS to Redmine..");

    failedAosTimesheetLineIds =
        redmineExportTimeSpentService.exportTimesheetLines(methodParameters, redmineUserLoginMap);
    return failedAosTimesheetLineIds;
  }

  protected void fillRedmineUserInfo(
      User user,
      HashMap<Integer, String> redmineUserMap,
      HashMap<String, String> redmineUserLoginMap) {
    redmineUserMap.put(user.getId(), user.getMail());
    redmineUserLoginMap.put(user.getMail(), user.getLogin());
  }

  protected Batch getLastBatch(Batch batch) {
    CriteriaBuilder criteriaBuilder = JPA.em().getCriteriaBuilder();
    CriteriaQuery<Batch> criteriaQuery = criteriaBuilder.createQuery(Batch.class);
    Root<Batch> root = criteriaQuery.from(Batch.class);

    Predicate predicate =
        criteriaBuilder.and(
            criteriaBuilder.notEqual(root.get("id"), batch.getId()),
            criteriaBuilder.equal(
                root.get("redmineBatch").get("id"), batch.getRedmineBatch().getId()),
            criteriaBuilder.isNotNull(root.get("endDate")));

    criteriaQuery.where(predicate).orderBy(criteriaBuilder.desc(root.get("updatedOn")));

    return JPA.em().createQuery(criteriaQuery).getSingleResult();
  }

  protected void setBatchError(Batch batch, List<Object[]> errorObjList) {
    MetaFile errorMetaFile = redmineErrorLogService.redmineErrorLogService(errorObjList);

    if (errorMetaFile == null) {
      return;
    }
    batch = batchRepo.find(batch.getId());
    batch.setErrorLogFile(errorMetaFile);
    batchRepo.save(batch);
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
