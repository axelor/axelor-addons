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

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.Wiki;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.WikiRepository;
import com.axelor.apps.redmine.db.OpenSuitRedmineSync;
import com.axelor.apps.redmine.db.repo.OpenSuitRedmineSyncRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.auth.db.User;
import com.axelor.common.StringUtils;
import com.axelor.db.mapper.Mapper;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaAttachment;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaAttachmentRepository;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Attachment;
import com.taskadapter.redmineapi.bean.Membership;
import com.taskadapter.redmineapi.bean.Role;
import com.taskadapter.redmineapi.bean.Tracker;
import com.taskadapter.redmineapi.bean.WikiPageDetail;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineExportProjectServiceImpl extends RedmineExportService
    implements RedmineExportProjectService {

  protected OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo;
  protected ProjectRepository projectRepo;
  protected RedmineDynamicExportService redmineDynamicExportService;
  protected MetaModelRepository metaModelRepository;
  protected WikiRepository wikiRepo;
  protected DMSFileRepository dmsFileRepo;
  protected AppRedmineRepository appRedmineRepo;

  @Inject
  public RedmineExportProjectServiceImpl(
      OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo,
      ProjectRepository projectRepo,
      RedmineDynamicExportService redmineDynamicExportService,
      MetaModelRepository metaModelRepository,
      WikiRepository wikiRepo,
      DMSFileRepository dmsFileRepo,
      AppRedmineRepository appRedmineRepo) {

    this.openSuiteRedmineSyncRepo = openSuiteRedmineSyncRepo;
    this.projectRepo = projectRepo;
    this.redmineDynamicExportService = redmineDynamicExportService;
    this.metaModelRepository = metaModelRepository;
    this.wikiRepo = wikiRepo;
    this.dmsFileRepo = dmsFileRepo;
    this.appRedmineRepo = appRedmineRepo;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());
  private LocalDateTime lastBatchUpdatedOn;

  @Override
  public void exportProject(
      Batch batch,
      LocalDateTime lastBatchUpdatedOn,
      RedmineManager redmineManager,
      List<Project> projectList,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError,
      List<Object[]> errorObjList) {

    if (projectList != null && !projectList.isEmpty()) {
      OpenSuitRedmineSync openSuiteRedmineSyncProject =
          openSuiteRedmineSyncRepo.findBySyncTypeSelect(
              OpenSuitRedmineSyncRepository.SYNC_TYPE_PROJECT);

      this.errorObjList = errorObjList;
      this.dynamicFieldsSyncList = openSuiteRedmineSyncProject.getDynamicFieldsSyncList();

      if (validateDynamicFieldsSyncList(
          dynamicFieldsSyncList,
          METAMODEL_PROJECT,
          Mapper.toMap(new Project()),
          Mapper.toMap(new com.taskadapter.redmineapi.bean.Project(null)))) {

        this.redmineManager = redmineManager;
        this.batch = batch;
        this.onError = onError;
        this.onSuccess = onSuccess;
        this.redmineProjectManager = redmineManager.getProjectManager();
        this.redmineUserManager = redmineManager.getUserManager();
        this.metaModel = metaModelRepository.findByName(METAMODEL_PROJECT);
        this.lastBatchUpdatedOn = lastBatchUpdatedOn;

        String syncTypeSelect = openSuiteRedmineSyncProject.getOpenSuiteToRedmineSyncSelect();

        for (Project project : projectList) {
          createRedmineProject(project, syncTypeSelect);
        }

        // Export project wikis
        exportProjectWikis();
      }
    }

    String resultStr =
        String.format("ABS Project -> Redmine Project : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  public void createRedmineProject(Project project, String syncTypeSelect) {

    try {
      com.taskadapter.redmineapi.bean.Project redmineProject = null;

      if (project.getRedmineId() == null || project.getRedmineId().equals(0)) {
        redmineProject = getRedmineProjectByKey(project.getCode());

        if (redmineProject == null) {
          redmineProject =
              new com.taskadapter.redmineapi.bean.Project(redmineManager.getTransport());
        }
      } else {
        redmineProject = redmineProjectManager.getProjectById(project.getRedmineId());
      }

      // Sync type - On create
      if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_CREATE)
          && (project.getRedmineId() != null && !project.getRedmineId().equals(0))) {
        return;
      }

      // Sync type - On update
      if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_UPDATE)
          && redmineProject.getUpdatedOn() != null
          && lastBatchUpdatedOn != null) {

        // If updates are made on both sides and redmine side is latest updated then abort export
        LocalDateTime redmineUpdatedOn =
            redmineProject
                .getUpdatedOn()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        if (redmineUpdatedOn.isAfter(lastBatchUpdatedOn)
            && redmineUpdatedOn.isAfter(project.getUpdatedOn())) {
          return;
        }
      }

      Map<String, Object> projectMap = Mapper.toMap(project);
      Map<String, Object> redmineProjectMap = Mapper.toMap(redmineProject);
      Map<String, Object> redmineProjectCustomFieldsMap = new HashMap<>();

      redmineProjectMap =
          redmineDynamicExportService.createRedmineDynamic(
              dynamicFieldsSyncList,
              projectMap,
              redmineProjectMap,
              redmineProjectCustomFieldsMap,
              metaModel,
              project,
              redmineManager);

      // Fix - JSONObj Error
      redmineProjectMap.remove("projectPublic");

      Mapper redmineProjectMapper = Mapper.of(redmineProject.getClass());
      Iterator<Entry<String, Object>> redmineProjectMapItr =
          redmineProjectMap.entrySet().iterator();

      while (redmineProjectMapItr.hasNext()) {
        Map.Entry<String, Object> entry = redmineProjectMapItr.next();
        redmineProjectMapper.set(redmineProject, entry.getKey(), entry.getValue());
      }

      // Special rule for identifier field
      redmineProject.setIdentifier(
          redmineProject.getIdentifier().toLowerCase().trim().replace(" ", ""));

      // Special rule for add trackers to redmine project
      Collection<Tracker> trackers = new ArrayList<Tracker>();
      redmineManager.getIssueManager().getTrackers().forEach(tracker -> trackers.add(tracker));
      redmineProject.addTrackers(trackers);

      // Create or update redmine object
      this.saveRedmineProject(redmineProject, project, redmineProjectCustomFieldsMap);
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);

      if (e.getMessage().equals(REDMINE_SERVER_404_NOT_FOUND)) {
        setErrorLog(
            Project.class.getSimpleName(),
            project.getId().toString(),
            null,
            null,
            I18n.get(IMessage.REDMINE_SYNC_ERROR_RECORD_NOT_FOUND));
      }
    }
  }

  @Transactional
  public void saveRedmineProject(
      com.taskadapter.redmineapi.bean.Project redmineProject,
      Project project,
      Map<String, Object> redmineProjectCustomFieldsMap) {

    try {

      if (redmineProject.getId() == null) {
        redmineProject = redmineProject.create();
      }

      // Special fixed rule for set project members

      Set<User> membersUserSet = project.getMembersUserSet();
      List<Role> roles = redmineUserManager.getRoles();

      for (User user : membersUserSet) {
        Partner partner = user.getPartner();

        if (partner != null && partner.getEmailAddress() != null) {
          com.taskadapter.redmineapi.bean.User redmineUser =
              findRedmineUserByEmail(partner.getEmailAddress().getAddress());

          if (redmineUser != null) {
            List<Membership> memberships =
                redmineProjectManager.getProjectMembers(redmineProject.getId());

            if (!memberships.stream().anyMatch(m -> m.getUserId() == redmineUser.getId())) {
              Membership membership =
                  new Membership(redmineManager.getTransport(), redmineProject, redmineUser.getId())
                      .addRoles(roles);
              membership.create();
            }
          }
        }
      }

      project.setRedmineId(redmineProject.getId());
      projectRepo.save(project);

      // Set custom fields
      setRedmineCustomFieldValues(
          redmineProject.getCustomFields(), redmineProjectCustomFieldsMap, project.getId());

      redmineProject.update();

      onSuccess.accept(project);
      success++;

      // Export project attachments
      addProjectAttachments(redmineProject, project);

    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
      fail++;
    }
  }

  public void exportProjectWikis() {

    List<Wiki> projectWikis =
        wikiRepo.all().filter("self.redmineTitle IS NULL OR self.redmineTitle = ''").fetch();

    for (Wiki wiki : projectWikis) {
      Project wikiProject = wiki.getProject();

      if (wikiProject != null
          && wikiProject.getRedmineId() != null
          && wikiProject.getRedmineId() != 0) {

        com.taskadapter.redmineapi.bean.Project redmineProject;

        try {
          redmineProject = redmineProjectManager.getProjectById(wikiProject.getRedmineId());

          if (redmineProject != null) {
            exportProjectWiki(redmineProject.getIdentifier(), wiki);
          }
        } catch (RedmineException e) {
          TraceBackService.trace(e, "", batch.getId());
          onError.accept(e);

          if (e.getMessage().equals(REDMINE_SERVER_404_NOT_FOUND)) {
            setErrorLog(
                Project.class.getSimpleName(),
                wikiProject.getId().toString(),
                null,
                null,
                I18n.get(IMessage.REDMINE_SYNC_ERROR_RECORD_NOT_FOUND));
          }
        }
      }
    }
  }

  @Transactional
  public void exportProjectWiki(String projectKey, Wiki wiki) {

    WikiPageDetail redmineWikiPageDetail = null;
    boolean isExist = false;

    try {

      if (wiki.getUpdatedOn() != null) {
        return;
      }

      if (!StringUtils.isBlank(wiki.getRedmineTitle())) {

        try {
          redmineWikiPageDetail =
              redmineManager
                  .getWikiManager()
                  .getWikiPageDetailByProjectAndTitle(projectKey, wiki.getRedmineTitle());

          if (redmineWikiPageDetail != null) {
            isExist = true;
          } else {
            redmineWikiPageDetail = new WikiPageDetail(redmineManager.getTransport());
          }
        } catch (RedmineException e) {
          redmineWikiPageDetail = new WikiPageDetail(redmineManager.getTransport());
        }
      } else {
        redmineWikiPageDetail = new WikiPageDetail(redmineManager.getTransport());
      }

      assignProjectWikiValues(wiki, redmineWikiPageDetail);

      if (isExist) {
        redmineWikiPageDetail.update();
      } else {
        redmineWikiPageDetail.setProjectKey(projectKey);
        redmineWikiPageDetail.create();
      }

      wikiRepo.save(wiki);
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
    }
  }

  public void assignProjectWikiValues(Wiki wiki, WikiPageDetail redmineWikiPageDetail) {

    String wikiTitle = wiki.getId().toString();

    if (!StringUtils.isBlank(wiki.getTitle())) {
      wikiTitle += "_" + wiki.getTitle().replaceAll("[ ]+", "_");
    }

    redmineWikiPageDetail.setTitle(wikiTitle);
    redmineWikiPageDetail.setText(getTextileFromHTML(wiki.getContent()));
    wiki.setRedmineTitle(wikiTitle.replace("_", " "));
    List<Attachment> attachments = getAttachments(wiki.getId(), wiki.getClass().getName());

    if (attachments != null && !attachments.isEmpty()) {
      redmineWikiPageDetail.setAttachments(attachments);
    }
  }

  @Transactional
  public List<Attachment> getAttachments(Long objectId, String objectName) {

    List<MetaAttachment> attachments =
        Beans.get(MetaAttachmentRepository.class)
            .all()
            .filter("self.objectId = ?1 AND self.objectName = ?2", objectId, objectName)
            .fetch();

    if (attachments != null && !attachments.isEmpty()) {
      List<Attachment> redmineAttachments = new ArrayList<>();

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
              Attachment attachment =
                  redmineManager
                      .getAttachmentManager()
                      .uploadAttachment(metaFile.getFileType(), attachmentFile);
              redmineAttachments.add(attachment);
              existingFile.setRedmineId(attachment.getId());
              dmsFileRepo.save(existingFile);
            }
          }
        } catch (RedmineException | IOException e) {
          TraceBackService.trace(e, "", batch.getId());
          onError.accept(e);
        }
      }

      return redmineAttachments;
    }

    return null;
  }

  public void addProjectAttachments(
      com.taskadapter.redmineapi.bean.Project redmineProject, Project project) {

    List<Attachment> projectAttachments =
        getAttachments(project.getId(), project.getClass().getName());

    if (projectAttachments != null && !projectAttachments.isEmpty()) {
      AppRedmine appRedmine = appRedmineRepo.all().fetchOne();
      String url = appRedmine.getUri() + "/projects/" + redmineProject.getId() + "/files.json";
      String accessKey = appRedmine.getApiAccessKey();
      OkHttpClient client = new OkHttpClient();
      Builder builder = new Request.Builder().url(url).addHeader("X-Redmine-API-Key", accessKey);

      for (Attachment attachment : projectAttachments) {
        attachProjectFile(attachment.getToken(), builder, client);
      }
    }
  }

  public void attachProjectFile(String attachmentTocken, Builder builder, OkHttpClient client) {

    try {
      RequestBody requestBody =
          RequestBody.create(
              MediaType.parse("application/json; charset=utf-8"),
              "{\"file\" :  {\"token\": \"" + attachmentTocken + "\"} }");

      Request request = builder.post(requestBody).build();

      try {
        client.newCall(request).execute();
      } catch (IOException e) {
        TraceBackService.trace(e, "", batch.getId());
        onError.accept(e);
      }
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
    }
  }
}
