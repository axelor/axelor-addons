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

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.redmine.exports.service.RedmineExportIssueService;
import com.axelor.apps.redmine.exports.service.RedmineExportProjectService;
import com.axelor.apps.redmine.exports.service.RedmineExportService;
import com.axelor.apps.redmine.exports.service.RedmineExportTimeSpentService;
import com.axelor.apps.redmine.exports.service.RedmineExportVersionService;
import com.axelor.apps.redmine.imports.service.RedmineImportIssueService;
import com.axelor.apps.redmine.imports.service.RedmineImportProjectService;
import com.axelor.apps.redmine.imports.service.RedmineImportService;
import com.axelor.apps.redmine.imports.service.RedmineImportTimeSpentService;
import com.axelor.apps.redmine.imports.service.RedmineImportTrackerService;
import com.axelor.apps.redmine.imports.service.RedmineImportVersionService;
import com.axelor.apps.redmine.log.service.RedmineErrorLogService;
import com.axelor.meta.db.MetaFile;
import com.axelor.team.db.TeamTask;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.TimeEntry;
import com.taskadapter.redmineapi.bean.Tracker;
import com.taskadapter.redmineapi.bean.Version;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class RedmineSyncProcessServiceImpl implements RedmineSyncProcessService {

  protected RedmineExportProjectService redmineExportProjectService;
  protected RedmineExportVersionService redmineExportVersionService;
  protected RedmineExportIssueService redmineExportIssueService;
  protected RedmineExportTimeSpentService redmineExportTimeSpentService;
  protected RedmineImportTrackerService redmineImportTrackerService;
  protected RedmineImportProjectService redmineImportProjectService;
  protected RedmineImportVersionService redmineImportVersionService;
  protected RedmineImportIssueService redmineImportIssueService;
  protected RedmineImportTimeSpentService redmineImportTimeSpentService;
  protected RedmineFetchExportDataService redmineFetchExportDataService;
  protected RedmineFetchImportDataService redmineFetchImportDataService;
  protected RedmineErrorLogService redmineErrorLogService;
  protected BatchRepository batchRepo;

  @Inject
  public RedmineSyncProcessServiceImpl(
      RedmineExportProjectService redmineExportProjectService,
      RedmineExportVersionService redmineExportVersionService,
      RedmineExportIssueService redmineExportIssueService,
      RedmineExportTimeSpentService redmineExportTimeSpentService,
      RedmineImportTrackerService redmineImportTrackerService,
      RedmineImportProjectService redmineImportProjectService,
      RedmineImportVersionService redmineImportVersionService,
      RedmineImportIssueService redmineImportIssueService,
      RedmineImportTimeSpentService redmineImportTimeSpentService,
      RedmineFetchExportDataService redmineFetchExportDataService,
      RedmineFetchImportDataService redmineFetchImportDataService,
      RedmineErrorLogService redmineErrorLogService,
      BatchRepository batchRepo) {

    this.redmineExportProjectService = redmineExportProjectService;
    this.redmineExportVersionService = redmineExportVersionService;
    this.redmineExportIssueService = redmineExportIssueService;
    this.redmineExportTimeSpentService = redmineExportTimeSpentService;
    this.redmineImportTrackerService = redmineImportTrackerService;
    this.redmineImportProjectService = redmineImportProjectService;
    this.redmineImportVersionService = redmineImportVersionService;
    this.redmineImportIssueService = redmineImportIssueService;
    this.redmineImportTimeSpentService = redmineImportTimeSpentService;
    this.redmineFetchExportDataService = redmineFetchExportDataService;
    this.redmineFetchImportDataService = redmineFetchImportDataService;
    this.redmineErrorLogService = redmineErrorLogService;
    this.batchRepo = batchRepo;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void redmineSyncProcess(
      Batch batch,
      RedmineManager redmineManager,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {

    RedmineExportService.result = "";
    RedmineImportService.result = "";

    // LOG REDMINE SYNC ERROR DATA

    List<Object[]> errorObjList = new ArrayList<Object[]>();

    // FETCH EXPORT AND IMPORT DATA

    Batch lastBatch =
        batchRepo
            .all()
            .filter(
                "self.id != ?1 and self.redmineBatch.id = ?2",
                batch.getId(),
                batch.getRedmineBatch().getId())
            .order("-updatedOn")
            .fetchOne();

    ZonedDateTime lastBatchEndDate = lastBatch != null ? lastBatch.getEndDate() : null;
    LocalDateTime lastBatchUpdatedOn = lastBatch != null ? lastBatch.getUpdatedOn() : null;

    Map<String, List<?>> exportDataMap =
        redmineFetchExportDataService.fetchExportData(
            lastBatchEndDate != null ? lastBatchEndDate.toLocalDateTime() : null);
    Map<String, List<?>> importDataMap =
        redmineFetchImportDataService.fetchImportData(redmineManager, lastBatchEndDate);

    // EXPORT PROCESS

    redmineExportProjectService.exportProject(
        batch,
        lastBatchUpdatedOn,
        redmineManager,
        (List<Project>) exportDataMap.get("exportProjectList"),
        onSuccess,
        onError,
        errorObjList);
    redmineExportVersionService.exportVersion(
        batch,
        lastBatchUpdatedOn,
        redmineManager,
        (List<ProjectVersion>) exportDataMap.get("exportVersionList"),
        onSuccess,
        onError,
        errorObjList);
    redmineExportIssueService.exportIssue(
        batch,
        lastBatchUpdatedOn,
        redmineManager,
        (List<TeamTask>) exportDataMap.get("exportIssueList"),
        onSuccess,
        onError,
        errorObjList);
    redmineExportTimeSpentService.exportTimeSpent(
        batch,
        lastBatchUpdatedOn,
        redmineManager,
        (List<TimesheetLine>) exportDataMap.get("exportTimeEntryList"),
        onSuccess,
        onError,
        errorObjList);

    // IMPORT PROCESS

    redmineImportTrackerService.importTracker(
        batch,
        redmineManager,
        (List<Tracker>) importDataMap.get("importTrackerList"),
        onSuccess,
        onError,
        errorObjList);
    redmineImportProjectService.importProject(
        batch,
        lastBatchUpdatedOn,
        redmineManager,
        (List<com.taskadapter.redmineapi.bean.Project>) importDataMap.get("importProjectList"),
        onSuccess,
        onError,
        errorObjList);
    redmineImportVersionService.importVersion(
        batch,
        lastBatchUpdatedOn,
        redmineManager,
        (List<Version>) importDataMap.get("importVersionList"),
        onSuccess,
        onError,
        errorObjList);
    redmineImportIssueService.importIssue(
        batch,
        lastBatchUpdatedOn,
        redmineManager,
        (List<Issue>) importDataMap.get("importIssueList"),
        onSuccess,
        onError,
        errorObjList);
    redmineImportTimeSpentService.importTimeSpent(
        batch,
        lastBatchUpdatedOn,
        redmineManager,
        (List<TimeEntry>) importDataMap.get("importTimeEntryList"),
        onSuccess,
        onError,
        errorObjList);

    // ATTACH ERROR LOG WITH BATCH

    if (errorObjList != null && errorObjList.size() > 0) {
      MetaFile errorMetaFile = redmineErrorLogService.redmineErrorLogService(errorObjList);

      if (errorMetaFile != null) {
        batch.setErrorLogFile(errorMetaFile);
      }
    }
  }
}
