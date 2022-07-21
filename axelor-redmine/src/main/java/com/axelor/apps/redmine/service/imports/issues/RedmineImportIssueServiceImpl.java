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
package com.axelor.apps.redmine.service.imports.issues;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectPriority;
import com.axelor.apps.project.db.ProjectStatus;
import com.axelor.apps.project.db.ProjectTask;
import com.axelor.apps.project.db.ProjectTaskCategory;
import com.axelor.apps.project.db.repo.ProjectPriorityRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.ProjectStatusRepository;
import com.axelor.apps.project.db.repo.ProjectTaskCategoryRepository;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.RedmineImportMapping;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportConfigRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.redmine.service.ProjectTaskRedmineService;
import com.axelor.apps.redmine.service.common.RedmineCommonService;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.axelor.meta.MetaStore;
import com.axelor.meta.schema.views.Selection.Option;
import com.google.common.base.Joiner;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.CustomFieldManager;
import com.taskadapter.redmineapi.Include;
import com.taskadapter.redmineapi.IssueManager;
import com.taskadapter.redmineapi.Params;
import com.taskadapter.redmineapi.ProjectManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.CustomFieldDefinition;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Journal;
import com.taskadapter.redmineapi.bean.JournalDetail;
import com.taskadapter.redmineapi.bean.Version;
import groovy.json.StringEscapeUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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

