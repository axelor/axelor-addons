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
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.apps.redmine.service.common.RedmineCommonService;
import com.axelor.apps.redmine.service.common.RedmineErrorLogService;
import com.axelor.apps.redmine.service.imports.fetch.RedmineFetchDataService;
import com.axelor.apps.redmine.service.imports.projects.pojo.MethodParameters;
import com.axelor.apps.redmine.service.imports.utils.FetchRedmineInfo;
import com.axelor.apps.redmine.service.sync.timeentries.pojo.TimeEntriesRedmineParameters;
import com.axelor.db.JPA;
import com.axelor.meta.db.MetaFile;
import com.axelor.studio.db.AppRedmine;
import com.axelor.studio.db.repo.AppRedmineRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.TimeEntry;
import com.taskadapter.redmineapi.bean.User;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Consumer;
import javax.persistence.NoResultException;
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

    TimeEntriesRedmineParameters timeEntriesRedmineParameters =
        new TimeEntriesRedmineParameters(
            onSuccess,
            onError,
            appRedmine,
            redmineUserMap,
            redmineUserLoginMap,
            errorObjList,
            lastBatchUpdatedOn,
            lastBatchEndDate,
            failedRedmineTimeEntriesIds,
            failedAosTimesheetLineIds);

    LOG.debug("Fetching time entries from redmine..");

    try {
      extractTimesEntriesToSetRedmineBatch(
          batch, redmineManager, timeEntriesRedmineParameters, redmineBatch);
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  private void extractTimesEntriesToSetRedmineBatch(
      Batch batch,
      RedmineManager redmineManager,
      TimeEntriesRedmineParameters timeEntriesRedmineParameters,
      RedmineBatch redmineBatch)
      throws RedmineException {
    manageRedmineUsers(
        redmineManager,
        timeEntriesRedmineParameters.getAppRedmine(),
        timeEntriesRedmineParameters.getRedmineUserMap(),
        timeEntriesRedmineParameters.getRedmineUserLoginMap());

    MethodParameters methodParameters =
        new MethodParameters(
            timeEntriesRedmineParameters.getOnError(),
            timeEntriesRedmineParameters.getOnSuccess(),
            batch,
            timeEntriesRedmineParameters.getErrorObjList(),
            timeEntriesRedmineParameters.getLastBatchUpdatedOn(),
            timeEntriesRedmineParameters.getRedmineUserMap(),
            redmineManager);

    // FETCH RECORDS TO IMPORT

    LOG.debug("Start timeentries fetching process from Redmine..");

    List<TimeEntry> redmineTimeEntryList =
        redmineFetchDataService.fetchTimeEntryImportData(
            redmineManager,
            timeEntriesRedmineParameters.getLastBatchEndDate(),
            timeEntriesRedmineParameters.getFailedRedmineTimeEntriesIds(),
            timeEntriesRedmineParameters.getAppRedmine().getSynchronisedWith());

    // EXPORT PROCESS
    if (isDirectionExport(batch.getRedmineBatch())) {
      timeEntriesRedmineParameters.setFailedAosTimesheetLineIds(
          getFailedAosTimesheetLineIdsWhenExporting(
              methodParameters, timeEntriesRedmineParameters.getRedmineUserLoginMap()));
    }

    methodParameters.setBatch(batchRepo.find(batch.getId()));

    // IMPORT PROCESS
    if (isDirectionImport(batch.getRedmineBatch())) {
      timeEntriesRedmineParameters.setFailedRedmineTimeEntriesIds(
          getFailedAosTimesheetLineIdsWhenImporting(redmineTimeEntryList, methodParameters));
    }

    // ATTACH ERROR LOG WITH BATCH

    manageRedmineBatch(
        redmineBatch,
        timeEntriesRedmineParameters.getFailedRedmineTimeEntriesIds(),
        timeEntriesRedmineParameters.getFailedAosTimesheetLineIds());

    LOG.debug("Prepare error log file to attach with batch..");

    if (CollectionUtils.isNotEmpty(timeEntriesRedmineParameters.getErrorObjList())) {
      setBatchError(batch, timeEntriesRedmineParameters.getErrorObjList());
    }
  }

  @Transactional
  private void manageRedmineBatch(
      RedmineBatch redmineBatch,
      String failedRedmineTimeEntriesIds,
      String failedAosTimesheetLineIds) {
    redmineBatch = redmineBatchRepo.find(redmineBatch.getId());
    redmineBatch.setFailedRedmineTimeEntriesIds(failedRedmineTimeEntriesIds);
    redmineBatch.setFailedAosTimesheetLineIds(failedAosTimesheetLineIds);
    redmineBatchRepo.save(redmineBatch);
  }

  protected void manageRedmineUsers(
      RedmineManager redmineManager,
      AppRedmine appRedmine,
      HashMap<Integer, String> redmineUserMap,
      HashMap<String, String> redmineUserLoginMap)
      throws RedmineException {
    List<User> redmineUserList = new ArrayList<>();
    Map<Integer, Boolean> includedIdsMap = new HashMap<>();

    LOG.debug("Fetching Axelor users from Redmine...");

    FetchRedmineInfo.fillUsersList(
        redmineManager,
        includedIdsMap,
        redmineUserList,
        FetchRedmineInfo.getFillUsersListParams(appRedmine));

    for (User user : redmineUserList) {
      LOG.debug("Fill redmine user info..");
      fillRedmineUserInfo(user, redmineUserMap, redmineUserLoginMap);
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

    try {
      return JPA.em().createQuery(criteriaQuery).setMaxResults(1).getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  @Transactional
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
