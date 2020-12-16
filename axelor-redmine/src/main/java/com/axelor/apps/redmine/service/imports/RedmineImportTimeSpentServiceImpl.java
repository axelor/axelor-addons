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
package com.axelor.apps.redmine.service.imports;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.db.repo.UnitRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.service.timesheet.TimesheetLineService;
import com.axelor.apps.hr.service.timesheet.TimesheetService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.redmine.db.RedmineImportMapping;
import com.axelor.apps.redmine.db.repo.RedmineImportConfigRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaStore;
import com.axelor.meta.schema.views.Selection.Option;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.TimeEntryManager;
import com.taskadapter.redmineapi.bean.TimeEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class RedmineImportTimeSpentServiceImpl extends RedmineImportCommonService
    implements RedmineImportTimeSpentService {

  protected ProductRepository productRepo;
  protected TeamTaskRepository teamTaskRepo;
  protected CompanyRepository companyRepo;
  protected TimesheetLineRepository timesheetLineRepo;
  protected TimesheetRepository timesheetRepo;
  protected TimesheetService timesheetService;
  protected UnitRepository unitRepo;
  protected TimesheetLineService timesheetLineService;

  @Inject
  public RedmineImportTimeSpentServiceImpl(
      UserRepository userRepo,
      ProjectRepository projectRepo,
      RedmineImportMappingRepository redmineImportMappingRepo,
      AppBaseService appBaseService,
      ProductRepository productRepo,
      TeamTaskRepository teamTaskRepo,
      CompanyRepository companyRepo,
      TimesheetLineRepository timesheetLineRepo,
      TimesheetRepository timesheetRepo,
      TimesheetService timesheetService,
      UnitRepository unitRepo,
      TimesheetLineService timesheetLineService) {

    super(userRepo, projectRepo, redmineImportMappingRepo, appBaseService);

    this.productRepo = productRepo;
    this.teamTaskRepo = teamTaskRepo;
    this.companyRepo = companyRepo;
    this.timesheetLineRepo = timesheetLineRepo;
    this.timesheetRepo = timesheetRepo;
    this.timesheetService = timesheetService;
    this.unitRepo = unitRepo;
    this.timesheetLineService = timesheetLineService;
  }

  protected TimeEntryManager timeEntryManager;

  protected String redmineTimeSpentProduct;
  protected String redmineTimeSpentDurationForCustomer;
  protected String redmineTimeSpentDurationUnit;
  protected String unitHoursName;
  protected String redmineTimeSpentProductDefault;
  protected String redmineTimeSpentDurationUnitDefault;
  protected Long defaultCompanyId;

  protected User user;
  protected Project project;
  protected TeamTask teamTask;

  protected List<Integer> redmineTimeEntryIdList = new ArrayList<>();

  @Override
  public String redmineTimeEntriesImportProcess(
      RedmineManager redmineManager,
      ZonedDateTime lastBatchEndDate,
      AppRedmine appRedmine,
      Map<Integer, String> redmineUserMap,
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
    this.redmineUserMap = redmineUserMap;

    try {
      prepareParamsAndImportRedmineTimeEntries(lastBatchEndDate);
    } catch (RedmineException e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }

    updateTransaction();

    String resultStr =
        String.format(
            "Redmine SpentTime -> AOS Timesheetline : Success: %d Fail: %d", success, fail);
    LOG.debug(resultStr);
    success = fail = 0;

    return resultStr;
  }

  public void prepareParamsAndImportRedmineTimeEntries(ZonedDateTime lastBatchEndDate)
      throws RedmineException {

    LOG.debug(
        "Preparing params, managers, maps and other variables for time entries import process..");

    Map<String, String> params = prepareParams(lastBatchEndDate);

    timeEntryManager = redmineManager.getTimeEntryManager();

    List<TimeEntry> failedRedmineTimeEntries =
        fetchFailedRedmineTimeEntryList(batch.getRedmineBatch().getFailedRedmineTimeEntriesIds());

    setDefaultFieldsAndMaps();

    String newFailedRedmineTimeEntriesIds = null;

    LOG.debug("Start fetching and importing time entries..");

    newFailedRedmineTimeEntriesIds =
        importRedmineTimeEntries(params, newFailedRedmineTimeEntriesIds);

    if (CollectionUtils.isNotEmpty(failedRedmineTimeEntries)) {

      LOG.debug("Start fetching and importing failed time entries from previous batch run..");

      newFailedRedmineTimeEntriesIds =
          importRedmineTimeEntries(failedRedmineTimeEntries, newFailedRedmineTimeEntriesIds);
    }

    batch.getRedmineBatch().setFailedRedmineTimeEntriesIds(newFailedRedmineTimeEntriesIds);
  }

  public String importRedmineTimeEntries(
      Map<String, String> params, String newFailedRedmineTimeEntriesIds) throws RedmineException {

    totalFetchCount = 0;

    LOG.debug("Fetching time entries from redmine as per fetch limit..");

    List<TimeEntry> redmineTimeEntryList = fetchTimeEntryList(params);

    if (CollectionUtils.isNotEmpty(redmineTimeEntryList)) {

      do {
        newFailedRedmineTimeEntriesIds =
            importRedmineTimeEntries(redmineTimeEntryList, newFailedRedmineTimeEntriesIds);

        if (!updatedOnMap.isEmpty()) {
          setUpdatedOnFromRedmine();
        }

        LOG.debug("Fetching time entries from redmine as per fetch limit..");

        redmineTimeEntryList = fetchTimeEntryList(params);
      } while (CollectionUtils.isNotEmpty(redmineTimeEntryList));
    }

    return newFailedRedmineTimeEntriesIds;
  }

  public String importRedmineTimeEntries(
      List<TimeEntry> redmineTimeEntryList, String newFailedRedmineTimeEntriesIds) {

    Comparator<TimeEntry> compareByDate =
        (TimeEntry o1, TimeEntry o2) -> o1.getSpentOn().compareTo(o2.getSpentOn());
    Collections.sort(redmineTimeEntryList, compareByDate);

    for (TimeEntry redmineTimeEntry : redmineTimeEntryList) {

      LOG.debug("Importing time entry: {}", redmineTimeEntry.getId());

      setRedmineCustomFieldsMap(redmineTimeEntry.getCustomFields());

      redmineTimeEntryIdList.add(redmineTimeEntry.getId());

      if (!validateRequiredFields(
          redmineTimeEntry.getId(),
          redmineTimeEntry.getUserId(),
          redmineTimeEntry.getProjectId(),
          redmineTimeEntry.getIssueId())) {
        newFailedRedmineTimeEntriesIds =
            StringUtils.isEmpty(newFailedRedmineTimeEntriesIds)
                ? String.valueOf(redmineTimeEntry.getId())
                : newFailedRedmineTimeEntriesIds + "," + redmineTimeEntry.getId();
        fail++;
        continue;
      }

      try {
        importRedmineTimeSpent(redmineTimeEntry);
      } finally {
        updateTransaction();
      }
    }

    return newFailedRedmineTimeEntriesIds;
  }

  public boolean validateRequiredFields(
      Integer redmineTimeEntryId,
      Integer redmineTimeEntryUserId,
      Integer redmineTimeEntryProjectId,
      Integer redmineTimeEntryIssueId) {

    user = getAosUser(redmineTimeEntryUserId);

    if (user == null) {
      setErrorLog(IMessage.REDMINE_IMPORT_USER_NOT_FOUND, redmineTimeEntryId);
      return false;
    }

    project = projectRepo.findByRedmineId(redmineTimeEntryProjectId);

    if (project == null) {
      setErrorLog(IMessage.REDMINE_IMPORT_PROJECT_NOT_FOUND, redmineTimeEntryId);
      return false;
    }

    if (redmineTimeEntryIssueId != null) {
      teamTask = teamTaskRepo.findByRedmineId(redmineTimeEntryIssueId);

      if (teamTask == null) {
        setErrorLog(IMessage.REDMINE_IMPORT_TEAM_TASK_NOT_FOUND, redmineTimeEntryId);
        return false;
      }
    } else {
      teamTask = null;
    }

    return true;
  }

  @Transactional
  public void importRedmineTimeSpent(TimeEntry redmineTimeEntry) {

    TimesheetLine timesheetLine = timesheetLineRepo.findByRedmineId(redmineTimeEntry.getId());
    LocalDateTime redmineUpdatedOn =
        redmineTimeEntry
            .getUpdatedOn()
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();

    if (timesheetLine == null) {
      timesheetLine = new TimesheetLine();
    } else if (lastBatchEndDate != null
        && (redmineUpdatedOn.isBefore(lastBatchEndDate)
            || (timesheetLine.getUpdatedOn().isAfter(lastBatchEndDate)
                && timesheetLine.getUpdatedOn().isAfter(redmineUpdatedOn)))) {
      return;
    }

    setTimesheetLineFields(timesheetLine, redmineTimeEntry);

    try {

      if (timesheetLine.getId() == null) {
        timesheetLine.addCreatedBatchSetItem(batch);
      } else {
        timesheetLine.addUpdatedBatchSetItem(batch);
      }

      timesheetLineRepo.save(timesheetLine);

      updatedOnMap.put(timesheetLine.getId(), redmineUpdatedOn);

      onSuccess.accept(timesheetLine);
      success++;
    } catch (Exception e) {
      onError.accept(e);
      fail++;
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  @Transactional
  public void setTimesheetLineFields(TimesheetLine timesheetLine, TimeEntry redmineTimeEntry) {

    timesheetLine.setUser(user);
    timesheetLine.setRedmineId(redmineTimeEntry.getId());
    timesheetLine.setProject(project);
    timesheetLine.setTeamTask(teamTask);
    timesheetLine.setComments(redmineTimeEntry.getComment());

    BigDecimal duration = BigDecimal.valueOf(redmineTimeEntry.getHours());
    timesheetLine.setHoursDuration(duration);

    LocalDate redmineSpentOn =
        redmineTimeEntry.getSpentOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    timesheetLine.setDate(redmineSpentOn);

    String value = redmineCustomFieldsMap.get(redmineTimeSpentDurationForCustomer);
    timesheetLine.setDurationForCustomer(
        StringUtils.isNotEmpty(value) ? new BigDecimal(value) : duration);

    value = redmineCustomFieldsMap.get(redmineTimeSpentDurationUnit);

    Unit unit = null;

    if (StringUtils.isNotEmpty(value)) {
      unit = unitRepo.findByName(value);
    }

    if (StringUtils.isNotEmpty(redmineCustomFieldsMap.get(redmineTimeSpentProduct))) {
      value = redmineCustomFieldsMap.get(redmineTimeSpentProduct);
    } else if (user.getEmployee() != null && user.getEmployee().getProduct() != null) {
      value = user.getEmployee().getProduct().getCode();
    } else {
      value = redmineTimeSpentProductDefault;
    }

    Product product = StringUtils.isNotEmpty(value) ? productRepo.findByCode(value) : null;

    if (product == null) {
      setErrorLog(I18n.get(IMessage.REDMINE_IMPORT_PRODUCT_NOT_FOUND), redmineTimeEntry.getId());
    }

    timesheetLine.setProduct(product);

    if (unit != null) {
      timesheetLine.setDurationUnit(unit);
    } else {
      unit = unitRepo.findByName(redmineTimeSpentDurationUnitDefault);
      timesheetLine.setDurationUnit(unit != null ? unit : unitRepo.findByName(unitHoursName));
    }

    String activityType = redmineTimeEntry.getActivityName();
    timesheetLine.setActivityTypeSelect(
        activityType != null && !activityType.isEmpty()
            ? (String) selectionMap.get(activityType)
            : null);

    Timesheet timesheet =
        timesheetRepo
            .all()
            .filter(
                "self.user = ?1 AND self.statusSelect != ?2",
                user,
                TimesheetRepository.STATUS_CANCELED)
            .order("-id")
            .fetchOne();

    if (timesheet == null) {
      timesheet = timesheetService.createTimesheet(user, redmineSpentOn, null);
      timesheet.setCompany(
          user.getActiveCompany() != null
              ? user.getActiveCompany()
              : companyRepo.find(defaultCompanyId));
      timesheet.setToDate(redmineSpentOn);
    } else {
      if (timesheet.getFromDate() == null || timesheet.getFromDate().isAfter(redmineSpentOn)) {
        timesheet.setFromDate(redmineSpentOn);
      }
      if (timesheet.getToDate() == null || timesheet.getToDate().isBefore(redmineSpentOn)) {
        timesheet.setToDate(redmineSpentOn);
      }
    }

    timesheet.setStatusSelect(TimesheetRepository.STATUS_VALIDATED);

    try {
      timesheetLine.setDuration(
          timesheetLineService.computeHoursDuration(timesheet, duration, false));
    } catch (AxelorException e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }

    timesheet.setPeriodTotal(timesheet.getPeriodTotal().add(timesheetLine.getHoursDuration()));
    timesheetLine.setTimesheet(timesheet);

    setCreatedByUser(timesheetLine, user, "setCreatedBy");
    setLocalDateTime(timesheetLine, redmineTimeEntry.getCreatedOn(), "setCreatedOn");
  }

  public Map<String, String> prepareParams(ZonedDateTime lastBatchEndDate) {

    Map<String, String> params = new HashMap<>();

    if (lastBatchEndDate != null) {
      params.put("set_filter", "1");
      params.put("f[]", "updated_on");
      params.put("op[updated_on]", ">=");
      params.put(
          "v[updated_on][]",
          lastBatchEndDate.withZoneSameInstant(ZoneOffset.UTC).withNano(0).toString());
    }

    return params;
  }

  public List<TimeEntry> fetchTimeEntryList(Map<String, String> params) throws RedmineException {

    List<TimeEntry> importTimeEntryList = new ArrayList<>();
    List<TimeEntry> tempImportTimeEntryList;

    Integer limit = redmineMaxFetchLimit;
    Integer remainingFetchItems = redmineFetchLimit;

    do {

      if (remainingFetchItems > redmineMaxFetchLimit) {
        remainingFetchItems = remainingFetchItems - redmineMaxFetchLimit;
      } else {
        limit = remainingFetchItems;
        remainingFetchItems = 0;
      }

      params.put("offset", totalFetchCount.toString());
      params.put("limit", limit.toString());

      tempImportTimeEntryList = timeEntryManager.getTimeEntries(params).getResults();

      if (CollectionUtils.isNotEmpty(tempImportTimeEntryList)) {
        importTimeEntryList.addAll(tempImportTimeEntryList);
        totalFetchCount += tempImportTimeEntryList.size();
      } else {
        remainingFetchItems = 0;
      }
    } while (remainingFetchItems != 0);

    return importTimeEntryList;
  }

  public List<TimeEntry> fetchFailedRedmineTimeEntryList(String failedRedmineTimeEntriesIds) {

    List<TimeEntry> importTimeEntryList = new ArrayList<>();

    if (!StringUtils.isEmpty(failedRedmineTimeEntriesIds)) {
      int[] failedIds =
          Arrays.asList(failedRedmineTimeEntriesIds.split(",")).stream()
              .map(String::trim)
              .mapToInt(Integer::parseInt)
              .toArray();

      for (int id : failedIds) {

        if (!redmineTimeEntryIdList.contains(id)) {

          try {
            TimeEntry timeEntry = timeEntryManager.getTimeEntry(id);
            importTimeEntryList.add(timeEntry);
          } catch (RedmineException e) {
            onError.accept(e);
            TraceBackService.trace(e);
          }
        }
      }
    }

    return importTimeEntryList;
  }

  public void setDefaultFieldsAndMaps() {

    redmineTimeSpentProduct = appRedmine.getRedmineTimeSpentProduct();
    redmineTimeSpentDurationForCustomer = appRedmine.getRedmineTimeSpentDurationForCustomer();
    redmineTimeSpentDurationUnit = appRedmine.getRedmineTimeSpentDurationUnit();
    redmineTimeSpentProductDefault = appRedmine.getRedmineTimeSpentProductDefault();
    redmineTimeSpentDurationUnitDefault = appRedmine.getRedmineTimeSpentDurationUnitDefault();
    defaultCompanyId = appRedmine.getCompany().getId();

    redmineFetchLimit = batch.getRedmineBatch().getRedmineFetchLimit();

    Unit unitHours = appBaseService.getAppBase().getUnitHours();
    unitHoursName = unitHours != null ? unitHours.getName() : null;

    List<Option> selectionList = new ArrayList<>();
    selectionList.addAll(MetaStore.getSelectionList("redmine.timesheetline.activity.type.select"));

    ResourceBundle fr = I18n.getBundle(Locale.FRANCE);
    ResourceBundle en = I18n.getBundle(Locale.ENGLISH);

    for (Option option : selectionList) {
      selectionMap.put(fr.getString(option.getTitle()), option.getValue());
      selectionMap.put(en.getString(option.getTitle()), option.getValue());
    }

    List<RedmineImportMapping> redmineImportMappingList =
        redmineImportMappingRepo
            .all()
            .filter(
                "self.redmineImportConfig.redmineMappingFieldSelect = ?1",
                RedmineImportConfigRepository.MAPPING_FIELD_TIMESHEETLINE_ACTIVITY)
            .fetch();

    for (RedmineImportMapping redmineImportMapping : redmineImportMappingList) {
      fieldMap.put(redmineImportMapping.getRedmineValue(), redmineImportMapping.getOsValue());
    }
  }

  public void setUpdatedOnFromRedmine() {

    LOG.debug("Setting updated on time of imported records as per redmine..");

    if (!JPA.em().getTransaction().isActive()) {
      JPA.em().getTransaction().begin();
    }

    String values =
        updatedOnMap.entrySet().stream()
            .map(
                entry ->
                    "("
                        + entry.getKey()
                        + ",TO_TIMESTAMP('"
                        + entry.getValue()
                        + "', 'YYYY-MM-DD HH24:MI:SS'))")
            .collect(Collectors.joining(","));

    String query =
        String.format(
            "UPDATE hr_timesheet_line as timesheetline SET updated_on = v.updated_on from (values %s) as v(id,updated_on) where timesheetline.id = v.id",
            values);

    JPA.em().createNativeQuery(query).executeUpdate();
    JPA.em().getTransaction().commit();

    updatedOnMap = new HashMap<>();
  }

  public void setErrorLog(String message, Integer redmineTimeSpentId) {

    errorObjList.add(
        new Object[] {
          I18n.get(IMessage.REDMINE_IMPORT_TIMESHEET_LINE_ERROR),
          String.valueOf(redmineTimeSpentId),
          I18n.get(message)
        });
  }
}