public class RedmineImportIssueServiceImpl extends RedmineCommonService
    implements RedmineImportIssueService {

  protected ProductRepository productRepo;
  protected ProjectTaskRepository projectTaskRepo;
  protected ProjectTaskCategoryRepository projectTaskCategoryRepo;
  protected ProjectVersionRepository projectVersionRepo;
  protected ProjectTaskRedmineService projectTaskRedmineService;
  protected MailMessageRepository mailMessageRepo;
  protected ProjectStatusRepository projectStatusRepo;
  protected ProjectPriorityRepository projectPriorityRepo;
  protected RedmineBatchRepository redmineBatchRepo;

  protected ProjectManager redmineProjectManager;
  protected IssueManager redmineIssueManager;

  protected Map<String, String> userMap = new HashMap<>();
  protected Map<String, String> projectMap = new HashMap<>();
  protected Map<String, String> projectTaskMap = new HashMap<>();
  protected Map<String, String> versionMap = new HashMap<>();
  protected Map<String, String> redmineStatusMap = new HashMap<>();
  protected Map<String, String> redminePriorityMap = new HashMap<>();
  protected Map<String, String> redmineTrackerMap = new HashMap<>();
  protected Map<String, String> redmineCfNameMap = new HashMap<>();
  protected Map<String, String> redmineCfTypeMap = new HashMap<>();

  protected List<Long> projectVersionIdList = new ArrayList<>();

  protected String redmineIssueProduct;
  protected String redmineIssueDueDate;
  protected String redmineIssueEstimatedTime;
  protected String redmineIssueInvoiced;
  protected String redmineIssueAccountedForMaintenance;
  protected String redmineIssueIsTaskAccepted;
  protected String redmineIssueIsOffered;
  protected String redmineIssueUnitPrice;
  protected String redmineIssueProductDefault;
  protected LocalDate redmineIssueDueDateDefault;
  protected BigDecimal redmineIssueEstimatedTimeDefault;
  protected BigDecimal redmineIssueUnitPriceDefault;

  protected Product product;
  protected Project project;
  protected ProjectTaskCategory projectTaskCategory;
  protected ProjectStatus projectTaskStatus;
  protected ProjectPriority projectTaskPriority;

  protected boolean isImportIssuesWithActivities;
  protected boolean isAppBusinessSupport;

  @Inject
  public RedmineImportIssueServiceImpl(
      UserRepository userRepo,
      EmployeeRepository employeeRepo,
      ProjectRepository projectRepo,
      RedmineImportMappingRepository redmineImportMappingRepo,
      AppBaseService appBaseService,
      ProductRepository productRepo,
      ProjectTaskRepository projectTaskRepo,
      ProjectTaskCategoryRepository projectTaskCategoryRepo,
      ProjectVersionRepository projectVersionRepo,
      ProjectTaskRedmineService projectTaskRedmineService,
      MailMessageRepository mailMessageRepo,
      ProjectStatusRepository projectStatusRepo,
      ProjectPriorityRepository projectPriorityRepo,
      RedmineBatchRepository redmineBatchRepo) {

    super(userRepo, employeeRepo, projectRepo, redmineImportMappingRepo, appBaseService);

    this.productRepo = productRepo;
    this.projectTaskRepo = projectTaskRepo;
    this.projectTaskCategoryRepo = projectTaskCategoryRepo;
    this.projectVersionRepo = projectVersionRepo;
    this.projectTaskRedmineService = projectTaskRedmineService;
    this.mailMessageRepo = mailMessageRepo;
    this.projectStatusRepo = projectStatusRepo;
    this.projectPriorityRepo = projectPriorityRepo;
    this.redmineBatchRepo = redmineBatchRepo;
  }

  @Override
  public String redmineIssuesImportProcess(
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
      prepareParamsAndImportRedmineIssues(lastBatchEndDate);
    } catch (RedmineException e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }

    updateTransaction();

    if (!parentMap.isEmpty()) {
      setParentTasks();
      updateTransaction();
    }

    if (CollectionUtils.isNotEmpty(projectVersionIdList)) {
      updateProjectVersionsProgress();
      updateTransaction();
    }

    String resultStr =
        String.format("Redmine Issue -> AOS ProjectTask : Success: %d Fail: %d", success, fail);
    LOG.debug(resultStr);
    success = fail = 0;

    return resultStr;
  }

  public void prepareParamsAndImportRedmineIssues(ZonedDateTime lastBatchEndDate)
      throws RedmineException {

    LOG.debug("Preparing params, managers, maps and other variables for issues import process..");

    RedmineBatch redmineBatch = batch.getRedmineBatch();
    String failedRedmineIssuesIds = redmineBatch.getFailedRedmineIssuesIds();

    Params params = prepareParams(failedRedmineIssuesIds, lastBatchEndDate);
    Params failedIdsParams = prepareFailedIdsParams(failedRedmineIssuesIds);

    redmineIssueManager = redmineManager.getIssueManager();

    setDefaultFieldsAndMaps();

    String newFailedRedmineIssuesIds = null;

    try {
      newFailedRedmineIssuesIds = importRedmineIssues(params, newFailedRedmineIssuesIds);

      if (CollectionUtils.isNotEmpty(failedIdsParams.getList())) {

        LOG.debug("Start fetching and importing failed issues from previous batch run..");

        newFailedRedmineIssuesIds = importRedmineIssues(failedIdsParams, newFailedRedmineIssuesIds);
      }
    } catch (Exception e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }

    redmineBatch = redmineBatchRepo.find(redmineBatch.getId());
    redmineBatch.setFailedRedmineIssuesIds(newFailedRedmineIssuesIds);
  }

  public String importRedmineIssues(Params params, String newFailedRedmineIssuesIds)
      throws RedmineException {

    totalFetchCount = 0;
    LOG.debug("Fetching issues from redmine as per fetch limit..");

    List<Issue> redmineIssueList = fetchRedmineIssuesList(params);

    if (CollectionUtils.isNotEmpty(redmineIssueList)) {
      do {
        for (Issue redmineIssue : redmineIssueList) {
          LOG.debug("Importing issue: {}", redmineIssue.getId());

          setRedmineCustomFieldsMap(redmineIssue.getCustomFields());

          if (!validateRequiredFields(
              redmineIssue.getProjectId(),
              redmineIssue.getTracker().getName(),
              redmineIssue.getStatusName(),
              redmineIssue.getPriorityText())) {
            newFailedRedmineIssuesIds =
                StringUtils.isEmpty(newFailedRedmineIssuesIds)
                    ? String.valueOf(redmineIssue.getId())
                    : newFailedRedmineIssuesIds + "," + redmineIssue.getId();
            setErrorLog(redmineIssue.getId());
            fail++;
            continue;
          }

          try {
            importRedmineIssue(redmineIssue);
            setErrorLog(redmineIssue.getId());
          } finally {
            updateTransaction();
          }
        }
        setUpdatedOnFromRedmine();
        LOG.debug("Fetching issues from redmine as per fetch limit..");
        redmineIssueList = fetchRedmineIssuesList(params);

      } while (CollectionUtils.isNotEmpty(redmineIssueList));
    }
    return newFailedRedmineIssuesIds;
  }

  public boolean validateRequiredFields(
      Integer redmineIssueProjectId,
      String redmineIssueTrackerName,
      String redmineIssueStatusName,
      String redmineIssuePrioriy) {

    String value =
        StringUtils.isNotEmpty(redmineCustomFieldsMap.get(redmineIssueProduct))
            ? redmineCustomFieldsMap.get(redmineIssueProduct)
            : redmineIssueProductDefault;

    if (!StringUtils.isEmpty(value)) {
      product = productRepo.findByCode(value);
      if (product == null) {
        errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PRODUCT_NOT_FOUND)};
        return false;
      }
    } else {
      product = null;
    }

    project = projectRepo.findByRedmineId(redmineIssueProjectId);
    if (project == null) {
      errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PROJECT_NOT_FOUND)};
      return false;
    }

    projectTaskCategory = projectTaskCategoryRepo.findByName(fieldMap.get(redmineIssueTrackerName));

    if (projectTaskCategory == null) {
      errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PROJECT_CATEGORY_NOT_FOUND)};
      return false;
    }

    projectTaskStatus = projectStatusRepo.findByName(fieldMap.get(redmineIssueStatusName));

    if (projectTaskStatus == null) {
      errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PROJECT_TASK_STATUS_NOT_FOUND)};
      return false;
    }

    projectTaskPriority = projectPriorityRepo.findByName(fieldMap.get(redmineIssuePrioriy));

    if (projectTaskPriority == null) {
      errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PROJECT_TASK_PRIORITY_NOT_FOUND)};
      return false;
    }

    return true;
  }

  @Transactional
  public void importRedmineIssue(Issue redmineIssue) {

    ProjectTask projectTask = projectTaskRepo.findByRedmineId(redmineIssue.getId());
    LocalDateTime redmineUpdatedOn = getRedmineDate(redmineIssue.getUpdatedOn());

    if (projectTask == null) {
      projectTask = new ProjectTask();
      projectTask.setTypeSelect(ProjectTaskRepository.TYPE_TASK);
    } else if (lastBatchEndDate != null
        && (redmineUpdatedOn.isBefore(lastBatchEndDate)
            || (projectTask.getUpdatedOn().isAfter(lastBatchEndDate)
                && projectTask.getUpdatedOn().isAfter(redmineUpdatedOn)))) {
      return;
    }

    setProjectTaskFields(projectTask, redmineIssue);

    try {
      if (projectTask.getId() == null) {
        projectTask.addCreatedBatchSetItem(batch);
      } else {
        projectTask.addUpdatedBatchSetItem(batch);
      }

      projectTaskRepo.save(projectTask);

      updatedOnMap.put(projectTask.getId(), redmineUpdatedOn);

      Integer parentId = redmineIssue.getParentId();
      if (parentId != null) {
        parentMap.put(projectTask.getId(), parentId);
      }

      if (isImportIssuesWithActivities) {
        importIssueJournals(redmineIssue.getId(), projectTask.getId(), projectTask.getFullName());
      }

      onSuccess.accept(projectTask);
      success++;
    } catch (Exception e) {
      onError.accept(e);
      fail++;
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public void setProjectTaskFields(ProjectTask projectTask, Issue redmineIssue) {

    try {
      projectTask.setRedmineId(redmineIssue.getId());
      projectTask.setProject(project);
      projectTask.setProjectTaskCategory(projectTaskCategory);
      projectTask.setName("#" + redmineIssue.getId() + " " + redmineIssue.getSubject());
      projectTask.setDescription(getHtmlFromTextile(redmineIssue.getDescription()));

      Integer assigneeId = redmineIssue.getAssigneeId();
      User assignedTo = assigneeId != null ? getAosUser(assigneeId) : null;

      if (assignedTo == null) {
        assignedTo = project.getAssignedTo();
      }

      projectTask.setAssignedTo(assignedTo);
      projectTask.setProgressSelect(redmineIssue.getDoneRatio());

      Float estimatedHours = redmineIssue.getEstimatedHours();
      projectTask.setBudgetedTime(
          estimatedHours != null ? BigDecimal.valueOf(estimatedHours) : null);

      Date closedOn = redmineIssue.getClosedOn();
      projectTask.setTaskEndDate(
          closedOn != null
              ? closedOn.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
              : null);

      Date startDate = redmineIssue.getStartDate();
      projectTask.setTaskDate(
          startDate != null
              ? startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
              : null);

      Version targetVersion = redmineIssue.getTargetVersion();
      projectTask.setFixedVersion(targetVersion != null ? targetVersion.getName() : null);

      if (redmineIssue.getParentId() == null) {
        projectTask.setParentTask(null);
      }

      if (isAppBusinessSupport) {

        if (projectTask.getTargetVersion() != null
            && !projectVersionIdList.contains(projectTask.getTargetVersion().getId())) {
          projectVersionIdList.add(projectTask.getTargetVersion().getId());
        }

        if (targetVersion != null) {
          ProjectVersion projectVersion = projectVersionRepo.findByRedmineId(targetVersion.getId());
          projectTask.setTargetVersion(projectVersion);

          if (projectVersion != null && !projectVersionIdList.contains(projectVersion.getId())) {
            projectVersionIdList.add(projectVersion.getId());
          }
        } else {
          projectTask.setTargetVersion(null);
        }
      }

      String value = redmineCustomFieldsMap.get(redmineIssueDueDate);
      projectTask.setDueDate(
          StringUtils.isNotEmpty(value) ? LocalDate.parse(value) : redmineIssueDueDateDefault);

      value = redmineCustomFieldsMap.get(redmineIssueEstimatedTime);
      projectTask.setEstimatedTime(
          StringUtils.isNotEmpty(value) ? new BigDecimal(value) : redmineIssueEstimatedTimeDefault);

      value = redmineCustomFieldsMap.get(redmineIssueInvoiced);

      if (!projectTask.getInvoiced()) {
        projectTask.setInvoiced(StringUtils.isNotEmpty(value) ? value.equals("1") : false);
        projectTask.setProduct(product);

        value = redmineCustomFieldsMap.get(redmineIssueUnitPrice);
        projectTask.setUnitPrice(
            StringUtils.isNotEmpty(value) ? new BigDecimal(value) : redmineIssueUnitPriceDefault);
        projectTask.setUnit(null);
        projectTask.setQuantity(BigDecimal.ZERO);
        projectTask.setExTaxTotal(BigDecimal.ZERO);
        projectTask.setInvoicingType(0);
        projectTask.setToInvoice(false);
        projectTask.setCurrency(null);
      }

      value = redmineCustomFieldsMap.get(redmineIssueAccountedForMaintenance);

      projectTask.setAccountedForMaintenance(
          StringUtils.isNotEmpty(value) ? value.equals("1") : false);

      value = redmineCustomFieldsMap.get(redmineIssueIsOffered);
      projectTask.setIsOffered(StringUtils.isNotEmpty(value) ? value.equals("1") : false);

      value = redmineCustomFieldsMap.get(redmineIssueIsTaskAccepted);
      projectTask.setIsTaskAccepted(StringUtils.isNotEmpty(value) ? value.equals("1") : false);

      projectTask.setStatus(
          projectStatusRepo
              .all()
              .filter(
                  "self.name = ?1 and self.relatedToSelect = ?2",
                  fieldMap.get(redmineIssue.getStatusName()),
                  ProjectStatusRepository.PROJECT_STATUS_TASK)
              .fetchOne());

      projectTask.setPriority(projectTaskPriority);

      setCreatedByUser(projectTask, getAosUser(redmineIssue.getAuthorId()), "setCreatedBy");
      setLocalDateTime(projectTask, redmineIssue.getCreatedOn(), "setCreatedOn");
    } catch (Exception e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public Params prepareParams(String failedRedmineIssuesIds, ZonedDateTime lastBatchEndDate) {
    Params params = new Params();

    if (lastBatchEndDate != null) {
      ZonedDateTime endOn = lastBatchEndDate.withZoneSameInstant(ZoneOffset.UTC).withNano(0);

      params
          .add("set_filter", "1")
          .add("f[]", "updated_on")
          .add("op[updated_on]", ">=")
          .add("v[updated_on][]", endOn.toString());

      if (!StringUtils.isEmpty(failedRedmineIssuesIds)) {
        params
            .add("f[]", "issue_id")
            .add("op[issue_id]", "!=")
            .add("v[issue_id][]", failedRedmineIssuesIds);
      }
    } else {
      params.add("status_id", "*");
    }
    params.add("sort", "updated_on");
    return params;
  }

  public Params prepareFailedIdsParams(String failedRedmineIssuesIds) {
    Params errorIdsParams = new Params();

    if (lastBatchEndDate != null && !StringUtils.isEmpty(failedRedmineIssuesIds)) {
      errorIdsParams
          .add("set_filter", "1")
          .add("f[]", "issue_id")
          .add("op[issue_id]", "=")
          .add("v[issue_id][]", failedRedmineIssuesIds)
          .add("sort", "updated_on");
    }
    return errorIdsParams;
  }

  public List<Issue> fetchRedmineIssuesList(Params params) throws RedmineException {
    List<Issue> importIssueList = new ArrayList<>();
    List<Issue> tempImportIssueList;

    Integer limit = redmineMaxFetchLimit;
    Integer remainingFetchItems = redmineFetchLimit;

    do {
      if (remainingFetchItems > redmineMaxFetchLimit) {
        remainingFetchItems = remainingFetchItems - redmineMaxFetchLimit;
      } else {
        limit = remainingFetchItems;
        remainingFetchItems = 0;
      }

      params.add("offset", totalFetchCount.toString());
      params.add("limit", limit.toString());

      tempImportIssueList = redmineIssueManager.getIssues(params).getResults();

      params.getList().removeIf(p -> p.getName().equals("offset") || p.getName().equals("limit"));

      if (CollectionUtils.isNotEmpty(tempImportIssueList)) {
        importIssueList.addAll(tempImportIssueList);
        totalFetchCount += tempImportIssueList.size();
      } else {
        remainingFetchItems = 0;
      }
    } while (remainingFetchItems != 0);

    return importIssueList;
  }

  public void setDefaultFieldsAndMaps() {
    redmineIssueProduct = appRedmine.getRedmineIssueProduct();
    redmineIssueDueDate = appRedmine.getRedmineIssueDueDate();
    redmineIssueEstimatedTime = appRedmine.getRedmineIssueEstimatedTime();
    redmineIssueInvoiced = appRedmine.getRedmineIssueInvoiced();
    redmineIssueAccountedForMaintenance = appRedmine.getRedmineIssueAccountedForMaintenance();
    redmineIssueIsTaskAccepted = appRedmine.getRedmineIssueIsTaskAccepted();
    redmineIssueIsOffered = appRedmine.getRedmineIssueIsOffered();
    redmineIssueUnitPrice = appRedmine.getRedmineIssueUnitPrice();
    redmineIssueProductDefault = appRedmine.getRedmineIssueProductDefault();
    redmineIssueDueDateDefault = appRedmine.getRedmineIssueDueDateDefault();
    redmineIssueEstimatedTimeDefault = appRedmine.getRedmineIssueEstimatedTimeDefault();
    redmineIssueUnitPriceDefault = appRedmine.getRedmineIssueUnitPriceDefault();

    isAppBusinessSupport = appBaseService.isApp("business-support");
    isImportIssuesWithActivities = batch.getRedmineBatch().getIsImportIssuesWithActivities();

    redmineFetchLimit = batch.getRedmineBatch().getRedmineFetchLimit();

    List<Option> selectionList = new ArrayList<>();
    selectionList.addAll(MetaStore.getSelectionList("project.task.status"));
    selectionList.addAll(MetaStore.getSelectionList("project.task.priority"));

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
                "self.redmineImportConfig.redmineMappingFieldSelect in (?1, ?2, ?3)",
                RedmineImportConfigRepository.MAPPING_FIELD_PROJECT_TRACKER,
                RedmineImportConfigRepository.MAPPING_FIELD_TASK_PRIORITY,
                RedmineImportConfigRepository.MAPPING_FIELD_TASK_STATUS)
            .fetch();

    for (RedmineImportMapping redmineImportMapping : redmineImportMappingList) {
      fieldMap.put(redmineImportMapping.getRedmineValue(), redmineImportMapping.getOsValue());
    }

    if (isImportIssuesWithActivities) {
      configureRedmineManagersAndMaps(redmineManager);
    }
  }

  public void configureRedmineManagersAndMaps(RedmineManager redmineManager) {
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
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public void setParentTasks() {
    LOG.debug("Setting parent task of imported records..");

    if (!JPA.em().getTransaction().isActive()) {
      JPA.em().getTransaction().begin();
    }

    String values =
        parentMap.entrySet().stream()
            .map(entry -> "(" + entry.getKey() + "," + entry.getValue() + ")")
            .collect(Collectors.joining(","));

    String query =
        String.format(
            "UPDATE project_project_task as projectTask SET parent_task = (SELECT id from project_project_task where project_project_task.redmine_id = v.redmine_id) from (values %s) as v(id,redmine_id) where projectTask.id = v.id",
            values);

    JPA.em().createNativeQuery(query).executeUpdate();
    JPA.em().getTransaction().commit();
  }

  public void setUpdatedOnFromRedmine() {
    if (!updatedOnMap.isEmpty()) {

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
              "UPDATE project_project_task as projectTask SET updated_on = v.updated_on from (values %s) as v(id,updated_on) where projectTask.id = v.id",
              values);

      JPA.em().createNativeQuery(query).executeUpdate();
      JPA.em().getTransaction().commit();

      LOG.debug("Deleting unwanted mail messages created during import..");

      try {
        deleteUnwantedMailMessages();
      } catch (Exception e) {
        onError.accept(e);
        TraceBackService.trace(e, "", batch.getId());
      }
      updatedOnMap = new HashMap<>();
    }
  }

  @Transactional
  public void deleteUnwantedMailMessages() {
    List<Long> idList = updatedOnMap.keySet().stream().collect(Collectors.toList());

    mailMessageRepo
        .all()
        .filter(
            "self.relatedId IN ("
                + Joiner.on(",").join(idList)
                + ") AND self.relatedModel = ?1 AND (self.redmineId = null OR self.redmineId = 0) AND (self.createdOn >= ?2 OR self.updatedOn >= ?2)",
            ProjectTask.class.getName(),
            batch.getStartDate().toLocalDateTime())
        .delete();
  }

  public void updateProjectVersionsProgress() {

    LOG.debug("Setting project versions total progress..");

    List<ProjectVersion> projectVersions =
        projectVersionRepo
            .all()
            .filter("self.id IN (:versionIds)")
            .bind("versionIds", projectVersionIdList)
            .fetch();

    for (ProjectVersion projectVersion : projectVersions) {
      LOG.debug("Updating project version: {}", projectVersion.getId());
      projectTaskRedmineService.updateProjectVersionProgress(projectVersion);
    }
  }

  @Transactional
  public void importIssueJournals(
      Integer redmineIssueId, Long projectTaskId, String projectTaskFullName) {
    try {
      Issue issueWithJournal = redmineIssueManager.getIssueById(redmineIssueId, Include.journals);
      Collection<Journal> journals = issueWithJournal.getJournals();

      for (Journal redmineJournal : journals) {

        if (lastBatchEndDate != null
            && redmineJournal
                .getCreatedOn()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .isBefore(lastBatchEndDate)) {
          continue;
        }

        MailMessage mailMessage =
            getMailMessage(redmineJournal, projectTaskId, projectTaskFullName);

        if (mailMessage != null) {
          mailMessageRepo.save(mailMessage);
        }
      }
    } catch (RedmineException e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public MailMessage getMailMessage(
      Journal redmineJournal, Long projectTaskId, String projectTaskFullName) {

    String body = "";
    MailMessage mailMessage =
        mailMessageRepo
            .all()
            .filter(
                "self.redmineId = ?1 AND self.relatedId = ?2",
                redmineJournal.getId(),
                projectTaskId)
            .fetchOne();

    if (mailMessage != null) {
      body = mailMessage.getBody();
      String note = getHtmlFromTextile(redmineJournal.getNotes()).replace("\"", "'");

      if (!StringUtils.isBlank(body)) {

        if (!StringUtils.isBlank(note)) {
          body = body.replaceAll(",\"content\":\".*\",", ",\"content\":\"" + note + "\",");
        } else {
          body = body.replaceAll(",\"content\":\".*\",", ",\"content\":\"\",");
        }
        mailMessage.setBody(body);
      }
    } else {
      body = getJournalDetails(redmineJournal);

      if (!StringUtils.isBlank(body)) {
        mailMessage = new MailMessage();
        mailMessage.setBody(body);
        mailMessage.setRelatedId(projectTaskId);
        mailMessage.setRelatedModel(ProjectTask.class.getName());
        mailMessage.setRelatedName(projectTaskFullName);
        mailMessage.setSubject("Task updated (Redmine)");
        mailMessage.setType(body.contains("Task updated (Redmine)") ? "notification" : "comment");
        mailMessage.setRedmineId(redmineJournal.getId());
        User redmineUser = getAosUser(redmineJournal.getUser().getId());
        mailMessage.setAuthor(redmineUser);
        setCreatedByUser(mailMessage, redmineUser, "setCreatedBy");
        setLocalDateTime(mailMessage, redmineJournal.getCreatedOn(), "setCreatedOn");
      }
    }
    return mailMessage;
  }

  public String getJournalDetails(Journal redmineJournal) {

    List<JournalDetail> journalDetails = redmineJournal.getDetails();

    if (CollectionUtils.isNotEmpty(journalDetails)) {
      StringBuilder strBuilder = new StringBuilder();
      StringBuilder trackStrBuilder = new StringBuilder();
      strBuilder.append("{\"title\":\"Task updated (Redmine)\",\"tracks\":[");
      getTrack(journalDetails, trackStrBuilder);

      if (trackStrBuilder.length() > 0) {
        trackStrBuilder.deleteCharAt(trackStrBuilder.length() - 1);
        strBuilder.append(trackStrBuilder);
      }

      strBuilder.append("],\"content\":\"");
      String note = redmineJournal.getNotes();

      if (!StringUtils.isBlank(note)) {
        strBuilder.append(getHtmlFromTextile(note).replace("\"", "'"));
      }

      strBuilder.append("\",\"tags\":[]}");

      if (StringUtils.isBlank(trackStrBuilder.toString())
          && StringUtils.isBlank(redmineJournal.getNotes())) {
        return null;
      }

      return strBuilder.toString();
    }

    String note = redmineJournal.getNotes();
    return getHtmlFromTextile(note).replace("\"", "'");
  }

  public void getTrack(List<JournalDetail> journalDetails, StringBuilder trackStrBuilder) {

    for (JournalDetail journalDetail : journalDetails) {

      String[] fieldNames = getFieldName(journalDetail.getName(), journalDetail.getProperty());
      trackStrBuilder.append("{\"name\":\"" + fieldNames[0] + "\",\"title\":\"" + fieldNames[1]);

      if (!StringUtils.isBlank(journalDetail.getOldValue())) {
        String oldValue =
            getValue(
                journalDetail.getOldValue(), journalDetail.getName(), journalDetail.getProperty());
        trackStrBuilder.append("\",\"oldValue\":\"" + oldValue);
      }

      trackStrBuilder.append("\",\"value\":\"");

      if (!StringUtils.isBlank(journalDetail.getNewValue())) {
        String newValue =
            getValue(
                journalDetail.getNewValue(), journalDetail.getName(), journalDetail.getProperty());
        trackStrBuilder.append(StringEscapeUtils.escapeJava(newValue));
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
        return new String[] {"projectTaskCategory", "Category"};
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
    value = value.replace("\"", "'");

    if (journalDetailProperty.equals("attr")) {

      switch (journalDetailName) {
        case "status_id":
          value = fieldMap.get(redmineStatusMap.get(value));
          break;
        case "tracker_id":
          value = fieldMap.get(redmineTrackerMap.get(value));
          break;
        case "priority_id":
          value = fieldMap.get(redminePriorityMap.get(value));
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
          value = getProjectTaskName(value);
          break;
        case "description":
          value = getHtmlFromTextile(value);
          break;
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
      User user = getAosUser(Integer.parseInt(value));
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

  public String getProjectTaskName(String value) {
    if (!projectTaskMap.containsKey(value)) {
      ProjectTask projectTask = projectTaskRepo.findByRedmineId(Integer.parseInt(value));
      String name = projectTask != null ? projectTask.getFullName() : "Unknown task";
      projectTaskMap.put(value, name);
      return name;
    }
    return projectTaskMap.get(value);
  }

  public String getVersionName(String value) {
    if (!versionMap.containsKey(value)) {
      ProjectVersion projectVersion = projectVersionRepo.findByRedmineId(Integer.parseInt(value));
      String name = projectVersion != null ? projectVersion.getTitle() : "Unknown version";
      versionMap.put(value, name);
      return name;
    }
    return versionMap.get(value);
  }

  public void setErrorLog(Integer redmineIssueId) {
    if (errors.length > 0) {
      errorObjList.add(
          ObjectArrays.concat(
              new Object[] {
                I18n.get(IMessage.REDMINE_IMPORT_PROJECT_TASK_ERROR), String.valueOf(redmineIssueId)
              },
              errors,
              Object.class));

      errors = new Object[] {};
    }
  }
}
