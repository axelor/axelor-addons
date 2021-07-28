/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.gsuite.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.crm.db.repo.EventManagementRepository;
import com.axelor.apps.gsuite.db.repo.GSuiteEventRepository;
import com.axelor.apps.gsuite.db.repo.GoogleAccountManagementRepository;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.service.ICalUserService;
import com.axelor.apps.gsuite.service.ICalUserServiceImpl;
import com.axelor.apps.gsuite.service.app.AppGSuiteService;
import com.axelor.apps.gsuite.service.app.AppGSuiteServiceImpl;
import com.axelor.apps.gsuite.service.drive.GSuiteDriveExportService;
import com.axelor.apps.gsuite.service.drive.GSuiteDriveExportServiceImpl;
import com.axelor.apps.gsuite.service.drive.GSuiteDriveImportService;
import com.axelor.apps.gsuite.service.drive.GSuiteDriveImportServiceImpl;
import com.axelor.apps.gsuite.service.event.GSuiteEventExportService;
import com.axelor.apps.gsuite.service.event.GSuiteEventExportServiceImpl;
import com.axelor.apps.gsuite.service.event.GSuiteEventImportService;
import com.axelor.apps.gsuite.service.event.GSuiteEventImportServiceImpl;
import com.axelor.apps.gsuite.service.message.GSuiteMessageImportService;
import com.axelor.apps.gsuite.service.message.GSuiteMessageImportServiceImpl;
import com.axelor.apps.gsuite.service.people.GSuitePartnerExporterService;
import com.axelor.apps.gsuite.service.people.GSuitePartnerExporterServiceImpl;
import com.axelor.apps.gsuite.service.people.GSuitePartnerImportService;
import com.axelor.apps.gsuite.service.people.GSuitePartnerImportServiceImpl;
import com.axelor.apps.gsuite.service.task.GSuiteTaskExportService;
import com.axelor.apps.gsuite.service.task.GSuiteTaskExportServiceImpl;
import com.axelor.apps.gsuite.service.task.GSuiteTaskImportService;
import com.axelor.apps.gsuite.service.task.GSuiteTaskImportServiceImpl;

public class GsuiteModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(AppGSuiteService.class).to(AppGSuiteServiceImpl.class);
    bind(GSuiteEventExportService.class).to(GSuiteEventExportServiceImpl.class);
    bind(GoogleAccountRepository.class).to(GoogleAccountManagementRepository.class);
    bind(GSuiteDriveExportService.class).to(GSuiteDriveExportServiceImpl.class);
    bind(GSuiteDriveImportService.class).to(GSuiteDriveImportServiceImpl.class);
    bind(GSuiteEventImportService.class).to(GSuiteEventImportServiceImpl.class);
    bind(GSuiteMessageImportService.class).to(GSuiteMessageImportServiceImpl.class);
    bind(GSuiteTaskImportService.class).to(GSuiteTaskImportServiceImpl.class);
    bind(ICalUserService.class).to(ICalUserServiceImpl.class);
    bind(GSuitePartnerImportService.class).to(GSuitePartnerImportServiceImpl.class);
    bind(GSuitePartnerExporterService.class).to(GSuitePartnerExporterServiceImpl.class);
    bind(GSuiteTaskExportService.class).to(GSuiteTaskExportServiceImpl.class);
    bind(EventManagementRepository.class).to(GSuiteEventRepository.class);
  }
}
