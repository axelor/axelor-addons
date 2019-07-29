/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
import com.axelor.apps.project.db.ProjectCategory;
import com.axelor.apps.redmine.imports.service.ImportIssueService;
import com.axelor.apps.redmine.imports.service.ImportService;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.db.repo.MailFollowerRepository;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaAttachment;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaAttachmentRepository;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.IssueManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.IssueCategory;
import com.taskadapter.redmineapi.bean.IssuePriority;
import com.taskadapter.redmineapi.bean.IssueStatus;
import com.taskadapter.redmineapi.bean.Journal;
import com.taskadapter.redmineapi.bean.JournalDetail;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.Tracker;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wslite.json.JSONArray;
import wslite.json.JSONObject;

public class ExportIssueServiceImpl extends ExportService implements ExportIssueService {

  @Inject TeamTaskRepository teamTaskRepo;
  @Inject DMSFileRepository dmsFileRepo;
  @Inject UserRepository userRepo;

  @Inject ImportIssueService importIssueService;
  @Inject ImportService importService;

  Logger LOG = LoggerFactory.getLogger(getClass());

  private Tracker redmineTracker;

  public void exportIssue(
      Batch batch,
      RedmineManager redmineManager,
      LocalDateTime lastExportDateTime,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {
    this.redmineManager = redmineManager;
    this.onError = onError;
    this.onSuccess = onSuccess;
    this.batch = batch;

    List<TeamTask> teamTasks =
        teamTaskRepo
            .all()
            .filter(
                "self.redmineId IS NULL OR self.redmineId = 0 OR self.updatedOn > ?",
                lastExportDateTime)
            .order("id")
            .fetch();

    if (teamTasks != null && !teamTasks.isEmpty()) {
      try {
        List<Tracker> trackers = redmineManager.getIssueManager().getTrackers();
        if (trackers != null && !trackers.isEmpty()) {
          redmineTracker = trackers.get(0);
        }
      } catch (RedmineException e) {
        TraceBackService.trace(e, "", batch.getId());
      }

      for (TeamTask teamTask : teamTasks) {
        exportRedmineIssue(teamTask);
      }
    }
    String resultStr =
        String.format("ABS TeamTask -> Redmine Issue : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  @Transactional
  protected void exportRedmineIssue(TeamTask teamTask) {
    boolean isExist = false;
    IssueManager im = redmineManager.getIssueManager();
    Issue redmineIssue;
    if (Optional.ofNullable(teamTask.getRedmineId()).orElse(0) != 0) {
      try {
        redmineIssue = im.getIssueById(teamTask.getRedmineId());
        isExist = true;
      } catch (RedmineException e) {
        redmineIssue = new Issue();
      }
    } else {
      redmineIssue = new Issue();
    }
    assignIssueValues(teamTask, redmineIssue);
    redmineIssue.setTransport(redmineManager.getTransport());
    try {
      if (isExist) {
        redmineIssue.update();
      } else {
        redmineIssue = redmineIssue.create();
        teamTask.setRedmineId(redmineIssue.getId());
        teamTaskRepo.save(teamTask);
      }
      onSuccess.accept(teamTask);
      success++;
      addIssueWatchers(teamTask, redmineIssue);
      addIssueAttachments(teamTask, redmineIssue);
      addIssueJournals(teamTask, redmineIssue);
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
      fail++;
    }
  }

  private Integer getPriorityId(String taskPriority) {
    try {
      List<IssuePriority> priorities = redmineManager.getIssueManager().getIssuePriorities();
      java.util.Optional<IssuePriority> prioriy = null;
      if (priorities != null && !priorities.isEmpty()) {
        prioriy =
            priorities
                .stream()
                .filter(priority -> priority.getName().equalsIgnoreCase(taskPriority))
                .findFirst();
      }
      if (prioriy != null && prioriy.isPresent()) {
        return prioriy.get().getId();
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
    return 0;
  }

  private Integer getStatusId(String taskStatus) {
    try {
      List<IssueStatus> issueStatus = redmineManager.getIssueManager().getStatuses();
      Optional<IssueStatus> optionalStatus = null;
      if (issueStatus != null && !issueStatus.isEmpty()) {
        optionalStatus =
            issueStatus
                .stream()
                .filter(
                    status -> status.getName().equalsIgnoreCase(taskStatus.replaceAll("[-]", " ")))
                .findFirst();
      }
      if (optionalStatus != null && optionalStatus.isPresent()) {
        return optionalStatus.get().getId();
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
    return 0;
  }

  private IssueCategory getCategory(ProjectCategory projectCategory, Integer redmineProjectId) {
    try {
      if (projectCategory != null) {
        List<IssueCategory> redmineIssueCategories =
            redmineManager.getIssueManager().getCategories(redmineProjectId);
        Optional<IssueCategory> redmineIssueCategory =
            redmineIssueCategories
                .stream()
                .filter(category -> category.getName().equals(projectCategory.getName()))
                .findFirst();
        if (redmineIssueCategory.isPresent()) {
          return redmineIssueCategory.get();
        }
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
    return null;
  }

  private void assignIssueValues(TeamTask teamTask, Issue redmineIssue) {
    redmineIssue.setSubject(teamTask.getName());
    redmineIssue.setDoneRatio(teamTask.getProgressSelect());
    redmineIssue.setDescription(getTextileFromHTML(teamTask.getDescription()));

    redmineIssue.setStatusId(getStatusId(teamTask.getStatus()));
    redmineIssue.setPriorityId(getPriorityId(teamTask.getPriority()));
    redmineIssue.setTracker(redmineTracker);

    if (teamTask.getTaskDeadline() != null) {
      Date taskDeadline =
          Date.from(teamTask.getTaskDeadline().atStartOfDay(ZoneId.systemDefault()).toInstant());
      redmineIssue.setDueDate(taskDeadline);
    }
    if (teamTask.getTaskDate() != null) {
      Date taskDate =
          Date.from(teamTask.getTaskDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
      redmineIssue.setStartDate(taskDate);
    }

    redmineIssue.setCreatedOn(
        Date.from(teamTask.getCreatedOn().atZone(ZoneId.systemDefault()).toInstant()));
    if (teamTask.getUpdatedOn() != null) {
      redmineIssue.setUpdatedOn(
          Date.from(teamTask.getUpdatedOn().atZone(ZoneId.systemDefault()).toInstant()));
    }
    // setTargetVersion(teamTask, redmineIssue);
    setAssignedTo(teamTask, redmineIssue);
    setProject(teamTask, redmineIssue);
    setParentTask(teamTask, redmineIssue);
  }

  /*private void setTargetVersion(TeamTask teamTask, Issue redmineIssue) {
    ProjectVersion targetVersion = teamTask.getTargetVersion();
    if (targetVersion != null && Optional.ofNullable(targetVersion.getRedmineId()).orElse(0) != 0) {
      Version issueVersion;
      try {
        issueVersion =
            redmineManager.getProjectManager().getVersionById(targetVersion.getRedmineId());
        redmineIssue.setTargetVersion(issueVersion);
      } catch (RedmineException e) {
        TraceBackService.trace(e, "", batch.getId());
      }
    }
  }*/

  private void setAssignedTo(TeamTask teamTask, Issue redmineIssue) {
    try {
      if (teamTask.getAssignedTo() != null
          && Optional.ofNullable(teamTask.getAssignedTo().getRedmineId()).orElse(0) != 0) {
        User redmineUser =
            redmineManager.getUserManager().getUserById(teamTask.getAssignedTo().getRedmineId());
        if (redmineUser != null) {
          redmineIssue.setAssigneeId(redmineUser.getId());
        }
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  private void setProject(TeamTask teamTask, Issue redmineIssue) {
    try {
      if (teamTask.getProject() != null
          && Optional.ofNullable(teamTask.getProject().getRedmineId()).orElse(0) != 0) {
        Project redmineProject =
            redmineManager.getProjectManager().getProjectById(teamTask.getProject().getRedmineId());
        if (redmineProject != null) {
          redmineIssue.setProjectId(redmineProject.getId());
          redmineIssue.setCategory(
              getCategory(teamTask.getProjectCategory(), redmineProject.getId()));
        }
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  private void setParentTask(TeamTask teamTask, Issue redmineIssue) {
    TeamTask parentTask = teamTask.getParentTask();
    if (parentTask != null) {
      Integer parentTaskId = parentTask.getRedmineId();
      try {
        if (Optional.ofNullable(parentTaskId).orElse(0) != 0) {
          Issue redmineParentIssue = redmineManager.getIssueManager().getIssueById(parentTaskId);
          if (redmineParentIssue != null) {
            redmineIssue.setParentId(parentTaskId);
          } else {
            exportRedmineIssue(parentTask);
            redmineIssue.setParentId(parentTaskId);
          }
        } else {
          exportRedmineIssue(parentTask);
          redmineIssue.setParentId(parentTaskId);
        }
      } catch (RedmineException e) {
        TraceBackService.trace(e, "", batch.getId());
      }
    }
  }

  private void addIssueJournals(TeamTask teamTask, Issue redmineIssue) {
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
      }
    }
  }

  private void addIssueJournal(MailMessage mailMessage, Collection<Journal> journals) {
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
    }
  }

  private Journal getRedmineJournal(MailMessage mailMessage) {
    Journal redmineJournal = null;
    try {
      redmineJournal = new Journal();
      redmineJournal.setId(mailMessage.getId().intValue());
      redmineJournal.setCreatedOn(
          Date.from(mailMessage.getCreatedOn().atZone(ZoneId.systemDefault()).toInstant()));

      if (Optional.ofNullable(mailMessage.getCreatedBy().getRedmineId()).orElse(0) != 0) {
        User redmineMailMessageUser =
            redmineManager.getUserManager().getUserById(mailMessage.getCreatedBy().getRedmineId());
        if (redmineMailMessageUser != null) {
          redmineJournal.setUser(redmineMailMessageUser);
        } else {
          redmineJournal.setUser(redmineManager.getUserManager().getCurrentUser());
        }
      } else {
        redmineJournal.setUser(redmineManager.getUserManager().getCurrentUser());
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
    return redmineJournal;
  }

  private Collection<JournalDetail> getJournalDetails(JSONArray trackArr) {
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
    }
    return journalDetails;
  }

  private String getJournalDetailName(String name) {
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

  private void addIssueWatchers(TeamTask teamTask, Issue redmineIssue) {
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
                redmineWatcher.setId(
                    userRepo.find(Long.parseLong(followerId.toString())).getRedmineId());
                redmineWatcher.setName(followerName);
                redmineIssue.addWatcher(redmineWatcher.getId());
              } catch (RedmineException e) {
                TraceBackService.trace(e, "", batch.getId());
              }
            }
          }
        }
      }
    }
  }

  @Transactional
  private void addIssueAttachments(TeamTask teamTask, Issue redmineIssue) {
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
      }
    }
  }
}
