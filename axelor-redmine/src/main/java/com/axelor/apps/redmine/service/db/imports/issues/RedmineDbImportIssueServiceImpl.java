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
package com.axelor.apps.redmine.service.db.imports.issues;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppBusinessSupportRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.TeamTaskCategoryRepository;
import com.axelor.apps.redmine.db.RedmineImportMapping;
import com.axelor.apps.redmine.db.repo.RedmineImportConfigRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.redmine.service.TeamTaskRedmineService;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.axelor.meta.MetaStore;
import com.axelor.meta.schema.views.Selection.Option;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class RedmineDbImportIssueServiceImpl extends RedmineDbImportIssueJournalServiceImpl
    implements RedmineDbImportIssueService {

  protected TeamTaskCategoryRepository teamTaskCategoryRepo;
  protected ProductRepository productRepo;
  protected TeamTaskRedmineService teamTaskRedmineService;

  @Inject
  public RedmineDbImportIssueServiceImpl(
      UserRepository userRepo,
      ProjectRepository projectRepo,
      RedmineImportMappingRepository redmineImportMappingRepo,
      AppBaseService appBaseService,
      TeamTaskRepository teamTaskRepo,
      MailMessageRepository mailMessageRepository,
      ProjectVersionRepository projectVersionRepo,
      TeamTaskCategoryRepository teamTaskCategoryRepo,
      ProductRepository productRepo,
      TeamTaskRedmineService teamTaskRedmineService) {

    super(
        userRepo,
        projectRepo,
        redmineImportMappingRepo,
        appBaseService,
        teamTaskRepo,
        mailMessageRepository,
        projectVersionRepo);

    this.teamTaskCategoryRepo = teamTaskCategoryRepo;
    this.productRepo = productRepo;
    this.teamTaskRedmineService = teamTaskRedmineService;
  }

  protected List<Long> projectVersionIdList = new ArrayList<>();

  @Override
  public String redmineIssuesImportProcess(
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

    setDefaultFieldsAndMaps(appRedmine);

    importRedmineIssues(
        prepareIssuePsqlQuery(lastBatchEndDate),
        connection,
        lastBatchEndDate != null ? lastBatchEndDate.toLocalDateTime() : null);

    if (!parentMap.isEmpty()) {
      updateTransaction();
      setParentIssues();
    }

    if (CollectionUtils.isNotEmpty(projectVersionIdList)) {
      updateTransaction();
      updateProjectVersionsProgress();
    }

    String resultStr =
        String.format("Redmine Issue -> AOS Teamtask : Success: %d Fail: %d", success, fail);
    LOG.debug(resultStr);
    success = fail = 0;

    return resultStr;
  }

  public void setDefaultFieldsAndMaps(AppRedmine appRedmine) {

    LOG.debug("Set required default fields and maps for redmine issues import..");

    serverTimeZone = appRedmine.getRedmineServerTimeZone();

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

    if (batch.getRedmineBatch().getIsImportIssuesWithActivities()) {
      setFieldNameMaps();
    }
  }

  public String prepareIssuePsqlQuery(ZonedDateTime lastBatchEndDate) {

    LOG.debug("Prepare PSQL query to import redmine issues..");

    Integer limit = batch.getRedmineBatch().getRedmineFetchLimit();

    if (limit == 0) {
      limit = 100;
    }

    String dateFilter = "";
    String whereClause = "";

    if (lastBatchEndDate != null) {
      dateFilter = getDateAtServerTimezone(lastBatchEndDate);
      whereClause = "where i.updated_on >= timestamp '" + dateFilter + "' ";
    }

    String failedIds = batch.getRedmineBatch().getFailedRedmineIssuesIds();

    if (!StringUtils.isEmpty(failedIds)) {
      whereClause =
          StringUtils.isEmpty(whereClause)
              ? "where i.id in (" + failedIds + ") "
              : (whereClause + "or i.id in (" + failedIds + ") ");
    }

    String issueQuery =
        "select i.id,t.name as tracker,i.project_id,i.subject,i.description,s.name as status,atea.address as assigned_to,p.name as priority,i.fixed_version_id,v.name as fixed_version_name,aea.address as author,i.created_on,i.updated_on,i.start_date,i.done_ratio,i.estimated_hours,i.parent_id,i.closed_on,\n"
            + "json_agg(distinct(array[cfs.name,cvs.value])) as custom_fields,\n"
            + "(select cv.value from custom_values as cv left join custom_fields as cf on cf.id = cv.custom_field_id where cv.customized_id = i.id and cv.customized_type = 'Issue' and cf.name = 'Product' and cf.type = 'IssueCustomField' limit 1) as cf_product \n"
            + "from issues as i \n"
            + "left join trackers as t on t.id = i.tracker_id \n"
            + "left join issue_statuses as s on s.id = i.status_id \n"
            + "left join email_addresses as atea on atea.user_id = i.assigned_to_id \n"
            + "left join enumerations as p on p.id = i.priority_id \n"
            + "left join versions as v on v.id = i.fixed_version_id \n"
            + "left join email_addresses as aea on aea.user_id = i.author_id \n"
            + "left join custom_values as cvs on cvs.customized_id = i.id and cvs.customized_type = 'Issue' \n"
            + "left join custom_fields as cfs on cfs.id = cvs.custom_field_id \n"
            + whereClause
            + "group by i.id,t.name,s.name,atea.address,p.name,v.name,aea.address order by i.id \n"
            + "limit "
            + limit
            + " offset ?";

    if (batch.getRedmineBatch().getIsImportIssuesWithActivities()) {
      prepareIssueJournalPsqlQueries(dateFilter, limit);
    }

    return issueQuery;
  }

  public void importRedmineIssues(
      String issueQuery, Connection connection, LocalDateTime lastBatchEndDate) {

    Integer offset = 0;

    LOG.debug("Start importing redmine issues..");

    try (PreparedStatement preparedStatement = connection.prepareStatement(issueQuery)) {
      preparedStatement.setFetchSize(batch.getRedmineBatch().getRedmineFetchLimit());
      ResultSet issueResultSet = null;
      retrieveIssueResultSet(
          preparedStatement, offset, lastBatchEndDate, issueResultSet, connection);
      batch.getRedmineBatch().setFailedRedmineIssuesIds(failedIds);
    } catch (Exception e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public void retrieveIssueResultSet(
      PreparedStatement preparedStatement,
      Integer offset,
      LocalDateTime lastBatchEndDate,
      ResultSet issueResultSet,
      Connection connection)
      throws SQLException {

    preparedStatement.setInt(1, offset);
    issueResultSet = preparedStatement.executeQuery();

    if (issueResultSet.next()) {
      offset = processIssueResultSet(offset, lastBatchEndDate, issueResultSet);

      if (!updatedOnMap.isEmpty()) {
        setUpdatedOnFromRedmine();
      }

      if (!redmineIssueIdMap.isEmpty()) {
        updateTransaction();
        importIssueJournals(connection);
      }

      retrieveIssueResultSet(
          preparedStatement, offset, lastBatchEndDate, issueResultSet, connection);
    }
  }

  public Integer processIssueResultSet(
      Integer offset, LocalDateTime lastBatchEndDate, ResultSet issueResultSet)
      throws SQLException {

    offset += 1;

    try {
      importRedmineIssue(lastBatchEndDate, issueResultSet);

      if (errors.length > 0) {
        setErrorLog(
            IMessage.REDMINE_IMPORT_TEAMTASK_ERROR, String.valueOf(issueResultSet.getInt("id")));
      }
    } finally {
      updateTransaction();
    }

    if (issueResultSet.next()) {
      offset = processIssueResultSet(offset, lastBatchEndDate, issueResultSet);
    }

    return offset;
  }

  @Transactional
  public void importRedmineIssue(LocalDateTime lastBatchEndDate, ResultSet issueResultSet) {

    int redmineIssueId = 0;

    try {
      redmineIssueId = issueResultSet.getInt("id");
      LOG.debug("Import issue: {}", redmineIssueId);

      validateRequiredFields(issueResultSet);

      if (errors.length > 0) {
        fail++;
        failedIds =
            StringUtils.isEmpty(failedIds)
                ? String.valueOf(redmineIssueId)
                : failedIds + "," + redmineIssueId;
        setErrorLog(IMessage.REDMINE_IMPORT_TEAMTASK_ERROR, String.valueOf(redmineIssueId));
        return;
      }

      TeamTask teamTask = teamTaskRepo.findByRedmineId(redmineIssueId);
      LocalDateTime redmineUpdatedOn =
          getDateAtLocalTimezone(issueResultSet.getObject("updated_on", LocalDateTime.class));

      if (teamTask == null) {
        teamTask = new TeamTask();
        teamTask.setTypeSelect(TeamTaskRepository.TYPE_TASK);
      } else if (lastBatchEndDate != null
          && (redmineUpdatedOn.isBefore(lastBatchEndDate)
              || (teamTask.getUpdatedOn().isAfter(lastBatchEndDate)
                  && teamTask.getUpdatedOn().isAfter(redmineUpdatedOn)))) {
        return;
      }

      teamTask = setTeamTaskFields(teamTask, issueResultSet);

      if (teamTask.getId() == null) {
        teamTask.addCreatedBatchSetItem(batch);
      } else {
        teamTask.addUpdatedBatchSetItem(batch);
      }

      teamTaskRepo.save(teamTask);

      updatedOnMap.put(teamTask.getId(), redmineUpdatedOn);

      if (batch.getRedmineBatch().getIsImportIssuesWithActivities()) {
        Object[] redmineIssueArray = {teamTask.getId(), teamTask.getFullName()};
        redmineIssueIdMap.put(redmineIssueId, redmineIssueArray);
      }

      int parentId = issueResultSet.getInt("parent_id");

      if (parentId != 0) {
        parentMap.put(teamTask.getId(), parentId);
      }

      onSuccess.accept(teamTask);
      success++;
    } catch (Exception e) {
      fail++;
      failedIds =
          StringUtils.isEmpty(failedIds)
              ? String.valueOf(redmineIssueId)
              : failedIds + "," + redmineIssueId;
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public void validateRequiredFields(ResultSet issueResultSet) throws SQLException {

    if (projectRepo.findByRedmineId(issueResultSet.getInt("project_id")) == null) {
      errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PROJECT_NOT_FOUND)};
    }

    if (teamTaskCategoryRepo.findByName(fieldMap.get(issueResultSet.getString("tracker")))
        == null) {
      errors =
          errors.length == 0
              ? new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PROJECT_CATEGORY_NOT_FOUND)}
              : ObjectArrays.concat(
                  errors,
                  new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PROJECT_CATEGORY_NOT_FOUND)},
                  Object.class);
    }

    String cfProduct = issueResultSet.getString("cf_product");
    String value =
        !StringUtils.isEmpty(cfProduct) ? cfProduct : appRedmine.getRedmineIssueProductDefault();

    if (!StringUtils.isEmpty(value) && productRepo.findByCode(value) == null) {
      errors =
          errors.length == 0
              ? new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PRODUCT_NOT_FOUND)}
              : ObjectArrays.concat(
                  errors,
                  new Object[] {I18n.get(IMessage.REDMINE_IMPORT_PRODUCT_NOT_FOUND)},
                  Object.class);
    }
  }

  public TeamTask setTeamTaskFields(TeamTask teamTask, ResultSet issueResultSet)
      throws SQLException, JSONException {

    teamTask.setRedmineId(issueResultSet.getInt("id"));
    teamTask.setProject(projectRepo.findByRedmineId(issueResultSet.getInt("project_id")));
    teamTask.setTeamTaskCategory(
        teamTaskCategoryRepo.findByName(fieldMap.get(issueResultSet.getString("tracker"))));
    teamTask.setName("#" + teamTask.getRedmineId() + " " + issueResultSet.getString("subject"));
    teamTask.setDescription(getHtmlFromTextile(issueResultSet.getString("description")));
    teamTask.setAssignedTo(getUserFromEmail(issueResultSet.getString("assigned_to")));
    teamTask.setProgressSelect(issueResultSet.getInt("done_ratio"));
    teamTask.setBudgetedTime(BigDecimal.valueOf(issueResultSet.getDouble("estimated_hours")));
    teamTask.setTaskDate(issueResultSet.getObject("start_date", LocalDate.class));
    teamTask.setFixedVersion(issueResultSet.getString("fixed_version_name"));

    LocalDateTime closedOn =
        getDateAtLocalTimezone(issueResultSet.getObject("closed_on", LocalDateTime.class));
    teamTask.setTaskEndDate(closedOn != null ? closedOn.toLocalDate() : null);

    if (appBaseService.isApp("business-support")) {
      ProjectVersion currentTargetVersion = teamTask.getTargetVersion();

      if (currentTargetVersion != null
          && !projectVersionIdList.contains(currentTargetVersion.getId())) {
        projectVersionIdList.add(currentTargetVersion.getId());
      }

      int fixedVersionId = issueResultSet.getInt("fixed_version_id");
      ProjectVersion targetVersion = null;

      if (fixedVersionId != 0) {
        targetVersion = projectVersionRepo.findByRedmineId(fixedVersionId);

        if (targetVersion != null && !projectVersionIdList.contains(targetVersion.getId())) {
          projectVersionIdList.add(targetVersion.getId());
        }
      }

      teamTask.setTargetVersion(targetVersion);
    }

    String status = (String) selectionMap.get(fieldMap.get(issueResultSet.getString("status")));

    if (StringUtils.isEmpty(status)) {
      status = TeamTaskRepository.TEAM_TASK_DEFAULT_STATUS;
      errors = new Object[] {I18n.get(IMessage.REDMINE_IMPORT_WITH_DEFAULT_STATUS)};
    }

    teamTask.setStatus(status);

    String priority = (String) selectionMap.get(fieldMap.get(issueResultSet.getString("priority")));

    if (StringUtils.isEmpty(priority)) {
      priority = TeamTaskRepository.TEAM_TASK_DEFAULT_PRIORITY;
      errors =
          errors.length == 0
              ? new Object[] {I18n.get(IMessage.REDMINE_IMPORT_WITH_DEFAULT_PRIORITY)}
              : ObjectArrays.concat(
                  errors,
                  new Object[] {I18n.get(IMessage.REDMINE_IMPORT_WITH_DEFAULT_PRIORITY)},
                  Object.class);
    }

    teamTask.setPriority(priority);

    String cfsJsonStr = issueResultSet.getString("custom_fields");

    if (!StringUtils.isEmpty(cfsJsonStr)) {
      teamTask = setTeamTaskFieldsFromCfs(new JSONArray(cfsJsonStr), teamTask);
    }

    if (!teamTask.getInvoiced()) {
      String cfProduct = issueResultSet.getString("cf_product");
      String value =
          !StringUtils.isEmpty(cfProduct) ? cfProduct : appRedmine.getRedmineIssueProductDefault();
      teamTask.setProduct(!StringUtils.isEmpty(value) ? productRepo.findByCode(value) : null);
      teamTask.setUnit(null);
      teamTask.setQuantity(BigDecimal.ZERO);
      teamTask.setExTaxTotal(BigDecimal.ZERO);
      teamTask.setInvoicingType(0);
      teamTask.setToInvoice(false);
      teamTask.setCurrency(null);
    }

    setCreatedByUser(
        teamTask, getUserFromEmail(issueResultSet.getString("author")), "setCreatedBy");
    setLocalDateTime(
        teamTask,
        getDateAtLocalTimezone(issueResultSet.getObject("created_on", LocalDateTime.class)),
        "setCreatedOn");

    return teamTask;
  }

  public TeamTask setTeamTaskFieldsFromCfs(JSONArray cfsJsonArray, TeamTask teamTask)
      throws JSONException {

    JSONArray resultCfJsonArray;
    String cfName;
    String cfValue;

    for (Integer i = 0; i < cfsJsonArray.length(); i++) {
      resultCfJsonArray = cfsJsonArray.getJSONArray(i);
      cfName = resultCfJsonArray.get(0) != JSONObject.NULL ? resultCfJsonArray.getString(0) : null;
      cfValue = resultCfJsonArray.get(1) != JSONObject.NULL ? resultCfJsonArray.getString(1) : null;

      if (!StringUtils.isEmpty(cfName)) {

        if (cfName.equals(appRedmine.getRedmineIssueDueDate())) {
          teamTask.setDueDate(
              !StringUtils.isEmpty(cfValue)
                  ? LocalDate.parse(cfValue)
                  : appRedmine.getRedmineIssueDueDateDefault());
        } else if (cfName.equals(appRedmine.getRedmineIssueEstimatedTime())) {
          teamTask.setEstimatedTime(
              !StringUtils.isEmpty(cfValue)
                  ? new BigDecimal(cfValue)
                  : appRedmine.getRedmineIssueEstimatedTimeDefault());
        } else if (cfName.equals(appRedmine.getRedmineIssueInvoiced()) && !teamTask.getInvoiced()) {
          teamTask.setInvoiced(!StringUtils.isEmpty(cfValue) ? cfValue.equals("1") : false);
        } else if (cfName.equals(appRedmine.getRedmineIssueAccountedForMaintenance())) {
          teamTask.setAccountedForMaintenance(
              !StringUtils.isEmpty(cfValue) ? cfValue.equals("1") : false);
        } else if (cfName.equals(appRedmine.getRedmineIssueIsTaskAccepted())) {
          teamTask.setIsTaskAccepted(!StringUtils.isEmpty(cfValue) ? cfValue.equals("1") : false);
        } else if (cfName.equals(appRedmine.getRedmineIssueIsOffered())) {
          teamTask.setIsOffered(!StringUtils.isEmpty(cfValue) ? cfValue.equals("1") : false);
        } else if (cfName.equals(appRedmine.getRedmineIssueUnitPrice())
            && !teamTask.getInvoiced()) {
          teamTask.setUnitPrice(
              !StringUtils.isEmpty(cfValue)
                  ? new BigDecimal(cfValue)
                  : appRedmine.getRedmineIssueUnitPriceDefault());
        }
      }
    }

    return teamTask;
  }

  public void setParentIssues() {

    LOG.debug("Set parent task of imported records..");

    if (!JPA.em().getTransaction().isActive()) {
      JPA.em().getTransaction().begin();
    }

    String values =
        parentMap.entrySet().stream()
            .map(entry -> "(" + entry.getKey() + "," + entry.getValue() + ")")
            .collect(Collectors.joining(","));

    String query =
        String.format(
            "UPDATE team_task as teamtask SET parent_task = (SELECT id from team_task where team_task.redmine_id = v.redmine_id) from (values %s) as v(id,redmine_id) where teamtask.id = v.id",
            values);

    JPA.em().createNativeQuery(query).executeUpdate();
    JPA.em().getTransaction().commit();
  }

  public void updateProjectVersionsProgress() {

    LOG.debug("Update project versions total progress..");

    String taskClosedStatusSelect =
        Beans.get(AppBusinessSupportRepository.class).all().fetchOne().getTaskClosedStatusSelect();

    for (Long id : projectVersionIdList) {

      LOG.debug("Update project version progress of: {}", id);

      teamTaskRedmineService.updateProjectVersionProgress(
          projectVersionRepo.find(id), taskClosedStatusSelect);
      updateTransaction();
    }
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
            "UPDATE team_task as teamtask SET updated_on = v.updated_on from (values %s) as v(id,updated_on) where teamtask.id = v.id",
            values);

    JPA.em().createNativeQuery(query).executeUpdate();
    JPA.em().getTransaction().commit();

    try {
      deleteUnwantedMailMessages();
    } catch (Exception e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }

    updatedOnMap = new HashMap<>();
  }
}
