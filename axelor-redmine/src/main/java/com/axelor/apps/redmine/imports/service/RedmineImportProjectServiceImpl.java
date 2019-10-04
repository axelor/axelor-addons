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
import com.axelor.apps.businesssupport.db.ProjectAnnouncement;
import com.axelor.apps.businesssupport.db.repo.ProjectAnnouncementRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.Wiki;
import com.axelor.apps.project.db.repo.ProjectCategoryRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.WikiRepository;
import com.axelor.apps.redmine.db.OpenSuitRedmineSync;
import com.axelor.apps.redmine.db.repo.OpenSuitRedmineSyncRepository;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.mapper.Mapper;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Attachment;
import com.taskadapter.redmineapi.bean.Membership;
import com.taskadapter.redmineapi.bean.News;
import com.taskadapter.redmineapi.bean.Tracker;
import com.taskadapter.redmineapi.bean.WikiPage;
import com.taskadapter.redmineapi.bean.WikiPageDetail;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineImportProjectServiceImpl extends RedmineImportService
    implements RedmineImportProjectService {

  protected OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo;
  protected ProjectRepository projectRepo;
  protected WikiRepository wikiRepo;
  protected ProjectAnnouncementRepository projectAnnouncementRepo;
  protected RedmineDynamicImportService redmineDynamicImportService;
  protected MetaModelRepository metaModelRepo;
  protected ProjectCategoryRepository projectCategoryRepo;

  @Inject
  public RedmineImportProjectServiceImpl(
      DMSFileRepository dmsFileRepo,
      AppRedmineRepository appRedmineRepo,
      MetaFiles metaFiles,
      BatchRepository batchRepo,
      UserRepository userRepo,
      OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo,
      ProjectRepository projectRepo,
      WikiRepository wikiRepo,
      ProjectAnnouncementRepository projectAnnouncementRepo,
      RedmineDynamicImportService redmineDynamicImportService,
      MetaModelRepository metaModelRepo,
      ProjectCategoryRepository projectCategoryRepo) {

    super(dmsFileRepo, appRedmineRepo, metaFiles, batchRepo, userRepo);

    this.openSuiteRedmineSyncRepo = openSuiteRedmineSyncRepo;
    this.projectRepo = projectRepo;
    this.wikiRepo = wikiRepo;
    this.projectAnnouncementRepo = projectAnnouncementRepo;
    this.redmineDynamicImportService = redmineDynamicImportService;
    this.metaModelRepo = metaModelRepo;
    this.projectCategoryRepo = projectCategoryRepo;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());
  private LocalDateTime lastBatchUpdatedOn;

  @Override
  public void importProject(
      Batch batch,
      LocalDateTime lastBatchUpdatedOn,
      RedmineManager redmineManager,
      List<com.taskadapter.redmineapi.bean.Project> redmineProjectList,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError,
      List<Object[]> errorObjList) {

    if (redmineProjectList != null && !redmineProjectList.isEmpty()) {
      OpenSuitRedmineSync openSuiteRedmineSyncProject =
          openSuiteRedmineSyncRepo.findBySyncTypeSelect(
              OpenSuitRedmineSyncRepository.SYNC_TYPE_PROJECT);

      this.errorObjList = errorObjList;
      this.dynamicFieldsSyncList = openSuiteRedmineSyncProject.getDynamicFieldsSyncList();

      if (validateDynamicFieldsSycList(
          dynamicFieldsSyncList,
          METAMODEL_PROJECT,
          Mapper.toMap(new Project()),
          Mapper.toMap(new com.taskadapter.redmineapi.bean.Project(null)))) {

        this.batch = batch;
        this.redmineManager = redmineManager;
        this.onError = onError;
        this.onSuccess = onSuccess;
        this.redmineProjectManager = redmineManager.getProjectManager();
        this.redmineWikiManager = redmineManager.getWikiManager();
        this.redmineAttachmentManager = redmineManager.getAttachmentManager();
        this.redmineUserManager = redmineManager.getUserManager();
        this.metaModel = metaModelRepo.findByName(METAMODEL_PROJECT);
        this.lastBatchUpdatedOn = lastBatchUpdatedOn;

        String syncTypeSelect = openSuiteRedmineSyncProject.getRedmineToOpenSuiteSyncSelect();

        for (com.taskadapter.redmineapi.bean.Project redmineProject : redmineProjectList) {
          createOpenSuiteProject(redmineProject, syncTypeSelect);
        }
      }
    }

    String resultStr =
        String.format("Redmine Project -> ABS Project : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  public void createOpenSuiteProject(
      com.taskadapter.redmineapi.bean.Project redmineProject, String syncTypeSelect) {

    Project project =
        projectRepo.findByRedmineId(redmineProject.getId()) != null
            ? projectRepo.findByRedmineId(redmineProject.getId())
            : new Project();

    if (project.getId() == null) {
      addInList = true;
    }

    // Sync type - On create
    if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_CREATE)
        && project.getId() != null) {
      return;
    }

    // Sync type - On update
    if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_UPDATE)
        && lastBatchUpdatedOn != null
        && project.getId() != null) {

      // If updates are made on both sides and os side is latest updated then abort import
      LocalDateTime redmineUpdatedOn =
          redmineProject
              .getUpdatedOn()
              .toInstant()
              .atZone(ZoneId.systemDefault())
              .toLocalDateTime();

      if (lastBatchUpdatedOn.isAfter(redmineUpdatedOn)) {
        return;
      }

      if (project.getUpdatedOn().isAfter(lastBatchUpdatedOn)
          && project.getUpdatedOn().isAfter(redmineUpdatedOn)) {
        return;
      }
    }

    project.setRedmineId(redmineProject.getId());

    Map<String, Object> projectMap = Mapper.toMap(project);
    Map<String, Object> redmineProjectMap = Mapper.toMap(redmineProject);
    Map<String, Object> redmineProjectCustomFieldsMap =
        setRedmineCustomFieldsMap(redmineProject.getCustomFields());

    projectMap =
        redmineDynamicImportService.createOpenSuiteDynamic(
            dynamicFieldsSyncList,
            projectMap,
            redmineProjectMap,
            redmineProjectCustomFieldsMap,
            metaModel,
            redmineProject,
            redmineManager);

    project = Mapper.toBean(project.getClass(), projectMap);

    // Special fix rule for set project members

    try {
      List<Membership> redmineProjectMembers =
          redmineProjectManager.getProjectMembers(redmineProject.getId());

      for (Membership membership : redmineProjectMembers) {
        if (membership.getUserId() == null) {
          continue;
        }
        com.taskadapter.redmineapi.bean.User redmineUser =
            redmineUserManager.getUserById(membership.getUserId());
        User user = findOpensuiteUser(null, redmineUser);

        if (user != null) {
          project.addMembersUserSetItem(user);
        }
      }

    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }

    // Special fix rule for set project categories

    Collection<Tracker> redmineTrackers = redmineProject.getTrackers();

    for (Tracker tracker : redmineTrackers) {
      project.addProjectCategorySetItem(projectCategoryRepo.findByRedmineId(tracker.getId()));
    }

    // Special fix rule for set project status

    if (redmineProject.getStatus().equals(REDMINE_PROJECT_STATUS_CLOSED)) {
      project.setStatusSelect(ProjectRepository.STATE_FINISHED);
    }

    setLocalDateTime(project, redmineProject.getCreatedOn(), "setCreatedOn");
    setLocalDateTime(project, redmineProject.getUpdatedOn(), "setUpdatedOn");

    // Create OS object
    this.saveOpenSuiteProject(project, redmineProject);
  }

  @Transactional
  public void saveOpenSuiteProject(
      Project project, com.taskadapter.redmineapi.bean.Project redmineProject) {

    try {

      if (addInList) {
        project.addBatchSetItem(batch);
      }
      projectRepo.save(project);

      //      JPA.em().getTransaction().commit();
      //      if (!JPA.em().getTransaction().isActive()) {
      //        JPA.em().getTransaction().begin();
      //      }
      onSuccess.accept(project);
      success++;

      project = projectRepo.find(project.getId());

      // Import project wikis
      //      importProjectWiki(redmineProject, project);

      // Import project attachments
      importProjectAttachments(redmineProject.getId(), project);

      // Import project news
      //      importProjectNews(redmineProject, project);
    } catch (Exception e) {
      onError.accept(e);
      fail++;
      //      JPA.em().getTransaction().rollback();
      //      JPA.em().getTransaction().begin();
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  @Transactional
  public void importProjectWiki(
      com.taskadapter.redmineapi.bean.Project redmineProject, Project project) {

    List<Attachment> redmineWikiAttachments = null;
    String projectKey = redmineProject.getIdentifier();
    List<WikiPage> redmineWikiPages;

    try {
      redmineWikiPages = redmineWikiManager.getWikiPagesByProject(projectKey);

      if (redmineWikiPages != null && !redmineWikiPages.isEmpty()) {

        for (WikiPage wikiPage : redmineWikiPages) {
          WikiPageDetail redmineWikiPageDetail =
              redmineWikiManager.getWikiPageDetailByProjectAndTitle(
                  projectKey, wikiPage.getTitle());

          if (redmineWikiPageDetail != null) {
            Wiki wiki = getWiki(redmineWikiPageDetail, projectKey, redmineWikiAttachments);

            if (wiki != null) {
              wiki.setProject(project);
              wikiRepo.save(wiki);
              redmineWikiAttachments = redmineWikiPageDetail.getAttachments();

              if (redmineWikiAttachments != null && !redmineWikiAttachments.isEmpty()) {
                importAttachments(wiki, redmineWikiAttachments, null);
              }
              wiki.setContent(getHtmlFromTextile(redmineWikiPageDetail.getText(), wiki));
            }
          }
        }
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
    }
  }

  public Wiki getWiki(
      WikiPageDetail redmineWikiPageDetail,
      String projectKey,
      List<Attachment> redmineWikiAttachments) {

    String wikiTitle = redmineWikiPageDetail.getTitle();
    Wiki wiki = wikiRepo.findByRedmineTitle(wikiTitle);

    if (wiki == null) {
      wiki = new Wiki();
    }

    wiki.setRedmineTitle(wikiTitle);
    wiki.setTitle(wikiTitle);

    return wiki;
  }

  public void importProjectAttachments(Integer redmineProjectId, Project project) {

    List<Attachment> projectAttachments = new ArrayList<>();
    String jsonData;

    try {
      jsonData = getResponseBody("/projects/" + redmineProjectId + "/files.json").string();

      if (Strings.isNullOrEmpty(jsonData)) {
        return;
      }
      JSONObject Jobject = null;
      Jobject = new JSONObject(jsonData);

      if (Jobject != null) {
        JSONArray Jarray = Jobject.getJSONArray("files");

        for (int i = 0; i < Jarray.length(); i++) {
          JSONObject fileObj = (JSONObject) Jarray.get(i);
          Integer id = fileObj.getInt("id");
          Attachment projectFile = redmineAttachmentManager.getAttachmentById(id);

          if (projectFile != null) {
            projectAttachments.add(projectFile);
          }
        }

        importAttachments(project, projectAttachments, null);
      }
    } catch (IOException | JSONException | RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
    }
  }

  @Transactional
  public void importProjectNews(
      com.taskadapter.redmineapi.bean.Project redmineProject, Project project)
      throws RedmineException {

    String projectKey = redmineProject.getIdentifier();
    List<News> redmineNews = null;

    try {
      redmineNews = redmineProjectManager.getNews(projectKey);
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
    }

    if (redmineNews != null && !redmineNews.isEmpty()) {

      for (News news : redmineNews) {
        ProjectAnnouncement announcement = getAnnouncement(news, projectKey);
        announcement.setProject(project);

        projectAnnouncementRepo.save(announcement);
      }
    }
  }

  public ProjectAnnouncement getAnnouncement(News news, String projectKey) throws RedmineException {

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
      User user = findOpensuiteUser(null, newsUser);

      if (user != null) {
        setCreatedUser(announcement, user, "setCreatedBy");
      }
    }

    setLocalDateTime(announcement, news.getCreatedOn(), "setCreatedOn");

    return announcement;
  }
}
