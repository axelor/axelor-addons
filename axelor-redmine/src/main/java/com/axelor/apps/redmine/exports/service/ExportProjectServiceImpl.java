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

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectCategory;
import com.axelor.apps.project.db.Wiki;
import com.axelor.apps.project.db.repo.ProjectCategoryRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.WikiRepository;
import com.axelor.apps.redmine.imports.service.ImportProjectService;
import com.axelor.apps.redmine.imports.service.ImportService;
import com.axelor.auth.db.User;
import com.axelor.common.StringUtils;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaAttachment;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaAttachmentRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.ProjectManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.UserManager;
import com.taskadapter.redmineapi.bean.Attachment;
import com.taskadapter.redmineapi.bean.IssueCategory;
import com.taskadapter.redmineapi.bean.Membership;
import com.taskadapter.redmineapi.bean.Role;
import com.taskadapter.redmineapi.bean.Version;
import com.taskadapter.redmineapi.bean.WikiPageDetail;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;

public class ExportProjectServiceImpl extends ExportService implements ExportProjectService {

  @Inject ProjectRepository projectRepo;
  @Inject DMSFileRepository dmsFileRepo;
  @Inject WikiRepository wikiRepo;
  @Inject AppRedmineRepository appRedmineRepo;
  @Inject ProjectVersionRepository projectVersionRepo;
  @Inject ProjectCategoryRepository projectCategoryRepo;

  @Inject ImportProjectService importProjectService;
  @Inject ImportService importService;

  Logger LOG = LoggerFactory.getLogger(getClass());

