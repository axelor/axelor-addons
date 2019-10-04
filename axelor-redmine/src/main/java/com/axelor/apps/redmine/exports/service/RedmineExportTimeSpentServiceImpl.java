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
package com.axelor.apps.redmine.exports.service;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.redmine.db.OpenSuitRedmineSync;
import com.axelor.apps.redmine.db.repo.OpenSuitRedmineSyncRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.TimeEntry;
import com.taskadapter.redmineapi.bean.User;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineExportTimeSpentServiceImpl extends RedmineExportService
    implements RedmineExportTimeSpentService {

  protected OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo;
  protected TimesheetLineRepository timesheetLineRepo;
  protected MetaModelRepository metaModelRepo;
  protected RedmineDynamicExportService redmineDynamicExportService;

  @Inject
  public RedmineExportTimeSpentServiceImpl(
      OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo,
      TimesheetLineRepository timesheetLineRepo,
      MetaModelRepository metaModelRepo,
      RedmineDynamicExportService redmineDynamicExportService) {

    this.openSuiteRedmineSyncRepo = openSuiteRedmineSyncRepo;
    this.timesheetLineRepo = timesheetLineRepo;
    this.metaModelRepo = metaModelRepo;
    this.redmineDynamicExportService = redmineDynamicExportService;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());
  private LocalDateTime lastBatchUpdatedOn;

  @Override
  public void exportTimeSpent(
      Batch batch,
      LocalDateTime lastBatchUpdatedOn,
      RedmineManager redmineManager,
      List<TimesheetLine> timesheetLineList,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError,
      List<Object[]> errorObjList) {

    if (timesheetLineList != null && !timesheetLineList.isEmpty()) {
      OpenSuitRedmineSync openSuiteRedmineSyncSpentTime =
          openSuiteRedmineSyncRepo.findBySyncTypeSelect(
              OpenSuitRedmineSyncRepository.SYNC_TYPE_SPENT_TIME);

      this.errorObjList = errorObjList;
      this.dynamicFieldsSyncList = openSuiteRedmineSyncSpentTime.getDynamicFieldsSyncList();

      if (validateDynamicFieldsSyncList(
          dynamicFieldsSyncList,
          METAMODEL_TIMESHEET_LINE,
          Mapper.toMap(new TimesheetLine()),
          Mapper.toMap(new com.taskadapter.redmineapi.bean.TimeEntry(null)))) {

        this.redmineManager = redmineManager;
        this.batch = batch;
        this.onError = onError;
        this.onSuccess = onSuccess;
        this.redmineTimeEntryManager = redmineManager.getTimeEntryManager();
        this.redmineUserManager = redmineManager.getUserManager();
        this.metaModel = metaModelRepo.findByName(METAMODEL_TIMESHEET_LINE);
        this.lastBatchUpdatedOn = lastBatchUpdatedOn;

        String syncTypeSelect = openSuiteRedmineSyncSpentTime.getOpenSuiteToRedmineSyncSelect();

        for (TimesheetLine timesheetLine : timesheetLineList) {
          createRedmineSpentTime(timesheetLine, syncTypeSelect);
        }
      }
    }

    String resultStr =
        String.format(
            "ABS Timesheetline -> Redmine SpentTime : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  public void createRedmineSpentTime(TimesheetLine timesheetLine, String syncTypeSelect) {

    try {
      com.taskadapter.redmineapi.bean.TimeEntry redmineTimeEntry = null;

      if (timesheetLine.getRedmineId() == null || timesheetLine.getRedmineId().equals(0)) {
        redmineTimeEntry =
            new com.taskadapter.redmineapi.bean.TimeEntry(redmineManager.getTransport());
      } else {
        redmineTimeEntry = redmineTimeEntryManager.getTimeEntry(timesheetLine.getRedmineId());
      }

      // Sync type - On create
      if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_CREATE)
          && (timesheetLine.getRedmineId() != null && !timesheetLine.getRedmineId().equals(0))) {
        return;
      }

      // Sync type - On update
      if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_UPDATE)
          && redmineTimeEntry.getUpdatedOn() != null
          && lastBatchUpdatedOn != null) {

        // If updates are made on both sides and redmine side is latest updated then abort export
        LocalDateTime redmineUpdatedOn =
            redmineTimeEntry
                .getUpdatedOn()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        if (redmineUpdatedOn.isAfter(lastBatchUpdatedOn)
            && redmineUpdatedOn.isAfter(timesheetLine.getUpdatedOn())) {
          return;
        }
      }

      Map<String, Object> timesheetLineMap = Mapper.toMap(timesheetLine);
      Map<String, Object> redmineTimeEntryMap = Mapper.toMap(redmineTimeEntry);
      Map<String, Object> redmineTimeEntryCustomFieldsMap = new HashMap<>();

      redmineTimeEntryMap =
          redmineDynamicExportService.createRedmineDynamic(
              dynamicFieldsSyncList,
              timesheetLineMap,
              redmineTimeEntryMap,
              redmineTimeEntryCustomFieldsMap,
              metaModel,
              timesheetLine,
              redmineManager);

      // Temporary error fix
      redmineTimeEntryMap.remove("valid");

      Mapper redmineTimeEntryMapper = Mapper.of(redmineTimeEntry.getClass());

      Iterator<Entry<String, Object>> redmineProjectMapItr =
          redmineTimeEntryMap.entrySet().iterator();

      while (redmineProjectMapItr.hasNext()) {
        Map.Entry<String, Object> entry = redmineProjectMapItr.next();
        redmineTimeEntryMapper.set(redmineTimeEntry, entry.getKey(), entry.getValue());
      }

      // Fix - spentOn and activity field values
      redmineTimeEntry.setSpentOn(new Date());
      redmineTimeEntry.setActivityId(
          redmineTimeEntryManager.getTimeEntryActivities().get(0).getId());

      // Special rule for user
      if (timesheetLine.getUser() != null
          && timesheetLine.getUser().getPartner() != null
          && timesheetLine.getUser().getPartner().getEmailAddress() != null) {
        User redmineUser =
            findRedmineUserByEmail(
                timesheetLine.getUser().getPartner().getEmailAddress().getAddress());

        if (redmineUser == null) {
          redmineUser = redmineUserManager.getCurrentUser();
        }

        redmineTimeEntry.setUserId(redmineUser.getId());
        redmineTimeEntry.setUserName(redmineUser.getFullName());
      }

      // Create or update redmine object
      this.saveRedmineSpentTime(redmineTimeEntry, timesheetLine, redmineTimeEntryCustomFieldsMap);
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);

      if (e.getMessage().equals(REDMINE_SERVER_404_NOT_FOUND)) {
        setErrorLog(
            TimesheetLine.class.getSimpleName(),
            timesheetLine.getId().toString(),
            null,
            null,
            I18n.get(IMessage.REDMINE_SYNC_ERROR_RECORD_NOT_FOUND));
      }
    }
  }

  @Transactional
  public void saveRedmineSpentTime(
      TimeEntry redmineTimeEntry,
      TimesheetLine timesheetLine,
      Map<String, Object> redmineTimeEntryCustomFieldsMap) {

    try {

      if (redmineTimeEntry.getId() == null) {
        redmineTimeEntry = redmineTimeEntry.create();
        timesheetLine.setRedmineId(redmineTimeEntry.getId());
        timesheetLineRepo.save(timesheetLine);
      }

      // Set custom fields
      setRedmineCustomFieldValues(
          redmineTimeEntry.getCustomFields(),
          redmineTimeEntryCustomFieldsMap,
          timesheetLine.getId());

      redmineTimeEntry.update();

      onSuccess.accept(timesheetLine);
      success++;
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
      fail++;
    }
  }
}
