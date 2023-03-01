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
package com.axelor.apps.redmine.service.imports.projects;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectPriority;
import com.axelor.apps.project.db.ProjectStatus;
import com.axelor.apps.project.db.ProjectTaskCategory;
import com.axelor.apps.project.db.repo.ProjectPriorityRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.ProjectStatusRepository;
import com.axelor.apps.project.db.repo.ProjectTaskCategoryRepository;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.apps.redmine.db.RedmineImportMapping;
import com.axelor.apps.redmine.db.repo.RedmineImportConfigRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.redmine.service.common.RedmineCommonService;
import com.axelor.apps.redmine.service.imports.projects.pojo.MethodParameters;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaStore;
import com.axelor.meta.schema.views.Selection.Option;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.bean.Membership;
import com.taskadapter.redmineapi.bean.Tracker;
import com.taskadapter.redmineapi.bean.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.Query;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineImportProjectServiceImpl extends RedmineCommonService
    implements RedmineImportProjectService {

  protected RedmineImportMappingRepository redmineImportMappingRepository;
  protected ProjectVersionRepository projectVersionRepo;
  protected AppBaseService appBaseService;
  protected ProjectStatusRepository projectStatusRepo;
  protected ProjectPriorityRepository projectPriorityRepo;

  @Inject
  public RedmineImportProjectServiceImpl(
      UserRepository userRepo,
      EmployeeRepository employeeRepo,
      ProjectRepository projectRepo,
      ProductRepository productRepo,
      ProjectTaskRepository projectTaskRepo,
      ProjectTaskCategoryRepository projectCategoryRepo,
      PartnerRepository partnerRepo,
      RedmineImportMappingRepository redmineImportMappingRepository,
      AppRedmineRepository appRedmineRepo,
      CompanyRepository companyRepo,
      ProjectVersionRepository projectVersionRepo,
      AppBaseService appBaseService,
      ProjectPriorityRepository projectPriorityRepo,
      ProjectStatusRepository projectStatusRepo) {

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
    this.redmineImportMappingRepository = redmineImportMappingRepository;
    this.projectVersionRepo = projectVersionRepo;
    this.appBaseService = appBaseService;
    this.projectStatusRepo = projectStatusRepo;
    this.projectPriorityRepo = projectPriorityRepo;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());
  protected Long defaultCompanyId;
  protected String redmineProjectClientPartnerDefault;
  protected String redmineProjectInvoicingSequenceSelectDefault;
  protected String redmineProjectInvoiceable;
  protected String redmineProjectClientPartner;
  protected String redmineProjectInvoicingSequenceSelect;
  protected String redmineProjectAssignedTo;
  protected String redmineVersionDeliveryDate;

  protected boolean isAppBusinessSupport;

  protected List<Integer> redmineProjectVersionIdList = new ArrayList<>();

  @Override
  @SuppressWarnings("unchecked")
  @Transactional
  public void importProject(
      List<com.taskadapter.redmineapi.bean.Project> redmineProjectList,
      MethodParameters methodParameters) {

    if (redmineProjectList != null && !redmineProjectList.isEmpty()) {
      this.fieldMap = new HashMap<>();
      this.selectionMap = new HashMap<>();
      this.methodParameters = methodParameters;

      AppRedmine appRedmine = appRedmineRepo.all().fetchOne();
      isAppBusinessSupport = appBaseService.isApp("business-support");

      this.redmineProjectInvoiceable = appRedmine.getRedmineProjectInvoiceable();
      this.redmineProjectClientPartner = appRedmine.getRedmineProjectClientPartner();
      this.redmineProjectInvoicingSequenceSelect =
          appRedmine.getRedmineProjectInvoicingSequenceSelect();
      this.redmineProjectAssignedTo = appRedmine.getRedmineProjectAssignedTo();
      this.redmineVersionDeliveryDate = appRedmine.getRedmineVersionDeliveryDate();

      this.defaultCompanyId = appRedmine.getCompany().getId();
      this.redmineProjectClientPartnerDefault = appRedmine.getRedmineProjectClientPartnerDefault();
      this.redmineProjectInvoicingSequenceSelectDefault =
          appRedmine.getRedmineProjectInvoicingSequenceSelectDefault();

      serverTimeZone = appRedmine.getServerTimezone();

      fillFieldMapWithImportMappingList();
      fillSelectionMapWithSelectionList();
      importProjectsFromList(redmineProjectList);

      updateTransaction();

      // SET PROJECTS PARENTS

      if (!parentMap.isEmpty()) {
        this.setParentProjects();
      }

      if (!updatedOnMap.isEmpty()) {
        executeProjectUpdatingQuery();
      }
    }

    String resultStr =
        String.format("Redmine Project -> ABS Project : Success: %d Fail: %d", success, fail);
    setResult(getResult() + String.format("%s \n", resultStr));
    LOG.debug(resultStr);
    success = 0;
    fail = 0;
  }

  protected void fillFieldMapWithImportMappingList() {
    List<RedmineImportMapping> redmineImportMappingList =
        redmineImportMappingRepository
            .all()
            .filter(
                "self.redmineImportConfig.redmineMappingFieldSelect in (?1, ?2)",
                RedmineImportConfigRepository.MAPPING_FIELD_PROJECT_TRACKER,
                RedmineImportConfigRepository.MAPPING_FIELD_VERSION_STATUS)
            .fetch();

    for (RedmineImportMapping redmineImportMapping : redmineImportMappingList) {
      fieldMap.put(redmineImportMapping.getRedmineValue(), redmineImportMapping.getOsValue());
    }
  }

  protected void fillSelectionMapWithSelectionList() {
    ArrayList<Option> selectionList =
        new ArrayList<>(MetaStore.getSelectionList("support.project.version.status.select"));
    ResourceBundle fr = I18n.getBundle(Locale.FRANCE);
    ResourceBundle en = I18n.getBundle(Locale.ENGLISH);

    for (Option option : selectionList) {
      selectionMap.put(fr.getString(option.getTitle()), Integer.parseInt(option.getValue()));
      selectionMap.put(en.getString(option.getTitle()), Integer.parseInt(option.getValue()));
    }
  }

  public void executeProjectUpdatingQuery() {
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

    /*String query =
        String.format(
            "UPDATE project_project as project SET updated_on = v.updated_on from (values %s) as v(id,updated_on) where project.id = v.id",
            values);*/

    //JPA.em().createNativeQuery(query).executeUpdate();

    String jpql = "UPDATE Project p SET p.updatedOn = :updated_on WHERE p.id IN :ids";
    Query query = JPA.em().createQuery(jpql);
    query.setParameter("updated_on", updatedOn);
    query.setParameter("ids", ids);
    query.executeUpdate();
  }

  public void importProjectsFromList(
      List<com.taskadapter.redmineapi.bean.Project> redmineProjectList) {

    int i = 0;

    for (com.taskadapter.redmineapi.bean.Project redmineProject : redmineProjectList) {

      errors = new Object[] {};

      try {
        this.createOpenSuiteProject(redmineProject);

        if (errors.length > 0) {
          setErrorLog(
              I18n.get(IMessage.REDMINE_IMPORT_PROJECT_ERROR), redmineProject.getIdentifier());
        }
      } finally {
        if (++i % AbstractBatch.FETCH_LIMIT == 0) {
          updateTransaction();
        }
      }
    }
  }

  public void createOpenSuiteProject(com.taskadapter.redmineapi.bean.Project redmineProject) {

    this.setRedmineCustomFieldsMap(redmineProject.getCustomFields());

    Project project = projectRepo.findByRedmineId(redmineProject.getId());
    LocalDateTime redmineUpdatedOn = getRedmineDate(redmineProject.getUpdatedOn());
    LocalDateTime lastBatchUpdatedOn = methodParameters.getLastBatchUpdatedOn();

    if (project == null) {
      project = new Project();
      project.setRedmineId(redmineProject.getId());
      project.setCode(redmineProject.getIdentifier().toUpperCase());

      List<ProjectStatus> projectStatuses =
          projectStatusRepo
              .all()
              .filter("self.relatedToSelect = ?1", ProjectStatusRepository.PROJECT_STATUS_TASK)
              .fetch();

      List<ProjectPriority> projectPriorities = projectPriorityRepo.all().fetch();

      Project finalProject = project;
      Stream.concat(projectStatuses.stream(), projectPriorities.stream())
          .filter(Objects::nonNull)
          .forEach(
              item -> {
                if (item instanceof ProjectStatus) {
                  finalProject.addProjectTaskStatusSetItem((ProjectStatus) item);
                } else if (item instanceof ProjectPriority) {
                  finalProject.addProjectTaskPrioritySetItem((ProjectPriority) item);
                }
              });
    } else if (lastBatchUpdatedOn != null
        && (redmineUpdatedOn.isBefore(lastBatchUpdatedOn)
            || (project.getUpdatedOn().isAfter(lastBatchUpdatedOn)
                && project.getUpdatedOn().isAfter(redmineUpdatedOn)))) {

      updateExistingProject(redmineProject, project);

      return;
    }

    LOG.debug("Importing project: " + redmineProject.getIdentifier());

    this.setProjectFields(redmineProject, project);
    saveNewProject(redmineProject, project, redmineUpdatedOn);
  }

  @Transactional
  protected void saveNewProject(
      com.taskadapter.redmineapi.bean.Project redmineProject,
      Project project,
      LocalDateTime redmineUpdatedOn) {
    try {

      Batch batch = methodParameters.getBatch();

      if (project.getId() == null) {
        project.addCreatedBatchSetItem(batch);
      } else {
        project.addUpdatedBatchSetItem(batch);
      }

      projectRepo.save(project);
      updatedOnMap.put(project.getId(), redmineUpdatedOn);

      if (isAppBusinessSupport) {
        importProjectVersions(redmineProject.getId(), project);
      }

      // CREATE MAP FOR CHILD-PARENT TASKS

      if (redmineProject.getParentId() != null) {
        parentMap.put(project.getId(), redmineProject.getParentId());
      }

      methodParameters.getOnSuccess().accept(project);
      success++;
    } catch (Exception e) {
      methodParameters.getOnError().accept(e);
      fail++;
      TraceBackService.trace(e, "", methodParameters.getBatch().getId());
    }
  }

  @Transactional
  protected void updateExistingProject(
      com.taskadapter.redmineapi.bean.Project redmineProject, Project project) {
    LOG.debug("Updating project members, trackers and versions: " + redmineProject.getIdentifier());

    importProjectMembersAndTrackers(redmineProject, project);
    projectRepo.save(project);

    if (isAppBusinessSupport) {
      importProjectVersions(redmineProject.getId(), project);
    }
  }

  @Transactional
  public void setParentProjects() {

    Project project;
    Project parentProject;
    HashMap<Integer, Project> parentProjectMap = new HashMap<>();

    for (Map.Entry<Long, Integer> entry : parentMap.entrySet()) {

      if (parentProjectMap.containsKey(entry.getValue())) {
        parentProject = parentProjectMap.get(entry.getValue());
      } else {
        parentProject = projectRepo.findByRedmineId(entry.getValue());
        parentProjectMap.put(entry.getValue(), parentProject);
      }

      if (parentProject != null) {
        project = projectRepo.find(entry.getKey());
        project.setParentProject(parentProject);
        projectRepo.save(project);
      }
    }
  }

  public void setProjectFields(
      com.taskadapter.redmineapi.bean.Project redmineProject, Project project) {

    project.setName(redmineProject.getName());
    project.setDescription(getHtmlFromTextile(redmineProject.getDescription()));
    project.setCompany(companyRepo.find(defaultCompanyId));

    String projectInvoiceable = redmineCustomFieldsMap.get(redmineProjectInvoiceable);

    boolean invoiceable = projectInvoiceable != null && (projectInvoiceable.equals("1"));
    project.setToInvoice(invoiceable);
    project.setIsBusinessProject(invoiceable);

    String projectAssignedTo = redmineCustomFieldsMap.get(redmineProjectAssignedTo);
    project.setAssignedTo(
        StringUtils.isNotEmpty(projectAssignedTo)
            ? getOsUser(Integer.parseInt(projectAssignedTo))
            : null);

    // ERROR AND IMPORT IF CLIENT PARTNER NOT FOUND

    String projectClient =
        StringUtils.isNotEmpty(redmineCustomFieldsMap.get(redmineProjectClientPartner))
            ? redmineCustomFieldsMap.get(redmineProjectClientPartner)
            : redmineProjectClientPartnerDefault;

    if (projectClient != null) {
      changeClientPartner(project, projectClient);
    } else {
      project.setClientPartner(null);
    }

    getMembersAndTrackers(redmineProject, project);
    importProjectMembersAndTrackers(redmineProject, project);

    project.setProjectStatus(
        projectStatusRepo
            .all()
            .filter(
                redmineProject.getStatus().equals(REDMINE_PROJECT_STATUS_CLOSED)
                    ? "self.relatedToSelect = ?1 and self.isDefaultCompleted = true"
                    : "self.relatedToSelect = ?1",
                ProjectStatusRepository.PROJECT_STATUS_PROJECT)
            .order("sequence")
            .fetchOne());

    // ERROR AND IMPORT IF INVOICING TYPE NOT FOUND

    String projectInvoicingSequence =
        StringUtils.isNotEmpty(redmineCustomFieldsMap.get(redmineProjectInvoicingSequenceSelect))
            ? redmineCustomFieldsMap.get(redmineProjectInvoicingSequenceSelect)
            : redmineProjectInvoicingSequenceSelectDefault;

    if (projectInvoicingSequence != null) {
      int invoicingSequenceSelect =
          projectInvoicingSequence.equals("Empty")
              ? ProjectRepository.INVOICING_SEQ_EMPTY
              : projectInvoicingSequence.equals("Pre-invoiced")
                  ? ProjectRepository.INVOICING_SEQ_INVOICE_PRE_TASK
                  : projectInvoicingSequence.equals("Post-invoiced")
                      ? ProjectRepository.INVOICING_SEQ_INVOICE_POST_TASK
                      : -1;

      if (invoicingSequenceSelect >= 0) {
        project.setInvoicingSequenceSelect(invoicingSequenceSelect);
      } else {
        errors =
            errors.length == 0
                ? new Object[] {I18n.get(IMessage.REDMINE_IMPORT_INVOICING_TYPE_NOT_FOUND)}
                : ObjectArrays.concat(
                    errors,
                    new Object[] {I18n.get(IMessage.REDMINE_IMPORT_INVOICING_TYPE_NOT_FOUND)},
                    Object.class);
      }
    } else {
      project.setInvoicingSequenceSelect(null);
    }

    setLocalDateTime(project, redmineProject.getCreatedOn(), "setCreatedOn");
  }

  protected void changeClientPartner(Project project, String projectClient) {
    Partner partner = partnerRepo.findByReference(projectClient);

    if (partner != null) {
      project.setClientPartner(partner);
    } else {
      errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_CLIENT_PARTNER_NOT_FOUND)};
    }
  }

  public void importProjectMembersAndTrackers(
      com.taskadapter.redmineapi.bean.Project redmineProject, Project project) {

    // Import members

    getMembersAndTrackers(redmineProject, project);
  }

  protected void getMembersAndTrackers(
      com.taskadapter.redmineapi.bean.Project redmineProject, Project project) {
    try {
      List<Membership> redmineProjectMembers =
          methodParameters
              .getRedmineManager()
              .getProjectManager()
              .getProjectMembers(redmineProject.getId());

      if (redmineProjectMembers != null && !redmineProjectMembers.isEmpty()) {

        for (Membership membership : redmineProjectMembers) {
          User user = getOsUser(membership.getUserId());

          if (user != null) {
            project.addMembersUserSetItem(user);
          }
        }
      } else {
        project.clearMembersUserSet();
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", methodParameters.getBatch().getId());
    }

    Collection<Tracker> redmineTrackers = redmineProject.getTrackers();

    if (redmineTrackers != null && !redmineTrackers.isEmpty()) {

      for (Tracker tracker : redmineTrackers) {
        ProjectTaskCategory projectCategory =
            projectCategoryRepo.findByName(fieldMap.get(tracker.getName()));

        if (projectCategory != null) {
          project.addProjectTaskCategorySetItem(projectCategory);
        }
      }
    } else {
      project.clearProjectTaskCategorySet();
    }
  }

  @Transactional
  public void importProjectVersions(Integer redmineProjectId, Project project) {

    try {
      List<Version> redmineVersionList =
          methodParameters.getRedmineManager().getProjectManager().getVersions(redmineProjectId);

      if (CollectionUtils.isNotEmpty(redmineVersionList)) {

        for (Version redmineVersion : redmineVersionList) {
          ProjectVersion version = projectVersionRepo.findByRedmineId(redmineVersion.getId());

          if (version == null || !redmineProjectVersionIdList.contains(redmineVersion.getId())) {

            if (version == null) {
              version = new ProjectVersion();
              version.setRedmineId(redmineVersion.getId());
            }

            version.setStatusSelect(
                (Integer) selectionMap.get(fieldMap.get(redmineVersion.getStatus())));
            version.setTitle(redmineVersion.getName());
            version.setContent(redmineVersion.getDescription());
            version.setTestingServerDate(
                redmineVersion.getDueDate() != null
                    ? redmineVersion
                        .getDueDate()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    : null);

            setRedmineCustomFieldsMap(redmineVersion.getCustomFields());

            String value = redmineCustomFieldsMap.get(redmineVersionDeliveryDate);
            version.setProductionServerDate(
                StringUtils.isNotEmpty(value) ? LocalDate.parse(value) : null);

            redmineProjectVersionIdList.add(redmineVersion.getId());
          }

          version.addProjectSetItem(project);
          projectVersionRepo.save(version);
        }
      }
    } catch (RedmineException e) {
      methodParameters.getOnError().accept(e);
      TraceBackService.trace(e, "", methodParameters.getBatch().getId());
    }
  }
}
