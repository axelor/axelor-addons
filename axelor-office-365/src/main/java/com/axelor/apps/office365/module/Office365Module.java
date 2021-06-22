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
package com.axelor.apps.office365.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.base.service.batch.BaseBatchService;
import com.axelor.apps.base.service.batch.BatchCalendarSynchronization;
import com.axelor.apps.crm.service.CalendarService;
import com.axelor.apps.office365.service.Office365Service;
import com.axelor.apps.office365.service.Office365ServiceImpl;
import com.axelor.apps.office365.service.batch.Office365BaseBatchService;
import com.axelor.apps.office365.service.batch.Office365BatchCalendarSynchronization;
import com.axelor.apps.office365.service.ical.Office365ICalendarService;

public class Office365Module extends AxelorModule {

  @Override
  protected void configure() {
    bind(Office365Service.class).to(Office365ServiceImpl.class);
    bind(BaseBatchService.class).to(Office365BaseBatchService.class);
    bind(BatchCalendarSynchronization.class).to(Office365BatchCalendarSynchronization.class);
    bind(CalendarService.class).to(Office365ICalendarService.class);
  }
}
