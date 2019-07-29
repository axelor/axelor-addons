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
package com.axelor.apps.redmine.imports;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.redmine.imports.service.ImportActivityService;
import com.axelor.apps.redmine.imports.service.ImportGroupService;
import com.axelor.apps.redmine.imports.service.ImportIssueService;
import com.axelor.apps.redmine.imports.service.ImportProjectService;
import com.axelor.apps.redmine.imports.service.ImportService;
import com.axelor.apps.redmine.imports.service.ImportUserService;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.RedmineManager;
import java.util.Date;
import java.util.function.Consumer;

public class RedmineImportServiceImpl implements RedmineImportService {

  @Inject BatchRepository batchRepo;

  @Inject ImportGroupService groupService;
  @Inject ImportUserService userService;
  @Inject ImportProjectService projectService;
  @Inject ImportIssueService issueService;
  @Inject ImportActivityService activityService;

  @Override
  public void importRedmine(
      Batch batch,
      Date lastImportDateTime,
      RedmineManager redmineManager,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {
    ImportService.result = "";
    groupService.importGroup(batch, redmineManager, onSuccess, onError);
    userService.importUser(batch, lastImportDateTime, redmineManager, onSuccess, onError);
    activityService.importActivity(batch, redmineManager, onSuccess, onError);
    projectService.importProject(batch, lastImportDateTime, redmineManager, onSuccess, onError);
    issueService.importIssue(batch, lastImportDateTime, redmineManager, onSuccess, onError);
  }
}
