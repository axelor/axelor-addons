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
import com.axelor.apps.redmine.service.api.imports.RedmineIssueService;
import com.axelor.apps.redmine.service.api.imports.RedmineIssueServiceImpl;
import com.axelor.apps.redmine.service.api.imports.RedmineProjectService;
import com.axelor.apps.redmine.service.api.imports.RedmineProjectServiceImpl;
import com.axelor.apps.redmine.service.api.imports.RedmineService;
import com.axelor.apps.redmine.service.api.imports.RedmineServiceImpl;
import com.axelor.apps.redmine.service.api.imports.issues.RedmineImportIssueService;
import com.axelor.apps.redmine.service.api.imports.issues.RedmineImportIssueServiceImpl;
import com.axelor.apps.redmine.service.api.imports.issues.RedmineImportTimeSpentService;
import com.axelor.apps.redmine.service.api.imports.issues.RedmineImportTimeSpentServiceImpl;
import com.axelor.apps.redmine.service.api.imports.projects.RedmineImportProjectService;
import com.axelor.apps.redmine.service.api.imports.projects.RedmineImportProjectServiceImpl;
import com.axelor.apps.redmine.service.db.imports.issues.RedmineDbImportIssueService;
import com.axelor.apps.redmine.service.db.imports.issues.RedmineDbImportIssueServiceImpl;
import com.axelor.apps.redmine.service.db.imports.issues.RedmineDbImportTimeSpentService;
import com.axelor.apps.redmine.service.db.imports.issues.RedmineDbImportTimeSpentServiceImpl;
import com.axelor.apps.redmine.service.db.imports.projects.RedmineDbImportProjectService;
import com.axelor.apps.redmine.service.db.imports.projects.RedmineDbImportProjectServiceImpl;

public class RedmineModule extends AxelorModule {

  @Override
  protected void configure() {

    bind(RedmineService.class).to(RedmineServiceImpl.class);
    bind(RedmineIssueService.class).to(RedmineIssueServiceImpl.class);
    bind(RedmineProjectService.class).to(RedmineProjectServiceImpl.class);
    bind(RedmineImportProjectService.class).to(RedmineImportProjectServiceImpl.class);
    bind(RedmineImportIssueService.class).to(RedmineImportIssueServiceImpl.class);
    bind(RedmineImportTimeSpentService.class).to(RedmineImportTimeSpentServiceImpl.class);
    bind(TeamTaskBusinessSupportRepository.class).to(TeamTaskRedmineRepositiry.class);
    bind(TimesheetBusinessProductionServiceImpl.class).to(TimesheetRedmineServiceImpl.class);
    bind(TeamTaskBusinessSupportServiceImpl.class).to(TeamTaskRedmineServiceImpl.class);
    bind(TeamTaskRedmineService.class).to(TeamTaskRedmineServiceImpl.class);
    bind(RedmineDbImportIssueService.class).to(RedmineDbImportIssueServiceImpl.class);
    bind(RedmineDbImportTimeSpentService.class).to(RedmineDbImportTimeSpentServiceImpl.class);
    bind(RedmineDbImportIssueService.class).to(RedmineDbImportIssueServiceImpl.class);
    bind(RedmineDbImportProjectService.class).to(RedmineDbImportProjectServiceImpl.class);
  }
}
