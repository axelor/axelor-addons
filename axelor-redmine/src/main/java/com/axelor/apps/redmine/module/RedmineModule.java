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
package com.axelor.apps.redmine.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.businessproduction.service.TimesheetBusinessProductionServiceImpl;
import com.axelor.apps.businesssupport.db.repo.TeamTaskBusinessSupportRepository;
import com.axelor.apps.businesssupport.service.TeamTaskBusinessSupportServiceImpl;
import com.axelor.apps.redmine.db.repo.TeamTaskRedmineRepositiry;
import com.axelor.apps.redmine.service.TeamTaskRedmineService;
import com.axelor.apps.redmine.service.TeamTaskRedmineServiceImpl;
import com.axelor.apps.redmine.service.TimesheetRedmineServiceImpl;
import com.axelor.apps.redmine.service.common.RedmineService;
import com.axelor.apps.redmine.service.common.RedmineServiceImpl;
import com.axelor.apps.redmine.service.imports.issues.RedmineImportIssueService;
import com.axelor.apps.redmine.service.imports.issues.RedmineImportIssueServiceImpl;
import com.axelor.apps.redmine.service.imports.issues.RedmineIssueService;
import com.axelor.apps.redmine.service.imports.issues.RedmineIssueServiceImpl;
import com.axelor.apps.redmine.service.imports.projects.RedmineImportProjectService;
import com.axelor.apps.redmine.service.imports.projects.RedmineImportProjectServiceImpl;
import com.axelor.apps.redmine.service.imports.projects.RedmineProjectService;
import com.axelor.apps.redmine.service.imports.projects.RedmineProjectServiceImpl;
import com.axelor.apps.redmine.service.sync.timeentries.RedmineExportTimeSpentService;
import com.axelor.apps.redmine.service.sync.timeentries.RedmineExportTimeSpentServiceImpl;
import com.axelor.apps.redmine.service.sync.timeentries.RedmineImportTimeSpentService;
import com.axelor.apps.redmine.service.sync.timeentries.RedmineImportTimeSpentServiceImpl;
import com.axelor.apps.redmine.service.sync.timeentries.RedmineTimeEntriesService;
import com.axelor.apps.redmine.service.sync.timeentries.RedmineTimeEntriesServiceImpl;

public class RedmineModule extends AxelorModule {

  @Override
  protected void configure() {

    bind(RedmineService.class).to(RedmineServiceImpl.class);
    bind(RedmineIssueService.class).to(RedmineIssueServiceImpl.class);
    bind(RedmineTimeEntriesService.class).to(RedmineTimeEntriesServiceImpl.class);
    bind(RedmineProjectService.class).to(RedmineProjectServiceImpl.class);
    bind(RedmineImportProjectService.class).to(RedmineImportProjectServiceImpl.class);
    bind(RedmineImportIssueService.class).to(RedmineImportIssueServiceImpl.class);
    bind(RedmineImportTimeSpentService.class).to(RedmineImportTimeSpentServiceImpl.class);
    bind(RedmineExportTimeSpentService.class).to(RedmineExportTimeSpentServiceImpl.class);
    bind(TeamTaskBusinessSupportRepository.class).to(TeamTaskRedmineRepositiry.class);
    bind(TimesheetBusinessProductionServiceImpl.class).to(TimesheetRedmineServiceImpl.class);
    bind(TeamTaskBusinessSupportServiceImpl.class).to(TeamTaskRedmineServiceImpl.class);
    bind(TeamTaskRedmineService.class).to(TeamTaskRedmineServiceImpl.class);
  }
}
