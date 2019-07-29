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

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.UserBaseRepository;
import com.axelor.apps.businesssupport.db.ProjectAnnouncement;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectAnnouncementRepository;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.Wiki;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.TrackerRepository;
import com.axelor.apps.project.db.repo.WikiRepository;
import com.axelor.auth.db.User;
import com.axelor.db.JPA;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.ProjectManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.WikiManager;
import com.taskadapter.redmineapi.bean.Attachment;
import com.taskadapter.redmineapi.bean.Membership;
import com.taskadapter.redmineapi.bean.News;
import com.taskadapter.redmineapi.bean.Tracker;
import com.taskadapter.redmineapi.bean.Version;
import com.taskadapter.redmineapi.bean.WikiPage;
import com.taskadapter.redmineapi.bean.WikiPageDetail;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.persistence.PersistenceException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportProjectServiceImpl extends ImportService implements ImportProjectService {

  @Inject ProjectRepository projectRepo;
  @Inject UserBaseRepository userRepo;
  @Inject WikiRepository wikiRepo;
  @Inject DMSFileRepository dmsFileRepo;
  @Inject TrackerRepository trackerRepo;
  @Inject ProjectVersionRepository projectVersionRepo;
  @Inject ProjectAnnouncementRepository projectAnnouncementRepo;

  Logger LOG = LoggerFactory.getLogger(getClass());

  @Transactional
  @Override
  public void importProject(
      Batch batch,
      Date lastImportDateTime,
      RedmineManager redmineManager,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {
    this.redmineManager = redmineManager;
    this.isReset = batch.getRedmineBatch().getUpdateAlreadyImported();
    this.batch = batch;

    try {
      importTrackers(lastImportDateTime);
      ProjectManager pm = redmineManager.getProjectManager();
      List<com.taskadapter.redmineapi.bean.Project> projectList = pm.getProjects();
      if (projectList != null && !projectList.isEmpty()) {
        for (com.taskadapter.redmineapi.bean.Project redmineProject : projectList) {
          if (!isReset
              && lastImportDateTime != null
              && redmineProject.getCreatedOn().before(lastImportDateTime)) {
            return;
          }
          Project project = projectRepo.findByRedmineId(redmineProject.getId());
          project = getProject(project, redmineProject, lastImportDateTime);
          try {
            if (project != null) {
              projectRepo.save(project);
              onSuccess.accept(project);
              success++;
              importProjectVersion(redmineProject, project, lastImportDateTime);
              importProjectWiki(redmineProject, project, lastImportDateTime);
              importProjectAttachments(redmineProject.getId(), lastImportDateTime, project);
              project.setDescription(getHtmlFromTextile(redmineProject.getDescription(), project));
              importProjectNews(redmineProject, project);
            }
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
    } catch (RedmineException e) {
      onError.accept(e);
      fail++;
      TraceBackService.trace(e, "", batch.getId());
    }
    String resultStr =
        String.format("Redmine Project -> ABS Project : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  private void importTrackers(Date lastImportDateTime) {
    try {
      List<Tracker> redmineTrackers = redmineManager.getIssueManager().getTrackers();
      for (Tracker redmineTracker : redmineTrackers) {
        com.axelor.apps.project.db.Tracker tracker =
            trackerRepo.findByRedmineId(redmineTracker.getId());
        if (tracker == null) {
          tracker = new com.axelor.apps.project.db.Tracker();
        }
        tracker.setName(redmineTracker.getName());
        tracker.setRedmineId(redmineTracker.getId());
        trackerRepo.save(tracker);
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  protected Project getProject(
      Project project,
      com.taskadapter.redmineapi.bean.Project redmineProject,
      Date lastImportDateTime) {
    if (project == null) {
      Project existProject =
          projectRepo
              .all()
              .filter(
                  "self.name = ? AND (self.redmineId IS NULL OR self.redmineId = 0)",
                  redmineProject.getIdentifier())
              .fetchOne();
      if (existProject != null) {
        existProject.setRedmineId(redmineProject.getId());
        return existProject;
      }
      project = new Project();
      project.setRedmineId(redmineProject.getId());
    } else {
      if (lastImportDateTime != null
          && redmineProject.getUpdatedOn() != null
          && lastImportDateTime.after(redmineProject.getUpdatedOn())) {
        return project;
      }
    }
    setProjectValues(project, redmineProject, lastImportDateTime);
    return project;
  }

  private void setProjectValues(
      Project project,
      com.taskadapter.redmineapi.bean.Project redmineProject,
      Date lastImportDateTime) {
    project.setCode(redmineProject.getIdentifier());
    project.setName(redmineProject.getName());
    project.setFullName(redmineProject.getName());
    project.setExtendsMembersFromParent(redmineProject.getInheritMembers());

    setProjectTrackers(project, redmineProject);
    setProjectMembers(project, redmineProject);
    setParentProject(project, redmineProject, lastImportDateTime);

    setLocalDateTime(project, redmineProject.getCreatedOn(), "setCreatedOn");
    setLocalDateTime(project, redmineProject.getUpdatedOn(), "setUpdatedOn");
  }

  private void setProjectTrackers(
      Project project, com.taskadapter.redmineapi.bean.Project redmineProject) {
    Collection<Tracker> redmineTrackers = redmineProject.getTrackers();
    if (redmineTrackers != null && !redmineTrackers.isEmpty()) {
      Set<com.axelor.apps.project.db.Tracker> trackers = new HashSet<>();
      com.axelor.apps.project.db.Tracker tracker;
      for (Tracker redmineTracker : redmineTrackers) {
        tracker = trackerRepo.findByRedmineId(redmineTracker.getId());
        if (tracker == null) {
          tracker = trackerRepo.findByName(redmineTracker.getName());
          if (tracker == null) {
            tracker = new com.axelor.apps.project.db.Tracker();
          }
        }
        tracker.setName(redmineTracker.getName());
        tracker.setRedmineId(redmineTracker.getId());
        trackers.add(tracker);
      }
      project.setTrackerSet(trackers);
    }
  }

  private void setProjectMembers(
      Project project, com.taskadapter.redmineapi.bean.Project redmineProject) {
    List<Membership> redmineProjectMembers = null;
    try {
      redmineProjectMembers =
          redmineManager.getProjectManager().getProjectMembers(redmineProject.getId());
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
    if (redmineProjectMembers != null) {
      Set<User> projectMembers = new HashSet<>();
      for (Membership redmineProjectMembership : redmineProjectMembers) {
        if (redmineProjectMembership.getUserId() != null) {
          User projectMember = userRepo.findByRedmineId(redmineProjectMembership.getUserId());
          if (projectMember != null) {
            projectMembers.add(projectMember);
          }
        }
      }
      project.setMembersUserSet(projectMembers);
    }
  }

  private void setParentProject(
      Project project,
      com.taskadapter.redmineapi.bean.Project redmineProject,
      Date lastImportDateTime) {
    try {
      if (redmineProject.getParentId() != null) {
        Project parentProject =
            projectRepo.findByRedmineId(
                Integer.parseInt((redmineProject.getParentId().toString())));
        if (parentProject == null) {
          com.taskadapter.redmineapi.bean.Project redmineParentProject = null;
          try {
            redmineParentProject =
                redmineManager.getProjectManager().getProjectById(redmineProject.getParentId());
            parentProject = getProject(parentProject, redmineParentProject, lastImportDateTime);
          } catch (RedmineException e) {
            TraceBackService.trace(e, "", batch.getId());
          }
        }
        project.setParentProject(parentProject);
      }
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  @Transactional
  public void importProjectVersion(
      com.taskadapter.redmineapi.bean.Project redmineProject,
      Project project,
      Date lastImportDateTime) {
    try {
      List<Version> redmineVersions =
          redmineManager.getProjectManager().getVersions(redmineProject.getId());
      for (Version redmineVersion : redmineVersions) {
        if (!isReset
            && lastImportDateTime != null
            && redmineVersion.getCreatedOn().before(lastImportDateTime)) {
          continue;
        }
        ProjectVersion projectVersion = projectVersionRepo.findByRedmineId(redmineVersion.getId());
        if (projectVersion == null) {
          projectVersion = new ProjectVersion();
        }
        projectVersion.setTitle(redmineVersion.getName());
        projectVersion.setContent(getHtmlFromTextile(redmineVersion.getDescription(), null));
        projectVersion.setProject(project);
        projectVersion.setRedmineId(redmineVersion.getId());
        projectVersionRepo.save(projectVersion);
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  private void importProjectWiki(
      com.taskadapter.redmineapi.bean.Project redmineProject,
      Project project,
      Date lastImportDateTime) {
    if (project != null) {
      List<Attachment> redmineWikiAttachments = null;
      try {
        String projectKey = redmineProject.getIdentifier();
        WikiManager wk = redmineManager.getWikiManager();
        List<WikiPage> redmineWikiPages = wk.getWikiPagesByProject(projectKey);
        if (redmineWikiPages != null && !redmineWikiPages.isEmpty()) {
          for (WikiPage wikiPage : redmineWikiPages) {
            try {
              WikiPageDetail redmineWikiPageDetail =
                  wk.getWikiPageDetailByProjectAndTitle(projectKey, wikiPage.getTitle());
              if (redmineWikiPageDetail != null) {
                Wiki wiki =
                    getWiki(
                        redmineWikiPageDetail,
                        projectKey,
                        lastImportDateTime,
                        redmineWikiAttachments);

                if (wiki != null) {
                  wiki.setProject(project);
                  wikiRepo.save(wiki);
                  redmineWikiAttachments = redmineWikiPageDetail.getAttachments();
                  if (redmineWikiAttachments != null && !redmineWikiAttachments.isEmpty()) {
                    importAttachments(wiki, redmineWikiAttachments, lastImportDateTime);
                  }
                  wiki.setContent(getHtmlFromTextile(redmineWikiPageDetail.getText(), wiki));
                }
              }
            } catch (RedmineException e) {
              TraceBackService.trace(e, "", batch.getId());
            }
          }
        }
      } catch (RedmineException e) {
        TraceBackService.trace(e, "", batch.getId());
      }
    }
  }

  private Wiki getWiki(
      WikiPageDetail redmineWikiPageDetail,
      String projectKey,
      Date lastImportDateTime,
      List<Attachment> redmineWikiAttachments) {

    String wikiTitle = redmineWikiPageDetail.getTitle();
    Wiki wiki = null;
    Date wikiUpdatedOn = redmineWikiPageDetail.getUpdatedOn();
    if (wikiUpdatedOn == null
        || lastImportDateTime == null
        || (wikiUpdatedOn != null
            && lastImportDateTime != null
            && ((Date) wikiUpdatedOn).after(lastImportDateTime))) {

      wiki = wikiRepo.findByRedmineTitle(wikiTitle);
      if (wiki == null) {
        wiki = new Wiki();
      }

      wiki.setRedmineTitle(wikiTitle);
      wiki.setTitle(wikiTitle);
    }
    return wiki;
  }

  private void importProjectAttachments(
      Integer redmineProjectId, Date lastImportDateTime, Project project) {
    List<Attachment> projectAttachments = new ArrayList<>();
    try {
      String jsonData = getResponseBody("/projects/" + redmineProjectId + "/files.json").string();
      JSONObject Jobject = null;
      try {
        Jobject = new JSONObject(jsonData);
      } catch (Exception e) {
      }
      if (Jobject != null) {
        JSONArray Jarray = Jobject.getJSONArray("files");
        for (int i = 0; i < Jarray.length(); i++) {
          JSONObject fileObj = (JSONObject) Jarray.get(i);
          Integer id = fileObj.getInt("id");
          Attachment projectFile = redmineManager.getAttachmentManager().getAttachmentById(id);
          if (projectFile != null) {
            projectAttachments.add(projectFile);
          }
        }
        importAttachments(project, projectAttachments, lastImportDateTime);
      }
    } catch (Exception e) {
      TraceBackService.trace(e, null, batch.getId());
    }
  }

  private void importProjectNews(
      com.taskadapter.redmineapi.bean.Project redmineProject, Project project) {
    if (project != null) {
      try {
        String projectKey = redmineProject.getIdentifier();
        ProjectManager pm = redmineManager.getProjectManager();
        List<News> redmineNews = pm.getNews(projectKey);
        if (redmineNews != null && !redmineNews.isEmpty()) {
          for (News news : redmineNews) {
            ProjectAnnouncement announcement = getAnnouncement(news, projectKey);
            announcement.setProject(project);
            projectAnnouncementRepo.save(announcement);
          }
        }
      } catch (RedmineException e) {
        TraceBackService.trace(e, "", batch.getId());
      }
    }
  }

  private ProjectAnnouncement getAnnouncement(News news, String projectKey) {
    ProjectAnnouncement announcement = projectAnnouncementRepo.findByRedmineId(news.getId());
    if (announcement == null) {
      announcement = new ProjectAnnouncement();
      announcement.setRedmineId(news.getId());
    }
    announcement.setTitle(news.getTitle());
    announcement.setDate(
        news.getCreatedOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    announcement.setContent(
        getHtmlFromTextile(news.getLink(), null)
            + "<br />"
            + getHtmlFromTextile(news.getDescription(), null));
    com.taskadapter.redmineapi.bean.User newsUser = news.getUser();
    if (newsUser != null) {
      User user = userRepo.findByRedmineId(newsUser.getId());
      if (user != null) {
        setCreatedUser(announcement, user, "setCreatedBy");
      }
    }
    setLocalDateTime(announcement, news.getCreatedOn(), "setCreatedOn");
    return announcement;
  }
}
