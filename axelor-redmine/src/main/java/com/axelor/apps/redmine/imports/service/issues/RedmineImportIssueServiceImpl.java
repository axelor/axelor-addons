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
import com.axelor.apps.base.db.repo.AppBusinessSupportRepository;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.TeamTaskCategory;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.TeamTaskCategoryRepository;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.RedmineImportMapping;
import com.axelor.apps.redmine.db.repo.RedmineImportConfigRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.apps.redmine.imports.service.RedmineImportService;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.redmine.service.TeamTaskRedmineService;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.axelor.meta.MetaStore;
import com.axelor.meta.schema.views.Selection.Option;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.common.base.Joiner;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.CustomFieldManager;
import com.taskadapter.redmineapi.Include;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.CustomFieldDefinition;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Journal;
import com.taskadapter.redmineapi.bean.JournalDetail;
import com.taskadapter.redmineapi.bean.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineImportIssueServiceImpl extends RedmineImportService
    implements RedmineImportIssueService {

  protected RedmineImportMappingRepository redmineImportMappingRepository;
  protected ProjectVersionRepository projectVersionRepository;
  protected AppBaseService appBaseService;
  protected TeamTaskRedmineService teamTaskRedmineService;
  protected MailMessageRepository mailMessageRepository;

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
      CompanyRepository companyRepo,
      ProjectVersionRepository projectVersionRepository,
      AppBaseService appBaseService,
      TeamTaskRedmineService teamTaskRedmineService,
      MailMessageRepository mailMessageRepository) {

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
    this.projectVersionRepository = projectVersionRepository;
    this.appBaseService = appBaseService;
    this.teamTaskRedmineService = teamTaskRedmineService;
    this.mailMessageRepository = mailMessageRepository;
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

  protected List<Long> projectVersionIdList = new ArrayList<>();
  protected boolean isAppBusinessSupport;

  // Maps used for issue activity import

  protected HashMap<String, String> redmineStatusMap;
  protected HashMap<String, String> redminePriorityMap;
  protected HashMap<String, String> redmineTrackerMap;
  protected HashMap<String, String> redmineCfNameMap;
  protected HashMap<String, String> redmineCfTypeMap;
  protected HashMap<String, String> userMap;
  protected HashMap<String, String> projectMap;
  protected HashMap<String, String> versionMap;
  protected HashMap<String, String> teamTaskMap;

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
      isAppBusinessSupport = appBaseService.isApp("business-support");

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
          redmineImportMappingRepository
              .all()
              .filter(
                  "self.redmineImportConfig.redmineMappingFieldSelect in (?1, ?2, ?3)",
                  RedmineImportConfigRepository.MAPPING_FIELD_PROJECT_TRACKER,
                  RedmineImportConfigRepository.MAPPING_FIELD_TASK_PRIORITY,
                  RedmineImportConfigRepository.MAPPING_FIELD_TASK_STATUS)
              .fetch();

      for (RedmineImportMapping redmineImportMapping : redmineImportMappingList) {
        fieldMap.put(redmineImportMapping.getRedmineValue(), redmineImportMapping.getOsValue());
      }

      RedmineBatch redmineBatch = batch.getRedmineBatch();
      redmineBatch.setFailedRedmineIssuesIds(null);

      if (redmineBatch.getIsImportIssuesWithActivities()) {
        configureRedmineManagersAndMaps((RedmineManager) paramsMap.get("redmineManager"));
      }

      LOG.debug("Total issues to import: {}", redmineIssueList.size());

      this.importIssuesFromList(redmineIssueList, redmineBatch);

      updateTransaction();

      // SET ISSUES PARENTS

      if (!parentMap.isEmpty()) {
        LOG.debug("Setting parent tasks on imported tasks..");
        this.setParentTasks();
        updateTransaction();
      }

      LOG.debug("Setting updateOn datetime on imported tasks..");

      if (!updatedOnMap.isEmpty()) {
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
                "UPDATE team_task as teamtask SET updated_on = v.updated_on from (values %s) as v(id,updated_on) where teamtask.id = v.id",
                values);

        JPA.em().createNativeQuery(query).executeUpdate();

        if (redmineBatch.getIsImportIssuesWithActivities()) {
          deleteUnwantedMailMessages();
        }
      }

      if (CollectionUtils.isNotEmpty(projectVersionIdList)) {
        LOG.debug("Setting project versions total progress..");
        this.updateProjectVersionsProgress();
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
    Boolean isImportIssuesWithActivities = redmineBatch.getIsImportIssuesWithActivities();

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
        this.createOpenSuiteIssue(redmineIssue, isImportIssuesWithActivities);

        if (errors.length > 0) {
          setErrorLog(
              I18n.get(IMessage.REDMINE_IMPORT_TEAMTASK_ERROR), redmineIssue.getId().toString());
        }
      } finally {
        if (++i % AbstractBatch.FETCH_LIMIT == 0) {
          updateTransaction();
        }
      }
    }
  }

  @Transactional
  public void createOpenSuiteIssue(Issue redmineIssue, Boolean isImportIssuesWithActivities) {

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

      // Import journals

      if (isImportIssuesWithActivities) {
        importIssueJournals(teamTask, redmineIssue);
      }

      onSuccess.accept(teamTask);
      success++;
    } catch (Exception e) {
      onError.accept(e);
      fail++;
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  @Transactional
  public void setParentTasks() {

    TeamTask task;
    TeamTask parentTask;
    HashMap<Integer, TeamTask> parentTaskMap = new HashMap<>();

    for (Map.Entry<Long, Integer> entry : parentMap.entrySet()) {

      if (parentTaskMap.containsKey(entry.getValue())) {
        parentTask = parentTaskMap.get(entry.getValue());
      } else {
        parentTask = teamTaskRepo.findByRedmineId(entry.getValue());
        parentTaskMap.put(entry.getValue(), parentTask);
      }

      if (parentTask != null) {
        task = teamTaskRepo.find(entry.getKey());
        task.setParentTask(parentTask);

        teamTaskRepo.save(task);
      }
    }
  }

  @Transactional
  public void updateProjectVersionsProgress() {

    String taskClosedStatusSelect =
        Beans.get(AppBusinessSupportRepository.class).all().fetchOne().getTaskClosedStatusSelect();

    for (Long id : projectVersionIdList) {
      teamTaskRedmineService.updateProjectVersionProgress(
          projectVersionRepository.find(id), taskClosedStatusSelect);
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

      if (isAppBusinessSupport) {

        if (teamTask.getTargetVersion() != null
            && !projectVersionIdList.contains(teamTask.getTargetVersion().getId())) {
          projectVersionIdList.add(teamTask.getTargetVersion().getId());
        }

        if (targetVersion != null) {
          ProjectVersion projectVersion =
              projectVersionRepository.findByRedmineId(targetVersion.getId());
          teamTask.setTargetVersion(projectVersion);

          if (!projectVersionIdList.contains(projectVersion.getId())) {
            projectVersionIdList.add(projectVersion.getId());
          }
        } else {
          teamTask.setTargetVersion(null);
        }
      }

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
      value = (String) selectionMap.get(status);

      if (status != null && value != null) {
        teamTask.setStatus(value);
      } else {
        teamTask.setStatus(TeamTaskRepository.TEAM_TASK_DEFAULT_STATUS);
        errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_WITH_DEFAULT_STATUS)};
      }

      // ERROR AND IMPORT WITH DEFAULT IF PRIORITY NOT FOUND

      String priority = fieldMap.get(redmineIssue.getPriorityText());
      value = (String) selectionMap.get(priority);

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

  public void configureRedmineManagersAndMaps(RedmineManager redmineManager) {

    redmineStatusMap = new HashMap<>();
    redminePriorityMap = new HashMap<>();
    redmineTrackerMap = new HashMap<>();
    redmineCfNameMap = new HashMap<>();
    redmineCfTypeMap = new HashMap<>();
    userMap = new HashMap<>();
    projectMap = new HashMap<>();
    versionMap = new HashMap<>();
    teamTaskMap = new HashMap<>();

    redmineIssueManager = redmineManager.getIssueManager();
    redmineProjectManager = redmineManager.getProjectManager();

    try {
      redmineIssueManager.getStatuses().stream()
          .forEach(s -> redmineStatusMap.put(s.getId().toString(), s.getName()));
      redmineIssueManager.getIssuePriorities().stream()
          .forEach(p -> redminePriorityMap.put(p.getId().toString(), p.getName()));
      redmineIssueManager.getTrackers().stream()
          .forEach(t -> redmineTrackerMap.put(t.getId().toString(), t.getName()));

      CustomFieldManager redmineCustomFieldManager = redmineManager.getCustomFieldManager();
      List<CustomFieldDefinition> redmineCustomFieldDefList =
          redmineCustomFieldManager.getCustomFieldDefinitions();

      if (CollectionUtils.isNotEmpty(redmineCustomFieldDefList)) {

        for (CustomFieldDefinition redmineCustomDef : redmineCustomFieldDefList) {

          if (redmineCustomDef.getCustomizedType().equals("issue")) {
            redmineCfNameMap.put(redmineCustomDef.getId().toString(), redmineCustomDef.getName());
            redmineCfTypeMap.put(
                redmineCustomDef.getId().toString(), redmineCustomDef.getFieldFormat());
          }
        }
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  @Transactional
  public void importIssueJournals(TeamTask teamTask, Issue redmineIssue) {

    try {
      Issue issueWithJournal =
          redmineIssueManager.getIssueById(redmineIssue.getId(), Include.journals);
      Collection<Journal> journals = issueWithJournal.getJournals();

      for (Journal redmineJournal : journals) {

        if (lastBatchUpdatedOn != null
            && redmineJournal
                .getCreatedOn()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .isBefore(lastBatchUpdatedOn)) {
          continue;
        }

        MailMessage mailMessage = getMailMessage(redmineJournal, teamTask);

        if (mailMessage != null) {
          mailMessageRepository.save(mailMessage);
        }
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public MailMessage getMailMessage(Journal redmineJournal, TeamTask teamTask) {

    String body = "";
    MailMessage mailMessage =
        mailMessageRepository
            .all()
            .filter(
                "self.redmineId = ?1 AND self.relatedId = ?2",
                redmineJournal.getId(),
                teamTask.getId())
            .fetchOne();

    if (mailMessage != null) {
      body = mailMessage.getBody();
      String note = getHtmlFromTextile(redmineJournal.getNotes()).replaceAll("\"", "'");

      if (!StringUtils.isBlank(body)) {

        if (!StringUtils.isBlank(note)) {

          try {
            body = body.replaceAll(",\"content\":\".*\",", ",\"content\":\"" + note + "\",");
          } catch (Exception e) {
          }
        } else {
          body = body.replaceAll(",\"content\":\".*\",", ",\"content\":\"\",");
        }
      }
    } else {
      body = getJournalDetails(redmineJournal, teamTask);

      if (!StringUtils.isBlank(body)) {
        mailMessage = new MailMessage();
        mailMessage.setRelatedId(teamTask.getId());
        mailMessage.setRelatedModel(TeamTask.class.getName());
        mailMessage.setRelatedName(teamTask.getFullName());
        mailMessage.setSubject("Task updated (Redmine)");
        mailMessage.setType(
            CollectionUtils.isNotEmpty(redmineJournal.getDetails()) ? "notification" : "comment");
        mailMessage.setRedmineId(redmineJournal.getId());
        User redmineUser = getOsUser(redmineJournal.getUser().getId());
        mailMessage.setAuthor(redmineUser);
        setCreatedByUser(mailMessage, redmineUser, "setCreatedBy");
        setLocalDateTime(mailMessage, redmineJournal.getCreatedOn(), "setCreatedOn");
      }
    }

    if (!StringUtils.isBlank(body)) {
      mailMessage.setBody(body);
    }

    return mailMessage;
  }

  public String getJournalDetails(Journal redmineJournal, TeamTask teamTask) {

    List<JournalDetail> journalDetails = redmineJournal.getDetails();

    if (journalDetails != null && !journalDetails.isEmpty()) {
      StringBuilder strBuilder = new StringBuilder();
      StringBuilder trackStrBuilder = new StringBuilder();
      strBuilder.append("{\"title\":\"Task updated (Redmine)\",\"tracks\":[");
      getTrack(journalDetails, trackStrBuilder, teamTask);

      if (trackStrBuilder.length() > 0) {
        trackStrBuilder.deleteCharAt(trackStrBuilder.length() - 1);
        strBuilder.append(trackStrBuilder);
      }

      strBuilder.append("],\"content\":\"");
      String note = redmineJournal.getNotes();

      if (!StringUtils.isBlank(note)) {
        strBuilder.append(getHtmlFromTextile(note).replaceAll("\"", "'"));
      }

      strBuilder.append("\",\"tags\":[]}");

      if (StringUtils.isBlank(trackStrBuilder.toString())
          && StringUtils.isBlank(redmineJournal.getNotes())) {
        return null;
      }

      return strBuilder.toString();
    }

    String note = redmineJournal.getNotes();
    return getHtmlFromTextile(note).replaceAll("\"", "'");
  }

  public void getTrack(
      List<JournalDetail> journalDetails, StringBuilder trackStrBuilder, TeamTask teamTask) {

    for (JournalDetail journalDetail : journalDetails) {

      String[] fieldNames = getFieldName(journalDetail.getName(), journalDetail.getProperty());
      trackStrBuilder.append(
          "{\"name\":\""
              + fieldNames[0]
              + "\",\"title\":\""
              + fieldNames[1]
              + "\",\"oldValue\":\"");

      if (!StringUtils.isBlank(journalDetail.getOldValue())) {
        String oldValue =
            getValue(
                journalDetail.getOldValue(), journalDetail.getName(), journalDetail.getProperty());
        trackStrBuilder.append(oldValue);
      }

      trackStrBuilder.append("\",\"value\":\"");

      if (!StringUtils.isBlank(journalDetail.getNewValue())) {
        String newValue =
            getValue(
                journalDetail.getNewValue(), journalDetail.getName(), journalDetail.getProperty());
        trackStrBuilder.append(newValue);
      }

      trackStrBuilder.append("\"},");
    }
  }

  public String[] getFieldName(String name, String journalDetailProperty) {

    if (journalDetailProperty.equals("attr")) {

      if (name.equals("project_id")) {
        return new String[] {"project", "Project"};
      } else if (name.equals("subject")) {
        return new String[] {"name", "Name"};
      } else if (name.equals("parent_id")) {
        return new String[] {"parentTask", "Parent task"};
      } else if (name.equals("assigned_to_id")) {
        return new String[] {"assignedTo", "Assigned to"};
      } else if (name.equals("start_date")) {
        return new String[] {"taskDate", "Task date"};
      } else if (name.equals("done_ratio")) {
        return new String[] {"progressSelect", "Progress"};
      } else if (name.equals("estimated_hours")) {
        return new String[] {"budgetedTime", "Estimated time"};
      } else if (name.equals("tracker_id")) {
        return new String[] {"teamTaskCategory", "Category"};
      } else if (name.equals("priority_id")) {
        return new String[] {"priority", "Priority"};
      } else if (name.equals("status_id")) {
        return new String[] {"status", "Status"};
      } else if (name.equals("description")) {
        return new String[] {"description", "Description"};
      } else if (name.equals("fixed_version_id")) {
        return new String[] {"fixedVersion", "Fixed version"};
      }
    } else if (journalDetailProperty.equals("cf")) {
      name = redmineCfNameMap.get(name);

      if (name.equals(redmineIssueProduct)) {
        return new String[] {"product", "Product"};
      } else if (name.equals(redmineIssueDueDate)) {
        return new String[] {"dueDate", "Due date"};
      } else if (name.equals(redmineIssueEstimatedTime)) {
        return new String[] {"estimatedTime", "Estimated time"};
      } else if (name.equals(redmineIssueInvoiced)) {
        return new String[] {"invoiced", "Invoiced"};
      } else if (name.equals(redmineIssueAccountedForMaintenance)) {
        return new String[] {"accountedForMaintenance", "Accounted for maintenance"};
      } else if (name.equals(redmineIssueIsTaskAccepted)) {
        return new String[] {"isTaskAccepted", "Task Accepted"};
      } else if (name.equals(redmineIssueIsOffered)) {
        return new String[] {"isOffered", "Offered"};
      } else if (name.equals(redmineIssueUnitPrice)) {
        return new String[] {"unitPrice", "Unit price"};
      }
    }

    return new String[] {name, name};
  }

  public String getValue(String value, String journalDetailName, String journalDetailProperty) {

    value = value.replaceAll("\"", "'");

    if (journalDetailProperty.equals("attr")) {

      switch (journalDetailName) {
        case "status_id":
          value = (String) selectionMap.get(fieldMap.get(redmineStatusMap.get(value)));
          break;
        case "tracker_id":
          value = fieldMap.get(redmineTrackerMap.get(value));
          break;
        case "priority_id":
          value = (String) selectionMap.get(fieldMap.get(redminePriorityMap.get(value)));
          break;
        case "assigned_to_id":
          value = getUserName(value);
          break;
        case "project_id":
          value = getProjectName(value);
          break;
        case "fixed_version_id":
          value = getVersionName(value);
          break;
        case "parent_id":
          value = getTeamTaskName(value);
        case "description":
          value = getHtmlFromTextile(value);
        default:
          break;
      }
    } else if (journalDetailProperty.equals("cf")) {
      String cfType = redmineCfTypeMap.get(journalDetailName);

      switch (cfType) {
        case "bool":
          value = value.equals("1") ? "true" : "false";
          break;
        case "user":
          value = getUserName(value);
          break;
        case "version":
          value = getVersionName(value);
          break;
        default:
          break;
      }
    }

    return value;
  }

  public String getUserName(String value) {

    if (!userMap.containsKey(value)) {
      User user = getOsUser(Integer.parseInt(value));
      String name = user != null ? user.getFullName() : "Unknown user";
      userMap.put(value, name);
      return name;
    }

    return userMap.get(value);
  }

  public String getProjectName(String value) {

    if (!projectMap.containsKey(value)) {
      project = projectRepo.findByRedmineId(Integer.parseInt(value));
      String name = project != null ? project.getFullName() : "Unknown project";
      projectMap.put(value, name);
      return name;
    }

    return projectMap.get(value);
  }

  // May not found teamtask name if parent task is not imported already

  public String getTeamTaskName(String value) {

    if (!teamTaskMap.containsKey(value)) {
      TeamTask teamTask = teamTaskRepo.findByRedmineId(Integer.parseInt(value));
      String name = teamTask != null ? teamTask.getFullName() : "Unknown task";
      teamTaskMap.put(value, name);
      return name;
    }

    return teamTaskMap.get(value);
  }

  // Need to modify after completion of Feature #32081

  public String getVersionName(String value) {

    String redmineProjectVersionName = "";

    if (!versionMap.containsKey(value)) {

      try {
        Version redmineProjectVersion =
            redmineProjectManager.getVersionById(Integer.parseInt(value));
        redmineProjectVersionName = redmineProjectVersion.getName();
      } catch (Exception e) {
        TraceBackService.trace(e, "", batch.getId());
      }
      versionMap.put(value, redmineProjectVersionName);
    } else {
      redmineProjectVersionName = versionMap.get(value);
    }

    return redmineProjectVersionName;
  }

  @Transactional
  public void deleteUnwantedMailMessages() {

    List<Long> idList = updatedOnMap.keySet().stream().collect(Collectors.toList());

    mailMessageRepository
        .all()
        .filter(
            "self.relatedId IN ("
                + Joiner.on(",").join(idList)
                + ") AND self.relatedModel = ?1 AND (self.redmineId = null OR self.redmineId = 0) AND (self.createdOn >= ?2 OR self.updatedOn >= ?2)",
            TeamTask.class.getName(),
            batch.getStartDate().toLocalDateTime())
        .delete();
  }
}
