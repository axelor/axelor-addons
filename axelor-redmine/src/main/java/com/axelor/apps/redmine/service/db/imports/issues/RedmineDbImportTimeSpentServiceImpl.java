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
package com.axelor.apps.redmine.service.db.imports.issues;

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
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.redmine.db.RedmineImportMapping;
import com.axelor.apps.redmine.db.repo.RedmineImportConfigRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.redmine.service.db.imports.RedmineDbImportCommonService;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaStore;
import com.axelor.meta.schema.views.Selection.Option;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class RedmineDbImportTimeSpentServiceImpl extends RedmineDbImportCommonService
    implements RedmineDbImportTimeSpentService {

  protected TeamTaskRepository teamTaskRepo;
  protected TimesheetLineRepository timesheetLineRepo;
  protected TimesheetRepository timesheetRepo;
  protected TimesheetService timesheetService;
  protected CompanyRepository companyRepo;
  protected TimesheetLineService timesheetLineService;
  protected UnitRepository unitRepo;
  protected ProductRepository productRepo;

  @Inject
  public RedmineDbImportTimeSpentServiceImpl(
      UserRepository userRepo,
      TeamTaskRepository teamTaskRepo,
      ProjectRepository projectRepo,
      AppBaseService appBaseService,
      TimesheetLineRepository timesheetLineRepo,
      TimesheetRepository timesheetRepo,
      TimesheetService timesheetService,
      CompanyRepository companyRepo,
      TimesheetLineService timesheetLineService,
      UnitRepository unitRepo,
      ProductRepository productRepo,
      RedmineImportMappingRepository redmineImportMappingRepo) {

    super(userRepo, projectRepo, redmineImportMappingRepo, appBaseService);

    this.teamTaskRepo = teamTaskRepo;
    this.timesheetLineRepo = timesheetLineRepo;
    this.timesheetRepo = timesheetRepo;
    this.timesheetService = timesheetService;
    this.companyRepo = companyRepo;
    this.timesheetLineService = timesheetLineService;
    this.unitRepo = unitRepo;
    this.productRepo = productRepo;
  }

  @Override
  public String redmineTimeEntriesImportProcess(
      Connection connection,
      ZonedDateTime lastBatchEndDate,
      AppRedmine appRedmine,
      Batch batch,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError,
      List<Object[]> errorObjList) {

    this.onError = onError;
    this.onSuccess = onSuccess;
    this.appRedmine = appRedmine;
    this.batch = batch;
    this.errorObjList = errorObjList;

    setDefaultFieldsAndMaps(appRedmine);

    importRedmineTimeSpents(
        prepareTimeEntryPsqlQuery(lastBatchEndDate),
        connection,
        lastBatchEndDate != null ? lastBatchEndDate.toLocalDateTime() : null);

    String resultStr =
        String.format(
            "Redmine SpentTime -> AOS Timesheetline : Success: %d Fail: %d", success, fail);
    LOG.debug(resultStr);
    success = fail = 0;

    return resultStr;
  }

  public void setDefaultFieldsAndMaps(AppRedmine appRedmine) {

    LOG.debug("Set required default fields and maps for redmine time entries import..");

    serverTimeZone = appRedmine.getRedmineServerTimeZone();

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

  public String prepareTimeEntryPsqlQuery(ZonedDateTime lastBatchEndDate) {

    LOG.debug("Prepare PSQL queries to import redmine time entries..");

    String whereClause = "";

    if (lastBatchEndDate != null) {
      String dateFilter = getDateAtServerTimezone(lastBatchEndDate);
      whereClause = "where te.updated_on >= timestamp '" + dateFilter + "' ";
    }

    String failedIds = batch.getRedmineBatch().getFailedRedmineTimeEntriesIds();

    if (!StringUtils.isEmpty(failedIds)) {
      whereClause =
          StringUtils.isEmpty(whereClause)
              ? "where te.id in (" + failedIds + ") "
              : (whereClause + "or te.id in (" + failedIds + ") ");
    }

    Integer limit = batch.getRedmineBatch().getRedmineFetchLimit();

    if (limit == 0) {
      limit = 100;
    }

    String timeEntryQuery =
        "select te.id,te.project_id,ea.address as user,te.issue_id,te.hours,te.comments,enum.name as activity,te.spent_on,te.created_on,te.updated_on,\n"
            + "json_agg(distinct(array[cfs.name,cvs.value])) as custom_fields \n"
            + "from time_entries as te \n"
            + "left join email_addresses as ea on ea.user_id = te.user_id \n"
            + "left join enumerations as enum on enum.id = te.activity_id \n"
            + "left join custom_values as cvs on cvs.customized_id = te.id and cvs.customized_type = 'TimeEntry' \n"
            + "left join custom_fields as cfs on cfs.id = cvs.custom_field_id \n"
            + whereClause
            + "group by te.id,ea.address,enum.name order by te.spent_on,te.id \n"
            + "limit "
            + limit
            + " offset ?";

    return timeEntryQuery;
  }

  public void importRedmineTimeSpents(
      String timeEntryQuery, Connection connection, LocalDateTime lastBatchEndDate) {

    Integer offset = 0;

    LOG.debug("Start importing redmine time entries..");

    try (PreparedStatement preparedStatement = connection.prepareStatement(timeEntryQuery)) {
      preparedStatement.setFetchSize(batch.getRedmineBatch().getRedmineFetchLimit());
      ResultSet timeSpentResultSet = null;
      retrieveTimeSpentResultSet(preparedStatement, offset, lastBatchEndDate, timeSpentResultSet);
      batch.getRedmineBatch().setFailedRedmineTimeEntriesIds(failedIds);
    } catch (Exception e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public void retrieveTimeSpentResultSet(
      PreparedStatement preparedStatement,
      Integer offset,
      LocalDateTime lastBatchEndDate,
      ResultSet timeSpentResultSet)
      throws SQLException {

    preparedStatement.setInt(1, offset);
    timeSpentResultSet = preparedStatement.executeQuery();

    if (timeSpentResultSet.next()) {
      offset = processTimeSpentResultSet(offset, lastBatchEndDate, timeSpentResultSet);

      if (!updatedOnMap.isEmpty()) {
        setUpdatedOnFromRedmine();
      }

      retrieveTimeSpentResultSet(preparedStatement, offset, lastBatchEndDate, timeSpentResultSet);
    }
  }

  public Integer processTimeSpentResultSet(
      Integer offset, LocalDateTime lastBatchEndDate, ResultSet timeSpentResultSet)
      throws SQLException {

    offset += 1;

    try {
      importRedmineTimeSpent(lastBatchEndDate, timeSpentResultSet);

      if (errors.length > 0) {
        setErrorLog(
            IMessage.REDMINE_IMPORT_TIMESHEET_LINE_ERROR,
            String.valueOf(timeSpentResultSet.getInt("id")));
      }
    } finally {
      updateTransaction();
    }

    if (timeSpentResultSet.next()) {
      offset = processTimeSpentResultSet(offset, lastBatchEndDate, timeSpentResultSet);
    }

    return offset;
  }

  @Transactional
  public void importRedmineTimeSpent(LocalDateTime lastBatchEndDate, ResultSet timeSpentResultSet) {

    int redmineTimeSpentId = 0;

    try {
      redmineTimeSpentId = timeSpentResultSet.getInt("id");
      LOG.debug("Import timespent: {}", redmineTimeSpentId);

      validateRequiredFields(timeSpentResultSet);

      if (errors.length > 0) {
        fail++;
        failedIds =
            StringUtils.isEmpty(failedIds)
                ? String.valueOf(redmineTimeSpentId)
                : failedIds + "," + redmineTimeSpentId;
        setErrorLog(
            IMessage.REDMINE_IMPORT_TIMESHEET_LINE_ERROR, String.valueOf(redmineTimeSpentId));
        return;
      }

      TimesheetLine timesheetLine = timesheetLineRepo.findByRedmineId(redmineTimeSpentId);
      LocalDateTime redmineUpdatedOn =
          getDateAtLocalTimezone(timeSpentResultSet.getObject("updated_on", LocalDateTime.class));

      if (timesheetLine == null) {
        timesheetLine = new TimesheetLine();
      } else if (lastBatchEndDate != null
          && (redmineUpdatedOn.isBefore(lastBatchEndDate)
              || (timesheetLine.getUpdatedOn().isAfter(lastBatchEndDate)
                  && timesheetLine.getUpdatedOn().isAfter(redmineUpdatedOn)))) {
        return;
      }

      timesheetLine = setTimesheetLineFields(timesheetLine, timeSpentResultSet);

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
      fail++;
      failedIds =
          StringUtils.isEmpty(failedIds)
              ? String.valueOf(redmineTimeSpentId)
              : failedIds + "," + redmineTimeSpentId;
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public void validateRequiredFields(ResultSet timeSpentResultSet) throws SQLException {

    if (getUserFromEmail(timeSpentResultSet.getString("user")) == null) {
      errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_USER_NOT_FOUND)};
    }

    if (projectRepo.findByRedmineId(timeSpentResultSet.getInt("project_id")) == null) {
      errors =
          errors.length == 0
              ? new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PROJECT_NOT_FOUND)}
              : ObjectArrays.concat(
                  errors,
                  new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PROJECT_NOT_FOUND)},
                  Object.class);
    }

    Integer issueId = timeSpentResultSet.getInt("issue_id");

    if (issueId != 0 && teamTaskRepo.findByRedmineId(issueId) == null) {
      errors =
          errors.length == 0
              ? new Object[] {I18n.get(IMessage.REDMINE_IMPORT_TEAM_TASK_NOT_FOUND)}
              : ObjectArrays.concat(
                  errors,
                  new Object[] {I18n.get(IMessage.REDMINE_IMPORT_TEAM_TASK_NOT_FOUND)},
                  Object.class);
    }
  }

  public TimesheetLine setTimesheetLineFields(
      TimesheetLine timesheetLine, ResultSet timeSpentResultSet)
      throws SQLException, JSONException, AxelorException {

    User user = getUserFromEmail(timeSpentResultSet.getString("user"));

    timesheetLine.setUser(user);
    timesheetLine.setRedmineId(timeSpentResultSet.getInt("id"));
    timesheetLine.setProject(projectRepo.findByRedmineId(timeSpentResultSet.getInt("project_id")));
    timesheetLine.setComments(timeSpentResultSet.getString("comments"));

    Integer issueId = timeSpentResultSet.getInt("issue_id");
    timesheetLine.setTeamTask(issueId != 0 ? teamTaskRepo.findByRedmineId(issueId) : null);

    BigDecimal duration = BigDecimal.valueOf(timeSpentResultSet.getDouble("hours"));
    timesheetLine.setHoursDuration(duration);

    LocalDate redmineSpentOn = timeSpentResultSet.getObject("spent_on", LocalDate.class);
    timesheetLine.setDate(redmineSpentOn);

    String activityType = timeSpentResultSet.getString("activity");
    timesheetLine.setActivityTypeSelect(
        !StringUtils.isEmpty(activityType)
            ? (String) selectionMap.get(fieldMap.get(activityType))
            : null);

    String cfsJsonStr = timeSpentResultSet.getString("custom_fields");

    if (!StringUtils.isEmpty(cfsJsonStr)) {
      timesheetLine = setTimesheetLineFieldsFromCfs(new JSONArray(cfsJsonStr), timesheetLine);
    }

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
              : companyRepo.find(appRedmine.getCompany().getId()));
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
    timesheetLine.setDuration(
        timesheetLineService.computeHoursDuration(timesheet, duration, false));
    timesheet.setPeriodTotal(timesheet.getPeriodTotal().add(timesheetLine.getHoursDuration()));
    timesheetLine.setTimesheet(timesheet);

    setCreatedByUser(timesheetLine, user, "setCreatedBy");
    setLocalDateTime(
        timesheetLine,
        getDateAtLocalTimezone(timeSpentResultSet.getObject("created_on", LocalDateTime.class)),
        "setCreatedOn");

    return timesheetLine;
  }

  public TimesheetLine setTimesheetLineFieldsFromCfs(
      JSONArray cfsJsonArray, TimesheetLine timesheetLine) throws JSONException {

    JSONArray resultCfJsonArray;
    String cfName;
    String cfValue;

    for (Integer i = 0; i < cfsJsonArray.length(); i++) {
      resultCfJsonArray = cfsJsonArray.getJSONArray(i);
      cfName = resultCfJsonArray.get(0) != JSONObject.NULL ? resultCfJsonArray.getString(0) : null;
      cfValue = resultCfJsonArray.get(1) != JSONObject.NULL ? resultCfJsonArray.getString(1) : null;

      if (!StringUtils.isEmpty(cfName)) {

        if (cfName.equals(appRedmine.getRedmineTimeSpentProduct())) {

          if (StringUtils.isEmpty(cfValue)) {
            User user = timesheetLine.getUser();
            cfValue =
                user.getEmployee() != null && user.getEmployee().getProduct() != null
                    ? user.getEmployee().getProduct().getCode()
                    : appRedmine.getRedmineTimeSpentProductDefault();
          }

          Product product = !StringUtils.isEmpty(cfValue) ? productRepo.findByCode(cfValue) : null;
          timesheetLine.setProduct(product);

          if (product == null) {
            errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_TIME_SPENT_WITHOUT_PRODUCT)};
          }
        } else if (cfName.equals(appRedmine.getRedmineTimeSpentDurationForCustomer())) {
          timesheetLine.setDurationForCustomer(
              !StringUtils.isEmpty(cfValue)
                  ? new BigDecimal(cfValue)
                  : timesheetLine.getHoursDuration());
        } else if (cfName.equals(appRedmine.getRedmineTimeSpentDurationUnit())) {
          Unit unit = null;

          if (!StringUtils.isEmpty(cfValue)) {
            unit = unitRepo.findByName(cfValue);
          }

          if (unit == null) {
            unit = unitRepo.findByName(appRedmine.getRedmineTimeSpentDurationUnitDefault());

            if (unit == null) {
              unit = appBaseService.getAppBase().getUnitHours();
            }
          }

          timesheetLine.setDurationUnit(unit);
        }
      }
    }

    return timesheetLine;
  }

  public void setUpdatedOnFromRedmine() {

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
}
