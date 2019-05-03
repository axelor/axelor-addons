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
package com.axelor.apps.redmine.imports.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import javax.persistence.PersistenceException;

import org.hibernate.proxy.HibernateProxyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.db.repo.UserBaseRepository;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectCategory;
import com.axelor.apps.project.db.ProjectPlanningTime;
import com.axelor.apps.project.db.repo.ProjectCategoryRepository;
import com.axelor.apps.project.db.repo.ProjectPlanningTimeRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.TrackerRepository;
import com.axelor.apps.redmine.service.RedmineServiceImpl;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.mail.db.MailFollower;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.db.repo.MailFollowerRepository;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.Include;
import com.taskadapter.redmineapi.Params;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.TimeEntryManager;
import com.taskadapter.redmineapi.bean.Attachment;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.IssueCategory;
import com.taskadapter.redmineapi.bean.Journal;
import com.taskadapter.redmineapi.bean.JournalDetail;
import com.taskadapter.redmineapi.bean.TimeEntry;
import com.taskadapter.redmineapi.bean.Tracker;
import com.taskadapter.redmineapi.bean.Version;
import com.taskadapter.redmineapi.bean.Watcher;

public class ImportIssueServiceImpl extends ImportService implements ImportIssueService {

  @Inject TeamTaskRepository teamTaskRepo;
  @Inject ProjectRepository projectRepo;
  @Inject UserBaseRepository userRepo;
  @Inject ProjectCategoryRepository projectCategoryRepo;
  @Inject MailFollowerRepository mailFollowerRepo;
  @Inject MailMessageRepository mailMessageRepo;
  @Inject DMSFileRepository dmsFileRepo;
  @Inject ProjectPlanningTimeRepository projectPlanningTimeRepo;
  @Inject TrackerRepository trackerRepo;
  @Inject ProjectVersionRepository projectVersionRepo;

  private static final Integer FETCH_LIMIT = 5;
  private static Integer TOTAL_FETCH_COUNT = 0;

  Logger LOG = LoggerFactory.getLogger(getClass());
  protected boolean isExist = false;

