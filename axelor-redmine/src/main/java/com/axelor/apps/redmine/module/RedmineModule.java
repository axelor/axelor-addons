/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
import com.axelor.apps.redmine.service.batch.RedmineBatchImportCommonService;
import com.axelor.apps.redmine.service.batch.RedmineBatchImportCommonServiceImpl;
import com.axelor.apps.redmine.service.imports.RedmineImportIssueService;
import com.axelor.apps.redmine.service.imports.RedmineImportIssueServiceImpl;
import com.axelor.apps.redmine.service.imports.RedmineImportProjectService;
import com.axelor.apps.redmine.service.imports.RedmineImportProjectServiceImpl;
import com.axelor.apps.redmine.service.imports.RedmineImportTimeSpentService;
import com.axelor.apps.redmine.service.imports.RedmineImportTimeSpentServiceImpl;

public class RedmineModule extends AxelorModule {

  @Override
  protected void configure() {

    bind(TeamTaskBusinessSupportRepository.class).to(TeamTaskRedmineRepositiry.class);
    bind(TimesheetBusinessProductionServiceImpl.class).to(TimesheetRedmineServiceImpl.class);
    bind(TeamTaskBusinessSupportServiceImpl.class).to(TeamTaskRedmineServiceImpl.class);
    bind(TeamTaskRedmineService.class).to(TeamTaskRedmineServiceImpl.class);
    bind(RedmineBatchImportCommonService.class).to(RedmineBatchImportCommonServiceImpl.class);
    bind(RedmineImportProjectService.class).to(RedmineImportProjectServiceImpl.class);
    bind(RedmineImportIssueService.class).to(RedmineImportIssueServiceImpl.class);
    bind(RedmineImportTimeSpentService.class).to(RedmineImportTimeSpentServiceImpl.class);
  }
}
