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
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.ProjectTaskCategoryRepository;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.RedmineImportMapping;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineExportTimeSpentServiceImpl extends RedmineCommonService
    implements RedmineExportTimeSpentService {

  protected TimesheetLineRepository timesheetLineRepo;
  protected EmailAddressRepository emailAddressRepo;
  protected RedmineImportMappingRepository redmineImportMappingRepo;

  @Inject
  public RedmineExportTimeSpentServiceImpl(
      UserRepository userRepo,
      EmployeeRepository employeeRepo,
      ProjectRepository projectRepo,
      ProductRepository productRepo,
      ProjectTaskRepository projectTaskRepo,
      ProjectTaskCategoryRepository projectCategoryRepo,
      PartnerRepository partnerRepo,
      AppRedmineRepository appRedmineRepo,
      CompanyRepository companyRepo,
      TimesheetLineRepository timesheetLineRepo,
      EmailAddressRepository emailAddressRepo,
      RedmineImportMappingRepository redmineImportMappingRepo) {

    super(
        userRepo,
        employeeRepo,
        projectRepo,
        productRepo,
        projectTaskRepo,
        projectCategoryRepo,
        partnerRepo,
        appRedmineRepo,
        companyRepo);

    this.timesheetLineRepo = timesheetLineRepo;
    this.emailAddressRepo = emailAddressRepo;
    this.redmineImportMappingRepo = redmineImportMappingRepo;
  }

  Logger exportLog = LoggerFactory.getLogger(getClass());

  protected HashMap<String, Integer> redmineTimeEntryActivityMap = new HashMap<>();
  protected HashMap<String, Integer> redmineUserEmailMap = new HashMap<>();
  protected HashMap<Long, String> aosEmployeeEmailMap = new HashMap<>();
  protected HashMap<String, String> redmineUserLoginMap = new HashMap<>();

  protected HashMap<Integer, Boolean> redmineProjectIdValidationMap = new HashMap<>();
  protected HashMap<Integer, Boolean> redmineIssueIdValidationMap = new HashMap<>();

  protected TimeEntryManager redmineTimeEntryManager;

  protected String failedAosTimesheetLineIds;

  @Override
  public String exportTimesheetLines(Map<String, Object> paramsMap) {

    RedmineManager redmineManager = (RedmineManager) paramsMap.get("redmineManager");

    setDefaultValuesAndMaps(redmineManager, paramsMap);

    RedmineBatch redmineBatch = methodParameters.getBatch().getRedmineBatch();

    List<Long> failedAosTimesheetLineIdList =
        Stream.of(
                (!StringUtils.isBlank(redmineBatch.getFailedAosTimesheetLineIds())
                        ? redmineBatch.getFailedAosTimesheetLineIds()
                        : "0")
                    .split(","))
            .map(Long::parseLong)
            .collect(Collectors.toList());
    String baseFilter =
        Boolean.FALSE.equals(redmineBatch.getIsOverrideRecords())
            ? "self.project != null AND (self.redmineId = null OR self.redmineId = 0)"
            : "self.project != null";

    Query<TimesheetLine> query =
        methodParameters.getLastBatchUpdatedOn() != null
            ? timesheetLineRepo
                .all()
                .filter(
                    "(" + baseFilter + " AND self.updatedOn > ?1) OR self.id IN (?2)",
                    methodParameters.getLastBatchUpdatedOn(),
                    failedAosTimesheetLineIdList)
                .order("id")
            : timesheetLineRepo.all().filter(baseFilter).order("id");

    int offset = 0;
    List<TimesheetLine> timesheetLineList;

    while (!(timesheetLineList = query.fetch(AbstractBatch.FETCH_LIMIT, offset)).isEmpty()) {
      offset += timesheetLineList.size();

      for (TimesheetLine timesheetLine : timesheetLineList) {

        try {
          redmineManager.setOnBehalfOfUser(null);
          exportTimesheetLine(timesheetLine, redmineManager.getTransport(), redmineBatch);
        } catch (Exception e) {
          failedAosTimesheetLineIds =
              failedAosTimesheetLineIds == null
                  ? timesheetLine.getId().toString()
                  : failedAosTimesheetLineIds + "," + timesheetLine.getId().toString();
          TraceBackService.trace(e, "", methodParameters.getBatch().getId());
          methodParameters.getOnError().accept(e);
        }
      }

      updateTransaction();
    }

    String resultStr =
        String.format(
            "AOS Timesheetline -> Redmine SpentTime : Success: %d Fail: %d", success, fail);
    setResult(getResult() + String.format("%s %n", resultStr));
    exportLog.debug(resultStr);
    success = fail = 0;

    return failedAosTimesheetLineIds;
  }

  @SuppressWarnings("unchecked")
  public void setDefaultValuesAndMaps(
      RedmineManager redmineManager, Map<String, Object> paramsMap) {

    methodParameters.setLastBatchUpdatedOn((LocalDateTime) paramsMap.get("lastBatchUpdatedOn"));
    redmineUserLoginMap = (HashMap<String, String>) paramsMap.get("redmineUserLoginMap");
    redmineUserEmailMap =
        (HashMap<String, Integer>)
            MapUtils.invertMap((HashMap<Integer, String>) paramsMap.get("redmineUserMap"));
    methodParameters.setOnError ((Consumer<Throwable>) paramsMap.get("onError"));
    methodParameters.setOnSuccess((Consumer<Object>) paramsMap.get("onSuccess"));
    methodParameters.setBatch((Batch) paramsMap.get("batch"));
    methodParameters.setErrorObjList((List<Object[]>) paramsMap.get("errorObjList"));

    serverTimeZone = appRedmineRepo.all().fetchOne().getServerTimezone();

    redmineTimeEntryManager = redmineManager.getTimeEntryManager();
    redmineIssueManager = redmineManager.getIssueManager();
    methodParameters.setRedmineManager(redmineManager);

    redmineProjectIdValidationMap.put(0, Boolean.FALSE);
    redmineIssueIdValidationMap.put(0, Boolean.FALSE);

    try {
      List<TimeEntryActivity> redmineTimeEntryActivities =
          redmineTimeEntryManager.getTimeEntryActivities();

      if (CollectionUtils.isNotEmpty(redmineTimeEntryActivities)) {

        for (TimeEntryActivity redmineTimeEntryActivity : redmineTimeEntryActivities) {
          redmineTimeEntryActivityMap.put(
              redmineTimeEntryActivity.getName(), redmineTimeEntryActivity.getId());
        }
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", methodParameters.getBatch().getId());
      methodParameters.getOnError().accept(e);
    }

    fieldMap = new HashMap<>();

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

  public void exportTimesheetLine(
      TimesheetLine timesheetLine, Transport redmineTransport, RedmineBatch redmineBatch)
      throws RedmineException {

    exportLog.debug("Exporting timesheetline: {}", timesheetLine.getId());

    TimeEntry redmineTimeEntry = null;

    if (timesheetLine.getRedmineId() != 0) {

      try {
        redmineTimeEntry = redmineTimeEntryManager.getTimeEntry(timesheetLine.getRedmineId());
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

    prepareRedmineTimeEntry(redmineTimeEntry, timesheetLine, redmineBatch, redmineTransport);
  }

  public void prepareRedmineTimeEntry(
      TimeEntry redmineTimeEntry,
      TimesheetLine timesheetLine,
      RedmineBatch redmineBatch,
      Transport redmineTransport)
      throws RedmineException {

    String emailAddress = aosEmployeeEmailMap.get(timesheetLine.getEmployee().getId());

    if (StringUtils.isEmpty(emailAddress)) {
      emailAddress = getEmailAddress(timesheetLine.getEmployee());
      aosEmployeeEmailMap.put(timesheetLine.getEmployee().getId(), emailAddress);

      if (StringUtils.isEmpty(emailAddress)) {
        setErrorLog(
            IMessage.REDMINE_EXPORT_TIMESHEET_LINE_AOS_EMPLOYEE_EMAIL_NOT_CONFIGURED,
            redmineBatch,
            timesheetLine.getId().toString());
        fail++;
        return;
      }
    }

    if (!redmineUserEmailMap.containsKey(emailAddress)) {
      setErrorLog(
          IMessage.REDMINE_EXPORT_TIMESHEET_LINE_REDMINE_EMPLOYEE_NOT_FOUND,
          redmineBatch,
          timesheetLine.getId().toString());
      fail++;
      return;
    }

    Integer redmineProjectId = timesheetLine.getProject().getRedmineId();

    if (!redmineProjectIdValidationMap.containsKey(redmineProjectId)) {

      try {
        redmineProjectIdValidationMap.put(
            methodParameters.getRedmineManager().getProjectManager().getProjectById(redmineProjectId).getId(), Boolean.TRUE);
      } catch (RedmineException e) {
        redmineProjectIdValidationMap.put(redmineProjectId, Boolean.FALSE);
      }
    }

    if (Boolean.FALSE.equals(redmineProjectIdValidationMap.get(redmineProjectId))) {
      setErrorLog(
          IMessage.REDMINE_EXPORT_TIMESHEET_LINE_PROJECT_NOT_FOUND,
          redmineBatch,
          timesheetLine.getId().toString());
      fail++;
      return;
    }

    Integer redmineIssueId = null;

    if (timesheetLine.getProjectTask() != null) {
      redmineIssueId = timesheetLine.getProjectTask().getRedmineId();

      if (!redmineIssueIdValidationMap.containsKey(redmineIssueId)) {

        try {
          redmineIssueIdValidationMap.put(
              redmineIssueManager.getIssueById(redmineIssueId).getId(), Boolean.TRUE);
        } catch (RedmineException e) {
          redmineIssueIdValidationMap.put(redmineIssueId, Boolean.FALSE);
        }
      }

      if (Boolean.FALSE.equals(redmineIssueIdValidationMap.get(redmineIssueId))) {
        setErrorLog(
            IMessage.REDMINE_EXPORT_TIMESHEET_LINE_ISSUE_NOT_FOUND,
            redmineBatch,
            timesheetLine.getId().toString());
        fail++;
        return;
      }
    }

    Integer redmineActivityId =
        redmineTimeEntryActivityMap.get(fieldMap.get(timesheetLine.getActivityTypeSelect()));

    if (redmineActivityId == null) {
      setErrorLog(
          IMessage.REDMINE_EXPORT_TIMESHEET_LINE_ACTIVITY_NOT_FOUND,
          redmineBatch,
          timesheetLine.getId().toString());
      fail++;
      return;
    }

    redmineTimeEntry.setUserId(redmineUserEmailMap.get(emailAddress));
    redmineTransport.setOnBehalfOfUser(redmineUserLoginMap.get(emailAddress));
    redmineTimeEntry.setProjectId(redmineProjectId);
    redmineTimeEntry.setIssueId(redmineIssueId);
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
    String cfName = "Temps passé ajusté client";
    CustomField customField =
        cfSet.stream().filter(cf -> cf.getName().equals(cfName)).findAny().get();
    if (!customField.getValue().equals(value)) {
      customField.setValue(value);
      redmineTimeEntry.addCustomFields(cfSet);
    }

    createOrUpdateRedmineTimeEntry(redmineTimeEntry, timesheetLine);

    success++;
    methodParameters.getOnSuccess().accept(timesheetLine);
  }

  @Transactional
  public void createOrUpdateRedmineTimeEntry(
      TimeEntry redmineTimeEntry, TimesheetLine timesheetLine) throws RedmineException {

    if (redmineTimeEntry.getId() == null) {
      redmineTimeEntry = redmineTimeEntry.create();
      timesheetLine.setRedmineId(redmineTimeEntry.getId());
      timesheetLine.addCreatedExportBatchSetItem(methodParameters.getBatch());
    } else {
      redmineTimeEntry.update();
      timesheetLine.addUpdatedExportBatchSetItem(methodParameters.getBatch());
    }

    timesheetLineRepo.save(timesheetLine);
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

  public void setErrorLog(String message, RedmineBatch redmineBatch, String timesheetLineId) {

    errors = new Object[] {I18n.get(message)};

    failedAosTimesheetLineIds =
        failedAosTimesheetLineIds == null
            ? timesheetLineId
            : failedAosTimesheetLineIds + "," + timesheetLineId;

    setErrorLog(I18n.get(IMessage.REDMINE_EXPORT_TIMESHEET_LINE_ERROR), timesheetLineId);
  }
}
