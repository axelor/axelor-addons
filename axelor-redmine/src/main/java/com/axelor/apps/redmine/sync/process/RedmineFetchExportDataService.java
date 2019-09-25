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
package com.axelor.apps.redmine.sync.process;

import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.redmine.db.OpenSuitRedmineSync;
import com.axelor.apps.redmine.db.repo.OpenSuitRedmineSyncRepository;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RedmineFetchExportDataService {

  protected OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo;
  protected ProjectRepository projectRepo;
  protected ProjectVersionRepository projectVersionRepo;
  protected TeamTaskRepository teamTaskRepo;
  protected TimesheetLineRepository timesheetLineRepo;

  @Inject
  public RedmineFetchExportDataService(
      OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo,
      ProjectRepository projectRepo,
      ProjectVersionRepository projectVersionRepo,
      TeamTaskRepository teamTaskRepo,
      TimesheetLineRepository timesheetLineRepo) {

    this.openSuiteRedmineSyncRepo = openSuiteRedmineSyncRepo;
    this.projectRepo = projectRepo;
    this.projectVersionRepo = projectVersionRepo;
    this.teamTaskRepo = teamTaskRepo;
    this.timesheetLineRepo = timesheetLineRepo;
  }

  private LocalDateTime lastBatchEndDate;
  private Map<String, List<?>> exportDataMap;

  public Map<String, List<?>> fetchExportData(LocalDateTime lastBatchEndDate) {

    this.lastBatchEndDate = lastBatchEndDate;
    this.exportDataMap = new HashMap<String, List<?>>();

    this.fetchExportProjectData();
    this.fetchExportVersionData();
    this.fetchExportIssueData();
    this.fetchExportTimeEntryData();

    return exportDataMap;
  }

  public void fetchExportProjectData() {

    List<Project> exportProjectList = null;

    OpenSuitRedmineSync openSuiteRedmineSyncProject =
        openSuiteRedmineSyncRepo.findBySyncTypeSelect(
            OpenSuitRedmineSyncRepository.SYNC_TYPE_PROJECT);

    if (openSuiteRedmineSyncProject != null
        && !openSuiteRedmineSyncProject
            .getOpenSuiteToRedmineSyncSelect()
            .equals(OpenSuitRedmineSyncRepository.SYNC_NONE)) {

      String openSuiteToRedmineFilter = openSuiteRedmineSyncProject.getOpenSuiteToRedmineFilter();

      if (openSuiteToRedmineFilter == null) {
        exportProjectList =
            lastBatchEndDate != null
                ? projectRepo.all().filter("self.updatedOn >= ?1", lastBatchEndDate).fetch()
                : projectRepo.all().fetch();
      } else {
        exportProjectList =
            lastBatchEndDate != null
                ? projectRepo
                    .all()
                    .filter(
                        "self.updatedOn >= ?1 AND " + openSuiteToRedmineFilter, lastBatchEndDate)
                    .fetch()
                : projectRepo.all().filter(openSuiteToRedmineFilter).fetch();
      }
    }

    exportDataMap.put("exportProjectList", exportProjectList);
  }

  public void fetchExportVersionData() {

    List<ProjectVersion> exportVersionList = null;

    OpenSuitRedmineSync openSuiteRedmineSyncVersion =
        openSuiteRedmineSyncRepo.findBySyncTypeSelect(
            OpenSuitRedmineSyncRepository.SYNC_TYPE_VERSION);

    if (openSuiteRedmineSyncVersion != null
        && !openSuiteRedmineSyncVersion
            .getOpenSuiteToRedmineSyncSelect()
            .equals(OpenSuitRedmineSyncRepository.SYNC_NONE)) {

      String openSuiteToRedmineFilter = openSuiteRedmineSyncVersion.getOpenSuiteToRedmineFilter();

      if (openSuiteToRedmineFilter == null) {
        exportVersionList =
            lastBatchEndDate != null
                ? projectVersionRepo.all().filter("self.updatedOn >= ?1", lastBatchEndDate).fetch()
                : projectVersionRepo.all().fetch();
      } else {
        exportVersionList =
            lastBatchEndDate != null
                ? projectVersionRepo
                    .all()
                    .filter(
                        "self.updatedOn >= ?1 AND " + openSuiteToRedmineFilter, lastBatchEndDate)
                    .fetch()
                : projectVersionRepo.all().filter(openSuiteToRedmineFilter).fetch();
      }
    }

    exportDataMap.put("exportVersionList", exportVersionList);
  }

  public void fetchExportIssueData() {

    List<TeamTask> exportIssueList = null;

    OpenSuitRedmineSync openSuiteRedmineSyncIssue =
        openSuiteRedmineSyncRepo.findBySyncTypeSelect(
            OpenSuitRedmineSyncRepository.SYNC_TYPE_ISSUE);

    if (openSuiteRedmineSyncIssue != null
        && !openSuiteRedmineSyncIssue
            .getOpenSuiteToRedmineSyncSelect()
            .equals(OpenSuitRedmineSyncRepository.SYNC_NONE)) {

      String openSuiteToRedmineFilter = openSuiteRedmineSyncIssue.getOpenSuiteToRedmineFilter();

      if (openSuiteToRedmineFilter == null) {
        exportIssueList =
            lastBatchEndDate != null
                ? teamTaskRepo.all().filter("self.updatedOn >= ?1", lastBatchEndDate).fetch()
                : teamTaskRepo.all().fetch();
      } else {
        exportIssueList =
            lastBatchEndDate != null
                ? teamTaskRepo
                    .all()
                    .filter(
                        "self.updatedOn >= ?1 AND " + openSuiteToRedmineFilter, lastBatchEndDate)
                    .fetch()
                : teamTaskRepo.all().filter(openSuiteToRedmineFilter).fetch();
      }
    }

    exportDataMap.put("exportIssueList", exportIssueList);
  }

  public void fetchExportTimeEntryData() {

    List<TimesheetLine> exportTimeEntryList = null;

    OpenSuitRedmineSync openSuiteRedmineSyncSpentTime =
        openSuiteRedmineSyncRepo.findBySyncTypeSelect(
            OpenSuitRedmineSyncRepository.SYNC_TYPE_SPENT_TIME);

    if (openSuiteRedmineSyncSpentTime != null
        && !openSuiteRedmineSyncSpentTime
            .getOpenSuiteToRedmineSyncSelect()
            .equals(OpenSuitRedmineSyncRepository.SYNC_NONE)) {

      String openSuiteToRedmineFilter = openSuiteRedmineSyncSpentTime.getOpenSuiteToRedmineFilter();

      if (openSuiteToRedmineFilter == null) {
        exportTimeEntryList =
            lastBatchEndDate != null
                ? timesheetLineRepo.all().filter("self.updatedOn >= ?1", lastBatchEndDate).fetch()
                : timesheetLineRepo.all().fetch();
      } else {
        exportTimeEntryList =
            lastBatchEndDate != null
                ? timesheetLineRepo
                    .all()
                    .filter(
                        "self.updatedOn >= ?1 AND " + openSuiteToRedmineFilter, lastBatchEndDate)
                    .fetch()
                : timesheetLineRepo.all().filter(openSuiteToRedmineFilter).fetch();
      }
    }

    exportDataMap.put("exportTimeEntryList", exportTimeEntryList);
  }
}
