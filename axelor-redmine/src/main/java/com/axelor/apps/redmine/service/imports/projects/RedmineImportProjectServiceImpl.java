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
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
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
import com.axelor.apps.redmine.db.RedmineImportMapping;
import com.axelor.apps.redmine.db.repo.RedmineImportConfigRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.redmine.service.common.RedmineCommonService;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.ProjectManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Membership;
import com.taskadapter.redmineapi.bean.Tracker;
import com.taskadapter.redmineapi.bean.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class RedmineImportProjectServiceImpl extends RedmineCommonService
    implements RedmineImportProjectService {

  protected ProjectTaskCategoryRepository projectTaskCategoryRepo;
  protected PartnerRepository partnerRepo;
  protected CompanyRepository companyRepo;
  protected ProjectVersionRepository projectVersionRepo;
  protected ProjectStatusRepository projectStatusRepo;
  protected ProjectPriorityRepository projectPriorityRepo;

  protected ProjectManager redmineProjectManager;

  protected String redmineProjectInvoiceable;
  protected String redmineProjectClientPartner;
  protected String redmineProjectInvoicingSequenceSelect;
  protected String redmineProjectAssignedTo;
  protected String redmineVersionDeliveryDate;

  protected String redmineProjectClientPartnerDefault;
  protected String redmineProjectInvoicingSequenceSelectDefault;
  protected Long defaultCompanyId;
  protected boolean isAppBusinessSupport;

  protected List<Integer> redmineProjectVersionIdList = new ArrayList<>();

  protected static final int REDMINE_PROJECT_STATUS_CLOSED = 5;

  @Inject
  public RedmineImportProjectServiceImpl(
      UserRepository userRepo,
      EmployeeRepository employeeRepo,
      ProjectRepository projectRepo,
      RedmineImportMappingRepository redmineImportMappingRepo,
      AppBaseService appBaseService,
      ProjectTaskCategoryRepository projectTaskCategoryRepo,
      PartnerRepository partnerRepo,
      CompanyRepository companyRepo,
      ProjectVersionRepository projectVersionRepo,
      ProjectPriorityRepository projectPriorityRepo,
      ProjectStatusRepository projectStatusRepo) {

    super(userRepo, employeeRepo, projectRepo, redmineImportMappingRepo, appBaseService);

    this.projectTaskCategoryRepo = projectTaskCategoryRepo;
    this.partnerRepo = partnerRepo;
    this.companyRepo = companyRepo;
    this.projectVersionRepo = projectVersionRepo;
    this.projectStatusRepo = projectStatusRepo;
    this.projectPriorityRepo = projectPriorityRepo;
  }

  @Override
  public String redmineProjectsImportProcess(
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
      importRedmineProjects();
    } catch (RedmineException e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }

    updateTransaction();

    if (!parentMap.isEmpty()) {
      setParentProjects();
      updateTransaction();
    }

    try {
      if (!updatedOnMap.isEmpty()) {
        setUpdatedOnFromRedmine();
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    String resultStr =
        String.format("Redmine Project -> AOS Project : Success: %d Fail: %d", success, fail);
    setResult(getResult() + String.format("%s \n", resultStr));
    LOG.debug(resultStr);
    success = fail = 0;

    return resultStr;
  }

  public void importRedmineProjects() throws RedmineException {

    redmineProjectManager = redmineManager.getProjectManager();

    LOG.debug("Fetching all projects from redmine..");

    List<com.taskadapter.redmineapi.bean.Project> redmineProjectList =
        redmineProjectManager.getProjects();

    if (CollectionUtils.isNotEmpty(redmineProjectList)) {

      LOG.debug("Total projects to import from redmine: {}", redmineProjectList.size());

      setDefaultFieldsAndMaps();

      for (com.taskadapter.redmineapi.bean.Project redmineProject : redmineProjectList) {

        LOG.debug("Importing project: {}", redmineProject.getIdentifier());

        setRedmineCustomFieldsMap(redmineProject.getCustomFields());

        try {
          importRedmineProject(redmineProject);
          setErrorLog(redmineProject.getIdentifier());
        } finally {
          updateTransaction();
        }
      }
    }
  }

  @Transactional
  public void importRedmineProject(com.taskadapter.redmineapi.bean.Project redmineProject) {
    Project project = projectRepo.findByRedmineId(redmineProject.getId());
    LocalDateTime redmineUpdatedOn = getRedmineDate(redmineProject.getUpdatedOn());

    if (project == null) {
      project = new Project();
      project.setRedmineId(redmineProject.getId());
      project.setCode(redmineProject.getIdentifier().toUpperCase());

      List<ProjectStatus> projectStatuses =
          projectStatusRepo
              .all()
              .filter("self.relatedToSelect = ?1", ProjectStatusRepository.PROJECT_STATUS_TASK)
              .fetch();
      if (projectStatuses != null && !projectStatuses.isEmpty()) {
        for (ProjectStatus projectStatus : projectStatuses) {
          project.addProjectTaskStatusSetItem(projectStatus);
        }
      }

      List<ProjectPriority> projectPriorities = projectPriorityRepo.all().fetch();
      if (projectPriorities != null && !projectPriorities.isEmpty()) {
        for (ProjectPriority projectPriority : projectPriorities) {
          project.addProjectTaskPrioritySetItem(projectPriority);
        }
      }
    } else if (lastBatchEndDate != null
        && (redmineUpdatedOn.isBefore(lastBatchEndDate)
            || (project.getUpdatedOn().isAfter(lastBatchEndDate)
                && project.getUpdatedOn().isAfter(redmineUpdatedOn)))) {
      return;
    }

    setProjectFields(redmineProject, project);

    try {
      if (project.getId() == null) {
        project.addCreatedBatchSetItem(batch);
      } else {
        project.addUpdatedBatchSetItem(batch);
      }

      projectRepo.save(project);

      updatedOnMap.put(project.getId(), redmineUpdatedOn);

      if (redmineProject.getParentId() != null) {
        parentMap.put(project.getId(), redmineProject.getParentId());
      }

      onSuccess.accept(project);
      success++;
    } catch (Exception e) {
      onError.accept(e);
      fail++;
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public void setProjectFields(
      com.taskadapter.redmineapi.bean.Project redmineProject, Project project) {

    project.setRedmineId(redmineProject.getId());
    project.setName(redmineProject.getName());
    project.setCode(redmineProject.getIdentifier());
    project.setDescription(getHtmlFromTextile(redmineProject.getDescription()));
    project.setCompany(companyRepo.find(defaultCompanyId));

    if (redmineProject.getStatus().equals(REDMINE_PROJECT_STATUS_CLOSED)) {
      project.setProjectStatus(projectStatusRepo.getCompleted());
    }

    try {
      List<Membership> redmineProjectMembers =
          redmineProjectManager.getProjectMembers(redmineProject.getId());

      if (CollectionUtils.isNotEmpty(redmineProjectMembers)) {

        for (Membership membership : redmineProjectMembers) {
          User user = getAosUser(membership.getUserId());

          if (user != null) {
            project.addMembersUserSetItem(user);
          }
        }
      } else {
        project.clearMembersUserSet();
      }
    } catch (RedmineException e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }

    Collection<Tracker> redmineTrackers = redmineProject.getTrackers();

    if (CollectionUtils.isNotEmpty(redmineTrackers)) {

      for (Tracker tracker : redmineTrackers) {
        ProjectTaskCategory projectTaskCategory =
            projectTaskCategoryRepo.findByName(fieldMap.get(tracker.getName()));

        if (projectTaskCategory != null) {
          project.addProjectTaskCategorySetItem(projectTaskCategory);
        }
      }
    } else {
      project.clearProjectTaskCategorySet();
    }

    String value = redmineCustomFieldsMap.get(redmineProjectInvoiceable);

    boolean invoiceable = StringUtils.isNotEmpty(value) ? value.equals("1") : false;
    project.setToInvoice(invoiceable);
    project.setIsBusinessProject(invoiceable);

    value = redmineCustomFieldsMap.get(redmineProjectAssignedTo);
    project.setAssignedTo(
        StringUtils.isNotEmpty(value) ? getAosUser(Integer.parseInt(value)) : null);

    value =
        StringUtils.isNotEmpty(redmineCustomFieldsMap.get(redmineProjectClientPartner))
            ? redmineCustomFieldsMap.get(redmineProjectClientPartner)
            : redmineProjectClientPartnerDefault;

    if (StringUtils.isNotEmpty(value)) {
      Partner partner = partnerRepo.findByReference(value);
      project.setClientPartner(partner);

      if (partner == null) {
        errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_CLIENT_PARTNER_NOT_FOUND)};
      }
    } else {
      project.setClientPartner(null);
    }

    value =
        StringUtils.isNotEmpty(redmineCustomFieldsMap.get(redmineProjectInvoicingSequenceSelect))
            ? redmineCustomFieldsMap.get(redmineProjectInvoicingSequenceSelect)
            : redmineProjectInvoicingSequenceSelectDefault;

    if (StringUtils.isNotEmpty(value)) {
      int invoicingSequenceSelect;

      if (value.equals("Empty")) {
        invoicingSequenceSelect = ProjectRepository.INVOICING_SEQ_EMPTY;
      } else if (value.equals("Pre-invoiced")) {
        invoicingSequenceSelect = ProjectRepository.INVOICING_SEQ_INVOICE_PRE_TASK;
      } else if (value.equals("Post-invoiced")) {
        invoicingSequenceSelect = ProjectRepository.INVOICING_SEQ_INVOICE_POST_TASK;
      } else {
        invoicingSequenceSelect = -1;
      }

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

    if (isAppBusinessSupport) {
      importProjectVersions(redmineProject, project);
    }

    setLocalDateTime(project, redmineProject.getCreatedOn(), "setCreatedOn");
  }

  public void importProjectVersions(
      com.taskadapter.redmineapi.bean.Project redmineProject, Project project) {

    try {
      List<Version> redmineVersionList = redmineProjectManager.getVersions(redmineProject.getId());

      if (CollectionUtils.isNotEmpty(redmineVersionList)) {

        for (Version redmineVersion : redmineVersionList) {
          ProjectVersion version = projectVersionRepo.findByRedmineId(redmineVersion.getId());

          if (!redmineProjectVersionIdList.contains(redmineVersion.getId())) {

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
          project.addRoadmapSetItem(version);
        }
      } else {
        project.clearRoadmapSet();
      }
    } catch (RedmineException e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public void setDefaultFieldsAndMaps() {
    redmineProjectInvoiceable = appRedmine.getRedmineProjectInvoiceable();
    redmineProjectClientPartner = appRedmine.getRedmineProjectClientPartner();
    redmineProjectInvoicingSequenceSelect = appRedmine.getRedmineProjectInvoicingSequenceSelect();
    redmineProjectAssignedTo = appRedmine.getRedmineProjectAssignedTo();
    redmineProjectClientPartnerDefault = appRedmine.getRedmineProjectClientPartnerDefault();
    redmineProjectInvoicingSequenceSelectDefault =
        appRedmine.getRedmineProjectInvoicingSequenceSelectDefault();
    defaultCompanyId = appRedmine.getCompany().getId();
    isAppBusinessSupport = appBaseService.isApp("business-support");

    List<RedmineImportMapping> redmineImportMappingList =
        redmineImportMappingRepo
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

  @Transactional
  public void setParentProjects() {

    LOG.debug("Setting parent project of imported records..");

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
            "UPDATE project_project as project SET updated_on = v.updated_on from (values %s) as v(id,updated_on) where project.id = v.id",
            values);

    JPA.em().createNativeQuery(query).executeUpdate();
    JPA.em().getTransaction().commit();

    updatedOnMap = new HashMap<>();
  }

  public void setErrorLog(String redmineProjectIdentifier) {
    if (errors.length > 0) {
      errorObjList.add(
          ObjectArrays.concat(
              new Object[] {
                I18n.get(IMessage.REDMINE_IMPORT_PROJECT_ERROR), redmineProjectIdentifier
              },
              errors,
              Object.class));

      errors = new Object[] {};
    }
  }
}
