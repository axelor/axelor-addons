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

import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.apps.redmine.service.db.imports.RedmineDbImportCommonService;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaStore;
import com.axelor.meta.schema.views.Selection.Option;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.apache.commons.collections.CollectionUtils;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class RedmineDbImportProjectVersionServiceImpl extends RedmineDbImportCommonService {

  protected ProjectVersionRepository projectVersionRepo;

  @Inject
  public RedmineDbImportProjectVersionServiceImpl(
      UserRepository userRepo,
      ProjectRepository projectRepo,
      RedmineImportMappingRepository redmineImportMappingRepo,
      AppBaseService appBaseService,
      ProjectVersionRepository projectVersionRepo) {

    super(userRepo, projectRepo, redmineImportMappingRepo, appBaseService);

    this.projectVersionRepo = projectVersionRepo;
  }

  public void importRedmineProjectVersions(Connection connection, LocalDateTime lastBatchEndDate) {

    LOG.debug("Set required default fields and maps for redmine project versions import..");

    ArrayList<Option> selectionList = new ArrayList<>();
    selectionList.addAll(MetaStore.getSelectionList("support.project.version.status.select"));
    ResourceBundle fr = I18n.getBundle(Locale.FRANCE);
    ResourceBundle en = I18n.getBundle(Locale.ENGLISH);

    for (Option option : selectionList) {
      selectionMap.put(fr.getString(option.getTitle()), Integer.parseInt(option.getValue()));
      selectionMap.put(en.getString(option.getTitle()), Integer.parseInt(option.getValue()));
    }

    Integer offset = 0;

    LOG.debug("Start importing redmine project versions..");

    try (PreparedStatement preparedStatement =
        connection.prepareStatement(prepareProjectVersionPsqlQuery())) {
      preparedStatement.setFetchSize(batch.getRedmineBatch().getRedmineFetchLimit());
      ResultSet projectVersionResultSet = null;
      retrieveProjectVersionResultSet(
          preparedStatement, offset, lastBatchEndDate, projectVersionResultSet);
    } catch (Exception e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public String prepareProjectVersionPsqlQuery() {

    LOG.debug("Prepare PSQL query to import redmine project versions..");

    Integer limit = batch.getRedmineBatch().getRedmineFetchLimit();

    if (limit == 0) {
      limit = 100;
    }

    String projectVersionQuery =
        "select v.id,v.project_id,v.name,v.description,v.effective_date,v.status,v.sharing,v.created_on,v.updated_on,\n"
            + "json_agg(distinct(array[cfs.name,cvs.value])) as custom_fields \n"
            + "from versions as v \n"
            + "left join custom_values as cvs on cvs.customized_id = v.id and cvs.customized_type = 'Version' \n"
            + "left join custom_fields as cfs on cfs.id = cvs.custom_field_id \n"
            + "group by v.id order by v.id \n"
            + "limit "
            + limit
            + " offset ?";

    return projectVersionQuery;
  }

  public void retrieveProjectVersionResultSet(
      PreparedStatement preparedStatement,
      Integer offset,
      LocalDateTime lastBatchEndDate,
      ResultSet projectVersionResultSet)
      throws SQLException {

    preparedStatement.setInt(1, offset);
    projectVersionResultSet = preparedStatement.executeQuery();

    if (projectVersionResultSet.next()) {
      offset = processProjectVersionResultSet(offset, lastBatchEndDate, projectVersionResultSet);
      retrieveProjectVersionResultSet(
          preparedStatement, offset, lastBatchEndDate, projectVersionResultSet);
    }
  }

  public Integer processProjectVersionResultSet(
      Integer offset, LocalDateTime lastBatchEndDate, ResultSet projectVersionResultSet)
      throws SQLException {

    offset += 1;

    LOG.debug("Import version: {}", projectVersionResultSet.getString("name"));

    try {
      importRedmineProjectVersion(lastBatchEndDate, projectVersionResultSet);
    } finally {
      updateTransaction();
    }

    if (projectVersionResultSet.next()) {
      offset = processProjectVersionResultSet(offset, lastBatchEndDate, projectVersionResultSet);
    }

    return offset;
  }

  @Transactional
  public void importRedmineProjectVersion(
      LocalDateTime lastBatchEndDate, ResultSet projectVersionResultSet) {

    try {
      int redmineProjectVersionId = projectVersionResultSet.getInt("id");
      ProjectVersion projectVersion = projectVersionRepo.findByRedmineId(redmineProjectVersionId);
      LocalDateTime redmineUpdatedOn =
          getDateAtLocalTimezone(
              projectVersionResultSet.getObject("updated_on", LocalDateTime.class));

      if (projectVersion == null) {
        projectVersion = new ProjectVersion();
        projectVersion.setRedmineId(redmineProjectVersionId);
      } else if (lastBatchEndDate != null
          && (redmineUpdatedOn.isBefore(lastBatchEndDate)
              || (projectVersion.getUpdatedOn().isAfter(lastBatchEndDate)
                  && projectVersion.getUpdatedOn().isAfter(redmineUpdatedOn)))) {
        return;
      }

      projectVersion.setStatusSelect(
          (Integer) selectionMap.get(fieldMap.get(projectVersionResultSet.getString("status"))));
      projectVersion.setTitle(projectVersionResultSet.getString("name"));
      projectVersion.setContent(projectVersionResultSet.getString("description"));
      projectVersion.setTestingServerDate(
          projectVersionResultSet.getObject("effective_date", LocalDate.class));

      String cfsJsonStr = projectVersionResultSet.getString("custom_fields");

      if (!StringUtils.isEmpty(cfsJsonStr)) {
        projectVersion = setProjectVersionFieldsFromCfs(new JSONArray(cfsJsonStr), projectVersion);
      }

      Project project = projectRepo.findByRedmineId(projectVersionResultSet.getInt("project_id"));

      if (project != null) {
        List<Project> resultList = new ArrayList<>();

        /** See https://www.redmine.org/projects/redmine/wiki/RedmineProjectSettings#Versions */
        switch (projectVersionResultSet.getString("sharing")) {
          case "none":
            projectVersion.addProjectSetItem(project);
            break;
          case "system":
            projectVersion.setProjectSet(
                new HashSet<>(projectRepo.all().filter("self.redmineId != 0").fetch()));
            break;
          case "descendants":
            resultList.add(project);
            resultList = getDescendantProjects(project, resultList);
            projectVersion.setProjectSet(new HashSet<>(resultList));
            break;
          case "hierarchy":
            resultList.add(project);
            resultList = getAncestorProjects(project, resultList);
            resultList = getDescendantProjects(project, resultList);
            projectVersion.setProjectSet(new HashSet<>(resultList));
            break;
          case "tree":
            Project rootProject = getRootProject(project);
            resultList.add(rootProject);
            resultList = getDescendantProjects(rootProject, resultList);
            projectVersion.setProjectSet(new HashSet<>(resultList));
            break;
          default:
            break;
        }
      }

      projectVersionRepo.save(projectVersion);
    } catch (Exception e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public ProjectVersion setProjectVersionFieldsFromCfs(
      JSONArray cfsJsonArray, ProjectVersion projectVersion) throws JSONException {

    JSONArray resultCfJsonArray;
    String cfName;
    String cfValue;

    for (Integer i = 0; i < cfsJsonArray.length(); i++) {
      resultCfJsonArray = cfsJsonArray.getJSONArray(i);
      cfName = resultCfJsonArray.get(0) != JSONObject.NULL ? resultCfJsonArray.getString(0) : null;
      cfValue = resultCfJsonArray.get(1) != JSONObject.NULL ? resultCfJsonArray.getString(1) : null;

      if (!StringUtils.isEmpty(cfName)
          && cfName.equals(appRedmine.getRedmineVersionDeliveryDate())) {
        projectVersion.setProductionServerDate(
            !StringUtils.isEmpty(cfValue) ? LocalDate.parse(cfValue) : null);
      }
    }

    return projectVersion;
  }

  public List<Project> getDescendantProjects(Project project, List<Project> resultList) {

    List<Project> childProjectList = project.getChildProjectList();

    if (CollectionUtils.isNotEmpty(childProjectList)) {

      for (Project childProject : childProjectList) {
        resultList.add(childProject);
        getDescendantProjects(childProject, resultList);
      }
    }

    return resultList;
  }

  public List<Project> getAncestorProjects(Project project, List<Project> resultList) {

    Project parentProject = project.getParentProject();

    if (parentProject != null) {
      resultList.add(parentProject);
      getAncestorProjects(parentProject, resultList);
    }

    return resultList;
  }

  public Project getRootProject(Project project) {

    Project rootProject = project.getParentProject();

    if (rootProject != null) {
      getRootProject(rootProject);
    }

    return project;
  }
}
