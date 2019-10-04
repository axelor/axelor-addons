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
package com.axelor.apps.redmine.exports.service;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.project.db.ProjectCategory;
import com.axelor.apps.redmine.db.OpenSuitRedmineSync;
import com.axelor.apps.redmine.db.repo.OpenSuitRedmineSyncRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.db.mapper.Mapper;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.db.repo.MailFollowerRepository;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaAttachment;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaAttachmentRepository;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.Include;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.IssuePriority;
import com.taskadapter.redmineapi.bean.IssueStatus;
import com.taskadapter.redmineapi.bean.Journal;
import com.taskadapter.redmineapi.bean.JournalDetail;
import com.taskadapter.redmineapi.bean.User;
import com.taskadapter.redmineapi.bean.Watcher;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wslite.json.JSONArray;
import wslite.json.JSONObject;

public class RedmineExportIssueServiceImpl extends RedmineExportService
    implements RedmineExportIssueService {

  protected OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo;
  protected TeamTaskRepository teamTaskRepo;
  protected RedmineDynamicExportService redmineDynamicExportService;
  protected MetaModelRepository metaModelRepo;
  protected UserRepository userRepo;
  protected DMSFileRepository dmsFileRepo;

  @Inject
  public RedmineExportIssueServiceImpl(
      OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo,
      TeamTaskRepository teamTaskRepo,
      RedmineDynamicExportService redmineDynamicExportService,
      MetaModelRepository metaModelRepo,
      UserRepository userRepo,
      DMSFileRepository dmsFileRepo) {

    this.openSuiteRedmineSyncRepo = openSuiteRedmineSyncRepo;
    this.teamTaskRepo = teamTaskRepo;
    this.redmineDynamicExportService = redmineDynamicExportService;
    this.metaModelRepo = metaModelRepo;
    this.userRepo = userRepo;
    this.dmsFileRepo = dmsFileRepo;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());
  private LocalDateTime lastBatchUpdatedOn;

  @Override
  public void exportIssue(
      Batch batch,
      LocalDateTime lastBatchUpdatedOn,
      RedmineManager redmineManager,
      List<TeamTask> teamTaskList,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError,
      List<Object[]> errorObjList) {

    if (teamTaskList != null && !teamTaskList.isEmpty()) {
      OpenSuitRedmineSync openSuiteRedmineSyncIssue =
          openSuiteRedmineSyncRepo.findBySyncTypeSelect(
              OpenSuitRedmineSyncRepository.SYNC_TYPE_ISSUE);

      this.errorObjList = errorObjList;
      this.dynamicFieldsSyncList = openSuiteRedmineSyncIssue.getDynamicFieldsSyncList();

      if (validateDynamicFieldsSyncList(
          dynamicFieldsSyncList,
          METAMODEL_TEAM_TASK,
          Mapper.toMap(new TeamTask()),
          Mapper.toMap(new Issue()))) {

        this.redmineManager = redmineManager;
        this.batch = batch;
        this.onError = onError;
        this.onSuccess = onSuccess;
        this.redmineIssueManager = redmineManager.getIssueManager();
        this.redmineUserManager = redmineManager.getUserManager();
        this.redmineProjectManager = redmineManager.getProjectManager();
        this.metaModel = metaModelRepo.findByName(METAMODEL_TEAM_TASK);
        this.lastBatchUpdatedOn = lastBatchUpdatedOn;

        String syncTypeSelect = openSuiteRedmineSyncIssue.getOpenSuiteToRedmineSyncSelect();

        for (TeamTask teamTask : teamTaskList) {
          createRedmineIssue(teamTask, syncTypeSelect);
        }
      }
    }

    String resultStr =
        String.format("ABS Teamtask -> Redmine Issue : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  public void createRedmineIssue(TeamTask teamTask, String syncTypeSelect) {

    try {
      com.taskadapter.redmineapi.bean.Issue redmineIssue = null;

      if (teamTask.getRedmineId() == null || teamTask.getRedmineId().equals(0)) {
        redmineIssue = new com.taskadapter.redmineapi.bean.Issue();
      } else {
        redmineIssue = redmineIssueManager.getIssueById(teamTask.getRedmineId(), Include.journals);
      }

      // Sync type - On create
      if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_CREATE)
          && (teamTask.getRedmineId() != null && !teamTask.getRedmineId().equals(0))) {
        return;
      }

      // Sync type - On update
      if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_UPDATE)
          && redmineIssue.getUpdatedOn() != null
          && lastBatchUpdatedOn != null) {

        // If updates are made on both sides and redmine side is latest updated then abort export
        LocalDateTime redmineUpdatedOn =
            redmineIssue
                .getUpdatedOn()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        if (redmineUpdatedOn.isAfter(lastBatchUpdatedOn)
            && redmineUpdatedOn.isAfter(teamTask.getUpdatedOn())) {
          return;
        }
      }

      Map<String, Object> teamTaskMap = Mapper.toMap(teamTask);
      Map<String, Object> redmineIssueMap = Mapper.toMap(redmineIssue);
      Map<String, Object> redmineIssueCustomFieldsMap = new HashMap<>();

      redmineIssueMap =
          redmineDynamicExportService.createRedmineDynamic(
              dynamicFieldsSyncList,
              teamTaskMap,
              redmineIssueMap,
              redmineIssueCustomFieldsMap,
              metaModel,
              teamTask,
              redmineManager);

      Mapper redmineIssueMapper = Mapper.of(redmineIssue.getClass());
      Iterator<Entry<String, Object>> redmineIssueMapItr = redmineIssueMap.entrySet().iterator();

      while (redmineIssueMapItr.hasNext()) {
        Map.Entry<String, Object> entry = redmineIssueMapItr.next();
        redmineIssueMapper.set(redmineIssue, entry.getKey(), entry.getValue());
      }

      // If redmine issue has no associated project
      if (redmineIssue.getProjectId() == null || redmineIssue.getProjectId() == 0) {
        setErrorLog(
            TeamTask.class.getSimpleName(),
            teamTask.getId().toString(),
            null,
            null,
            I18n.get(IMessage.REDMINE_SYNC_ERROR_ISSUE_PROJECT_NOT_FOUND));
        return;
      }

      // Special rule for user
      if (teamTask.getAssignedTo() != null
          && teamTask.getAssignedTo().getPartner() != null
          && teamTask.getAssignedTo().getPartner().getEmailAddress() != null) {
        User redmineUser =
            findRedmineUserByEmail(
                teamTask.getAssignedTo().getPartner().getEmailAddress().getAddress());

        if (redmineUser == null) {
          redmineUser = redmineUserManager.getCurrentUser();
        }

        redmineIssue.setAssigneeId(redmineUser.getId());
        redmineIssue.setAssigneeName(redmineUser.getFullName());
      }

      // Special rule for priority (problem with directly set redmine priorityText)
      String priorityText = redmineIssue.getPriorityText();
      IssuePriority issuePriority =
          redmineIssueManager
              .getIssuePriorities()
              .stream()
              .filter(priority -> priority.getName().equals(priorityText))
              .findAny()
              .orElse(redmineIssueManager.getIssuePriorities().get(0));
      redmineIssue.setPriorityId(issuePriority.getId());

      // Special rule for status (problem with directly set redmine statusName)
      String statusName = redmineIssue.getStatusName();
      IssueStatus issueStatus =
          redmineIssueManager
              .getStatuses()
              .stream()
              .filter(status -> status.getName().equals(statusName))
              .findAny()
              .orElse(redmineIssueManager.getStatuses().get(0));
      redmineIssue.setStatusId(issueStatus.getId());

      // Fixed rule for tracker
      ProjectCategory projectCategory = teamTask.getProjectCategory();

      if (projectCategory != null && !projectCategory.getRedmineId().equals(0)) {
        redmineIssue.setTracker(getTrackerById(projectCategory.getRedmineId()));
      } else {
        redmineIssue.setTracker(redmineIssueManager.getTrackers().get(0));
      }

      // Fixed rule for targetVersion
      // Fix Error - The bean of type: com.taskadapter.redmineapi.bean.Issue has no property
      // called: targetVersion
      ProjectVersion targetVersion = teamTask.getTargetVersion();

      if (targetVersion != null && !targetVersion.getRedmineId().equals(0)) {
        redmineIssue.setTargetVersion(
            redmineProjectManager.getVersionById(targetVersion.getRedmineId()));
      }

      // Create or update redmine object
      this.saveRedmineIssue(redmineIssue, teamTask, redmineIssueCustomFieldsMap);
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);

      if (e.getMessage().equals(REDMINE_SERVER_404_NOT_FOUND)) {
        setErrorLog(
            TeamTask.class.getSimpleName(),
            teamTask.getId().toString(),
            null,
            null,
            I18n.get(IMessage.REDMINE_SYNC_ERROR_RECORD_NOT_FOUND));
      }
    }
  }

  @Transactional
  public void saveRedmineIssue(
      Issue redmineIssue, TeamTask teamTask, Map<String, Object> redmineIssueCustomFieldsMap) {

    try {
      redmineIssue.setTransport(redmineManager.getTransport());

      if (redmineIssue.getId() == null) {
        redmineIssue = redmineIssue.create();
        teamTask.setRedmineId(redmineIssue.getId());
        teamTaskRepo.save(teamTask);
      }

      // Set custom fields
      setRedmineCustomFieldValues(
          redmineIssue.getCustomFields(), redmineIssueCustomFieldsMap, teamTask.getId());

      redmineIssue.update();

      teamTask.addRedminebatchSetItem(batch);

      onSuccess.accept(teamTask);
      success++;

      // Export issue watchers
      addIssueWatchers(teamTask, redmineIssue);

      // Export issue attachments
      addIssueAttachments(teamTask, redmineIssue);

      // Export issue journals
      addIssueJournals(teamTask, redmineIssue);
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
      fail++;

      if (e.getMessage().equals(REDMINE_ISSUE_ASSIGNEE_INVALID)) {
        setErrorLog(
            TeamTask.class.getSimpleName(),
            teamTask.getId().toString(),
            null,
            null,
            I18n.get(IMessage.REDMINE_SYNC_ERROR_ASSIGNEE_IS_NOT_VALID));
      }
    }
  }

  public void addIssueWatchers(TeamTask teamTask, Issue redmineIssue) {

    Collection<Watcher> redmineIssueWatchers = redmineIssue.getWatchers();
    List<Map<String, Object>> mailFollowers =
        Beans.get(MailFollowerRepository.class).findFollowers(teamTask);

    if (mailFollowers != null) {

      for (Map<String, Object> mailFollower : mailFollowers) {

        @SuppressWarnings("unchecked")
        HashMap<String, String> follower = (HashMap<String, String>) mailFollower.get("$author");

        if (follower != null) {
          String followerName = follower.get("fullName");
          Object followerId = follower.get("id");

          if (redmineIssueWatchers != null) {
            Optional<Watcher> existingWatcher =
                redmineIssueWatchers
                    .stream()
                    .filter(watcher -> watcher.getName().equalsIgnoreCase(followerName))
                    .findFirst();

            if (!existingWatcher.isPresent()) {

              try {
                Watcher redmineWatcher = new Watcher();
                com.axelor.auth.db.User user = userRepo.find(Long.parseLong(followerId.toString()));
                User redmineUser = null;

                if (user.getPartner() != null && user.getPartner().getEmailAddress() != null) {
                  redmineUser =
                      findRedmineUserByEmail(user.getPartner().getEmailAddress().getAddress());
                }

                if (redmineUser != null) {
                  redmineWatcher.setId(redmineUser.getId());
                  redmineWatcher.setName(followerName);
                  redmineIssue.addWatcher(redmineWatcher.getId());
                }
              } catch (RedmineException e) {
                TraceBackService.trace(e, "", batch.getId());
                onError.accept(e);
              }
            }
          }
        }
      }
    }
  }

  @Transactional
  public void addIssueAttachments(TeamTask teamTask, Issue redmineIssue) {

    List<MetaAttachment> attachments =
        Beans.get(MetaAttachmentRepository.class)
            .all()
            .filter(
                "self.objectId = ?1 AND self.objectName = ?2", // self.createdOn > ?3
                teamTask.getId(),
                teamTask.getClass().getName())
            .fetch();

    for (MetaAttachment metaAttachment : attachments) {

      try {
        DMSFile existingFile =
            dmsFileRepo
                .all()
                .filter(
                    "self.relatedId = ?1 AND self.relatedModel = ?2 AND self.metaFile = ?3 AND (self.redmineId IS NULL OR self.redmineId = 0)",
                    metaAttachment.getObjectId(),
                    metaAttachment.getObjectName(),
                    metaAttachment.getMetaFile())
                .fetchOne();

        if (existingFile != null) {
          MetaFile metaFile = metaAttachment.getMetaFile();
          File attachmentFile = MetaFiles.getPath(metaFile).toFile();

          if (attachmentFile.exists()) {
            // Attachment attachment =
            redmineManager
                .getAttachmentManager()
                .addAttachmentToIssue(redmineIssue.getId(), attachmentFile, metaFile.getFileType());
            existingFile.setRedmineId(-1); // attachment.getId());
            dmsFileRepo.save(existingFile);
          }
        }
      } catch (RedmineException | IOException e) {
        TraceBackService.trace(e, "", batch.getId());
        onError.accept(e);
      }
    }
  }

  public void addIssueJournals(TeamTask teamTask, Issue redmineIssue) {

    List<MailMessage> mailMessages = Beans.get(MailMessageRepository.class).findAll(teamTask, 0, 0);
    Collection<Journal> journals = new HashSet<>();

    for (MailMessage mailMessage : mailMessages) {

      if (!StringUtils.isBlank(mailMessage.getBody())) {
        addIssueJournal(mailMessage, journals);
      }
    }

    if (!journals.isEmpty()) {
      redmineIssue.addJournals(journals);

      try {
        redmineIssue.update();
      } catch (RedmineException e) {
        TraceBackService.trace(e, "", batch.getId());
        onError.accept(e);
      }
    }
  }

  public void addIssueJournal(MailMessage mailMessage, Collection<Journal> journals) {

    try {
      JSONObject jsonMailMessage = new JSONObject(mailMessage.getBody());

      if (jsonMailMessage.containsKey("title")) {
        String title = jsonMailMessage.getString("title");

        if (!StringUtils.isBlank(title) && title.equals("Task updated")) {

          JSONArray trackArr = (JSONArray) new JSONObject(mailMessage.getBody()).get("tracks");

          if (trackArr != null && !trackArr.isEmpty()) {
            Journal redmineJournal = getRedmineJournal(mailMessage);
            Collection<JournalDetail> journalDetails = getJournalDetails(trackArr);

            if (!journalDetails.isEmpty()) {
              redmineJournal.addDetails(journalDetails);
            }
            String content = null;

            if (jsonMailMessage.containsKey("content")) {
              content = jsonMailMessage.getString("content");
            }
            redmineJournal.setNotes(getTextileFromHTML(content));
            journals.add(redmineJournal);
          }
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
    }
  }

  public Journal getRedmineJournal(MailMessage mailMessage) {

    Journal redmineJournal = null;

    try {
      redmineJournal = new Journal();
      redmineJournal.setId(mailMessage.getId().intValue());
      redmineJournal.setCreatedOn(
          Date.from(mailMessage.getCreatedOn().atZone(ZoneId.systemDefault()).toInstant()));
      redmineJournal.setUser(redmineUserManager.getCurrentUser());
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
    }

    return redmineJournal;
  }

  public Collection<JournalDetail> getJournalDetails(JSONArray trackArr) {

    Collection<JournalDetail> journalDetails = new HashSet<>();

    try {

      for (Object track : trackArr) {
        JSONObject jsonTrack = (JSONObject) track;
        JournalDetail journalDetail = new JournalDetail();
        journalDetail.setName(getJournalDetailName(jsonTrack.getString("name")));

        if (jsonTrack.containsKey("oldValue")) {
          journalDetail.setOldValue(jsonTrack.getString("oldValue"));
        }

        journalDetail.setNewValue(jsonTrack.getString("value"));
        journalDetail.setProperty("attr");
        journalDetails.add(journalDetail);
      }
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
    }

    return journalDetails;
  }

  public String getJournalDetailName(String name) {

    switch (name) {
      case "project":
        return "project_id";
      case "name":
        return "subject";
      case "parentTask":
        return "parent_id";
      case "assignedTo":
        return "assigned_to_id";
      case "isPrivate":
        return "is_private";
      case "taskDate":
        return "start_date";
      case "progressSelect":
        return "done_ratio";
      case "taskDeadline":
        return "due_date";
      case "totalPlannedHrs":
        return "estimated_hours";
      case "projectCategory":
        return "category_id";
      case "priority":
        return "priority_id";
      case "status":
        return "status_id";
      default:
        return name;
    }
  }
}
