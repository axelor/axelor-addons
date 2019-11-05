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
package com.axelor.apps.redmine.imports.service.issues;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.service.timesheet.TimesheetService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectCategoryRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.imports.service.RedmineImportService;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.TimeEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineImportTimeSpentServiceImpl extends RedmineImportService
    implements RedmineImportTimeSpentService {

  protected TimesheetLineRepository timesheetLineRepo;
  protected TimesheetRepository timesheetRepo;
  protected TimesheetService timesheetService;

  @Inject
  public RedmineImportTimeSpentServiceImpl(
      UserRepository userRepo,
      ProjectRepository projectRepo,
      ProductRepository productRepo,
      TeamTaskRepository teamTaskRepo,
      ProjectCategoryRepository projectCategoryRepo,
      PartnerRepository partnerRepo,
      TimesheetLineRepository timesheetLineRepo,
      TimesheetRepository timesheetRepo,
      TimesheetService timesheetService,
      AppRedmineRepository appRedmineRepo,
      CompanyRepository companyRepo) {

    super(
        userRepo,
        projectRepo,
        productRepo,
        teamTaskRepo,
        projectCategoryRepo,
        partnerRepo,
        appRedmineRepo,
        companyRepo);
    this.timesheetLineRepo = timesheetLineRepo;
    this.timesheetRepo = timesheetRepo;
    this.timesheetService = timesheetService;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());
  protected Long projectId = (long) 0;
  protected Long teamTaskId = (long) 0;
  protected Long userId = (long) 0;
  protected Long productId = (long) 0;
  protected Long defaultCompanyId;

  @Override
  @SuppressWarnings("unchecked")
  public void importTimeSpent(
      List<TimeEntry> redmineTimeEntryList, HashMap<String, Object> paramsMap) {

    if (redmineTimeEntryList != null && !redmineTimeEntryList.isEmpty()) {
      this.onError = (Consumer<Throwable>) paramsMap.get("onError");
      this.onSuccess = (Consumer<Object>) paramsMap.get("onSuccess");
      this.batch = (Batch) paramsMap.get("batch");
      this.errorObjList = (List<Object[]>) paramsMap.get("errorObjList");
      this.lastBatchUpdatedOn = (LocalDateTime) paramsMap.get("lastBatchUpdatedOn");
      this.redmineUserMap = (HashMap<Integer, String>) paramsMap.get("redmineUserMap");
      this.defaultCompanyId = appRedmineRepo.all().fetchOne().getCompany().getId();

      Comparator<TimeEntry> compareByDate =
          (TimeEntry o1, TimeEntry o2) -> o1.getSpentOn().compareTo(o2.getSpentOn());
      Collections.sort(redmineTimeEntryList, compareByDate);

      RedmineBatch redmineBatch = batch.getRedmineBatch();
      redmineBatch.setFailedRedmineTimeEntriesIds(null);

      int i = 0;

      for (TimeEntry redmineTimeEntry : redmineTimeEntryList) {

        errors = new Object[] {};
        String failedRedmineTimeEntriesIds = redmineBatch.getFailedRedmineTimeEntriesIds();

        // ERROR AND DON'T IMPORT IF USER NOT FOUND

        User user = getOsUser(redmineTimeEntry.getUserId());

        if (user != null) {
          this.userId = user.getId();
        } else {
          errors =
              errors.length == 0
                  ? new Object[] {I18n.get(IMessage.REDMINE_IMPORT_USER_NOT_FOUND)}
                  : ObjectArrays.concat(
                      errors,
                      new Object[] {I18n.get(IMessage.REDMINE_IMPORT_USER_NOT_FOUND)},
                      Object.class);

          redmineBatch.setFailedRedmineTimeEntriesIds(
              failedRedmineTimeEntriesIds == null
                  ? redmineTimeEntry.getId().toString()
                  : failedRedmineTimeEntriesIds + "," + redmineTimeEntry.getId().toString());

          setErrorLog(
              I18n.get(IMessage.REDMINE_IMPORT_TIMESHEET_LINE_ERROR),
              redmineTimeEntry.getId().toString());

          fail++;
          continue;
        }

        // ERROR AND DON'T IMPORT IF PROJECT NOT FOUND

        Project project = projectRepo.findByRedmineId(redmineTimeEntry.getProjectId());

        if (project != null) {
          this.projectId = project.getId();
        } else {
          errors =
              errors.length == 0
                  ? new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PROJECT_NOT_FOUND)}
                  : ObjectArrays.concat(
                      errors,
                      new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PROJECT_NOT_FOUND)},
                      Object.class);

          redmineBatch.setFailedRedmineTimeEntriesIds(
              failedRedmineTimeEntriesIds == null
                  ? redmineTimeEntry.getId().toString()
                  : failedRedmineTimeEntriesIds + "," + redmineTimeEntry.getId().toString());

          setErrorLog(
              I18n.get(IMessage.REDMINE_IMPORT_TIMESHEET_LINE_ERROR),
              redmineTimeEntry.getId().toString());

          fail++;
          continue;
        }

        // ERROR AND DON'T IMPORT IF TEAMTASK NOT FOUND

        Integer issueId = redmineTimeEntry.getIssueId();

        if (issueId != null) {
          TeamTask teamTask = teamTaskRepo.findByRedmineId(issueId);

          if (teamTask != null) {
            this.teamTaskId = teamTask.getId();
          } else {
            errors =
                errors.length == 0
                    ? new Object[] {I18n.get(IMessage.REDMINE_IMPORT_TEAM_TASK_NOT_FOUND)}
                    : ObjectArrays.concat(
                        errors,
                        new Object[] {I18n.get(IMessage.REDMINE_IMPORT_TEAM_TASK_NOT_FOUND)},
                        Object.class);

            redmineBatch.setFailedRedmineTimeEntriesIds(
                failedRedmineTimeEntriesIds == null
                    ? redmineTimeEntry.getId().toString()
                    : failedRedmineTimeEntriesIds + "," + redmineTimeEntry.getId().toString());

            setErrorLog(
                I18n.get(IMessage.REDMINE_IMPORT_TIMESHEET_LINE_ERROR),
                redmineTimeEntry.getId().toString());

            fail++;
            continue;
          }
        }

        // ERROR AND DON'T IMPORT IF PRODUCT NOT FOUND

        CustomField redmineProduct = redmineTimeEntry.getCustomField("Product");

        if (redmineProduct != null) {
          String value = redmineProduct.getValue();
          Product product;

          if (value != null && !value.equals("")) {
            product = productRepo.findByCode(value);

            if (product == null) {
              errors =
                  errors.length == 0
                      ? new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PRODUCT_NOT_FOUND)}
                      : ObjectArrays.concat(
                          errors,
                          new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PRODUCT_NOT_FOUND)},
                          Object.class);

              redmineBatch.setFailedRedmineTimeEntriesIds(
                  failedRedmineTimeEntriesIds == null
                      ? redmineTimeEntry.getId().toString()
                      : failedRedmineTimeEntriesIds + "," + redmineTimeEntry.getId().toString());

              setErrorLog(
                  I18n.get(IMessage.REDMINE_IMPORT_TIMESHEET_LINE_ERROR),
                  redmineTimeEntry.getId().toString());

              fail++;
              continue;
            }
          } else {
            product = user.getEmployee() != null ? user.getEmployee().getProduct() : null;
          }

          if (product != null) {
            this.productId = product.getId();
          }
        }

        try {
          this.createOpenSuiteTimesheetLine(redmineTimeEntry);

          if (errors.length > 0) {
            setErrorLog(
                I18n.get(IMessage.REDMINE_IMPORT_TIMESHEET_LINE_ERROR),
                redmineTimeEntry.getId().toString());
          }
        } finally {
          if (++i % AbstractBatch.FETCH_LIMIT == 0) {
            JPA.em().getTransaction().commit();

            if (!JPA.em().getTransaction().isActive()) {
              JPA.em().getTransaction().begin();
            }

            JPA.clear();

            if (!JPA.em().contains(batch)) {
              batch = JPA.find(Batch.class, batch.getId());
            }
          }
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

  @Transactional
  public void createOpenSuiteTimesheetLine(TimeEntry redmineTimeEntry) {

    TimesheetLine timesheetLine = timesheetLineRepo.findByRedmineId(redmineTimeEntry.getId());
    LocalDateTime redmineUpdatedOn =
        redmineTimeEntry
            .getUpdatedOn()
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();

    if (timesheetLine == null) {
      timesheetLine = new TimesheetLine();
    } else if (lastBatchUpdatedOn != null
        && (redmineUpdatedOn.isBefore(lastBatchUpdatedOn)
            || (timesheetLine.getUpdatedOn().isAfter(lastBatchUpdatedOn)
                && timesheetLine.getUpdatedOn().isAfter(redmineUpdatedOn)))) {
      return;
    }

    LOG.debug("Importing time entry: " + redmineTimeEntry.getId());

    this.setTimesheetLineFields(timesheetLine, redmineTimeEntry);

    if (timesheetLine.getTimesheet() != null) {

      try {

        if (timesheetLine.getId() == null) {
          timesheetLine.addBatchSetItem(batch);
        }

        timesheetLineRepo.save(timesheetLine);
        this.setUpdatedOn(
            "update hr_timesheet_line SET updated_on = ?1 where id = ?2",
            redmineUpdatedOn,
            timesheetLine.getId());

        onSuccess.accept(timesheetLine);
        success++;
      } catch (Exception e) {
        onError.accept(e);
        fail++;
        TraceBackService.trace(e, "", batch.getId());
      }
    }
  }

  @Transactional
  public void setTimesheetLineFields(TimesheetLine timesheetLine, TimeEntry redmineTimeEntry) {

    User user = userRepo.find(userId);

    timesheetLine.setUser(user);
    timesheetLine.setRedmineId(redmineTimeEntry.getId());
    timesheetLine.setProject(projectRepo.find(projectId));
    timesheetLine.setTeamTask(teamTaskRepo.find(teamTaskId));
    timesheetLine.setProduct(productRepo.find(productId));
    timesheetLine.setComments(redmineTimeEntry.getComment());
    timesheetLine.setDuration(BigDecimal.valueOf(redmineTimeEntry.getHours()));
    timesheetLine.setHoursDuration(timesheetLine.getDuration());
    timesheetLine.setDate(
        redmineTimeEntry.getSpentOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());

    CustomField customField = redmineTimeEntry.getCustomField("Temps passé ajusté client");
    String value;

    if (customField != null) {
      value = customField.getValue();

      if (value != null && !value.equals("")) {
        timesheetLine.setDurationForCustomer(new BigDecimal(value));
      }
    }

    Timesheet timesheet =
        timesheetRepo.all().filter("self.user = ?1", user).order("-id").fetchOne();

    LocalDate redmineSpentOn =
        redmineTimeEntry.getSpentOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

    if (timesheet == null) {
      timesheet = timesheetService.createTimesheet(user, redmineSpentOn, null);
      timesheet.setCompany(companyRepo.find(defaultCompanyId));
      timesheet.setStatusSelect(TimesheetRepository.STATUS_VALIDATED);
    } else if (timesheet.getFromDate().isAfter(redmineSpentOn)) {
      timesheet.setFromDate(redmineSpentOn);
    }

    timesheet.setPeriodTotal(timesheet.getPeriodTotal().add(timesheetLine.getHoursDuration()));
    timesheetRepo.save(timesheet);
    timesheetLine.setTimesheet(timesheet);

    setCreatedByUser(timesheetLine, user, "setCreatedBy");
    setLocalDateTime(timesheetLine, redmineTimeEntry.getCreatedOn(), "setCreatedOn");
  }
}
