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

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.TeamTaskCategory;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.TeamTaskCategoryRepository;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.RedmineImportMapping;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.apps.redmine.imports.service.RedmineImportService;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaStore;
import com.axelor.meta.schema.views.Selection.Option;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineImportIssueServiceImpl extends RedmineImportService
    implements RedmineImportIssueService {

  protected RedmineImportMappingRepository redmineImportMappingRepository;

  @Inject
  public RedmineImportIssueServiceImpl(
      UserRepository userRepo,
      ProjectRepository projectRepo,
      ProductRepository productRepo,
      TeamTaskRepository teamTaskRepo,
      TeamTaskCategoryRepository projectCategoryRepo,
      PartnerRepository partnerRepo,
      RedmineImportMappingRepository redmineImportMappingRepository,
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
    this.redmineImportMappingRepository = redmineImportMappingRepository;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());
  protected Product product;
  protected Project project;
  protected TeamTaskCategory projectCategory;
  protected String redmineIssueProductDefault;
  protected LocalDate redmineIssueDueDateDefault;
  protected BigDecimal redmineIssueEstimatedTimeDefault;
  protected BigDecimal redmineIssueUnitPriceDefault;
  protected String redmineIssueProduct;
  protected String redmineIssueDueDate;
  protected String redmineIssueEstimatedTime;
  protected String redmineIssueInvoiced;
  protected String redmineIssueAccountedForMaintenance;
  protected String redmineIssueIsTaskAccepted;
  protected String redmineIssueIsOffered;
  protected String redmineIssueUnitPrice;

  @Override
  @SuppressWarnings("unchecked")
  public void importIssue(List<Issue> redmineIssueList, HashMap<String, Object> paramsMap) {

    if (redmineIssueList != null && !redmineIssueList.isEmpty()) {
      this.onError = (Consumer<Throwable>) paramsMap.get("onError");
      this.onSuccess = (Consumer<Object>) paramsMap.get("onSuccess");
      this.batch = (Batch) paramsMap.get("batch");
      this.errorObjList = (List<Object[]>) paramsMap.get("errorObjList");
      this.lastBatchUpdatedOn = (LocalDateTime) paramsMap.get("lastBatchUpdatedOn");
      this.redmineUserMap = (HashMap<Integer, String>) paramsMap.get("redmineUserMap");
      this.selectionMap = new HashMap<>();
      this.fieldMap = new HashMap<>();

      AppRedmine appRedmine = appRedmineRepo.all().fetchOne();

      this.redmineIssueProduct = appRedmine.getRedmineIssueProduct();
      this.redmineIssueDueDate = appRedmine.getRedmineIssueDueDate();
      this.redmineIssueEstimatedTime = appRedmine.getRedmineIssueEstimatedTime();
      this.redmineIssueInvoiced = appRedmine.getRedmineIssueInvoiced();
      this.redmineIssueAccountedForMaintenance =
          appRedmine.getRedmineIssueAccountedForMaintenance();
      this.redmineIssueIsTaskAccepted = appRedmine.getRedmineIssueIsTaskAccepted();
      this.redmineIssueIsOffered = appRedmine.getRedmineIssueIsOffered();
      this.redmineIssueUnitPrice = appRedmine.getRedmineIssueUnitPrice();

      this.redmineIssueProductDefault = appRedmine.getRedmineIssueProductDefault();
      this.redmineIssueDueDateDefault = appRedmine.getRedmineIssueDueDateDefault();
      this.redmineIssueEstimatedTimeDefault = appRedmine.getRedmineIssueEstimatedTimeDefault();
      this.redmineIssueUnitPriceDefault = appRedmine.getRedmineIssueUnitPriceDefault();

      List<Option> selectionList = new ArrayList<>();
      selectionList.addAll(MetaStore.getSelectionList("team.task.status"));
      selectionList.addAll(MetaStore.getSelectionList("team.task.priority"));

      ResourceBundle fr = I18n.getBundle(Locale.FRANCE);
      ResourceBundle en = I18n.getBundle(Locale.ENGLISH);

      for (Option option : selectionList) {
        selectionMap.put(fr.getString(option.getTitle()), option.getValue());
        selectionMap.put(en.getString(option.getTitle()), option.getValue());
      }

      List<RedmineImportMapping> redmineImportMappingList =
          redmineImportMappingRepository.all().fetch();

      for (RedmineImportMapping redmineImportMapping : redmineImportMappingList) {
        fieldMap.put(redmineImportMapping.getRedmineValue(), redmineImportMapping.getOsValue());
      }

      RedmineBatch redmineBatch = batch.getRedmineBatch();
      redmineBatch.setFailedRedmineIssuesIds(null);

      LOG.debug("Total issues to import: {}", redmineIssueList.size());

      this.importIssuesFromList(redmineIssueList, redmineBatch);

      // SET ISSUES PARENTS

      if (!parentMap.isEmpty()) {
        String values =
            parentMap
                .entrySet()
                .stream()
                .map(entry -> "(" + entry.getKey() + "," + entry.getValue() + ")")
                .collect(Collectors.joining(","));

        String query =
            String.format(
                "UPDATE team_task as teamtask SET parent_task = (SELECT id from team_task where team_task.redmine_id = v.redmine_id) from (values %s) as v(id,redmine_id) where teamtask.id = v.id",
                values);

        JPA.em().createNativeQuery(query).executeUpdate();
      }

      if (!updatedOnMap.isEmpty()) {
        String values =
            updatedOnMap
                .entrySet()
                .stream()
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
                "UPDATE team_task as teamtask SET updated_on = v.updated_on from (values %s) as v(id,updated_on) where teamtask.id = v.id",
                values);

        JPA.em().createNativeQuery(query).executeUpdate();
      }
    }

    String resultStr =
        String.format("Redmine Issue -> ABS Teamtask : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  public void importIssuesFromList(List<Issue> redmineIssueList, RedmineBatch redmineBatch) {

    int i = 0;

    for (Issue redmineIssue : redmineIssueList) {
      LOG.debug("Importing issue: " + redmineIssue.getId());

      errors = new Object[] {};
      String failedRedmineIssuesIds = redmineBatch.getFailedRedmineIssuesIds();

      setRedmineCustomFieldsMap(redmineIssue.getCustomFields());

      // ERROR AND DON'T IMPORT IF PRODUCT IS SELECTED IN REDMINE AND NOT FOUND IN OS

      String value =
          StringUtils.isNotEmpty(redmineCustomFieldsMap.get(redmineIssueProduct))
              ? redmineCustomFieldsMap.get(redmineIssueProduct)
              : redmineIssueProductDefault;

      if (value != null) {
        this.product = productRepo.findByCode(value);

        if (product == null) {
          errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PRODUCT_NOT_FOUND)};

          redmineBatch.setFailedRedmineIssuesIds(
              failedRedmineIssuesIds == null
                  ? redmineIssue.getId().toString()
                  : failedRedmineIssuesIds + "," + redmineIssue.getId().toString());

          setErrorLog(
              I18n.get(IMessage.REDMINE_IMPORT_TEAMTASK_ERROR), redmineIssue.getId().toString());

          fail++;
          continue;
        }
      } else {
        this.product = null;
      }

      // ERROR AND DON'T IMPORT IF PROJECT NOT FOUND

      this.project = projectRepo.findByRedmineId(redmineIssue.getProjectId());

      if (project == null) {
        errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PROJECT_NOT_FOUND)};

        redmineBatch.setFailedRedmineIssuesIds(
            failedRedmineIssuesIds == null
                ? redmineIssue.getId().toString()
                : failedRedmineIssuesIds + "," + redmineIssue.getId().toString());

        setErrorLog(
            I18n.get(IMessage.REDMINE_IMPORT_TEAMTASK_ERROR), redmineIssue.getId().toString());

        fail++;
        continue;
      }

      // ERROR AND DON'T IMPORT IF PROJECT CATEGORY NOT FOUND

      String trackerName = fieldMap.get(redmineIssue.getTracker().getName());

      this.projectCategory = projectCategoryRepo.findByName(trackerName);

      if (projectCategory == null) {
        errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PROJECT_CATEGORY_NOT_FOUND)};

        redmineBatch.setFailedRedmineIssuesIds(
            failedRedmineIssuesIds == null
                ? redmineIssue.getId().toString()
                : failedRedmineIssuesIds + "," + redmineIssue.getId().toString());

        setErrorLog(
            I18n.get(IMessage.REDMINE_IMPORT_TEAMTASK_ERROR), redmineIssue.getId().toString());

        fail++;
        continue;
      }

      try {
        this.createOpenSuiteIssue(redmineIssue);

        if (errors.length > 0) {
          setErrorLog(
              I18n.get(IMessage.REDMINE_IMPORT_TEAMTASK_ERROR), redmineIssue.getId().toString());
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

  @Transactional
  public void createOpenSuiteIssue(Issue redmineIssue) {

    TeamTask teamTask = teamTaskRepo.findByRedmineId(redmineIssue.getId());
    LocalDateTime redmineUpdatedOn =
        redmineIssue.getUpdatedOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

    if (teamTask == null) {
      teamTask = new TeamTask();
      teamTask.setTypeSelect(TeamTaskRepository.TYPE_TASK);
    } else if (lastBatchUpdatedOn != null
        && (redmineUpdatedOn.isBefore(lastBatchUpdatedOn)
            || (teamTask.getUpdatedOn().isAfter(lastBatchUpdatedOn)
                && teamTask.getUpdatedOn().isAfter(redmineUpdatedOn)))) {
      return;
    }

    this.setTeamTaskFields(teamTask, redmineIssue);

    try {

      if (teamTask.getId() == null) {
        teamTask.addCreatedBatchSetItem(batch);
      } else {
        teamTask.addUpdatedBatchSetItem(batch);
      }

      teamTaskRepo.save(teamTask);
      updatedOnMap.put(teamTask.getId(), redmineUpdatedOn);

      // CREATE MAP FOR CHILD-PARENT TASKS

      if (redmineIssue.getParentId() != null) {
        parentMap.put(teamTask.getId(), redmineIssue.getParentId());
      }

      onSuccess.accept(teamTask);
      success++;
    } catch (Exception e) {
      onError.accept(e);
      fail++;
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public void setTeamTaskFields(TeamTask teamTask, Issue redmineIssue) {

    try {
      teamTask.setRedmineId(redmineIssue.getId());
      teamTask.setProject(project);
      teamTask.setTeamTaskCategory(projectCategory);
      teamTask.setName("#" + redmineIssue.getId() + " " + redmineIssue.getSubject());
      teamTask.setDescription(getHtmlFromTextile(redmineIssue.getDescription()));

      Integer assigneeId = redmineIssue.getAssigneeId();
      User assignedTo = assigneeId != null ? getOsUser(assigneeId) : null;

      if (assignedTo == null) {
        assignedTo = project.getAssignedTo();
      }

      teamTask.setAssignedTo(assignedTo);
      teamTask.setProgressSelect(redmineIssue.getDoneRatio());

      Float estimatedHours = redmineIssue.getEstimatedHours();
      teamTask.setBudgetedTime(estimatedHours != null ? BigDecimal.valueOf(estimatedHours) : null);

      Date closedOn = redmineIssue.getClosedOn();
      teamTask.setTaskEndDate(
          closedOn != null
              ? closedOn.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
              : null);

      Date startDate = redmineIssue.getStartDate();
      teamTask.setTaskDate(
          startDate != null
              ? startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
              : null);

      Version targetVersion = redmineIssue.getTargetVersion();
      teamTask.setFixedVersion(targetVersion != null ? targetVersion.getName() : null);

      String value = redmineCustomFieldsMap.get(redmineIssueDueDate);
      teamTask.setDueDate(
          value != null && !value.equals("") ? LocalDate.parse(value) : redmineIssueDueDateDefault);

      value = redmineCustomFieldsMap.get(redmineIssueEstimatedTime);
      teamTask.setEstimatedTime(
          value != null && !value.equals("")
              ? new BigDecimal(value)
              : redmineIssueEstimatedTimeDefault);

      value = redmineCustomFieldsMap.get(redmineIssueInvoiced);

      if (!teamTask.getInvoiced()) {
        teamTask.setInvoiced(value != null ? (value.equals("1") ? true : false) : false);
        teamTask.setProduct(product);

        value = redmineCustomFieldsMap.get(redmineIssueUnitPrice);
        teamTask.setUnitPrice(
            value != null && !value.equals("")
                ? new BigDecimal(value)
                : redmineIssueUnitPriceDefault);
        teamTask.setUnit(null);
        teamTask.setQuantity(BigDecimal.ZERO);
        teamTask.setExTaxTotal(BigDecimal.ZERO);
        teamTask.setInvoicingType(0);
        teamTask.setToInvoice(false);
        teamTask.setCurrency(null);
      }

      value = redmineCustomFieldsMap.get(redmineIssueAccountedForMaintenance);

      teamTask.setAccountedForMaintenance(
          value != null ? (value.equals("1") ? true : false) : false);

      value = redmineCustomFieldsMap.get(redmineIssueIsOffered);
      teamTask.setIsOffered(value != null ? (value.equals("1") ? true : false) : false);

      value = redmineCustomFieldsMap.get(redmineIssueIsTaskAccepted);
      teamTask.setIsTaskAccepted(value != null ? (value.equals("1") ? true : false) : false);

      // ERROR AND IMPORT WITH DEFAULT IF STATUS NOT FOUND

      String status = fieldMap.get(redmineIssue.getStatusName());
      value = selectionMap.get(status);

      if (status != null && value != null) {
        teamTask.setStatus(value);
      } else {
        teamTask.setStatus(TeamTaskRepository.TEAM_TASK_DEFAULT_STATUS);
        errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_WITH_DEFAULT_STATUS)};
      }

      // ERROR AND IMPORT WITH DEFAULT IF PRIORITY NOT FOUND

      String priority = fieldMap.get(redmineIssue.getPriorityText());
      value = selectionMap.get(priority);

      if (priority != null && value != null) {
        teamTask.setPriority(value);
      } else {
        teamTask.setPriority(TeamTaskRepository.TEAM_TASK_DEFAULT_PRIORITY);
        errors =
            errors.length == 0
                ? new Object[] {I18n.get(IMessage.REDMINE_IMPORT_WITH_DEFAULT_PRIORITY)}
                : ObjectArrays.concat(
                    errors,
                    new Object[] {I18n.get(IMessage.REDMINE_IMPORT_WITH_DEFAULT_PRIORITY)},
                    Object.class);
      }

      setCreatedByUser(teamTask, getOsUser(redmineIssue.getAuthorId()), "setCreatedBy");
      setLocalDateTime(teamTask, redmineIssue.getCreatedOn(), "setCreatedOn");
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }
}
