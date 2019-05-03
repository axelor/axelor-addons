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
package com.axelor.apps.redmine.service;

import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.exports.RedmineExportService;
import com.axelor.apps.redmine.imports.RedmineImportService;
import com.axelor.apps.redmine.imports.service.ImportIssueService;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.inject.Beans;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.NotFoundException;
import com.taskadapter.redmineapi.Params;
import com.taskadapter.redmineapi.RedmineAuthenticationException;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.RedmineTransportException;
import com.taskadapter.redmineapi.bean.Issue;

public class RedmineServiceImpl implements RedmineService {

  @Inject private AppRedmineRepository appRedmineRepo;
  @Inject private ProjectRepository projectRepo;
  @Inject private TeamTaskRepository teamTaskRepo;
  @Inject protected RedmineImportService importService;
  @Inject protected RedmineExportService exportService;
  @Inject protected ImportIssueService importIssueService;

  private static final Integer FETCH_LIMIT = 100;

  private static final String LIMIT = "limit";
  private static final String OFFSET = "offset";
  private static final String FILTER_BY_PROJECT = "project_id";
  private static final String FILTER_BY_STATUS = "status_id";
  private static final String FILTER_BY_PRIORITY = "priority_id";
  private static final String FILTER_BY_START_DATE = "start_date";

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  @Transactional
  public void importRedmine(Batch batch, Consumer<Object> onSuccess, Consumer<Throwable> onError)
      throws AxelorException, RedmineException {
    final RedmineManager redmineManager = getRedmineManager();
    if (redmineManager == null) {
      return;
    }

    LocalDateTime lastImportDateTime = getLastOperationDate(batch.getRedmineBatch());
    Date lastImportDate = null;
    if (lastImportDateTime != null) {
      lastImportDate = Date.from(lastImportDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
    importService.importRedmine(batch, lastImportDate, redmineManager, onSuccess, onError);
  }

  @Override
  @Transactional
  public void exportRedmine(Batch batch, Consumer<Object> onSuccess, Consumer<Throwable> onError)
      throws AxelorException, RedmineException {

    final RedmineManager redmineManager = getRedmineManager();
    if (redmineManager == null) {
      return;
    }
    LocalDateTime lastExportDate = getLastOperationDate(batch.getRedmineBatch());
    exportService.exportRedmine(batch, lastExportDate, redmineManager, onSuccess, onError);
  }

  @Override
  @Transactional
  public void importRedmineIssues(
      Batch batch, Consumer<TeamTask> onSuccess, Consumer<Throwable> onError)
      throws AxelorException, RedmineException {

    final RedmineManager redmineManager = getRedmineManager();
    if (redmineManager == null) {
      return;
    }

    Set<Project> projects = batch.getRedmineBatch().getProjectSet();
    if (projects.size() > 0) {
      projects =
          projects
              .stream()
              .filter(project -> project.getCode() != null)
              .collect(Collectors.toSet());
    } else {
      projects =
          projectRepo
              .all()
              .filter("self.code is not null")
              .fetchStream()
              .collect(Collectors.toSet());
    }

    Map<Integer, Project> commonProjects = obtainCommonProjects(projects, redmineManager);

    if (commonProjects.size() > 0) {
      Integer totalFetchCount = 0;

      LocalDateTime lastImportDateTime = getLastOperationDate(batch.getRedmineBatch());
      Date lastImportDate = null;
      if (lastImportDateTime != null) {
        lastImportDate = Date.from(lastImportDateTime.atZone(ZoneId.systemDefault()).toInstant());
      }
      List<Issue> issueList;
      do {
        Params params = applyFilters(batch.getRedmineBatch(), redmineManager, commonProjects);
        params.add(OFFSET, totalFetchCount.toString());
        issueList = redmineManager.getIssueManager().getIssues(params).getResults();
        if (issueList != null && issueList.size() > 0) {
          totalFetchCount += issueList.size();
          for (Issue issue : issueList) {
            try {
              TeamTask teamTask =
                  importIssueService.getTeamTask(
                      issue, lastImportDate, batch.getRedmineBatch().getUpdateAlreadyImported());
              teamTaskRepo.save(teamTask);
              onSuccess.accept(teamTask);
            } catch (Exception e) {
              onError.accept(e);
            }
          }
        }
      } while (issueList.size() > 0);
    }
  }

  protected LocalDateTime getLastOperationDate(RedmineBatch redmineBatch) {
    Stream<Batch> stream =
        Beans.get(BatchRepository.class)
            .all()
            .filter(
                "self.redmineBatch IS NOT NULL AND self.endDate IS NOT NULL AND self.redmineBatch.actionSelect = ? AND self.done > 0",
                redmineBatch.getActionSelect())
            .fetchStream();
    if (stream != null) {
      Optional<Batch> lastRedmineBatch = stream.max(Comparator.comparing(Batch::getEndDate));
      if (lastRedmineBatch.isPresent()) {
        return lastRedmineBatch.get().getUpdatedOn();
      }
    }
    return null;
  }

  public RedmineManager getRedmineManager() throws AxelorException {
    AppRedmine appRedmine = appRedmineRepo.all().fetchOne();

    if (!StringUtils.isBlank(appRedmine.getUri())
        && !StringUtils.isBlank(appRedmine.getApiAccessKey())) {
      RedmineManager redmineManager =
          RedmineManagerFactory.createWithApiKey(appRedmine.getUri(), appRedmine.getApiAccessKey());
      try {
        redmineManager.getUserManager().getCurrentUser();
      } catch (RedmineTransportException | NotFoundException e) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_NO_VALUE, IMessage.REDMINE_TRANSPORT);
      } catch (RedmineAuthenticationException e) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_NO_VALUE, IMessage.REDMINE_AUTHENTICATION_2);
      } catch (RedmineException e) {
        throw new AxelorException(TraceBackRepository.CATEGORY_NO_VALUE, e.getLocalizedMessage());
      }

