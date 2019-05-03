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
package com.axelor.apps.redmine.exports;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.redmine.exports.service.ExportGroupService;
import com.axelor.apps.redmine.exports.service.ExportIssueService;
import com.axelor.apps.redmine.exports.service.ExportProjectService;
import com.axelor.apps.redmine.exports.service.ExportService;
import com.axelor.apps.redmine.exports.service.ExportTimeEntryService;
import com.axelor.apps.redmine.exports.service.ExportUserService;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.RedmineManager;

public class RedmineExportServiceImpl implements RedmineExportService {

  @Inject ExportGroupService groupService;
  @Inject ExportUserService userService;
  @Inject ExportProjectService projectService;
  @Inject ExportIssueService issueService;
  @Inject ExportTimeEntryService timeEntryService;

  @Override
  public void exportRedmine(
      Batch batch,
      LocalDateTime lastExportDateTime,
      RedmineManager redmineManager,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {
    ExportService.result = "";
    groupService.exportGroup(batch, redmineManager, lastExportDateTime, onSuccess, onError);
    userService.exportUser(batch, redmineManager, lastExportDateTime, onSuccess, onError);
    projectService.exportProject(batch, redmineManager, lastExportDateTime, onSuccess, onError);
    issueService.exportIssue(batch, redmineManager, lastExportDateTime, onSuccess, onError);
    timeEntryService.exportTimeEntry(batch, redmineManager, lastExportDateTime, onSuccess, onError);
  }
}
