/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2022 Axelor (<http://axelor.com>).
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
package com.axelor.apps.redmine.service.imports.fetch;

import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.common.StringUtils;
import com.axelor.studio.db.repo.AppRedmineRepository;
import com.taskadapter.redmineapi.IssueManager;
import com.taskadapter.redmineapi.Params;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.TimeEntryManager;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.TimeEntry;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;

public class RedmineFetchDataService {

  private IssueManager redmineIssueManager;
  private TimeEntryManager redmineTimeEntryManager;

  private static Integer FETCH_LIMIT = 100;

  public List<Project> fetchProjectImportData(RedmineManager redmineManager)
      throws RedmineException {

    return redmineManager.getProjectManager().getProjects();
  }

  public List<Issue> fetchIssueImportData(
      RedmineManager redmineManager,
      ZonedDateTime lastBatchEndDate,
      String failedRedmineIssuesIds,
      int synchronisedWith)
      throws RedmineException {

    redmineIssueManager = redmineManager.getIssueManager();

    if (synchronisedWith == AppRedmineRepository.SYNCHRONISED_WITH_EASY_REDMINE) {
      return fetchIssueImportDataFromEasyRedmine(lastBatchEndDate, failedRedmineIssuesIds);
    }

    List<Issue> importIssueList = new ArrayList<Issue>();

    Params params = new Params();

    params.add("sort", "updated_on");
    params.add("limit", FETCH_LIMIT.toString());

    if (lastBatchEndDate != null) {
      ZonedDateTime endOn = lastBatchEndDate.withZoneSameInstant(ZoneOffset.UTC).withNano(0);
      params
          .add("set_filter", "1")
          .add("f[]", "updated_on")
          .add("op[updated_on]", ">=")
          .add("v[updated_on][]", endOn.toString());

      if (!StringUtils.isEmpty(failedRedmineIssuesIds)) {
        params.add("f[]", "issue_id").add("op[issue_id]", "!");

        for (String failedId : failedRedmineIssuesIds.split(",")) {
          params.add("v[issue_id][]", failedId);
        }

        Params errorIdsParams =
            new Params()
                .add("set_filter", "1")
                .add("f[]", "issue_id")
                .add("op[issue_id]", "=")
                .add("v[issue_id][]", failedRedmineIssuesIds);

        addIssues(importIssueList, errorIdsParams);
      }
    } else {
      params.add("status_id", "*");
    }

    addIssues(importIssueList, params);

    return importIssueList;
  }

  private List<Issue> fetchIssueImportDataFromEasyRedmine(
      ZonedDateTime lastBatchEndDate, String failedRedmineIssuesIds) throws RedmineException {

    Map<String, String> params = new HashMap<String, String>();

    params.put("sort", "updated_on");
    params.put("limit", FETCH_LIMIT.toString());

    if (lastBatchEndDate != null) {
      ZonedDateTime endOn = lastBatchEndDate.withZoneSameInstant(ZoneOffset.UTC).withNano(0);
      ZonedDateTime now = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC).withNano(0);

      params.put("set_filter", "1");
      params.put("updated_on", endOn.toString() + "|" + now.toString());
    } else {
      params.put("status_id", "*");
    }

    HashSet<Issue> importIssueSet = new HashSet<Issue>();
    importIssueSet = addIssuesFromEasyRedmine(importIssueSet, params);

    if (failedRedmineIssuesIds != null) {
      String[] failedIds = failedRedmineIssuesIds.split(",");

      params.clear();
      params.put("limit", FETCH_LIMIT.toString());
      params.put("set_filter", "1");

      for (int i = 0; i < failedIds.length; i += FETCH_LIMIT) {
        int end = Math.min(i + FETCH_LIMIT, failedIds.length);
        String[] batchIds = Arrays.copyOfRange(failedIds, i, end);

        params.put("issue_id", String.join(",", batchIds));

        importIssueSet = addIssuesFromEasyRedmine(importIssueSet, params);
      }
    }

    return new ArrayList<>(importIssueSet);
  }

  private void addIssues(List<Issue> importIssueList, Params params) throws RedmineException {

    Long count = 0L;

    do {
      params.add("offset", count.toString());

      List<Issue> tempIssueList = redmineIssueManager.getIssues(params).getResults();
      if (tempIssueList == null || tempIssueList.isEmpty()) {
        break;
      }
      importIssueList.addAll(tempIssueList);
      count += tempIssueList.size();
    } while (true);
  }

  private HashSet<Issue> addIssuesFromEasyRedmine(
      HashSet<Issue> importIssueList, Map<String, String> params) throws RedmineException {

    Long count = 0L;

    do {
      params.put("offset", count.toString());

      List<Issue> tempIssueList = redmineIssueManager.getIssues(params).getResults();
      if (tempIssueList == null || tempIssueList.isEmpty()) {
        break;
      }
      importIssueList.addAll(tempIssueList);
      count += tempIssueList.size();
    } while (true);

    return importIssueList;
  }

  public List<TimeEntry> fetchTimeEntryImportData(
      RedmineManager redmineManager,
      ZonedDateTime lastBatchEndDate,
      String failedRedmineTimeEntriesIds,
      int synchronisedWith)
      throws RedmineException {

    redmineTimeEntryManager = redmineManager.getTimeEntryManager();

    List<com.taskadapter.redmineapi.bean.TimeEntry> importTimeEntryList = null;

    Map<String, String> params = new HashMap<String, String>();
    params.put("set_filter", "1");

    if (lastBatchEndDate != null) {
      ZonedDateTime endOn = lastBatchEndDate.withZoneSameInstant(ZoneOffset.UTC).withNano(0);
      ZonedDateTime now = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC).withNano(0);

      if (synchronisedWith == AppRedmineRepository.SYNCHRONISED_WITH_EASY_REDMINE) {
        params.put("updated_on", endOn.toString() + "|" + now.toString());
      } else {
        params.put("f[]", "updated_on");
        params.put("op[updated_on]", ">=");
        params.put("v[updated_on][]", endOn.toString());
      }

      importTimeEntryList = fetchTimeEntries(params);
    } else {
      importTimeEntryList = redmineTimeEntryManager.getTimeEntries();
    }

    if (failedRedmineTimeEntriesIds != null) {
      int[] failedIds =
          Arrays.asList(failedRedmineTimeEntriesIds.split(",")).stream()
              .map(String::trim)
              .mapToInt(Integer::parseInt)
              .toArray();

      for (int id : failedIds) {
        try {
          TimeEntry timeEntry = redmineTimeEntryManager.getTimeEntry(id);

          if (!importTimeEntryList.contains(timeEntry)) {
            importTimeEntryList.add(timeEntry);
          }
        } catch (RedmineException e) {
          TraceBackService.trace(e);
        }
      }
    }

    return importTimeEntryList;
  }

  public List<TimeEntry> fetchTimeEntries(Map<String, String> params) throws RedmineException {

    List<TimeEntry> redmineTimeEntryList = new ArrayList<>();
    List<TimeEntry> tempRedmineTimeEntryList;
    Map<String, String> tempParams;

    params.put("limit", FETCH_LIMIT.toString());
    Long count = 0L;

    do {
      tempParams = params;
      tempParams.put("offset", count.toString());
      tempRedmineTimeEntryList = redmineTimeEntryManager.getTimeEntries(tempParams).getResults();

      if (CollectionUtils.isNotEmpty(tempRedmineTimeEntryList)) {
        redmineTimeEntryList.addAll(tempRedmineTimeEntryList);
        count += tempRedmineTimeEntryList.size();
        tempRedmineTimeEntryList.clear();
      } else {
        params = null;
      }
    } while (params != null);

    return redmineTimeEntryList;
  }
}
