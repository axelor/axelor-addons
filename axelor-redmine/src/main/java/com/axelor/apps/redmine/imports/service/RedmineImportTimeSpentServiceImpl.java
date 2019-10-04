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
package com.axelor.apps.redmine.imports.service;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.service.timesheet.TimesheetService;
import com.axelor.apps.redmine.db.OpenSuitRedmineSync;
import com.axelor.apps.redmine.db.repo.OpenSuitRedmineSyncRepository;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.mapper.Mapper;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.TimeEntry;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineImportTimeSpentServiceImpl extends RedmineImportService
    implements RedmineImportTimeSpentService {

  protected OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo;
  protected TimesheetLineRepository timesheetLineRepo;
  protected RedmineDynamicImportService redmineDynamicImportService;
  protected MetaModelRepository metaModelRepo;
  protected TimesheetRepository timesheetRepo;
  protected TimesheetService timesheetService;

  @Inject
  public RedmineImportTimeSpentServiceImpl(
      DMSFileRepository dmsFileRepo,
      AppRedmineRepository appRedmineRepo,
      MetaFiles metaFiles,
      BatchRepository batchRepo,
      UserRepository userRepo,
      OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo,
      TimesheetLineRepository timesheetLineRepo,
      RedmineDynamicImportService redmineDynamicImportService,
      MetaModelRepository metaModelRepo,
      TimesheetRepository timesheetRepo,
      TimesheetService timesheetService) {

    super(dmsFileRepo, appRedmineRepo, metaFiles, batchRepo, userRepo);

    this.openSuiteRedmineSyncRepo = openSuiteRedmineSyncRepo;
    this.timesheetLineRepo = timesheetLineRepo;
    this.redmineDynamicImportService = redmineDynamicImportService;
    this.metaModelRepo = metaModelRepo;
    this.timesheetRepo = timesheetRepo;
    this.timesheetService = timesheetService;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());
  private LocalDateTime lastBatchUpdatedOn;

  @Override
  public void importTimeSpent(
      Batch batch,
      LocalDateTime lastBatchUpdatedOn,
      RedmineManager redmineManager,
      List<TimeEntry> redmineTimeEntryList,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError,
      List<Object[]> errorObjList) {

    if (redmineTimeEntryList != null && !redmineTimeEntryList.isEmpty()) {
      OpenSuitRedmineSync openSuiteRedmineSyncTimesheet =
          openSuiteRedmineSyncRepo.findBySyncTypeSelect(
              OpenSuitRedmineSyncRepository.SYNC_TYPE_SPENT_TIME);

      this.errorObjList = errorObjList;
      this.dynamicFieldsSyncList = openSuiteRedmineSyncTimesheet.getDynamicFieldsSyncList();

      if (validateDynamicFieldsSycList(
          dynamicFieldsSyncList,
          METAMODEL_TIMESHEET_LINE,
          Mapper.toMap(new TimesheetLine()),
          Mapper.toMap(new com.taskadapter.redmineapi.bean.TimeEntry(null)))) {

        this.batch = batch;
        this.redmineManager = redmineManager;
        this.onError = onError;
        this.onSuccess = onSuccess;
        this.redmineTimeEntryManager = redmineManager.getTimeEntryManager();
        this.redmineUserManager = redmineManager.getUserManager();
        this.metaModel = metaModelRepo.findByName(METAMODEL_TIMESHEET_LINE);
        this.lastBatchUpdatedOn = lastBatchUpdatedOn;

        Comparator<TimeEntry> compareByDate =
            (TimeEntry o1, TimeEntry o2) -> o1.getSpentOn().compareTo(o2.getSpentOn());
        Collections.sort(redmineTimeEntryList, compareByDate);

        String syncTypeSelect = openSuiteRedmineSyncTimesheet.getRedmineToOpenSuiteSyncSelect();

        for (TimeEntry redmineTimeEntry : redmineTimeEntryList) {
          createOpenSuiteTimesheetLine(redmineTimeEntry, syncTypeSelect);
        }
      }
    }

    String resultStr =
        String.format(
            "Redmine SpentTime -> ABS Timesheetline : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  public void createOpenSuiteTimesheetLine(TimeEntry redmineTimeEntry, String syncTypeSelect) {

    TimesheetLine timesheetLine =
        timesheetLineRepo.findByRedmineId(redmineTimeEntry.getId()) != null
            ? timesheetLineRepo.findByRedmineId(redmineTimeEntry.getId())
            : new TimesheetLine();

    if (timesheetLine.getId() == null) {
      addInList = true;
    }

    // Sync type - On create
    if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_CREATE)
        && timesheetLine.getId() != null) {
      return;
    }

    // Sync type - On update
    if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_UPDATE)
        && lastBatchUpdatedOn != null
        && timesheetLine.getId() != null) {

      LocalDateTime redmineUpdatedOn =
          redmineTimeEntry
              .getUpdatedOn()
              .toInstant()
              .atZone(ZoneId.systemDefault())
              .toLocalDateTime();

      if (lastBatchUpdatedOn.isAfter(redmineUpdatedOn)) {
        return;
      }

      // If updates are made on both sides and os side is latest updated then abort import
      if (timesheetLine.getUpdatedOn().isAfter(lastBatchUpdatedOn)
          && timesheetLine.getUpdatedOn().isAfter(redmineUpdatedOn)) {
        return;
      }
    }

    timesheetLine.setRedmineId(redmineTimeEntry.getId());

    Map<String, Object> timesheetLineMap = Mapper.toMap(timesheetLine);
    Map<String, Object> redmineTimeEntryMap = Mapper.toMap(redmineTimeEntry);
    Map<String, Object> redmineTimeEntryCustomFieldsMap =
        setRedmineCustomFieldsMap(redmineTimeEntry.getCustomFields());

    timesheetLineMap =
        redmineDynamicImportService.createOpenSuiteDynamic(
            dynamicFieldsSyncList,
            timesheetLineMap,
            redmineTimeEntryMap,
            redmineTimeEntryCustomFieldsMap,
            metaModel,
            redmineTimeEntry,
            redmineManager);

    timesheetLine = Mapper.toBean(timesheetLine.getClass(), timesheetLineMap);

    // Set special fixed rules
    this.setSpecialFixedRules(timesheetLine, redmineTimeEntry);

    // Create or update OS object
    if (timesheetLine.getTimesheet() != null) {
      this.saveOpenSuiteTimeEntry(timesheetLine, redmineTimeEntry);
    }
  }

  @Transactional
  public void setSpecialFixedRules(TimesheetLine timesheetLine, TimeEntry redmineTimeEntry) {

    try {
      User user = findOpensuiteUser(redmineTimeEntry.getUserId(), null);

      if (user != null) {
        timesheetLine.setUser(user);
        setCreatedUser(timesheetLine, user, "setCreatedBy");

        // Set timesheet (current or create timesheet)

        Timesheet timesheet =
            timesheetRepo
                .all()
                .filter(
                    "self.user = ?1 AND self.statusSelect = ?2",
                    user,
                    TimesheetRepository.STATUS_DRAFT)
                .order("-id")
                .fetchOne();

        LocalDate redmineSpentOn =
            redmineTimeEntry.getSpentOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        if (timesheet == null) {
          timesheet = timesheetService.createTimesheet(user, redmineSpentOn, null);
          timesheetRepo.save(timesheet);
        } else if (timesheet.getFromDate().isAfter(redmineSpentOn)) {
          timesheet.setFromDate(redmineSpentOn);
        }

        timesheetLine.setTimesheet(timesheet);
      }

      setLocalDateTime(timesheetLine, redmineTimeEntry.getCreatedOn(), "setCreatedOn");
      setLocalDateTime(timesheetLine, redmineTimeEntry.getUpdatedOn(), "setUpdatedOn");
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  @Transactional
  public void saveOpenSuiteTimeEntry(TimesheetLine timesheetLine, TimeEntry redmineTimeEntry) {

    try {

      if (addInList) {
        timesheetLine.addBatchSetItem(batch);
      }

      timesheetLineRepo.save(timesheetLine);

      //      JPA.em().getTransaction().commit();
      //      if (!JPA.em().getTransaction().isActive()) {
      //        JPA.em().getTransaction().begin();
      //      }
      onSuccess.accept(timesheetLine);
      success++;
    } catch (Exception e) {
      onError.accept(e);
      fail++;
      //      JPA.em().getTransaction().rollback();
      //      JPA.em().getTransaction().begin();
      TraceBackService.trace(e, "", batch.getId());
    }
  }
}
