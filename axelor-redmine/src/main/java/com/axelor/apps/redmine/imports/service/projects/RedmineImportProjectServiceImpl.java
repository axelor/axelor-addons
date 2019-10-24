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
package com.axelor.apps.redmine.imports.service.projects;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectCategory;
import com.axelor.apps.project.db.repo.ProjectCategoryRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.redmine.db.RedmineImportMapping;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.apps.redmine.imports.service.RedmineImportService;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.ProjectManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.Membership;
import com.taskadapter.redmineapi.bean.Tracker;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineImportProjectServiceImpl extends RedmineImportService
    implements RedmineImportProjectService {

  protected RedmineImportMappingRepository redmineImportMappingRepository;

  @Inject
  public RedmineImportProjectServiceImpl(
      UserRepository userRepo,
      ProjectRepository projectRepo,
      ProductRepository productRepo,
      TeamTaskRepository teamTaskRepo,
      ProjectCategoryRepository projectCategoryRepo,
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
    this.appRedmineRepo = appRedmineRepo;
    this.companyRepo = companyRepo;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());
  protected Long defaultCompanyId;

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
      this.defaultCompanyId = appRedmineRepo.all().fetchOne().getCompany().getId();

      List<RedmineImportMapping> redmineImportMappingList =
          redmineImportMappingRepository.all().fetch();

      for (RedmineImportMapping redmineImportMapping : redmineImportMappingList) {
        fieldMap.put(redmineImportMapping.getRedmineValue(), redmineImportMapping.getOsValue());
      }

      this.importProjectsFromList(redmineProjectList);

      // SET PROJECTS PARENTS
      this.setParentProjects();
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
        project.addBatchSetItem(batch);
      }

      projectRepo.save(project);

      JPA.em()
          .createNativeQuery("update project_project SET updated_on = ?1 where id = ?2")
          .setParameter(1, redmineUpdatedOn)
          .setParameter(2, project.getId())
          .executeUpdate();

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

    if (!parentMap.isEmpty()) {
      Project project;

      for (Map.Entry<Long, Integer> entry : parentMap.entrySet()) {
        project = projectRepo.find(entry.getKey());

        if (project != null) {
          project.setParentProject(projectRepo.findByRedmineId(entry.getValue()));
          LocalDateTime updatedOn =
              project.getUpdatedOn() != null
                  ? project.getUpdatedOn().atZone(ZoneId.systemDefault()).toLocalDateTime()
                  : null;
          projectRepo.save(project);

          if (updatedOn != null) {
            JPA.em()
                .createNativeQuery("update project_project SET updated_on = ?1 where id = ?2")
                .setParameter(1, updatedOn)
                .setParameter(2, project.getId())
                .executeUpdate();
          }
        }
      }
    }
  }

  public void setProjectFields(
      com.taskadapter.redmineapi.bean.Project redmineProject, Project project) {

    project.setRedmineId(redmineProject.getId());
    project.setName(redmineProject.getName());
    project.setCode(redmineProject.getIdentifier());
    project.setDescription(redmineProject.getDescription());
    project.setCompany(companyRepo.find(defaultCompanyId));

    CustomField customField = (CustomField) redmineCustomFieldsMap.get("Invoiceable");
    String value = customField != null ? customField.getValue() : null;

    project.setIsInvoiceable(value != null ? (value.equals("1") ? true : false) : false);
    project.setIsBusinessProject(project.getIsInvoiceable());

    // ERROR AND IMPORT IF CLIENT PARTNER NOT FOUND

    customField = (CustomField) redmineCustomFieldsMap.get("Customer Code");
    value = customField != null ? customField.getValue() : null;

    if (value != null && !value.equals("")) {
      Partner partner = partnerRepo.findByReference((String) value);

      if (partner != null) {
        project.setClientPartner(partner);
      } else {
        errors =
            errors.length == 0
                ? new Object[] {I18n.get(IMessage.REDMINE_IMPORT_CLIENT_PARTNER_NOT_FOUND)}
                : ObjectArrays.concat(
                    errors,
                    new Object[] {I18n.get(IMessage.REDMINE_IMPORT_CLIENT_PARTNER_NOT_FOUND)},
                    Object.class);
      }
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

          Integer userId = membership.getUserId();
          User user = getOsUser(userId);

          if (user != null) {
            project.addMembersUserSetItem(user);
          }
        }
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }

    Collection<Tracker> redmineTrackers = redmineProject.getTrackers();

    if (redmineTrackers != null && !redmineTrackers.isEmpty()) {

      for (Tracker tracker : redmineTrackers) {
        String name = fieldMap.get(tracker.getName());
        ProjectCategory projectCategory = projectCategoryRepo.findByName(name);

        if (projectCategory != null) {
          project.addProjectCategorySetItem(projectCategory);
        }
      }
    }

    if (redmineProject.getStatus().equals(REDMINE_PROJECT_STATUS_CLOSED)) {
      project.setStatusSelect(ProjectRepository.STATE_FINISHED);
    }

    // ERROR AND IMPORT IF INVOICING TYPE NOT FOUND

    customField = (CustomField) redmineCustomFieldsMap.get("Invoicing Type");
    value = customField != null ? customField.getValue() : null;

    if (value != null && !value.equals("")) {
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
    }

    setLocalDateTime(project, redmineProject.getCreatedOn(), "setCreatedOn");
  }
}
