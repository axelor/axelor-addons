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
package com.axelor.apps.gsuite.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.gsuite.db.repo.GoogleAccountManagementRepository;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.service.GSuiteAOSContactService;
import com.axelor.apps.gsuite.service.GSuiteAOSContactServiceImpl;
import com.axelor.apps.gsuite.service.GSuiteAOSDriveService;
import com.axelor.apps.gsuite.service.GSuiteAOSDriveServiceImpl;
import com.axelor.apps.gsuite.service.GSuiteAOSEventService;
import com.axelor.apps.gsuite.service.GSuiteAOSEventServiceImpl;
import com.axelor.apps.gsuite.service.GSuiteAOSMessageService;
import com.axelor.apps.gsuite.service.GSuiteAOSMessageServiceImpl;
import com.axelor.apps.gsuite.service.GSuiteContactService;
import com.axelor.apps.gsuite.service.GSuiteContactServiceImpl;
import com.axelor.apps.gsuite.service.GSuiteDriveService;
import com.axelor.apps.gsuite.service.GSuiteDriveServiceImpl;
import com.axelor.apps.gsuite.service.GSuiteEventService;
import com.axelor.apps.gsuite.service.GSuiteEventServiceImpl;
import com.axelor.apps.gsuite.service.app.AppGSuiteService;
import com.axelor.apps.gsuite.service.app.AppGSuiteServiceImpl;

public class GsuiteModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(AppGSuiteService.class).to(AppGSuiteServiceImpl.class);
    bind(GSuiteEventService.class).to(GSuiteEventServiceImpl.class);
    bind(GSuiteContactService.class).to(GSuiteContactServiceImpl.class);
    bind(GoogleAccountRepository.class).to(GoogleAccountManagementRepository.class);
    bind(GSuiteDriveService.class).to(GSuiteDriveServiceImpl.class);
    bind(GSuiteAOSContactService.class).to(GSuiteAOSContactServiceImpl.class);
    bind(GSuiteAOSDriveService.class).to(GSuiteAOSDriveServiceImpl.class);
    bind(GSuiteAOSEventService.class).to(GSuiteAOSEventServiceImpl.class);
    bind(GSuiteAOSMessageService.class).to(GSuiteAOSMessageServiceImpl.class);
  }
}
