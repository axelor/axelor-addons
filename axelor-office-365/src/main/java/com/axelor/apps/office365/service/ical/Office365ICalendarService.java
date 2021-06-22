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
package com.axelor.apps.office365.service.ical;

import com.axelor.apps.base.db.ICalendar;
import com.axelor.apps.base.db.repo.ICalendarRepository;
import com.axelor.apps.base.ical.ICalendarException;
import com.axelor.apps.crm.service.CalendarService;
import com.axelor.apps.office365.service.Office365Service;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import java.net.MalformedURLException;

public class Office365ICalendarService extends CalendarService {

  @Override
  public void sync(ICalendar calendar, boolean all, int weeks)
      throws MalformedURLException, ICalendarException {

    if (calendar.getTypeSelect() == ICalendarRepository.OFFICE_365) {
      try {
        Beans.get(Office365Service.class).syncCalendar(calendar);
      } catch (AxelorException e) {
        TraceBackService.trace(e);
      }
      return;
    }

    super.sync(calendar, all, weeks);
  }
}
