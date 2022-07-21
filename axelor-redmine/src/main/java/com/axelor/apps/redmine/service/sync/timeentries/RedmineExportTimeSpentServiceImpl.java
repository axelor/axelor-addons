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
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectTask;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.RedmineImportMapping;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportConfigRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.redmine.service.common.RedmineCommonService;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.db.Query;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.IssueManager;
import com.taskadapter.redmineapi.ProjectManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.TimeEntryManager;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.TimeEntry;
import com.taskadapter.redmineapi.bean.TimeEntryActivity;
import com.taskadapter.redmineapi.internal.Transport;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;

public class RedmineExportTimeSpentServiceImpl extends RedmineCommonService
    implements RedmineExportTimeSpentService {

  protected TimesheetLineRepository timesheetLineRepo;
  protected EmailAddressRepository emailAddressRepo;
  protected RedmineImportMappingRepository redmineImportMappingRepo;
  protected RedmineBatchRepository redmineBatchRepo;

  protected Map<String, Integer> redmineTimeEntryActivityMap = new HashMap<>();
  protected Map<String, Integer> redmineUserEmailMap = new HashMap<>();
  protected Map<Long, String> aosEmployeeEmailMap = new HashMap<>();
  protected Map<String, String> redmineUserLoginMap = new HashMap<>();

  protected Map<Integer, Boolean> redmineProjectIdValidationMap = new HashMap<>();
  protected Map<Integer, Boolean> redmineIssueIdValidationMap = new HashMap<>();

  protected TimeEntryManager timeEntryManager;
  protected IssueManager issueManager;
  protected ProjectManager projectManager;

  @Inject
  public RedmineExportTimeSpentServiceImpl(
      UserRepository userRepo,
      EmployeeRepository employeeRepo,
      ProjectRepository projectRepo,
      AppRedmineRepository appRedmineRepo,
      TimesheetLineRepository timesheetLineRepo,
      EmailAddressRepository emailAddressRepo,
      RedmineImportMappingRepository redmineImportMappingRepo,
      AppBaseService appBaseService,
      RedmineBatchRepository redmineBatchRepo) {

    super(userRepo, employeeRepo, projectRepo, redmineImportMappingRepo, appBaseService);

    this.timesheetLineRepo = timesheetLineRepo;
    this.emailAddressRepo = emailAddressRepo;
    this.redmineImportMappingRepo = redmineImportMappingRepo;
    this.redmineBatchRepo = redmineBatchRepo;
  }

  @SuppressWarnings("unchecked")
  @Override
  public String redmineTimeEntryExportProcess(
      RedmineManager redmineManager,
      ZonedDateTime lastBatchEndDate,
      AppRedmine appRedmine,
      Map<Integer, String> redmineUserEmailMap,
      Map<String, String> redmineUserLoginMap,
      Batch batch,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError,
      List<Object[]> errorObjList) {

    this.redmineManager = redmineManager;
    this.onError = onError;
    this.onSuccess = onSuccess;
    this.batch = batch;
    this.lastBatchEndDate = lastBatchEndDate != null ? lastBatchEndDate.toLocalDateTime() : null;
    this.appRedmine = appRedmine;
    this.errorObjList = errorObjList;
    this.redmineUserEmailMap = MapUtils.invertMap(redmineUserEmailMap);
    this.redmineUserLoginMap = redmineUserLoginMap;

    try {
      prepareParamsAndExportTimeEntries(lastBatchEndDate);
    } catch (RedmineException e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }

    updateTransaction();

    String resultStr =
        String.format(
            "AOS Timesheetline -> Redmine SpentTime : Success: %d Fail: %d", success, fail);
    setResult(getResult() + String.format("%s %n", resultStr));
    success = fail = 0;

    return resultStr;
  }

  public void prepareParamsAndExportTimeEntries(ZonedDateTime lastBatchEndDate)
      throws RedmineException {

    LOG.debug(
        "Preparing params, managers, maps and other variables for time entries export process..");

    timeEntryManager = redmineManager.getTimeEntryManager();

    RedmineBatch redmineBatch = batch.getRedmineBatch();
    List<TimesheetLine> failedAosTimesheetLines =
        fetchFailedAosTimesheetLineList(redmineBatch.getFailedAosTimesheetLineIds());

    setDefaultFieldsAndMaps();

    String newFailedTimesheetLineIds = null;

    LOG.debug("Start fetching and exporting time entries..");

    newFailedTimesheetLineIds = exportTimesheetLines(newFailedTimesheetLineIds);

    if (CollectionUtils.isNotEmpty(failedAosTimesheetLines)) {

      LOG.debug("Start fetching and exporting failed time entries from previous batch run..");

      newFailedTimesheetLineIds =
          exportTimesheetLines(failedAosTimesheetLines, newFailedTimesheetLineIds);
    }

    redmineBatch = redmineBatchRepo.find(redmineBatch.getId());
    redmineBatch.setFailedAosTimesheetLineIds(newFailedTimesheetLineIds);
  }

  public String exportTimesheetLines(String newFailedTimesheetLineIds) {

    LOG.debug("Fetching timesheetlines from AOS as per fetch limit..");

    String baseFilter =
        Boolean.FALSE.equals(batch.getRedmineBatch().getIsOverrideRecords())
            ? "self.project != null AND (self.redmineId = null OR self.redmineId = 0)"
            : "self.project != null";

    Query<TimesheetLine> query =
        lastBatchEndDate != null
            ? timesheetLineRepo
                .all()
                .filter(
                    "(" + baseFilter + " AND self.updatedOn > ?1) OR self.id IN (?2)",
                    lastBatchEndDate,
                    newFailedTimesheetLineIds)
                .order("id")
            : timesheetLineRepo.all().filter(baseFilter).order("id");

    int offset = 0;
    List<TimesheetLine> timesheetLineList = query.fetch(AbstractBatch.FETCH_LIMIT, offset);

    if (CollectionUtils.isNotEmpty(timesheetLineList)) {
      do {
        newFailedTimesheetLineIds =
            exportTimesheetLines(timesheetLineList, newFailedTimesheetLineIds);

        timesheetLineList =
            query.fetch(AbstractBatch.FETCH_LIMIT, offset += timesheetLineList.size());
      } while (CollectionUtils.isNotEmpty(timesheetLineList));
    }

    return newFailedTimesheetLineIds;
  }

  public String exportTimesheetLines(
      List<TimesheetLine> timesheetLineList, String newFailedTimesheetLineIds) {

    for (TimesheetLine timesheetLine : timesheetLineList) {
      timesheetLine = timesheetLineRepo.find(timesheetLine.getId());

      LOG.debug("Exporting timesheetline: {}", timesheetLine.getId());

      redmineManager.setOnBehalfOfUser(null);

      if (!validateRequiredFields(timesheetLine)) {
        newFailedTimesheetLineIds =
            StringUtils.isEmpty(newFailedTimesheetLineIds)
                ? String.valueOf(timesheetLine.getId())
                : newFailedTimesheetLineIds + "," + timesheetLine.getId();
        fail++;
        continue;
      }

      try {
        exportTimesheetLine(timesheetLine, redmineManager.getTransport(), batch.getRedmineBatch());
      } finally {
        updateTransaction();
      }
    }
    return newFailedTimesheetLineIds;
  }

  public boolean validateRequiredFields(TimesheetLine timesheetLine) {
    String emailAddress = aosEmployeeEmailMap.get(timesheetLine.getEmployee().getId());

    if (StringUtils.isEmpty(emailAddress)) {
      emailAddress = getEmailAddress(timesheetLine.getEmployee());
      aosEmployeeEmailMap.put(timesheetLine.getEmployee().getId(), emailAddress);

      if (StringUtils.isEmpty(emailAddress)) {
        setErrorLog(
            IMessage.REDMINE_EXPORT_TIMESHEET_LINE_AOS_EMPLOYEE_EMAIL_NOT_CONFIGURED,
            timesheetLine.getId().toString());
        return false;
      }
    }

    if (!redmineUserEmailMap.containsKey(emailAddress)) {
      setErrorLog(
          IMessage.REDMINE_EXPORT_TIMESHEET_LINE_REDMINE_EMPLOYEE_NOT_FOUND,
          timesheetLine.getId().toString());
      return false;
    }

    Project project = timesheetLine.getProject();
    Integer redmineProjectId = project.getRedmineId();
    if (!redmineProjectIdValidationMap.containsKey(redmineProjectId)) {
      try {
        redmineProjectIdValidationMap.put(
            projectManager.getProjectById(redmineProjectId).getId(), Boolean.TRUE);
      } catch (RedmineException e) {
        redmineProjectIdValidationMap.put(redmineProjectId, Boolean.FALSE);
      }
    }

    if (Boolean.FALSE.equals(redmineProjectIdValidationMap.get(redmineProjectId))) {
      setErrorLog(
          IMessage.REDMINE_EXPORT_TIMESHEET_LINE_PROJECT_NOT_FOUND,
          timesheetLine.getId().toString());
      return false;
    }

    Integer redmineIssueId = null;
    ProjectTask projectTask = timesheetLine.getProjectTask();
    if (projectTask != null) {
      redmineIssueId = projectTask.getRedmineId();

      if (!redmineIssueIdValidationMap.containsKey(redmineIssueId)) {
        try {
          redmineIssueIdValidationMap.put(
              issueManager.getIssueById(redmineIssueId).getId(), Boolean.TRUE);
        } catch (RedmineException e) {
          redmineIssueIdValidationMap.put(redmineIssueId, Boolean.FALSE);
        }
      }

      if (Boolean.FALSE.equals(redmineIssueIdValidationMap.get(redmineIssueId))) {
        setErrorLog(
            IMessage.REDMINE_EXPORT_TIMESHEET_LINE_ISSUE_NOT_FOUND,
            timesheetLine.getId().toString());
        return false;
      }
    }

    Integer redmineActivityId =
        redmineTimeEntryActivityMap.get(fieldMap.get(timesheetLine.getActivityTypeSelect()));

    if (redmineActivityId == null) {
      setErrorLog(
          IMessage.REDMINE_EXPORT_TIMESHEET_LINE_ACTIVITY_NOT_FOUND,
          timesheetLine.getId().toString());
      return false;
    }

    return true;
  }

  public void exportTimesheetLine(
      TimesheetLine timesheetLine, Transport redmineTransport, RedmineBatch redmineBatch) {

    TimeEntry redmineTimeEntry = null;

    if (timesheetLine.getRedmineId() != 0) {
      try {
        redmineTimeEntry = timeEntryManager.getTimeEntry(timesheetLine.getRedmineId());
      } catch (RedmineException e) {
        return;
      }
    }

    if (redmineTimeEntry == null) {
      redmineTimeEntry = new TimeEntry(redmineTransport);
    } else {
      LocalDateTime timesheetLineUpdatedOn = timesheetLine.getUpdatedOn();
      LocalDateTime redmineTimeEntryUpdatedOn = getRedmineDate(redmineTimeEntry.getUpdatedOn());

      if (timesheetLineUpdatedOn != null
          && redmineTimeEntryUpdatedOn.isAfter(timesheetLineUpdatedOn)) {
        return;
      }
    }

    String emailAddress = aosEmployeeEmailMap.get(timesheetLine.getEmployee().getId());
    redmineTimeEntry.setUserId(redmineUserEmailMap.get(emailAddress));
    redmineTransport.setOnBehalfOfUser(redmineUserLoginMap.get(emailAddress));
    redmineTimeEntry.setProjectId(timesheetLine.getProject().getRedmineId());
    redmineTimeEntry.setIssueId(timesheetLine.getProjectTask().getRedmineId());

    Integer redmineActivityId =
        redmineTimeEntryActivityMap.get(fieldMap.get(timesheetLine.getActivityTypeSelect()));
    redmineTimeEntry.setActivityId(redmineActivityId);

    redmineTimeEntry.setComment(timesheetLine.getComments());
    redmineTimeEntry.setSpentOn(
        Date.from(timesheetLine.getDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));
    redmineTimeEntry.setHours(timesheetLine.getHoursDuration().floatValue());

    BigDecimal durationForCustomer = timesheetLine.getDurationForCustomer();
    String value =
        durationForCustomer != null && durationForCustomer.compareTo(BigDecimal.ZERO) != 0
            ? durationForCustomer.toString()
            : null;

    Set<CustomField> cfSet = redmineTimeEntry.getCustomFields();
    String cfName = "Temps final";
    CustomField customField =
        CollectionUtils.isNotEmpty(cfSet)
            ? cfSet.stream().filter(cf -> cf.getName().equals(cfName)).findAny().get()
            : null;
    if (customField != null && !customField.getValue().equals(value)) {
      customField.setValue(value);
      redmineTimeEntry.addCustomFields(cfSet);
    }

    try {
      createOrUpdateRedmineTimeEntry(redmineTimeEntry, timesheetLine);
    } catch (Exception e) {
      return;
    }
  }

  @Transactional
  public void createOrUpdateRedmineTimeEntry(
      TimeEntry redmineTimeEntry, TimesheetLine timesheetLine) throws RedmineException {
    try {
      if (redmineTimeEntry.getId() == null) {
        redmineTimeEntry = redmineTimeEntry.create();
        timesheetLine.setRedmineId(redmineTimeEntry.getId());
        timesheetLine.addCreatedExportBatchSetItem(batch);
      } else {
        redmineTimeEntry.update();
        timesheetLine.addUpdatedExportBatchSetItem(batch);
      }

      timesheetLineRepo.save(timesheetLine);

      onSuccess.accept(timesheetLine);
      success++;
    } catch (Exception e) {
      onError.accept(e);
      fail++;
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public void setDefaultFieldsAndMaps() {
    serverTimeZone = appRedmine.getServerTimezone();
    issueManager = redmineManager.getIssueManager();
    projectManager = redmineManager.getProjectManager();

    redmineProjectIdValidationMap.put(0, Boolean.FALSE);
    redmineIssueIdValidationMap.put(0, Boolean.FALSE);

    try {
      List<TimeEntryActivity> redmineTimeEntryActivities =
          timeEntryManager.getTimeEntryActivities();

      if (CollectionUtils.isNotEmpty(redmineTimeEntryActivities)) {

        for (TimeEntryActivity redmineTimeEntryActivity : redmineTimeEntryActivities) {
          redmineTimeEntryActivityMap.put(
              redmineTimeEntryActivity.getName(), redmineTimeEntryActivity.getId());
        }
      }
    } catch (RedmineException e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }

    List<RedmineImportMapping> redmineImportMappingList =
        redmineImportMappingRepo
            .all()
            .filter(
                "self.redmineImportConfig.redmineMappingFieldSelect in (?1)",
                RedmineImportConfigRepository.MAPPING_FIELD_TIMESHEETLINE_ACTIVITY)
            .fetch();

    for (RedmineImportMapping redmineImportMapping : redmineImportMappingList) {
      fieldMap.put(redmineImportMapping.getOsValue(), redmineImportMapping.getRedmineValue());
    }
  }

  public List<TimesheetLine> fetchFailedAosTimesheetLineList(String failedAosTimesheetLineIds) {
    List<TimesheetLine> timesheetLineList = new ArrayList<>();

    if (!StringUtils.isEmpty(failedAosTimesheetLineIds)) {
      long[] failedIds =
          Arrays.asList(failedAosTimesheetLineIds.split(",")).stream()
              .map(String::trim)
              .mapToLong(Long::parseLong)
              .toArray();

      for (long id : failedIds) {
        TimesheetLine timesheetLine = timesheetLineRepo.find(id);
        timesheetLineList.add(timesheetLine);
      }
    }
    return timesheetLineList;
  }

  public String getEmailAddress(Employee employee) {
    EmailAddress emailAddress = null;
    User user = employee.getUser();

    if (employee != null
        && employee.getContactPartner() != null
        && employee.getContactPartner().getEmailAddress() != null) {
      emailAddress = employee.getContactPartner().getEmailAddress();

    } else if (user != null) {
      if (user.getPartner() != null && user.getPartner().getEmailAddress() != null) {
        emailAddress = user.getPartner().getEmailAddress();
      } else if (!Strings.isNullOrEmpty(user.getEmail())) {
        emailAddress = emailAddressRepo.findByAddress(user.getEmail());
      }
    }
    return emailAddress != null ? emailAddress.getAddress() : null;
  }

  public void setErrorLog(String message, String timesheetLineId) {
    errorObjList.add(
        new Object[] {
          I18n.get(IMessage.REDMINE_EXPORT_TIMESHEET_LINE_ERROR),
          String.valueOf(timesheetLineId),
          I18n.get(message)
        });
  }
}
