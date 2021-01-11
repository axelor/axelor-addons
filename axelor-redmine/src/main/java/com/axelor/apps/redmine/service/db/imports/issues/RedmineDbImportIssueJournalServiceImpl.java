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

import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.apps.redmine.service.db.imports.RedmineDbImportCommonService;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.exception.service.TraceBackService;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RedmineDbImportIssueJournalServiceImpl extends RedmineDbImportCommonService {

  protected TeamTaskRepository teamTaskRepo;
  protected MailMessageRepository mailMessageRepository;
  protected ProjectVersionRepository projectVersionRepo;

  @Inject
  public RedmineDbImportIssueJournalServiceImpl(
      UserRepository userRepo,
      ProjectRepository projectRepo,
      RedmineImportMappingRepository redmineImportMappingRepo,
      AppBaseService appBaseService,
      TeamTaskRepository teamTaskRepo,
      MailMessageRepository mailMessageRepository,
      ProjectVersionRepository projectVersionRepo) {

    super(userRepo, projectRepo, redmineImportMappingRepo, appBaseService);

    this.teamTaskRepo = teamTaskRepo;
    this.mailMessageRepository = mailMessageRepository;
    this.projectVersionRepo = projectVersionRepo;
  }

  protected Map<Integer, Object[]> redmineIssueIdMap = new HashMap<>();

  protected Map<String, String> userMap = new HashMap<>();
  protected Map<String, String> projectMap = new HashMap<>();
  protected Map<String, String> teamTaskMap = new HashMap<>();
  protected Map<String, String> versionMap = new HashMap<>();
  protected Map<String, String[]> attrFieldNameMap = new HashMap<>();
  protected Map<String, String[]> cfFieldNameMap = new HashMap<>();

  protected int previousJournalizedId = 0;
  protected boolean isNotification;

  protected String journalQuery;
  protected String journalDetailQuery;

  public void setFieldNameMaps() {

    LOG.debug("Set field names map for journals import..");

    attrFieldNameMap.put("project_id", new String[] {"project", "Project"});
    attrFieldNameMap.put("subject", new String[] {"name", "Name"});
    attrFieldNameMap.put("parent_id", new String[] {"parentTask", "Parent task"});
    attrFieldNameMap.put("assigned_to_id", new String[] {"assignedTo", "Assigned to"});
    attrFieldNameMap.put("start_date", new String[] {"taskDate", "Task date"});
    attrFieldNameMap.put("done_ratio", new String[] {"progressSelect", "Progress"});
    attrFieldNameMap.put("estimated_hours", new String[] {"budgetedTime", "Estimated time"});
    attrFieldNameMap.put("tracker_id", new String[] {"teamTaskCategory", "Category"});
    attrFieldNameMap.put("priority_id", new String[] {"priority", "Priority"});
    attrFieldNameMap.put("status_id", new String[] {"status", "Status"});
    attrFieldNameMap.put("description", new String[] {"description", "Description"});
    attrFieldNameMap.put("fixed_version_id", new String[] {"fixedVersion", "Fixed version"});

    cfFieldNameMap.put(appRedmine.getRedmineIssueProduct(), new String[] {"product", "Product"});
    cfFieldNameMap.put(appRedmine.getRedmineIssueDueDate(), new String[] {"dueDate", "Due date"});
    cfFieldNameMap.put(
        appRedmine.getRedmineIssueEstimatedTime(),
        new String[] {"estimatedTime", "Estimated time"});
    cfFieldNameMap.put(appRedmine.getRedmineIssueInvoiced(), new String[] {"invoiced", "Invoiced"});
    cfFieldNameMap.put(
        appRedmine.getRedmineIssueAccountedForMaintenance(),
        new String[] {"accountedForMaintenance", "Accounted for maintenance"});
    cfFieldNameMap.put(
        appRedmine.getRedmineIssueIsTaskAccepted(),
        new String[] {"isTaskAccepted", "Task Accepted"});
    cfFieldNameMap.put(
        appRedmine.getRedmineIssueIsOffered(), new String[] {"isOffered", "Offered"});
    cfFieldNameMap.put(
        appRedmine.getRedmineIssueUnitPrice(), new String[] {"unitPrice", "Unit price"});
  }

  public void prepareIssueJournalPsqlQueries(String dateFilter, Integer limit) {

    LOG.debug("Prepare PSQL queries to import redmine issue journals..");

    journalQuery =
        "select j.id,j.journalized_id,ea.address as user,j.notes,j.created_on \n"
            + "from journals as j \n"
            + "left join email_addresses as ea on ea.user_id = j.user_id \n"
            + "where j.journalized_type = 'Issue' and j.journalized_id in (?) ";

    if (!StringUtils.isEmpty(dateFilter)) {
      journalQuery = journalQuery + "and j.created_on >= timestamp '" + dateFilter + "' ";
    }

    journalQuery =
        journalQuery + "\n" + "order by j.journalized_id \n" + "limit " + limit + " offset ?";

    journalDetailQuery =
        "select jd.id,jd.property,\n"
            + "case \n"
            + "when jd.property = 'cf' then (select cf.name from custom_fields as cf where cf.id = cast(jd.prop_key as integer) limit 1) \n"
            + "else jd.prop_key \n"
            + "end,\n"
            + "case \n"
            + "when jd.property = 'cf' then (select cf.field_format from custom_fields as cf where cf.id = cast(jd.prop_key as integer) limit 1) \n"
            + "end as cf_field_format,\n"
            + "case when jd.property = 'attr' then \n"
            + "case \n"
            + "when jd.prop_key = 'status_id' then (select s.name from issue_statuses as s where s.id = cast(jd.old_value as integer) limit 1) \n"
            + "when jd.prop_key = 'tracker_id' then (select t.name from trackers as t where t.id = cast(jd.old_value as integer) limit 1) \n"
            + "when jd.prop_key = 'priority_id' then (select p.name from enumerations as p where p.id = cast(jd.old_value as integer) limit 1) \n"
            + "when jd.prop_key = 'assigned_to_id' then (select ea.address from email_addresses as ea where ea.user_id = cast(jd.old_value as integer) limit 1) \n"
            + "else jd.old_value \n"
            + "end \n"
            + "else jd.old_value \n"
            + "end,\n"
            + "case when jd.property = 'attr' then \n"
            + "case \n"
            + "when jd.prop_key = 'status_id' then (select s.name from issue_statuses as s where s.id = cast(jd.value as integer) limit 1) \n"
            + "when jd.prop_key = 'tracker_id' then (select t.name from trackers as t where t.id = cast(jd.value as integer) limit 1) \n"
            + "when jd.prop_key = 'priority_id' then (select p.name from enumerations as p where p.id = cast(jd.value as integer) limit 1) \n"
            + "when jd.prop_key = 'assigned_to_id' then (select ea.address from email_addresses as ea where ea.user_id = cast(jd.value as integer) limit 1) \n"
            + "else jd.value \n"
            + "end \n"
            + "else jd.value \n"
            + "end \n"
            + "from journal_details as jd \n"
            + "where (jd.property = 'attr' or jd.property = 'cf') and journal_id = ? \n"
            + "order by jd.id";
  }

  public void importIssueJournals(Connection connection) {

    Integer offset = 0;

    List<Integer> idList = redmineIssueIdMap.keySet().stream().collect(Collectors.toList());
    String inIds =
        idList.stream().map(x -> String.valueOf(x)).collect(Collectors.joining(",", "(", ")"));

    try (PreparedStatement preparedStatement =
        connection.prepareStatement(journalQuery.replace("(?)", inIds))) {
      preparedStatement.setFetchSize(batch.getRedmineBatch().getRedmineFetchLimit());
      ResultSet journalResultSet = null;
      retrieveIssueJournalResultSet(preparedStatement, offset, journalResultSet, connection);
    } catch (Exception e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }

    redmineIssueIdMap = new HashMap<>();
  }

  public void retrieveIssueJournalResultSet(
      PreparedStatement preparedStatement,
      Integer offset,
      ResultSet journalResultSet,
      Connection connection)
      throws SQLException {

    preparedStatement.setInt(1, offset);
    journalResultSet = preparedStatement.executeQuery();

    if (journalResultSet.next()) {
      offset = processIssueJournalResultSet(offset, journalResultSet, connection);
      retrieveIssueJournalResultSet(preparedStatement, offset, journalResultSet, connection);
    }
  }

  public Integer processIssueJournalResultSet(
      Integer offset, ResultSet journalResultSet, Connection connection) throws SQLException {

    offset += 1;

    int journalizedId = journalResultSet.getInt("journalized_id");
    Object[] values = redmineIssueIdMap.get(journalizedId);

    if (previousJournalizedId != journalizedId) {
      previousJournalizedId = journalizedId;
      LOG.debug("Import journals of issue: {}", journalizedId);
    }

    try {
      createMailMessage(
          (Long) values[0],
          (String) values[1],
          getDateAtLocalTimezone(journalResultSet.getObject("created_on", LocalDateTime.class)),
          journalResultSet.getInt("id"),
          journalResultSet.getString("notes"),
          journalResultSet.getString("user"),
          connection);
    } finally {
      updateTransaction();
    }

    if (journalResultSet.next()) {
      offset = processIssueJournalResultSet(offset, journalResultSet, connection);
    }

    return offset;
  }

  @Transactional
  public void createMailMessage(
      Long teamTaskId,
      String teamTaskFullName,
      LocalDateTime journalCreatedOn,
      int journalId,
      String journalNotes,
      String journalUser,
      Connection connection) {

    try {
      String body = "";

      MailMessage mailMessage =
          mailMessageRepository
              .all()
              .filter("self.redmineId = ?1 AND self.relatedId = ?2", journalId, teamTaskId)
              .fetchOne();

      if (mailMessage != null) {
        body = mailMessage.getBody();

        if (!StringUtils.isBlank(body)) {
          String note = getHtmlFromTextile(journalNotes).replace("\"", "'");
          body =
              !StringUtils.isBlank(note)
                  ? body.replaceAll(",\"content\":\".*\",", ",\"content\":\"" + note + "\",")
                  : body.replaceAll(",\"content\":\".*\",", ",\"content\":\"\",");

          mailMessage.setBody(body);
          mailMessageRepository.save(mailMessage);
        }
      } else {
        isNotification = false;

        body = getJournalDetails(journalId, journalNotes, connection);

        if (!StringUtils.isBlank(body)) {
          mailMessage = new MailMessage();
          mailMessage.setRelatedId(teamTaskId);
          mailMessage.setRelatedModel(TeamTask.class.getName());
          mailMessage.setRelatedName(teamTaskFullName);
          mailMessage.setSubject("Task updated (Redmine)");
          mailMessage.setType(isNotification ? "notification" : "comment");
          mailMessage.setBody(body);
          mailMessage.setRedmineId(journalId);
          User redmineUser = getUserFromEmail(journalUser);
          mailMessage.setAuthor(redmineUser);
          setCreatedByUser(mailMessage, redmineUser, "setCreatedBy");
          setLocalDateTime(mailMessage, journalCreatedOn, "setCreatedOn");

          mailMessageRepository.save(mailMessage);
        }
      }
    } catch (Exception e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public String getJournalDetails(int journalId, String journalNotes, Connection connection) {

    try (PreparedStatement preparedStatement = connection.prepareStatement(journalDetailQuery)) {
      preparedStatement.setInt(1, journalId);
      ResultSet journalDetailResultSet = preparedStatement.executeQuery();

      StringBuilder strBuilder = new StringBuilder();
      StringBuilder trackStrBuilder = new StringBuilder();
      strBuilder.append("{\"title\":\"Task updated (Redmine)\",\"tracks\":[");
      getTrack(trackStrBuilder, journalDetailResultSet);

      if (trackStrBuilder.length() > 0) {
        trackStrBuilder.deleteCharAt(trackStrBuilder.length() - 1);
        strBuilder.append(trackStrBuilder);
        isNotification = true;
      }

      strBuilder.append("],\"content\":\"");
      String note = journalNotes;

      if (!StringUtils.isBlank(note)) {
        strBuilder.append(getHtmlFromTextile(note).replace("\"", "'"));
      }

      strBuilder.append("\",\"tags\":[]}");

      if (StringUtils.isBlank(trackStrBuilder.toString()) && StringUtils.isBlank(journalNotes)) {
        return null;
      }

      return strBuilder.toString();
    } catch (Exception e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }

    return getHtmlFromTextile(journalNotes).replace("\"", "'");
  }

  public void getTrack(StringBuilder trackStrBuilder, ResultSet journalDetailResultSet)
      throws SQLException {

    String journalDetailPropKey;
    String journalDetailProperty;
    String journalDetailOldValue;
    String journalDetailValue;
    String journalDetailCfFormat;

    while (journalDetailResultSet.next()) {
      journalDetailPropKey = journalDetailResultSet.getString("prop_key");
      journalDetailProperty = journalDetailResultSet.getString("property");
      journalDetailOldValue = journalDetailResultSet.getString("old_value");
      journalDetailValue = journalDetailResultSet.getString("value");
      journalDetailCfFormat = journalDetailResultSet.getString("cf_field_format");

      if (!StringUtils.isBlank(journalDetailPropKey)) {
        String[] fieldNames = getFieldName(journalDetailPropKey, journalDetailProperty);
        trackStrBuilder.append("{\"name\":\"" + fieldNames[0] + "\",\"title\":\"" + fieldNames[1]);

        if (!StringUtils.isBlank(journalDetailOldValue)) {
          String oldValue =
              getValue(
                  journalDetailOldValue,
                  journalDetailPropKey,
                  journalDetailProperty,
                  journalDetailCfFormat);
          trackStrBuilder.append("\",\"oldValue\":\"" + oldValue);
        }

        trackStrBuilder.append("\",\"value\":\"");

        if (!StringUtils.isBlank(journalDetailValue)) {
          String newValue =
              getValue(
                  journalDetailValue,
                  journalDetailPropKey,
                  journalDetailProperty,
                  journalDetailCfFormat);
          trackStrBuilder.append(newValue);
        }

        trackStrBuilder.append("\"},");
      }
    }
  }

  public String[] getFieldName(String name, String journalDetailProperty) {

    if (journalDetailProperty.equals("attr") && attrFieldNameMap.containsKey(name)) {
      return attrFieldNameMap.get(name);
    } else if (journalDetailProperty.equals("cf") && cfFieldNameMap.containsKey(name)) {
      return cfFieldNameMap.get(name);
    }

    return new String[] {name, name};
  }

  public String getValue(
      String value,
      String journalDetailName,
      String journalDetailProperty,
      String journalDetailCfFormat) {

    if (journalDetailProperty.equals("attr")) {

      switch (journalDetailName) {
        case "status_id":
          value = (String) selectionMap.get(fieldMap.get(value));
          break;
        case "tracker_id":
          value = fieldMap.get(value);
          break;
        case "priority_id":
          value = (String) selectionMap.get(fieldMap.get(value));
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
          break;
        case "description":
          value = getHtmlFromTextile(value);
          break;
        default:
          break;
      }
    } else if (journalDetailProperty.equals("cf")) {

      switch (journalDetailCfFormat) {
        case "user":
          value = getUserName(value);
          break;
        case "version":
          value = getVersionName(value);
          break;
        case "bool":
          value = value.equals("1") ? "true" : "false";
          break;
        default:
          break;
      }
    }

    return !StringUtils.isEmpty(value) ? value.replace("\"", "'") : value;
  }

  public String getUserName(String value) {

    if (!userMap.containsKey(value)) {
      User user = getUserFromEmail(value);
      String name = user != null ? user.getFullName() : "Unknown user";
      userMap.put(value, name);
      return name;
    }

    return userMap.get(value);
  }

  public String getProjectName(String value) {

    if (!projectMap.containsKey(value)) {
      Project project = projectRepo.findByRedmineId(Integer.parseInt(value));
      String name = project != null ? project.getFullName() : "Unknown project";
      projectMap.put(value, name);
      return name;
    }

    return projectMap.get(value);
  }

  public String getTeamTaskName(String value) {

    if (!teamTaskMap.containsKey(value)) {
      TeamTask teamTask = teamTaskRepo.findByRedmineId(Integer.parseInt(value));
      String name = teamTask != null ? teamTask.getFullName() : "Unknown task";
      teamTaskMap.put(value, name);
      return name;
    }

    return teamTaskMap.get(value);
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
