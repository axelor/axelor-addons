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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectPlanningTime;
import com.axelor.apps.project.db.repo.ProjectPlanningTimeRepository;
import com.axelor.auth.db.User;
import com.axelor.exception.service.TraceBackService;
import com.axelor.team.db.TeamTask;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.TimeEntryManager;
import com.taskadapter.redmineapi.bean.TimeEntry;
import com.taskadapter.redmineapi.bean.TimeEntryActivity;

public class ExportTimeEntryServiceImpl extends ExportService implements ExportTimeEntryService {

  @Inject ProjectPlanningTimeRepository projectPlanningTimeRepo;

  Logger LOG = LoggerFactory.getLogger(getClass());

  private List<TimeEntryActivity> redmineActivities;

  @Override
  public void exportTimeEntry(
      Batch batch,
      RedmineManager redmineManager,
      LocalDateTime lastExportDateTime,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {
    this.redmineManager = redmineManager;
    this.onError = onError;
    this.onSuccess = onSuccess;
    this.batch = batch;

    /*
     * Filter Project Planning Time : Fetch all Spent type (ProjectPlanningTime.typeSelect = 2) Project Planning Time.
     */
    List<ProjectPlanningTime> projectPlanningTimes =
        projectPlanningTimeRepo
            .all()
            .filter(
                "self.typeSelect = 2 AND self.redmineId IS NULL OR self.redmineId = 0 OR self.updatedOn > ?",
                lastExportDateTime)
            .fetch();

    if (projectPlanningTimes != null && !projectPlanningTimes.isEmpty()) {
      try {
        redmineActivities = redmineManager.getTimeEntryManager().getTimeEntryActivities();
      } catch (RedmineException e) {
      }
      for (ProjectPlanningTime projectPlanningTime : projectPlanningTimes) {
        exportRedmineTimeEntry(projectPlanningTime);
      }
    }
    String resultStr = String.format("ABS ProjectPlanning -> Redmine TimeEntry : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  @Transactional
  protected void exportRedmineTimeEntry(ProjectPlanningTime projectPlanningTime) {
    boolean isExist = false;
    TimeEntryManager tm = redmineManager.getTimeEntryManager();

    TimeEntry redmineTimeEntry;
    if (Optional.ofNullable(projectPlanningTime.getRedmineId()).orElse(0) != 0) {
      try {
        redmineTimeEntry = tm.getTimeEntry(projectPlanningTime.getRedmineId());
        isExist = true;
      } catch (RedmineException e) {
        redmineTimeEntry = new TimeEntry(redmineManager.getTransport());
      }
    } else {
      redmineTimeEntry = new TimeEntry(redmineManager.getTransport());
    }

    assignTimeEntryValues(projectPlanningTime, redmineTimeEntry);

    try {
      if (isExist) {
        redmineTimeEntry.update();
      } else {
        redmineTimeEntry = redmineTimeEntry.create();
        projectPlanningTime.setRedmineId(redmineTimeEntry.getId());
        projectPlanningTimeRepo.save(projectPlanningTime);
      }
      onSuccess.accept(projectPlanningTime);
      success++;
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
      fail++;
    }
  }

  private void assignTimeEntryValues(
      ProjectPlanningTime projectPlanningTime, TimeEntry redmineTimeEntry) {

    setProject(projectPlanningTime, redmineTimeEntry);
    setUser(projectPlanningTime, redmineTimeEntry);
    setActivity(projectPlanningTime, redmineTimeEntry);
    setIssue(projectPlanningTime, redmineTimeEntry);

    redmineTimeEntry.setComment(getTextileFromHTML(projectPlanningTime.getDescription()));
    redmineTimeEntry.setHours(projectPlanningTime.getRealHours().floatValue());
    redmineTimeEntry.setSpentOn(
        Date.from(projectPlanningTime.getDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));

    redmineTimeEntry.setCreatedOn(
        Date.from(projectPlanningTime.getCreatedOn().atZone(ZoneId.systemDefault()).toInstant()));
    if (projectPlanningTime.getUpdatedOn() != null) {
      redmineTimeEntry.setUpdatedOn(
          Date.from(projectPlanningTime.getUpdatedOn().atZone(ZoneId.systemDefault()).toInstant()));
    }
  }

  private void setProject(ProjectPlanningTime projectPlanningTime, TimeEntry redmineTimeEntry) {
    Project projectPlanningTimeProject = projectPlanningTime.getProject();
    if (projectPlanningTimeProject != null) {
      if (Optional.ofNullable(projectPlanningTimeProject.getRedmineId()).orElse(0) != 0) {
        redmineTimeEntry.setProjectId(projectPlanningTimeProject.getRedmineId());
      } else {
        redmineTimeEntry.setProjectName(projectPlanningTimeProject.getName());
      }
    }
  }

  private void setUser(ProjectPlanningTime projectPlanningTime, TimeEntry redmineTimeEntry) {
    User projectPlanningTimeUser = projectPlanningTime.getUser();
    if (projectPlanningTimeUser != null) {
      if (Optional.ofNullable(projectPlanningTimeUser.getRedmineId()).orElse(0) != 0) {
        redmineTimeEntry.setUserId(projectPlanningTime.getUser().getRedmineId());
      } else {
        redmineTimeEntry.setUserName(projectPlanningTime.getUser().getName());
      }
    }
  }

  private void setActivity(ProjectPlanningTime projectPlanningTime, TimeEntry redmineTimeEntry) {
    Product projectPlanningTimeProduct = projectPlanningTime.getProduct();
    if (projectPlanningTimeProduct != null) {
      String productName = projectPlanningTimeProduct.getName().toLowerCase();
      Optional<TimeEntryActivity> timeEntryActivity =
          redmineActivities
              .stream()
              .filter(activity -> activity.getName().equalsIgnoreCase(productName))
              .findFirst();
      if (timeEntryActivity.isPresent()) {
        TimeEntryActivity redmineActivity = timeEntryActivity.get();
        redmineTimeEntry.setActivityId(redmineActivity.getId());
        redmineTimeEntry.setActivityName(redmineActivity.getName());
      }
    }
  }

  private void setIssue(ProjectPlanningTime projectPlanningTime, TimeEntry redmineTimeEntry) {
    TeamTask projectPlanningTimeTeamTask = projectPlanningTime.getTask();
    if (projectPlanningTimeTeamTask != null) {
      if (Optional.ofNullable(projectPlanningTimeTeamTask.getRedmineId()).orElse(0) != 0) {
        redmineTimeEntry.setIssueId(projectPlanningTimeTeamTask.getRedmineId());
      }
    }
  }
}