  @Override
  public void importIssue(
      Batch batch,
      Date lastImportDateTime,
      RedmineManager redmineManager,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {
    TOTAL_FETCH_COUNT = 0;
    this.redmineManager = redmineManager;
    this.isReset = batch.getRedmineBatch().getUpdateAlreadyImported();
    this.batch = batch;

    try {
      List<Issue> issueList;
      do {
        issueList = fetchIssues(lastImportDateTime);
        TOTAL_FETCH_COUNT += issueList.size();
        if (issueList != null && !issueList.isEmpty()) {
          for (Issue redmineIssue : issueList) {
            if (!isReset
                && lastImportDateTime != null
                && redmineIssue.getCreatedOn().before(lastImportDateTime)) {
              continue;
            }
            isExist = false;
            importRedmineIssue(redmineIssue, lastImportDateTime, onSuccess, onError);
          }
        }
      } while (issueList.size() > 0);
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    }
    String resultStr = String.format("Issue : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  public TeamTask getTeamTask(Issue redmineIssue, Date lastImportDateTime, boolean isReset) {
    if (redmineManager == null) {
      try {
        redmineManager = Beans.get(RedmineServiceImpl.class).getRedmineManager();
      } catch (AxelorException e) {
      }
      LOG = LoggerFactory.getLogger(getClass());
      this.isReset = isReset;
    }
    TeamTask teamTask = teamTaskRepo.findByRedmineId(redmineIssue.getId());
    if (teamTask == null) {
      isExist = false;
      teamTask = new TeamTask();
      teamTask.setRedmineId(redmineIssue.getId());
    } else {
      isExist = true;
      if (lastImportDateTime != null
          && redmineIssue.getUpdatedOn() != null
          && lastImportDateTime.after(redmineIssue.getUpdatedOn())) {
        setTeamTaskProjectPlanningTimeList(teamTask, redmineIssue, lastImportDateTime);
        return teamTask;
      }
    }
    setTeamTaskValues(teamTask, redmineIssue, lastImportDateTime);
    return teamTask;
  }

  @Transactional
  private void importRedmineIssue(
      Issue redmineIssue,
      Date lastImportDateTime,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {
    TeamTask teamTask = getTeamTask(redmineIssue, lastImportDateTime, isReset);
    if (teamTask != null) {
      try {
        teamTaskRepo.save(teamTask);
        manageTeamTask(teamTask, redmineIssue, lastImportDateTime);

        onSuccess.accept(teamTask);
        success++;
      } catch (PersistenceException e) {
        onError.accept(e);
        fail++;
        JPA.em().getTransaction().rollback();
        JPA.em().getTransaction().begin();
      } catch (Exception e) {
        onError.accept(e);
        fail++;
        TraceBackService.trace(e, "", batch.getId());
      }
    }
  }

  @Transactional
  private void manageTeamTask(TeamTask teamTask, Issue redmineIssue, Date lastImportDateTime) {
    try {
      Collection<Attachment> attachments =
          redmineManager
              .getIssueManager()
              .getIssueById(redmineIssue.getId(), Include.attachments)
              .getAttachments();
      importAttachments(teamTask, attachments, lastImportDateTime);
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
    importIssueWatchers(teamTask, redmineIssue);
    importIssueJournals(teamTask, redmineIssue, lastImportDateTime);
    JPA.em().getTransaction().commit();
    JPA.em().getTransaction().begin();
    MailMessage mailMessage =
        mailMessageRepo
            .all()
            .filter(
                "self.relatedId = ?1 AND self.relatedModel = ?2 AND self.body LIKE '%Record created%'",
                teamTask.getId(), teamTask.getClass().getName())
            .fetchOne();
    if (mailMessage != null) {
      setLocalDateTime(mailMessage, redmineIssue.getCreatedOn(), "setCreatedOn");
      mailMessageRepo.save(mailMessage);
    }
  }

  private void setTeamTaskValues(TeamTask teamTask, Issue redmineIssue, Date lastImportDateTime) {
    Project project = projectRepo.findByRedmineId(redmineIssue.getProjectId());
    teamTask.setProject(project);
    teamTask.setTypeSelect(TeamTaskRepository.TYPE_TASK);
    teamTask.setDescription(getHtmlFromTextile(redmineIssue.getDescription()));
    teamTask.setName(redmineIssue.getSubject());
    teamTask.setProgressSelect(redmineIssue.getDoneRatio());
    teamTask.setStatus(redmineIssue.getStatusName().toLowerCase());
    teamTask.setPriority(redmineIssue.getPriorityText().toLowerCase());
    teamTask.setFullName(redmineIssue.getSubject());

    if (redmineIssue.getCategory() != null) {
      ProjectCategory projectCategory = getProjectCategory(redmineIssue.getCategory(), project);
      teamTask.setProjectCategory(projectCategory);
      if (project != null) {
        project.addProjectCategorySetItem(projectCategory);
      }
    }

    if (redmineIssue.getAssigneeId() != null) {
      teamTask.setAssignedTo(userRepo.findByRedmineId(redmineIssue.getAssigneeId()));
    }
    if (redmineIssue.getStartDate() != null) {
      teamTask.setTaskDate(
          redmineIssue.getStartDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    if (redmineIssue.getDueDate() != null) {
      teamTask.setTaskDeadline(
          redmineIssue.getDueDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    setTargetVersion(teamTask, redmineIssue);
    setTracker(teamTask, redmineIssue);
    setParentTask(teamTask, redmineIssue, lastImportDateTime);
    setTeamTaskProjectPlanningTimeList(teamTask, redmineIssue, lastImportDateTime);

    setLocalDateTime(teamTask, redmineIssue.getCreatedOn(), "setCreatedOn");
    setLocalDateTime(teamTask, redmineIssue.getUpdatedOn(), "setUpdatedOn");
  }

  private void setTargetVersion(TeamTask teamTask, Issue redmineIssue) {
    Version redmineVersion = redmineIssue.getTargetVersion();
    if (redmineVersion != null) {
      ProjectVersion projectVersion = projectVersionRepo.findByRedmineId(redmineVersion.getId());
      if (projectVersion != null) {
        teamTask.setTargetVersion(projectVersion);
      }
    }
  }

  private void setTracker(TeamTask teamTask, Issue redmineIssue) {
    Tracker redmineTracker = redmineIssue.getTracker();
    com.axelor.apps.project.db.Tracker tracker =
        trackerRepo.findByRedmineId(redmineTracker.getId());
    if (tracker == null) {
      tracker = trackerRepo.findByName(redmineTracker.getName());
      if (tracker != null) {
        tracker.setRedmineId(redmineTracker.getId());
      }
    }
    teamTask.setTracker(tracker);
  }

  private void setParentTask(TeamTask teamTask, Issue redmineIssue, Date lastImportDateTime) {
    Integer redmineParentTaskId = redmineIssue.getParentId();
    if (redmineParentTaskId != null) {
      TeamTask parentTask = teamTaskRepo.findByRedmineId(redmineParentTaskId);
      if (parentTask == null) {
        try {
          parentTask =
              getTeamTask(
                  redmineManager.getIssueManager().getIssueById(redmineParentTaskId),
                  lastImportDateTime,
                  isReset);
        } catch (RedmineException e) {
          TraceBackService.trace(e, "", batch.getId());
        }
      }
      teamTask.setParentTask(parentTask);
    }
  }

  private List<Issue> fetchIssues(Date lastImportDateTime) {
    try {
      Params params = new Params();
      params.add("status_id", "*");
      params.add("limit", FETCH_LIMIT.toString());
      params.add("offset", TOTAL_FETCH_COUNT.toString());
      /*
       * if (lastImportDate != null) { params.add("f[]", "updated_on").add("op[" +
       * "updated_on" + "]", "lt") .add("v[" + "updated_on" + "][]",lastImportDate); }
       */
      List<Issue> issueList = redmineManager.getIssueManager().getIssues(params).getResults();
      return issueList;
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
    return null;
  }

  private ProjectCategory getProjectCategory(IssueCategory redmineIssueCategory, Project project) {
    ProjectCategory projectCategory =
        projectCategoryRepo.findByRedmineId(redmineIssueCategory.getId());
    if (projectCategory != null) {
      if (isReset) {
        projectCategory.setName(redmineIssueCategory.getName());
      }
      return projectCategory;
    } else {
      projectCategory = new ProjectCategory();
      projectCategory.setRedmineId(redmineIssueCategory.getId());
      // projectCategory.addRedmineProject(project);
      projectCategory.setName(redmineIssueCategory.getName());
      return projectCategory;
    }
  }

  private void setTeamTaskProjectPlanningTimeList(
      TeamTask teamTask, Issue redmineIssue, Date lastImportDateTime) {
    TimeEntryManager tm = redmineManager.getTimeEntryManager();
    ProjectPlanningTime projectPlanningTime;
    try {
      List<TimeEntry> timeEntries = tm.getTimeEntriesForIssue(redmineIssue.getId());
      for (TimeEntry timeEntry : timeEntries) {
        if (!isReset
            && lastImportDateTime != null
            && timeEntry.getCreatedOn().before(lastImportDateTime)) {
          continue;
        }
        projectPlanningTime = getProjectPlanningTime(teamTask, timeEntry, lastImportDateTime);
        if (projectPlanningTime != null) {
          teamTask.addProjectPlanningTimeListItem(projectPlanningTime);
        }
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  private ProjectPlanningTime getProjectPlanningTime(
      TeamTask teamTask, TimeEntry redmineTimeEntry, Date lastImportDateTime) {
    if (lastImportDateTime != null
        && redmineTimeEntry.getUpdatedOn() != null
        && redmineTimeEntry.getUpdatedOn().before(lastImportDateTime)) {
      return null;
    }

    ProjectPlanningTime projectPlanningTime =
        projectPlanningTimeRepo.findByRedmineId(redmineTimeEntry.getId());
    if (projectPlanningTime == null) {
      projectPlanningTime = new ProjectPlanningTime();
      projectPlanningTime.setRedmineId(redmineTimeEntry.getId());
    }

    projectPlanningTime.setUser(getPlanningTimeUser(redmineTimeEntry, projectPlanningTime));
    Project project = getPlanningTimeProject(redmineTimeEntry, projectPlanningTime);
    Product product = getPlanningTimeProduct(redmineTimeEntry, projectPlanningTime);
    projectPlanningTime.setProject(project);
    projectPlanningTime.setProduct(product);
    if (project != null && product != null) {
      project.addProductSetItem(product);
    }

    projectPlanningTime.setTask(teamTask);
    projectPlanningTime.setDescription(redmineTimeEntry.getComment());
    projectPlanningTime.setRealHours(BigDecimal.valueOf(redmineTimeEntry.getHours()));
    projectPlanningTime.setTypeSelect(2);

    LocalDate projectPlanningDate = null;
    if (redmineTimeEntry.getSpentOn() != null) {
      projectPlanningDate =
          redmineTimeEntry.getSpentOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
    projectPlanningTime.setDate(projectPlanningDate);

    setLocalDateTime(projectPlanningTime, redmineTimeEntry.getCreatedOn(), "setCreatedOn");
    setLocalDateTime(projectPlanningTime, redmineTimeEntry.getUpdatedOn(), "setUpdatedOn");
    return projectPlanningTime;
  }

  private Project getPlanningTimeProject(
      TimeEntry redmineTimeEntry, ProjectPlanningTime projectPlanningTime) {
    Project project =
        Beans.get(ProjectRepository.class).findByRedmineId(redmineTimeEntry.getProjectId());
    if (project == null) {
      project = Beans.get(ProjectRepository.class).findByName(redmineTimeEntry.getProjectName());
    }
    return project;
  }

  private User getPlanningTimeUser(
      TimeEntry redmineTimeEntry, ProjectPlanningTime projectPlanningTime) {
    User user = Beans.get(UserRepository.class).findByRedmineId(redmineTimeEntry.getUserId());
    if (user == null) {
      user = Beans.get(UserRepository.class).findByName(redmineTimeEntry.getUserName());
    }
    return user;
  }

  private Product getPlanningTimeProduct(
      TimeEntry redmineTimeEntry, ProjectPlanningTime projectPlanningTime) {
    Product product =
        Beans.get(ProductRepository.class).findByRedmineId(redmineTimeEntry.getActivityId());
    if (product == null) {
      product =
          Beans.get(ProductRepository.class)
              .findByCode(redmineTimeEntry.getActivityName().toUpperCase());
    }
    return product;
  }

  private void importIssueWatchers(TeamTask teamTask, Issue redmineIssue) {
    String typeName = HibernateProxyHelper.getClassWithoutInitializingProxy(teamTask).getTypeName();
    try {
      mailFollowerRepo
          .all()
          .filter("self.relatedId = ?1 AND self.relatedModel = ?2", teamTask.getId(), typeName)
          .delete();
      Collection<Watcher> watchers =
          redmineManager
              .getIssueManager()
              .getIssueById(redmineIssue.getId(), Include.watchers)
              .getWatchers();
      if (watchers != null && !watchers.isEmpty()) {
        for (Watcher redmineWatcher : watchers) {
          User teamTaskFollower = userRepo.findByName(redmineWatcher.getName());
          if (teamTaskFollower != null) {
            MailFollower mailFollower = new MailFollower();
            mailFollower.setUser(teamTaskFollower);
            mailFollower.setRelatedId(teamTask.getId());
            mailFollower.setRelatedModel(typeName);
            try {
              mailFollowerRepo.save(mailFollower);
            } catch (Exception e) {
              TraceBackService.trace(e, "", batch.getId());
            }
          }
        }
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  private void importIssueJournals(TeamTask teamTask, Issue redmineIssue, Date lastImportDateTime) {
    try {
      Issue issueWithJournal =
          redmineManager.getIssueManager().getIssueById(redmineIssue.getId(), Include.journals);
      Collection<Journal> journals = issueWithJournal.getJournals();
      for (Journal redmineJournal : journals) {
        if (lastImportDateTime != null
            && !isReset
            && redmineJournal.getCreatedOn().before(lastImportDateTime)) {
          continue;
        }
        MailMessage mailMessage =
            getMailMessage(redmineJournal, teamTask, redmineIssue.getSubject());
        if (mailMessage != null) {
          mailMessageRepo.save(mailMessage);
        }
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  private MailMessage getMailMessage(Journal redmineJournal, TeamTask teamTask, String Subject) {
    String body = "";
    MailMessage mailMessage =
        mailMessageRepo.all().filter("self.redmineId = ?", redmineJournal.getId()).fetchOne();
    if (mailMessage != null) {
      body = mailMessage.getBody();
      String note = redmineJournal.getNotes();
      if (!StringUtils.isBlank(body)) {
        if (!StringUtils.isBlank(note)) {
          note = getHtmlFromTextile(note);
          body = body.replaceAll(",\"content\":\".*\",", ",\"content\":\"" + note + "\",");
        } else {
          body = body.replaceAll(",\"content\":\".*\",", ",\"content\":\"\",");
        }
      }
    } else {
      body = getJournalDetails(redmineJournal);
      if (!StringUtils.isBlank(body)) {
        mailMessage = new MailMessage();
        mailMessage.setRelatedId(teamTask.getId());
        mailMessage.setRelatedModel(teamTask.getClass().getTypeName());
        mailMessage.setRelatedName(Subject);
        mailMessage.setSubject("Task Updated");
        mailMessage.setRedmineId(redmineJournal.getId());
        setLocalDateTime(mailMessage, redmineJournal.getCreatedOn(), "setCreatedOn");
        if (redmineJournal.getUser() != null) {
          User user = userRepo.findByRedmineId(redmineJournal.getUser().getId());
          mailMessage.setAuthor(user);
        }
      }
    }
    if (!StringUtils.isBlank(body)) {
      mailMessage.setBody(body);
    }
    return mailMessage;
  }

  private String getJournalDetails(Journal redmineJournal) {
    StringBuilder strBuilder = new StringBuilder();
    StringBuilder trackStrBuilder = new StringBuilder();
    strBuilder.append("{\"title\":\"Task Updated\",\"tracks\":[");
    List<JournalDetail> journalDetails = redmineJournal.getDetails();
    if (journalDetails != null && !journalDetails.isEmpty()) {
      getTrack(journalDetails, trackStrBuilder);
      if (trackStrBuilder.length() > 0) {
        trackStrBuilder.deleteCharAt(trackStrBuilder.length() - 1);
        strBuilder.append(trackStrBuilder);
      }
    }
    strBuilder.append("],\"content\":\"");
    String note = redmineJournal.getNotes();
    if (!StringUtils.isBlank(note)) {
      strBuilder.append(getHtmlFromTextile(note));
    }
    strBuilder.append("\",\"tags\":[]}");
    if (StringUtils.isBlank(trackStrBuilder.toString())
        && StringUtils.isBlank(redmineJournal.getNotes())) {
      return null;
    }
    return strBuilder.toString();
  }

  private void getTrack(List<JournalDetail> journalDetails, StringBuilder trackStrBuilder) {
    for (JournalDetail journalDetail : journalDetails) {
      if (journalDetail.getProperty().equals("attr")) {
        String[] fieldNames = getFieldName(journalDetail.getName());
        trackStrBuilder.append(
            "{\"name\":\""
                + fieldNames[0]
                + "\",\"title\":\""
                + fieldNames[1]
                + "\",\"oldValue\":\"");
        if (!StringUtils.isBlank(journalDetail.getOldValue())) {
          trackStrBuilder.append(journalDetail.getOldValue());
        }
        trackStrBuilder.append("\",\"value\":\"");
        if (!StringUtils.isBlank(journalDetail.getNewValue())) {
          trackStrBuilder.append(journalDetail.getNewValue());
        }
        trackStrBuilder.append("\"},");
      }
    }
  }

  private String[] getFieldName(String name) {
    switch (name) {
      case "project_id":
        return new String[] {"project", "Project"};
      case "subject":
        return new String[] {"name", "Name"};
      case "parent_id":
        return new String[] {"parentTask", "Parent Task"};
      case "assigned_to_id":
        return new String[] {"assignedTo", "Assigned To"};
      case "author_id":
        return new String[] {"createdBy", "Created By"};
      case "is_private":
        return new String[] {"isPrivate", "Is Private"};
      case "start_date":
        return new String[] {"taskDate", "Task Date"};
      case "done_ratio":
        return new String[] {"progressSelect", "Progress Select"};
      case "due_date":
        return new String[] {"taskDeadline", "Task Deadline"};
      case "estimated_hours":
        return new String[] {"totalPlannedHrs", "Total Planned Hrs"};
      case "category_id":
        return new String[] {"projectCategory", "Project Category"};
      case "priority_id":
        return new String[] {"priority", "Priority"};
      case "status_id":
        return new String[] {"status", "Status"};
      case "description":
        return new String[] {"description", "Description"};
      default:
        return new String[] {name, name};
    }
  }
}
