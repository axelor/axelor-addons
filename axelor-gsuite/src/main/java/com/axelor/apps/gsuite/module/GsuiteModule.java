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
package com.axelor.apps.gsuite.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.gsuite.db.GoogleAccountGoogleRepository;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.service.GoogleAbsContactService;
import com.axelor.apps.gsuite.service.GoogleAbsContactServiceImpl;
import com.axelor.apps.gsuite.service.GoogleAbsDriveService;
import com.axelor.apps.gsuite.service.GoogleAbsDriveServiceImpl;
import com.axelor.apps.gsuite.service.GoogleAbsEventService;
import com.axelor.apps.gsuite.service.GoogleAbsEventServiceImpl;
import com.axelor.apps.gsuite.service.GoogleContactService;
import com.axelor.apps.gsuite.service.GoogleContactServiceImpl;
import com.axelor.apps.gsuite.service.GoogleDriveService;
import com.axelor.apps.gsuite.service.GoogleDriveServiceImpl;
import com.axelor.apps.gsuite.service.GoogleEventService;
import com.axelor.apps.gsuite.service.GoogleEventServiceImpl;

public class GsuiteModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(GoogleEventService.class).to(GoogleEventServiceImpl.class);
    bind(GoogleContactService.class).to(GoogleContactServiceImpl.class);
    bind(GoogleAccountRepository.class).to(GoogleAccountGoogleRepository.class);
    bind(GoogleDriveService.class).to(GoogleDriveServiceImpl.class);
    bind(GoogleAbsContactService.class).to(GoogleAbsContactServiceImpl.class);
    bind(GoogleAbsDriveService.class).to(GoogleAbsDriveServiceImpl.class);
    bind(GoogleAbsEventService.class).to(GoogleAbsEventServiceImpl.class);
  }
}
