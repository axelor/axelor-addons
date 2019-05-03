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
import com.axelor.apps.redmine.exports.RedmineExportService;
import com.axelor.apps.redmine.exports.RedmineExportServiceImpl;
import com.axelor.apps.redmine.exports.service.ExportGroupService;
import com.axelor.apps.redmine.exports.service.ExportGroupServiceImpl;
import com.axelor.apps.redmine.exports.service.ExportIssueService;
import com.axelor.apps.redmine.exports.service.ExportIssueServiceImpl;
import com.axelor.apps.redmine.exports.service.ExportProjectService;
import com.axelor.apps.redmine.exports.service.ExportProjectServiceImpl;
import com.axelor.apps.redmine.exports.service.ExportTimeEntryService;
import com.axelor.apps.redmine.exports.service.ExportTimeEntryServiceImpl;
import com.axelor.apps.redmine.exports.service.ExportUserService;
import com.axelor.apps.redmine.exports.service.ExportUserServiceImpl;
import com.axelor.apps.redmine.imports.RedmineImportService;
import com.axelor.apps.redmine.imports.RedmineImportServiceImpl;
import com.axelor.apps.redmine.imports.service.ImportActivityService;
import com.axelor.apps.redmine.imports.service.ImportActivityServiceImpl;
import com.axelor.apps.redmine.imports.service.ImportGroupService;
import com.axelor.apps.redmine.imports.service.ImportGroupServiceImpl;
import com.axelor.apps.redmine.imports.service.ImportIssueService;
import com.axelor.apps.redmine.imports.service.ImportIssueServiceImpl;
import com.axelor.apps.redmine.imports.service.ImportProjectService;
import com.axelor.apps.redmine.imports.service.ImportProjectServiceImpl;
import com.axelor.apps.redmine.imports.service.ImportUserService;
import com.axelor.apps.redmine.imports.service.ImportUserServiceImpl;
import com.axelor.apps.redmine.service.RedmineService;
import com.axelor.apps.redmine.service.RedmineServiceImpl;

public class RedmineModule extends AxelorModule {

	@Override
	protected void configure() {
		bind(RedmineService.class).to(RedmineServiceImpl.class);

		bind(RedmineExportService.class).to(RedmineExportServiceImpl.class);
		bind(ExportGroupService.class).to(ExportGroupServiceImpl.class);
		bind(ExportUserService.class).to(ExportUserServiceImpl.class);
		bind(ExportProjectService.class).to(ExportProjectServiceImpl.class);
		bind(ExportIssueService.class).to(ExportIssueServiceImpl.class);
		bind(ExportTimeEntryService.class).to(ExportTimeEntryServiceImpl.class);

		bind(RedmineImportService.class).to(RedmineImportServiceImpl.class);
		bind(ImportGroupService.class).to(ImportGroupServiceImpl.class);
		bind(ImportUserService.class).to(ImportUserServiceImpl.class);
		bind(ImportProjectService.class).to(ImportProjectServiceImpl.class);
		bind(ImportIssueService.class).to(ImportIssueServiceImpl.class);
		bind(ImportActivityService.class).to(ImportActivityServiceImpl.class);
	}

}
