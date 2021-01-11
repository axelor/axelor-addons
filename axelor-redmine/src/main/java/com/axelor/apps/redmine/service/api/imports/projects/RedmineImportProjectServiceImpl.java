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
package com.axelor.apps.redmine.service.api.imports.projects;

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
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.TeamTaskCategory;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.TeamTaskCategoryRepository;
import com.axelor.apps.redmine.db.RedmineImportMapping;
import com.axelor.apps.redmine.db.repo.RedmineImportConfigRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.redmine.service.api.imports.RedmineImportService;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaStore;
import com.axelor.meta.schema.views.Selection.Option;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.ProjectManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.bean.CustomField;
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
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineImportProjectServiceImpl extends RedmineImportService
    implements RedmineImportProjectService {

  protected RedmineImportMappingRepository redmineImportMappingRepository;
  protected ProjectVersionRepository projectVersionRepo;
  protected AppBaseService appBaseService;

  @Inject
  public RedmineImportProjectServiceImpl(
      UserRepository userRepo,
      ProjectRepository projectRepo,
      ProductRepository productRepo,
      TeamTaskRepository teamTaskRepo,
      TeamTaskCategoryRepository projectCategoryRepo,
      PartnerRepository partnerRepo,
      RedmineImportMappingRepository redmineImportMappingRepository,
      AppRedmineRepository appRedmineRepo,
      CompanyRepository companyRepo,
      ProjectVersionRepository projectVersionRepo,
      AppBaseService appBaseService) {

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
    this.projectVersionRepo = projectVersionRepo;
    this.appBaseService = appBaseService;
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
  protected Integer redmineVersionDeliveryDateCfId = 0;

  @Override
  @SuppressWarnings("unchecked")
  public void importProject(
      List<com.taskadapter.redmineapi.bean.Project> redmineProjectList,
      HashMap<String, Object> paramsMap) {

    if (redmineProjectList != null && !redmineProjectList.isEmpty()) {
      this.onError = (Consumer<Throwable>) paramsMap.get("onError");
      this.onSuccess = (Consumer<Object>) paramsMap.get("onSuccess");
      this.batch = (Batch) paramsMap.get("batch");
      this.errorObjList = (List<Object[]>) paramsMap.get("errorObjList");
      this.lastBatchUpdatedOn = (LocalDateTime) paramsMap.get("lastBatchUpdatedOn");
      this.redmineUserMap = (HashMap<Integer, String>) paramsMap.get("redmineUserMap");
      this.redmineProjectManager = (ProjectManager) paramsMap.get("redmineProjectManager");
      this.fieldMap = new HashMap<>();
      this.selectionMap = new HashMap<>();

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

      ArrayList<Option> selectionList = new ArrayList<>();
      selectionList.addAll(MetaStore.getSelectionList("support.project.version.status.select"));
      ResourceBundle fr = I18n.getBundle(Locale.FRANCE);
      ResourceBundle en = I18n.getBundle(Locale.ENGLISH);

      for (Option option : selectionList) {
        selectionMap.put(fr.getString(option.getTitle()), Integer.parseInt(option.getValue()));
        selectionMap.put(en.getString(option.getTitle()), Integer.parseInt(option.getValue()));
      }

      this.importProjectsFromList(redmineProjectList);

      updateTransaction();

      // SET PROJECTS PARENTS

      if (!parentMap.isEmpty()) {
        this.setParentProjects();
      }

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
                "UPDATE project_project as project SET updated_on = v.updated_on from (values %s) as v(id,updated_on) where project.id = v.id",
                values);

        JPA.em().createNativeQuery(query).executeUpdate();
      }
    }

    String resultStr =
        String.format("Redmine Project -> ABS Project : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
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

  @Transactional
  public void createOpenSuiteProject(com.taskadapter.redmineapi.bean.Project redmineProject) {

    this.setRedmineCustomFieldsMap(redmineProject.getCustomFields());

    Project project = projectRepo.findByRedmineId(redmineProject.getId());
    LocalDateTime redmineUpdatedOn =
        redmineProject.getUpdatedOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

    if (project == null) {
      project = new Project();
    } else if (lastBatchUpdatedOn != null
        && (redmineUpdatedOn.isBefore(lastBatchUpdatedOn)
            || (project.getUpdatedOn().isAfter(lastBatchUpdatedOn)
                && project.getUpdatedOn().isAfter(redmineUpdatedOn)))) {
      return;
    }

    LOG.debug("Importing project: " + redmineProject.getIdentifier());

    this.setProjectFields(redmineProject, project);

    try {

      if (project.getId() == null) {
        project.addCreatedBatchSetItem(batch);
      } else {
        project.addUpdatedBatchSetItem(batch);
      }

      projectRepo.save(project);
      updatedOnMap.put(project.getId(), redmineUpdatedOn);

      // CREATE MAP FOR CHILD-PARENT TASKS

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

    project.setRedmineId(redmineProject.getId());
    project.setName(redmineProject.getName());
    project.setCode(redmineProject.getIdentifier());
    project.setDescription(getHtmlFromTextile(redmineProject.getDescription()));
    project.setCompany(companyRepo.find(defaultCompanyId));

    String value = redmineCustomFieldsMap.get(redmineProjectInvoiceable);

    boolean invoiceable = value != null ? (value.equals("1") ? true : false) : false;
    project.setToInvoice(invoiceable);
    project.setIsBusinessProject(invoiceable);

    value = redmineCustomFieldsMap.get(redmineProjectAssignedTo);
    project.setAssignedTo(
        StringUtils.isNotEmpty(value) ? getOsUser(Integer.parseInt(value)) : null);

    // ERROR AND IMPORT IF CLIENT PARTNER NOT FOUND

    value =
        StringUtils.isNotEmpty(redmineCustomFieldsMap.get(redmineProjectClientPartner))
            ? redmineCustomFieldsMap.get(redmineProjectClientPartner)
            : redmineProjectClientPartnerDefault;

    if (value != null) {
      Partner partner = partnerRepo.findByReference(value);

      if (partner != null) {
        project.setClientPartner(partner);
      } else {
        errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_CLIENT_PARTNER_NOT_FOUND)};
      }
    } else {
      project.setClientPartner(null);
    }

    project.setProjectTypeSelect(
        redmineProject.getParentId() != null
            ? ProjectRepository.TYPE_PHASE
            : ProjectRepository.TYPE_PROJECT);

    try {
      List<Membership> redmineProjectMembers =
          redmineProjectManager.getProjectMembers(redmineProject.getId());

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
      TraceBackService.trace(e, "", batch.getId());
    }

    Collection<Tracker> redmineTrackers = redmineProject.getTrackers();

    if (redmineTrackers != null && !redmineTrackers.isEmpty()) {

      for (Tracker tracker : redmineTrackers) {
        TeamTaskCategory projectCategory =
            projectCategoryRepo.findByName(fieldMap.get(tracker.getName()));

        if (projectCategory != null) {
          project.addTeamTaskCategorySetItem(projectCategory);
        }
      }
    } else {
      project.clearTeamTaskCategorySet();
    }

    if (redmineProject.getStatus().equals(REDMINE_PROJECT_STATUS_CLOSED)) {
      project.setStatusSelect(ProjectRepository.STATE_FINISHED);
    }

    // ERROR AND IMPORT IF INVOICING TYPE NOT FOUND

    value =
        StringUtils.isNotEmpty(redmineCustomFieldsMap.get(redmineProjectInvoicingSequenceSelect))
            ? redmineCustomFieldsMap.get(redmineProjectInvoicingSequenceSelect)
            : redmineProjectInvoicingSequenceSelectDefault;

    if (value != null) {
      int invoicingSequenceSelect =
          value.equals("Empty")
              ? ProjectRepository.INVOICING_SEQ_EMPTY
              : value.equals("Pre-invoiced")
                  ? ProjectRepository.INVOICING_SEQ_INVOICE_PRE_TASK
                  : value.equals("Post-invoiced")
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

          CustomField deliveryDateCf = null;

          if (redmineVersionDeliveryDateCfId == 0) {
            Collection<CustomField> redmineVersionCfs = redmineVersion.getCustomFields();

            if (CollectionUtils.isNotEmpty(redmineVersionCfs)) {
              Optional<CustomField> deliveryDateCfOptional =
                  redmineVersionCfs.stream()
                      .filter(v -> v.getName().equals(redmineVersionDeliveryDate))
                      .findFirst();

              if (deliveryDateCfOptional.isPresent()) {
                deliveryDateCf = deliveryDateCfOptional.get();
                redmineVersionDeliveryDateCfId = deliveryDateCf.getId();
              }
            }
          } else {
            deliveryDateCf = redmineVersion.getCustomFieldById(redmineVersionDeliveryDateCfId);
          }

          if (deliveryDateCf != null) {
            String value = deliveryDateCf.getValue();
            version.setProductionServerDate(
                StringUtils.isNotEmpty(value) ? LocalDate.parse(value) : null);
          }

          version.addProjectSetItem(project);
          project.addRoadmapSetItem(version);
        }
      } else {
        project.clearRoadmapSet();
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }
}