  public void exportProject(
      Batch batch,
      RedmineManager redmineManager,
      LocalDateTime lastExportDateTime,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {
    this.redmineManager = redmineManager;
    this.onError = onError;
    this.onSuccess = onSuccess;
    this.batch = batch;

    List<Project> projects =
        projectRepo
            .all()
            .filter(
                "self.redmineId IS NULL OR self.redmineId = 0 OR self.updatedOn > ?",
                lastExportDateTime)
            .fetch();
    for (Project project : projects) {
      exportRedmineProject(project, lastExportDateTime);
    }
    exportProjectWikis(lastExportDateTime);
    exportProjectVersion(lastExportDateTime);
    exportProjectCategories(lastExportDateTime);
    String resultStr = String.format("Project : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  @Transactional
  protected void exportRedmineProject(Project project, LocalDateTime lastExportDateTime) {
    boolean isExist = false;
    com.taskadapter.redmineapi.bean.Project redmineProject = null;
    if (Optional.ofNullable(project.getRedmineId()).orElse(0) != 0) {
      try {
        redmineProject = redmineManager.getProjectManager().getProjectById(project.getRedmineId());
        isExist = true;
      } catch (RedmineException e) {
        redmineProject = new com.taskadapter.redmineapi.bean.Project(redmineManager.getTransport());
      }
    } else {
      redmineProject = new com.taskadapter.redmineapi.bean.Project(redmineManager.getTransport());
    }

    assignProjectValues(project, redmineProject, lastExportDateTime);

    try {
      if (isExist) {
        redmineProject.update();
      } else {
        redmineProject = redmineProject.create();
        project.setRedmineId(redmineProject.getId());
        projectRepo.save(project);
      }
      onSuccess.accept(project);
      success++;
      addProjectMember(redmineProject, project);
      addProjectAttachments(redmineProject, project);
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
      fail++;
    }
  }

  private void assignProjectValues(
      Project project,
      com.taskadapter.redmineapi.bean.Project redmineProject,
      LocalDateTime lastExportDateTime) {
    redmineProject.setName(project.getName());
    redmineProject.setIdentifier(project.getCode().toLowerCase().trim().replace(" ", ""));
    redmineProject.setDescription(getTextileFromHTML(project.getDescription()));
    redmineProject.setInheritMembers(project.getExtendsMembersFromParent());
    assignParentProject(project, redmineProject, lastExportDateTime);
  }

  private void assignParentProject(
      Project project,
      com.taskadapter.redmineapi.bean.Project redmineProject,
      LocalDateTime lastExportDateTime) {
    Project parentProject = project.getParentProject();
    if (parentProject != null) {
      try {
        if (Optional.ofNullable(parentProject.getRedmineId()).orElse(0) != 0) {
          com.taskadapter.redmineapi.bean.Project redmineParentProject =
              redmineManager.getProjectManager().getProjectById(parentProject.getRedmineId());
          if (redmineParentProject != null) {
            redmineProject.setParentId(parentProject.getRedmineId());
          } else {
            exportRedmineProject(parentProject, lastExportDateTime);
            redmineProject.setParentId(parentProject.getRedmineId());
          }
        } else {
          exportRedmineProject(parentProject, lastExportDateTime);
          redmineProject.setParentId(parentProject.getRedmineId());
        }
      } catch (RedmineException e) {
        TraceBackService.trace(e, "", batch.getId());
      }
    }
  }

  @Transactional
  private void exportProjectCategories(LocalDateTime lastExportDateTime) {
    List<ProjectCategory> projectCategories =
        projectCategoryRepo
            .all()
            .filter(
                "self.redmineId IS NULL OR self.redmineId = 0 OR self.updatedOn > ?",
                lastExportDateTime)
            .fetch();
    for (ProjectCategory projectCategory : projectCategories) {
      List<Project> projects =
          projectRepo.all().filter("? MEMBER OF self.projectCategorySet", projectCategory).fetch();
      for (Project project : projects) {
        Integer projectId = project.getRedmineId();
        if (Optional.ofNullable(projectId).orElse(0) != 0) {
          exportRedmineIssueCategory(projectCategory, projectId);
        }
      }
    }
  }

  @Transactional
  private IssueCategory exportRedmineIssueCategory(
      ProjectCategory projectCategory, Integer projectId) {
    IssueCategory issueCategory = null;
    try {
      issueCategory = new IssueCategory(redmineManager.getTransport());
      issueCategory.setProjectId(projectId);
      issueCategory.setName(projectCategory.getName());
      issueCategory = issueCategory.create();
    } catch (RedmineException e) {
    }
    return issueCategory;
  }

  private void addProjectMember(
      com.taskadapter.redmineapi.bean.Project redmineProject, Project project) {
    Set<User> pojectMembers = project.getMembersUserSet();
    UserManager um = redmineManager.getUserManager();
    ProjectManager pm = redmineManager.getProjectManager();
    try {
      Collection<Role> redmineRoles = um.getRoles();
      for (User user : pojectMembers) {
        if (Optional.ofNullable(user.getRedmineId()).orElse(0) != 0) {
          try {
            com.taskadapter.redmineapi.bean.User redmineUser = um.getUserById(user.getRedmineId());
            if (redmineUser != null) {
              List<Membership> redmineProjectMembers = pm.getProjectMembers(redmineProject.getId());
              Optional<Membership> existingMembership =
                  redmineProjectMembers
                      .stream()
                      .filter(
                          member ->
                              member.getUserId() == redmineUser.getId()
                                  || member.getUserId() == user.getRedmineId())
                      .findFirst();
              if (!existingMembership.isPresent()) {
                Membership membership =
                    new Membership(
                        redmineManager.getTransport(), redmineProject, redmineUser.getId());
                membership.addRoles(redmineRoles);
                membership.create();
              }
            }
          } catch (RedmineException e) {
            TraceBackService.trace(e, "", batch.getId());
          }
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  @Transactional
  private void exportProjectVersion(LocalDateTime lastExportDateTime) {
    List<ProjectVersion> projectVersions =
        projectVersionRepo
            .all()
            .filter(
                "self.redmineId IS NULL OR self.redmineId = 0 OR self.updatedOn > ?",
                lastExportDateTime)
            .fetch();
    if (projectVersions != null && !projectVersions.isEmpty()) {
      Version redmineVersion = null;
      ProjectManager pm = redmineManager.getProjectManager();
      for (ProjectVersion projectVersion : projectVersions) {
        boolean isExist = false;
        if (Optional.ofNullable(projectVersion.getRedmineId()).orElse(0) != 0) {
          try {
            redmineVersion = pm.getVersionById(projectVersion.getRedmineId());
            if (redmineVersion != null) {
              isExist = true;
            } else {
              redmineVersion = new Version();
            }
          } catch (Exception e) {
            TraceBackService.trace(e, "", batch.getId());
          }
        } else {
          redmineVersion = new Version();
        }
        try {
          assignVersionValues(projectVersion, redmineVersion);
          if (isExist) {
            redmineVersion.update();
          } else {
            redmineVersion.create();
            projectVersion.setRedmineId(redmineVersion.getId());
            projectVersionRepo.save(projectVersion);
          }
        } catch (RedmineException e) {
          TraceBackService.trace(e, "", batch.getId());
        }
      }
    }
  }

  private void assignVersionValues(ProjectVersion projectVersion, Version redmineVersion) {
    try {
      Integer projectRedmineId = projectVersion.getProject().getRedmineId();
      if (Optional.ofNullable(projectRedmineId).orElse(0) != 0) {
        com.taskadapter.redmineapi.bean.Project redmineProject =
            redmineManager.getProjectManager().getProjectById(projectRedmineId);
        if (redmineProject != null) {
          redmineVersion.setProjectId(redmineProject.getId());
          redmineVersion.setProjectName(redmineProject.getName());

          redmineVersion.setTransport(redmineManager.getTransport());
          redmineVersion.setId(projectVersion.getId().intValue());
          redmineVersion.setName(projectVersion.getTitle());
          redmineVersion.setDescription(getTextileFromHTML(projectVersion.getContent()));
        }
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  private void exportProjectWikis(LocalDateTime lastExportDateTime) {
    List<Wiki> projectWikis =
        wikiRepo
            .all()
            .filter(
                "self.redmineTitle IS NULL OR self.redmineTitle = '' OR self.updatedOn > ?",
                lastExportDateTime)
            .fetch();
    for (Wiki wiki : projectWikis) {
      Project wikiProject = wiki.getProject();
      if (wikiProject != null
          && wikiProject.getRedmineId() != null
          && wikiProject.getRedmineId() != 0) {

        com.taskadapter.redmineapi.bean.Project redmineProject;
        try {
          redmineProject =
              redmineManager.getProjectManager().getProjectById(wikiProject.getRedmineId());
          if (redmineProject != null) {
            exportProjectWiki(redmineProject.getIdentifier(), wiki, lastExportDateTime);
          }
        } catch (RedmineException e) {
          TraceBackService.trace(e, "", batch.getId());
        }
      }
    }
  }

  @Transactional
  private void exportProjectWiki(String projectKey, Wiki wiki, LocalDateTime lastExportDateTime) {
    WikiPageDetail redmineWikiPageDetail = null;
    boolean isExist = false;

    try {
      if (wiki.getUpdatedOn() != null
          && lastExportDateTime != null
          && lastExportDateTime.isAfter(wiki.getUpdatedOn())
          && lastExportDateTime.isAfter(wiki.getCreatedOn())) {
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
            redmineWikiPageDetail = new WikiPageDetail();
          }
        } catch (RedmineException e) {
          redmineWikiPageDetail = new WikiPageDetail();
        }
      } else {
        redmineWikiPageDetail = new WikiPageDetail();
      }
      assignProjectWikiValues(wiki, redmineWikiPageDetail);
      if (isExist) {
        redmineManager.getWikiManager().update(projectKey, redmineWikiPageDetail);
      } else {
        redmineManager.getWikiManager().create(projectKey, redmineWikiPageDetail);
      }
      wikiRepo.save(wiki);
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  private void assignProjectWikiValues(Wiki wiki, WikiPageDetail redmineWikiPageDetail) {
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
  private List<Attachment> getAttachments(Long objectId, String objectName) {
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
        }
      }
      return redmineAttachments;
    }
    return null;
  }

  private void addProjectAttachments(
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

  private void attachProjectFile(String attachmentTocken, Builder builder, OkHttpClient client) {
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
      }
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }
}
