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
package com.axelor.apps.redmine.service.db.imports.projects;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.TeamTaskCategory;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.TeamTaskCategoryRepository;
import com.axelor.apps.redmine.db.RedmineImportMapping;
import com.axelor.apps.redmine.db.repo.RedmineImportConfigRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class RedmineDbImportProjectServiceImpl extends RedmineDbImportProjectVersionServiceImpl
    implements RedmineDbImportProjectService {

  protected CompanyRepository companyRepo;
  protected TeamTaskCategoryRepository teamTaskCategoryRepo;
  protected PartnerRepository partnerRepo;

  @Inject
  public RedmineDbImportProjectServiceImpl(
      UserRepository userRepo,
      ProjectRepository projectRepo,
      RedmineImportMappingRepository redmineImportMappingRepo,
      AppBaseService appBaseService,
      ProjectVersionRepository projectVersionRepo,
      CompanyRepository companyRepo,
      TeamTaskCategoryRepository teamTaskCategoryRepo,
      PartnerRepository partnerRepo) {

    super(userRepo, projectRepo, redmineImportMappingRepo, appBaseService, projectVersionRepo);

    this.companyRepo = companyRepo;
    this.teamTaskCategoryRepo = teamTaskCategoryRepo;
    this.partnerRepo = partnerRepo;
  }

  @Override
  public String redmineProjectsImportProcess(
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

    setDefaultFieldsAndMaps();

    importRedmineProjects(
        connection, lastBatchEndDate != null ? lastBatchEndDate.toLocalDateTime() : null);

    if (!parentMap.isEmpty()) {
      updateTransaction();
      setParentProjects();
    }

    if (appBaseService.isApp("business-support")) {
      updateTransaction();
      importRedmineProjectVersions(
          connection, lastBatchEndDate != null ? lastBatchEndDate.toLocalDateTime() : null);
    }

    String resultStr =
        String.format("Redmine Project -> AOS Project : Success: %d Fail: %d", success, fail);
    LOG.debug(resultStr);
    success = fail = 0;

    return resultStr;
  }

  public void setDefaultFieldsAndMaps() {

    LOG.debug("Set required default fields and maps for redmine projects import..");

    serverTimeZone = appRedmine.getRedmineServerTimeZone();

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

  public void importRedmineProjects(Connection connection, LocalDateTime lastBatchEndDate) {

    Integer offset = 0;

    LOG.debug("Start importing redmine projects..");

    try (PreparedStatement preparedStatement =
        connection.prepareStatement(prepareProjectPsqlQuery())) {
      preparedStatement.setFetchSize(batch.getRedmineBatch().getRedmineFetchLimit());
      ResultSet projectResultSet = null;
      retrieveProjectResultSet(preparedStatement, offset, lastBatchEndDate, projectResultSet);
    } catch (Exception e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public String prepareProjectPsqlQuery() {

    LOG.debug("Prepare PSQL query to import redmine projects..");

    Integer limit = batch.getRedmineBatch().getRedmineFetchLimit();

    if (limit == 0) {
      limit = 100;
    }

    String projectQuery =
        "select p.id,p.name,p.description,p.parent_id,p.identifier,p.status,p.created_on,p.updated_on,\n"
            + "string_agg(distinct(ea.address)::text, ',') as members,\n"
            + "string_agg(distinct(t.name)::text, ',') as trackers,\n"
            + "json_agg(distinct(array[cfs.name,case when cfs.field_format = 'user' and cvs.value != '' then (select address from email_addresses where user_id = cast(cvs.value as integer)) else cvs.value end])) as custom_fields \n"
            + "from projects as p \n"
            + "left join members as m on m.project_id = p.id \n"
            + "left join projects_trackers as pt on pt.project_id = p.id \n"
            + "left join trackers as t on t.id = pt.tracker_id \n"
            + "left join email_addresses as ea on ea.user_id = m.user_id \n"
            + "left join custom_values as cvs on cvs.customized_id = p.id and cvs.customized_type = 'Project' \n"
            + "left join custom_fields as cfs on cfs.id = cvs.custom_field_id \n"
            + "group by p.id order by p.id \n"
            + "limit "
            + limit
            + " offset ?";

    return projectQuery;
  }

  public void retrieveProjectResultSet(
      PreparedStatement preparedStatement,
      Integer offset,
      LocalDateTime lastBatchEndDate,
      ResultSet projectResultSet)
      throws SQLException {

    preparedStatement.setInt(1, offset);
    projectResultSet = preparedStatement.executeQuery();

    if (projectResultSet.next()) {
      offset = processProjectResultSet(offset, lastBatchEndDate, projectResultSet);

      if (!updatedOnMap.isEmpty()) {
        setUpdatedOnFromRedmine();
      }

      retrieveProjectResultSet(preparedStatement, offset, lastBatchEndDate, projectResultSet);
    }
  }

  public Integer processProjectResultSet(
      Integer offset, LocalDateTime lastBatchEndDate, ResultSet projectResultSet)
      throws SQLException {

    offset += 1;

    LOG.debug("Import project: {}", projectResultSet.getString("identifier"));

    try {
      importRedmineProject(lastBatchEndDate, projectResultSet);

      if (errors.length > 0) {
        setErrorLog(
            IMessage.REDMINE_IMPORT_PROJECT_ERROR, projectResultSet.getString("identifier"));
      }
    } finally {
      updateTransaction();
    }

    if (projectResultSet.next()) {
      offset = processProjectResultSet(offset, lastBatchEndDate, projectResultSet);
    }

    return offset;
  }

  @Transactional
  public void importRedmineProject(LocalDateTime lastBatchEndDate, ResultSet projectResultSet) {

    try {
      Project project = projectRepo.findByRedmineId(projectResultSet.getInt("id"));
      LocalDateTime redmineUpdatedOn =
          getDateAtLocalTimezone(projectResultSet.getObject("updated_on", LocalDateTime.class));

      if (project == null) {
        project = new Project();
      } else if (lastBatchEndDate != null
          && (redmineUpdatedOn.isBefore(lastBatchEndDate)
              || (project.getUpdatedOn().isAfter(lastBatchEndDate)
                  && project.getUpdatedOn().isAfter(redmineUpdatedOn)))) {
        return;
      }

      project = setProjectFields(project, projectResultSet);

      if (project.getId() == null) {
        project.addCreatedBatchSetItem(batch);
      } else {
        project.addUpdatedBatchSetItem(batch);
      }

      projectRepo.save(project);

      updatedOnMap.put(project.getId(), redmineUpdatedOn);

      if (project.getProjectTypeSelect().equals(ProjectRepository.TYPE_PHASE)) {
        parentMap.put(project.getId(), projectResultSet.getInt("parent_id"));
      }

      onSuccess.accept(project);
      success++;
    } catch (Exception e) {
      fail++;
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public Project setProjectFields(Project project, ResultSet projectResultSet)
      throws SQLException, JSONException {

    project.setRedmineId(projectResultSet.getInt("id"));
    project.setName(projectResultSet.getString("name"));
    project.setCode(projectResultSet.getString("identifier"));
    project.setDescription(getHtmlFromTextile(projectResultSet.getString("description")));
    project.setCompany(companyRepo.find(appRedmine.getCompany().getId()));
    project.setProjectTypeSelect(
        projectResultSet.getInt("parent_id") != 0
            ? ProjectRepository.TYPE_PHASE
            : ProjectRepository.TYPE_PROJECT);

    String membersEmailStr = projectResultSet.getString("members");

    if (!StringUtils.isEmpty(membersEmailStr)) {
      String[] membersEmailArray = membersEmailStr.split(",");

      for (String memberEmail : membersEmailArray) {

        User user = getUserFromEmail(memberEmail);

        if (user != null) {
          project.addMembersUserSetItem(user);
        }
      }
    } else {
      project.clearMembersUserSet();
    }

    String trackersNameStr = projectResultSet.getString("trackers");

    if (!StringUtils.isEmpty(trackersNameStr)) {
      String[] trackersNameArray = trackersNameStr.split(",");

      for (String trackerName : trackersNameArray) {
        TeamTaskCategory projectCategory =
            teamTaskCategoryRepo.findByName(fieldMap.get(trackerName));

        if (projectCategory != null) {
          project.addTeamTaskCategorySetItem(projectCategory);
        }
      }
    } else {
      project.clearTeamTaskCategorySet();
    }

    /**
     * See https://www.redmine.org/projects/redmine/repository/entry/trunk/app/models/project.rb#L26
     */
    if (projectResultSet.getInt("status") == 5) {
      project.setStatusSelect(ProjectRepository.STATE_FINISHED);
    }

    String cfsJsonStr = projectResultSet.getString("custom_fields");

    if (!StringUtils.isEmpty(cfsJsonStr)) {
      project = setProjectFieldsFromCfs(new JSONArray(cfsJsonStr), project);
    }

    setLocalDateTime(
        project,
        getDateAtLocalTimezone(projectResultSet.getObject("created_on", LocalDateTime.class)),
        "setCreatedOn");

    return project;
  }

  public Project setProjectFieldsFromCfs(JSONArray cfsJsonArray, Project project)
      throws JSONException {

    JSONArray resultCfJsonArray;
    String cfName;
    String cfValue;

    for (Integer i = 0; i < cfsJsonArray.length(); i++) {
      resultCfJsonArray = cfsJsonArray.getJSONArray(i);
      cfName = resultCfJsonArray.get(0) != JSONObject.NULL ? resultCfJsonArray.getString(0) : null;
      cfValue = resultCfJsonArray.get(1) != JSONObject.NULL ? resultCfJsonArray.getString(1) : null;

      if (!StringUtils.isEmpty(cfName)) {

        if (cfName.equals(appRedmine.getRedmineProjectInvoiceable())) {
          boolean invoiceable = !StringUtils.isEmpty(cfValue) ? cfValue.equals("1") : false;
          project.setToInvoice(invoiceable);
          project.setIsBusinessProject(invoiceable);
        } else if (cfName.equals(appRedmine.getRedmineProjectClientPartner())) {
          cfValue =
              !StringUtils.isEmpty(cfValue)
                  ? cfValue
                  : appRedmine.getRedmineProjectClientPartnerDefault();

          if (!StringUtils.isEmpty(cfValue)) {
            Partner partner = partnerRepo.findByReference(cfValue);

            if (partner != null) {
              project.setClientPartner(partner);
            } else {
              errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_CLIENT_PARTNER_NOT_FOUND)};
            }
          } else {
            project.setClientPartner(null);
          }
        } else if (cfName.equals(appRedmine.getRedmineProjectInvoicingSequenceSelect())) {
          cfValue =
              !StringUtils.isEmpty(cfValue)
                  ? cfValue
                  : appRedmine.getRedmineProjectInvoicingSequenceSelectDefault();

          if (!StringUtils.isEmpty(cfValue)) {

            int invoicingSequenceSelect;

            if (cfValue.equals("Empty")) {
              invoicingSequenceSelect = ProjectRepository.INVOICING_SEQ_EMPTY;
            } else if (cfValue.equals("Pre-invoiced")) {
              invoicingSequenceSelect = ProjectRepository.INVOICING_SEQ_INVOICE_PRE_TASK;
            } else if (cfValue.equals("Post-invoiced")) {
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
        } else if (cfName.equals(appRedmine.getRedmineProjectAssignedTo())) {
          project.setAssignedTo(getUserFromEmail(cfValue));
        }
      }
    }

    return project;
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
            "UPDATE project_project as project SET updated_on = v.updated_on from (values %s) as v(id,updated_on) where project.id = v.id",
            values);

    JPA.em().createNativeQuery(query).executeUpdate();
    JPA.em().getTransaction().commit();

    updatedOnMap = new HashMap<>();
  }

  @Transactional
  public void setParentProjects() {

    LOG.debug("Set parent project of imported records..");

    Project project;
    Project parentProject;
    Map<Integer, Project> parentProjectMap = new HashMap<>();

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
}
