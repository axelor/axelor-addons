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
import com.axelor.apps.redmine.exports.service.RedmineDynamicExportService;
import com.axelor.apps.redmine.exports.service.RedmineDynamicExportServiceImpl;
import com.axelor.apps.redmine.exports.service.RedmineExportIssueService;
import com.axelor.apps.redmine.exports.service.RedmineExportIssueServiceImpl;
import com.axelor.apps.redmine.exports.service.RedmineExportProjectService;
import com.axelor.apps.redmine.exports.service.RedmineExportProjectServiceImpl;
import com.axelor.apps.redmine.exports.service.RedmineExportTimeSpentService;
import com.axelor.apps.redmine.exports.service.RedmineExportTimeSpentServiceImpl;
import com.axelor.apps.redmine.exports.service.RedmineExportVersionService;
import com.axelor.apps.redmine.exports.service.RedmineExportVersionServiceImpl;
import com.axelor.apps.redmine.imports.service.RedmineDynamicImportService;
import com.axelor.apps.redmine.imports.service.RedmineDynamicImportServiceImpl;
import com.axelor.apps.redmine.imports.service.RedmineImportIssueService;
import com.axelor.apps.redmine.imports.service.RedmineImportIssueServiceImpl;
import com.axelor.apps.redmine.imports.service.RedmineImportProjectService;
import com.axelor.apps.redmine.imports.service.RedmineImportProjectServiceImpl;
import com.axelor.apps.redmine.imports.service.RedmineImportTimeSpentService;
import com.axelor.apps.redmine.imports.service.RedmineImportTimeSpentServiceImpl;
import com.axelor.apps.redmine.imports.service.RedmineImportTrackerService;
import com.axelor.apps.redmine.imports.service.RedmineImportTrackerServiceImp;
import com.axelor.apps.redmine.imports.service.RedmineImportVersionService;
import com.axelor.apps.redmine.imports.service.RedmineImportVersionServiceImpl;
import com.axelor.apps.redmine.service.RedmineService;
import com.axelor.apps.redmine.service.RedmineServiceImpl;
import com.axelor.apps.redmine.sync.process.RedmineSyncProcessService;
import com.axelor.apps.redmine.sync.process.RedmineSyncProcessServiceImpl;

public class RedmineModule extends AxelorModule {

  @Override
  protected void configure() {

    bind(RedmineService.class).to(RedmineServiceImpl.class);
    bind(RedmineSyncProcessService.class).to(RedmineSyncProcessServiceImpl.class);

    // Import Methods
    bind(RedmineImportTrackerService.class).to(RedmineImportTrackerServiceImp.class);
    bind(RedmineImportProjectService.class).to(RedmineImportProjectServiceImpl.class);
    bind(RedmineImportVersionService.class).to(RedmineImportVersionServiceImpl.class);
    bind(RedmineImportIssueService.class).to(RedmineImportIssueServiceImpl.class);
    bind(RedmineImportTimeSpentService.class).to(RedmineImportTimeSpentServiceImpl.class);
    bind(RedmineDynamicImportService.class).to(RedmineDynamicImportServiceImpl.class);

    // Export Methods
    bind(RedmineExportProjectService.class).to(RedmineExportProjectServiceImpl.class);
    bind(RedmineExportVersionService.class).to(RedmineExportVersionServiceImpl.class);
    bind(RedmineExportIssueService.class).to(RedmineExportIssueServiceImpl.class);
    bind(RedmineExportTimeSpentService.class).to(RedmineExportTimeSpentServiceImpl.class);
    bind(RedmineDynamicExportService.class).to(RedmineDynamicExportServiceImpl.class);
  }
}