      return redmineManager;
    } else {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE, IMessage.REDMINE_AUTHENTICATION_1);
    }
  }

  private Map<Integer, Project> obtainCommonProjects(
      Set<Project> projects, RedmineManager redmineManager) {

    Map<Integer, Project> commonProjects = new HashMap<>();

    projects
        .stream()
        .forEach(
            project -> {
              com.taskadapter.redmineapi.bean.Project redmineProject;

              try {
                redmineProject =
                    redmineManager
                        .getProjectManager()
                        .getProjectByKey(project.getCode().toLowerCase());
                commonProjects.put(redmineProject.getId(), project);
              } catch (RedmineException e) {
                LOG.trace("Project with code : " + project.getCode() + " not found");
              }
            });
    return commonProjects;
  }

  private Params applyFilters(
      RedmineBatch redmineBatch,
      RedmineManager redmineManager,
      Map<Integer, Project> commonProjects)
      throws RedmineException {

    Params params = new Params();

    for (Integer projectId : commonProjects.keySet()) {
      params
          .add("f[]", FILTER_BY_PROJECT)
          .add("op[" + FILTER_BY_PROJECT + "]", "=")
          .add("v[" + FILTER_BY_PROJECT + "][]", projectId.toString());
    }
    if (!redmineBatch.getUpdateAlreadyImported()) {
      /*params.add("f[]", FILTER_BY_CUSTOM_FIELD1 + cdf.getId())
      .add("op[" + FILTER_BY_CUSTOM_FIELD1 + cdf.getId() + "]", "!")
      .add("v[" + FILTER_BY_CUSTOM_FIELD1 + cdf.getId() + "][]", "1");*/
    }
    if (redmineBatch.getStatusSelect() != 0) {
      params
          .add("f[]", FILTER_BY_STATUS)
          .add("op[" + FILTER_BY_STATUS + "]", "=")
          .add("v[" + FILTER_BY_STATUS + "][]", redmineBatch.getStatusSelect().toString());
    }
    if (redmineBatch.getPrioritySelect() != 0) {
      params
          .add("f[]", FILTER_BY_PRIORITY)
          .add("op[" + FILTER_BY_PRIORITY + "]", "=")
          .add("v[" + FILTER_BY_PRIORITY + "][]", redmineBatch.getPrioritySelect().toString());
    }
    if (redmineBatch.getStartDate() != null) {
      params
          .add("f[]", FILTER_BY_START_DATE)
          .add("op[" + FILTER_BY_START_DATE + "]", "=")
          .add("v[" + FILTER_BY_START_DATE + "][]", redmineBatch.getStartDate().toString());
    }
    params.add(LIMIT, FETCH_LIMIT.toString());
    return params;
  }
}
