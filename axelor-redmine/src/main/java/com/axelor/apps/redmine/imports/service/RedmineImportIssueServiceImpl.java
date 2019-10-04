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
package com.axelor.apps.redmine.imports.service;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.project.db.repo.ProjectCategoryRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.redmine.db.OpenSuitRedmineSync;
import com.axelor.apps.redmine.db.repo.OpenSuitRedmineSyncRepository;
import com.axelor.auth.db.AuditableModel;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.db.mapper.Mapper;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.db.repo.MailFollowerRepository;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.Include;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Journal;
import com.taskadapter.redmineapi.bean.JournalDetail;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineImportIssueServiceImpl extends RedmineImportService
    implements RedmineImportIssueService {

  protected OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo;
  protected TeamTaskRepository teamTaskRepo;
  protected ProjectRepository projectRepo;
  protected MailFollowerRepository mailFollowerRepo;
  protected RedmineDynamicImportService redmineDynamicImportService;
  protected MailMessageRepository mailMessageRepo;
  protected MetaModelRepository metaModelRepo;
  protected ProjectCategoryRepository projectCategoryRepo;
  protected ProjectVersionRepository projectVersionRepo;

  @Inject
  public RedmineImportIssueServiceImpl(
      DMSFileRepository dmsFileRepo,
      AppRedmineRepository appRedmineRepo,
      MetaFiles metaFiles,
      BatchRepository batchRepo,
      UserRepository userRepo,
      OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo,
      TeamTaskRepository teamTaskRepo,
      ProjectRepository projectRepo,
      MailFollowerRepository mailFollowerRepo,
      RedmineDynamicImportService redmineDynamicImportService,
      MailMessageRepository mailMessageRepo,
      MetaModelRepository metaModelRepo,
      ProjectCategoryRepository projectCategoryRepo,
      ProjectVersionRepository projectVersionRepo) {

    super(dmsFileRepo, appRedmineRepo, metaFiles, batchRepo, userRepo);

    this.openSuiteRedmineSyncRepo = openSuiteRedmineSyncRepo;
    this.teamTaskRepo = teamTaskRepo;
    this.projectRepo = projectRepo;
    this.mailFollowerRepo = mailFollowerRepo;
    this.redmineDynamicImportService = redmineDynamicImportService;
    this.mailMessageRepo = mailMessageRepo;
    this.metaModelRepo = metaModelRepo;
    this.projectCategoryRepo = projectCategoryRepo;
    this.projectVersionRepo = projectVersionRepo;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());
  private LocalDateTime lastBatchUpdatedOn;

  @Override
  public void importIssue(
      Batch batch,
      LocalDateTime lastBatchUpdatedOn,
      RedmineManager redmineManager,
      List<com.taskadapter.redmineapi.bean.Issue> redmineIssueList,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError,
      List<Object[]> errorObjList) {

    if (redmineIssueList != null && !redmineIssueList.isEmpty()) {
      OpenSuitRedmineSync openSuiteRedmineSyncIssue =
          openSuiteRedmineSyncRepo.findBySyncTypeSelect(
              OpenSuitRedmineSyncRepository.SYNC_TYPE_ISSUE);

      this.errorObjList = errorObjList;
      this.dynamicFieldsSyncList = openSuiteRedmineSyncIssue.getDynamicFieldsSyncList();

      if (validateDynamicFieldsSycList(
          dynamicFieldsSyncList,
          METAMODEL_TEAM_TASK,
          Mapper.toMap(new TeamTask()),
          Mapper.toMap(new Issue()))) {

        this.batch = batch;
        this.redmineManager = redmineManager;
        this.onError = onError;
        this.onSuccess = onSuccess;
        this.redmineIssueManager = redmineManager.getIssueManager();
        this.redmineUserManager = redmineManager.getUserManager();
        this.metaModel = metaModelRepo.findByName(METAMODEL_TEAM_TASK);
        this.lastBatchUpdatedOn = lastBatchUpdatedOn;

        String syncTypeSelect = openSuiteRedmineSyncIssue.getRedmineToOpenSuiteSyncSelect();

        LOG.debug("Total issues to import: {}", redmineIssueList.size());
        for (Issue redmineIssue : redmineIssueList) {
          createOpenSuiteIssue(redmineIssue, syncTypeSelect);
        }
      }
    }

    String resultStr =
        String.format("Redmine Issue -> ABS Teamtask : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  public void createOpenSuiteIssue(Issue redmineIssue, String syncTypeSelect) {

    TeamTask teamTask =
        teamTaskRepo.findByRedmineId(redmineIssue.getId()) != null
            ? teamTaskRepo.findByRedmineId(redmineIssue.getId())
            : new TeamTask();

    // Sync type - On create
    if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_CREATE)
        && teamTask.getId() != null) {
      return;
    }

    // Sync type - On update
    if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_UPDATE)
        && lastBatchUpdatedOn != null
        && teamTask.getId() != null) {

      // If updates are made on both sides and os side is latest updated then abort import
      LocalDateTime redmineUpdatedOn =
          redmineIssue.getUpdatedOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

      if (teamTask.getUpdatedOn().isAfter(lastBatchUpdatedOn)
          && teamTask.getUpdatedOn().isAfter(redmineUpdatedOn)) {
        return;
      }
    }

    teamTask.setRedmineId(redmineIssue.getId());
    teamTask.setTypeSelect(TeamTaskRepository.TYPE_TASK);

    Map<String, Object> teamTaskMap = Mapper.toMap(teamTask);
    Map<String, Object> redmineIssueMap = Mapper.toMap(redmineIssue);
    Map<String, Object> redmineIssueCustomFieldsMap =
        setRedmineCustomFieldsMap(redmineIssue.getCustomFields());

    teamTaskMap =
        redmineDynamicImportService.createOpenSuiteDynamic(
            dynamicFieldsSyncList,
            teamTaskMap,
            redmineIssueMap,
            redmineIssueCustomFieldsMap,
            metaModel,
            redmineIssue,
            redmineManager);

    teamTask = Mapper.toBean(teamTask.getClass(), teamTaskMap);

    // Set special fixed rules
    this.setSpecialFixedRules(teamTask, redmineIssue);
    LOG.debug("Importing issue: " + redmineIssue.getId());

    // Create or update OS object
    this.saveOpenSuiteIssue(teamTask, redmineIssue);
  }

  public void setSpecialFixedRules(TeamTask teamTask, Issue redmineIssue) {

    try {
      teamTask.setName(redmineIssue.getSubject());
      teamTask.setDescription(redmineIssue.getDescription());
      teamTask.setProject(projectRepo.findByRedmineId(redmineIssue.getProjectId()));
      teamTask.setTaskDate(
          redmineIssue.getCreatedOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
      teamTask.setAssignedTo(findOpensuiteUser(redmineIssue.getAssigneeId(), null));

      teamTask.setTargetVersion(
          redmineIssue.getTargetVersion() != null
              ? projectVersionRepo.findByRedmineId(redmineIssue.getTargetVersion().getId())
              : null);
      teamTask.setProjectCategory(
          projectCategoryRepo.findByRedmineId(redmineIssue.getTracker().getId()));

      setCreatedBy(teamTask, findOpensuiteUser(redmineIssue.getAuthorId(), null));
      setLocalDateTime(teamTask, redmineIssue.getCreatedOn(), "setCreatedOn");
      setLocalDateTime(teamTask, redmineIssue.getUpdatedOn(), "setUpdatedOn");
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  @Transactional
  public void saveOpenSuiteIssue(TeamTask teamTask, Issue redmineIssue) {

    try {
      teamTask.addOsbatchSetItem(batch);
      teamTaskRepo.save(teamTask);

      //      JPA.em().getTransaction().commit();
      //      if (!JPA.em().getTransaction().isActive()) {
      //        JPA.em().getTransaction().begin();
      //      }
      onSuccess.accept(teamTask);
      success++;

      teamTask = teamTaskRepo.find(teamTask.getId());

      // Import issue watchers
      //      importIssueWatchers(teamTask, redmineIssue);

      // Import issue comments
      importIssueJournals(teamTask, redmineIssue);
    } catch (Exception e) {
      onError.accept(e);
      fail++;
      //      JPA.em().getTransaction().rollback();
      //      JPA.em().getTransaction().begin();
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  //  @Transactional
  //  public void importIssueWatchers(TeamTask teamTask, Issue redmineIssue) {
  //
  //      mailFollowerRepo
  //          .all()
  //          .filter("self.relatedId = ?1 AND self.relatedModel = ?2", teamTask.getId(),
  // TeamTask.class.getName());
  //
  //      Collection<Watcher> watchers = redmineIssue.getWatchers();
  //
  //      if (watchers != null && !watchers.isEmpty()) {
  //
  //        for (Watcher redmineWatcher : watchers) {
  //          User teamTaskFollower = userRepo.findByName(redmineWatcher.getName());
  //
  //          if (teamTaskFollower != null) {
  //            MailFollower mailFollower = new MailFollower();
  //            mailFollower.setUser(teamTaskFollower);
  //            mailFollower.setRelatedId(teamTask.getId());
  //            mailFollower.setRelatedModel( TeamTask.class.getName());
  //            try {
  //              mailFollowerRepo.save(mailFollower);
  //            } catch (Exception e) {
  //              TraceBackService.trace(e, "", batch.getId());
  //              onError.accept(e);
  //            }
  //          }
  //        }
  //      }
  //  }

  @Transactional
  public void importIssueJournals(TeamTask teamTask, Issue redmineIssue) {

    try {
      Issue issueWithJournal =
          redmineIssueManager.getIssueById(redmineIssue.getId(), Include.journals);
      Collection<Journal> journals = issueWithJournal.getJournals();

      for (Journal redmineJournal : journals) {
        MailMessage mailMessage =
            getMailMessage(redmineJournal, teamTask, redmineIssue.getSubject());

        if (mailMessage != null) {
          mailMessageRepo.save(mailMessage);
        }
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
    }
  }

  public MailMessage getMailMessage(Journal redmineJournal, TeamTask teamTask, String Subject)
      throws RedmineException {

    String body = "";
    MailMessage mailMessage =
        mailMessageRepo.all().filter("self.redmineId = ?", redmineJournal.getId()).fetchOne();

    if (mailMessage != null && mailMessage.getRelatedId().compareTo(teamTask.getId()) == 0) {
      body = mailMessage.getBody();
      String note = getHtmlFromTextile(redmineJournal.getNotes(), teamTask).replaceAll("\"", "'");

      if (!StringUtils.isBlank(body)) {

        if (!StringUtils.isBlank(note)) {

          try {
            body = body.replaceAll(",\"content\":\".*\",", ",\"content\":\"" + note + "\",");
          } catch (Exception e) {
          }
        } else {
          body = body.replaceAll(",\"content\":\".*\",", ",\"content\":\"\",");
        }
      }
    } else {
      body = getJournalDetails(redmineJournal, teamTask);

      if (!StringUtils.isBlank(body)) {
        mailMessage = new MailMessage();
        mailMessage.setRelatedId(teamTask.getId());
        mailMessage.setRelatedModel(teamTask.getClass().getTypeName());
        mailMessage.setRelatedName(Subject);
        mailMessage.setSubject("Task Updated");
        mailMessage.setRedmineId(redmineJournal.getId());
        setLocalDateTime(mailMessage, redmineJournal.getCreatedOn(), "setCreatedOn");

        if (redmineJournal.getUser() != null) {
          User user = findOpensuiteUser(null, redmineJournal.getUser());
          setCreatedBy(mailMessage, user);
          mailMessage.setAuthor(user);
        }
      }
    }

    if (!StringUtils.isBlank(body)) {
      mailMessage.setBody(body);
    }

    return mailMessage;
  }

  public String getJournalDetails(Journal redmineJournal, TeamTask teamTask) {

    StringBuilder strBuilder = new StringBuilder();
    StringBuilder trackStrBuilder = new StringBuilder();
    strBuilder.append("{\"title\":\"Task Updated\",\"tracks\":[");
    List<JournalDetail> journalDetails = redmineJournal.getDetails();

    if (journalDetails != null && !journalDetails.isEmpty()) {
      getTrack(journalDetails, trackStrBuilder, teamTask);

      if (trackStrBuilder.length() > 0) {
        trackStrBuilder.deleteCharAt(trackStrBuilder.length() - 1);
        strBuilder.append(trackStrBuilder);
      }
    }
    strBuilder.append("],\"content\":\"");
    String note = redmineJournal.getNotes();

    if (!StringUtils.isBlank(note)) {
      strBuilder.append(getHtmlFromTextile(note, teamTask).replaceAll("\"", "'"));
    }
    strBuilder.append("\",\"tags\":[]}");

    if (StringUtils.isBlank(trackStrBuilder.toString())
        && StringUtils.isBlank(redmineJournal.getNotes())) {
      return null;
    }

    return strBuilder.toString();
  }

  public void getTrack(
      List<JournalDetail> journalDetails, StringBuilder trackStrBuilder, TeamTask teamTask) {

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
          String oldValue =
              getJournalHTML(journalDetail.getOldValue(), teamTask).replaceAll("\"", "'");
          trackStrBuilder.append(oldValue);
        }
        trackStrBuilder.append("\",\"value\":\"");

        if (!StringUtils.isBlank(journalDetail.getNewValue())) {
          String newValue =
              getJournalHTML(journalDetail.getNewValue(), teamTask).replaceAll("\"", "'");
          trackStrBuilder.append(newValue);
        }
        trackStrBuilder.append("\"},");
      }
    }
  }

  public String[] getFieldName(String name) {

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

  public String getJournalHTML(String content, AuditableModel obj) {

    content = getHtmlFromTextile(content, obj);
    int startindex = content.indexOf("<p>") + ("<p>").length();
    int endindex = content.lastIndexOf("</p>");

    if (startindex > -1 && endindex > -1 && startindex < endindex && endindex < content.length()) {
      content = content.substring(startindex, endindex);
    }

    return content;
  }

  public void setCreatedBy(AuditableModel obj, User redmineUser) {

    if (redmineUser == null) {
      return;
    }

    try {
      Method setCreatedByMethod =
          AuditableModel.class.getDeclaredMethod("setCreatedBy", User.class);
      invokeMethod(setCreatedByMethod, obj, redmineUser);
    } catch (NoSuchMethodException | SecurityException e) {
      TraceBackService.trace(e);
    }
  }
}
