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

import com.axelor.apps.redmine.db.OpenSuitRedmineSync;
import com.axelor.apps.redmine.db.repo.OpenSuitRedmineSyncRepository;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.Params;
import com.taskadapter.redmineapi.ProjectManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Project;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RedmineFetchImportDataService {

  @Inject private OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo;

  private ZonedDateTime lastBatchEndDate;
  private RedmineManager redmineManager;
  private HashMap<String, List<?>> importDataMap;

  private static Integer FETCH_LIMIT = 100;
  private static Integer TOTAL_FETCH_COUNT = 0;

  public Map<String, List<?>> fetchImportData(
      RedmineManager redmineManager, ZonedDateTime lastBatchEndDate) {

    this.lastBatchEndDate = lastBatchEndDate;
    this.redmineManager = redmineManager;
    this.importDataMap = new HashMap<String, List<?>>();

    this.fetchImportTrackerData();
    this.fetchImportProjectData();
    this.fetchImportVersionData();
    this.fetchImportIssueData();
    this.fetchImportTimeEntryData();

    return importDataMap;
  }

  public void fetchImportTrackerData() {

    List<com.taskadapter.redmineapi.bean.Tracker> importTrackerList = null;

    OpenSuitRedmineSync openSuiteRedmineSyncTracker =
        openSuiteRedmineSyncRepo.findBySyncTypeSelect(
            OpenSuitRedmineSyncRepository.SYNC_TYPE_TRACKER);

    if (openSuiteRedmineSyncTracker != null
        && !openSuiteRedmineSyncTracker
            .getRedmineToOpenSuiteSyncSelect()
            .equals(OpenSuitRedmineSyncRepository.SYNC_NONE)) {

      try {
        importTrackerList = redmineManager.getIssueManager().getTrackers();
      } catch (RedmineException e) {
        TraceBackService.trace(e);
      }
    }

    importDataMap.put("importTrackerList", importTrackerList);
  }

  public void fetchImportProjectData() {

    List<com.taskadapter.redmineapi.bean.Project> importProjectList = null;

    OpenSuitRedmineSync openSuiteRedmineSyncProject =
        openSuiteRedmineSyncRepo.findBySyncTypeSelect(
            OpenSuitRedmineSyncRepository.SYNC_TYPE_PROJECT);

    if (openSuiteRedmineSyncProject != null
        && !openSuiteRedmineSyncProject
            .getRedmineToOpenSuiteSyncSelect()
            .equals(OpenSuitRedmineSyncRepository.SYNC_NONE)) {

      try {
        importProjectList = redmineManager.getProjectManager().getProjects();
      } catch (RedmineException e) {
        TraceBackService.trace(e);
      }
    }

    importDataMap.put("importProjectList", importProjectList);
  }

  public void fetchImportVersionData() {

    List<com.taskadapter.redmineapi.bean.Version> importVersionList =
        new ArrayList<com.taskadapter.redmineapi.bean.Version>();

    OpenSuitRedmineSync openSuiteRedmineSyncVersion =
        openSuiteRedmineSyncRepo.findBySyncTypeSelect(
            OpenSuitRedmineSyncRepository.SYNC_TYPE_VERSION);

    if (openSuiteRedmineSyncVersion != null
        && !openSuiteRedmineSyncVersion
            .getRedmineToOpenSuiteSyncSelect()
            .equals(OpenSuitRedmineSyncRepository.SYNC_NONE)) {

      try {
        ProjectManager redmineProjectManager = redmineManager.getProjectManager();
        List<com.taskadapter.redmineapi.bean.Project> importProjectList =
            (List<Project>) importDataMap.get("importProjectList");

        if (importProjectList != null && !importProjectList.isEmpty()) {
          List<com.taskadapter.redmineapi.bean.Version> tempVersionList;

          for (com.taskadapter.redmineapi.bean.Project redmineProject : importProjectList) {
            tempVersionList = redmineProjectManager.getVersions(redmineProject.getId());

            if (tempVersionList != null && tempVersionList.size() > 0) {
              importVersionList.addAll(tempVersionList);
            }
          }
        }
      } catch (RedmineException e) {
        TraceBackService.trace(e);
      }
    }

    importDataMap.put("importVersionList", importVersionList);
  }

  public void fetchImportIssueData() {

    TOTAL_FETCH_COUNT = 0;
    List<com.taskadapter.redmineapi.bean.Issue> importIssueList =
        new ArrayList<com.taskadapter.redmineapi.bean.Issue>();

    OpenSuitRedmineSync openSuiteRedmineSyncIssue =
        openSuiteRedmineSyncRepo.findBySyncTypeSelect(
            OpenSuitRedmineSyncRepository.SYNC_TYPE_ISSUE);

    if (openSuiteRedmineSyncIssue != null
        && !openSuiteRedmineSyncIssue
            .getRedmineToOpenSuiteSyncSelect()
            .equals(OpenSuitRedmineSyncRepository.SYNC_NONE)) {

      Params params = new Params();

      if (lastBatchEndDate != null) {
        ZonedDateTime endOn = lastBatchEndDate.withZoneSameInstant(ZoneOffset.UTC).withNano(0);

        params
            .add("set_filter", "1")
            .add("f[]", "updated_on")
            .add("op[updated_on]", ">=")
            .add("v[updated_on][]", endOn.toString());
      }

      String redmineToOpenSuiteFilter = openSuiteRedmineSyncIssue.getRedmineToOpenSuiteFilter();

      if (redmineToOpenSuiteFilter != null) {
        redmineToOpenSuiteFilter = redmineToOpenSuiteFilter.trim().replace(" ", "");
        long count = redmineToOpenSuiteFilter.chars().filter(ch -> ch == '&').count();

        for (int i = 0; i <= count; i++) {
          int indexOfAnd = redmineToOpenSuiteFilter.indexOf("&");
          String subFilter =
              redmineToOpenSuiteFilter.substring(
                  0, indexOfAnd != -1 ? indexOfAnd : redmineToOpenSuiteFilter.length());

          int indexOfOperator = subFilter.indexOf("=");
          String name = subFilter.substring(0, indexOfOperator);
          String value = subFilter.substring(indexOfOperator + 1, subFilter.length());
          params.add(name, value);

          redmineToOpenSuiteFilter = redmineToOpenSuiteFilter.substring(indexOfAnd + 1);
        }
      }

      List<Issue> tempIssueList;

      do {
        tempIssueList = fetchIssues(params);

        if (tempIssueList != null && tempIssueList.size() > 0) {
          importIssueList.addAll(tempIssueList);
        }

        TOTAL_FETCH_COUNT += tempIssueList.size();
      } while (tempIssueList != null && tempIssueList.size() > 0);
    }

    importDataMap.put("importIssueList", importIssueList);
  }

  public List<Issue> fetchIssues(Params params) {

    List<Issue> issueList = null;

    try {
      params.add("limit", FETCH_LIMIT.toString());
      params.add("offset", TOTAL_FETCH_COUNT.toString());
      issueList = redmineManager.getIssueManager().getIssues(params).getResults();
    } catch (RedmineException e) {
      TraceBackService.trace(e);
    }

    return issueList;
  }

  public void fetchImportTimeEntryData() {

    List<com.taskadapter.redmineapi.bean.TimeEntry> importTimeEntryList = null;

    OpenSuitRedmineSync openSuiteRedmineSyncTimesheet =
        openSuiteRedmineSyncRepo.findBySyncTypeSelect(
            OpenSuitRedmineSyncRepository.SYNC_TYPE_SPENT_TIME);

    if (openSuiteRedmineSyncTimesheet != null
        && !openSuiteRedmineSyncTimesheet
            .getRedmineToOpenSuiteSyncSelect()
            .equals(OpenSuitRedmineSyncRepository.SYNC_NONE)) {

      Map<String, String> params = new HashMap<String, String>();
      String redmineToOpenSuiteFilter = openSuiteRedmineSyncTimesheet.getRedmineToOpenSuiteFilter();

      if (redmineToOpenSuiteFilter != null) {
        redmineToOpenSuiteFilter = redmineToOpenSuiteFilter.trim().replace(" ", "");
        long count = redmineToOpenSuiteFilter.chars().filter(ch -> ch == '&').count();

        for (int i = 0; i <= count; i++) {
          int indexOfAnd = redmineToOpenSuiteFilter.indexOf("&");
          String subFilter =
              redmineToOpenSuiteFilter.substring(
                  0, indexOfAnd != -1 ? indexOfAnd : redmineToOpenSuiteFilter.length());

          int indexOfOperator = subFilter.indexOf("=");
          String name = subFilter.substring(0, indexOfOperator);
          String value = subFilter.substring(indexOfOperator + 1, subFilter.length());
          params.put(name, value);

          redmineToOpenSuiteFilter = redmineToOpenSuiteFilter.substring(indexOfAnd + 1);
        }
      }

      if (lastBatchEndDate != null && (params.get("from") == null || params.get("to") == null)) {
        params.put("from", lastBatchEndDate.toLocalDate().toString());
      }

      try {
        importTimeEntryList =
            redmineManager.getTimeEntryManager().getTimeEntries(params).getResults();
      } catch (RedmineException e) {
        TraceBackService.trace(e);
      }
    }

    importDataMap.put("importTimeEntryList", importTimeEntryList);
  }
}
