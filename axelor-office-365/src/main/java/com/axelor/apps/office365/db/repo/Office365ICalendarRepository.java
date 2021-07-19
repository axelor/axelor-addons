/* Axelor Business Solutions
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
package com.axelor.apps.office365.db.repo;

import com.axelor.apps.base.db.ICalendar;
import com.axelor.apps.base.db.repo.ICalendarRepository;
import com.axelor.apps.office365.service.Office365Service;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;

public class Office365ICalendarRepository extends ICalendarRepository {

  @Inject Office365Service office365Service;

  @Override
  public void remove(ICalendar calendar) {

    if (calendar.getOffice365Id() != null
        && calendar.getOfficeAccount() != null
        && calendar.getIsOfficeRemovableCalendar()) {
      try {
        String accessToken = office365Service.getAccessTocken(calendar.getOfficeAccount());
        office365Service.deleteOffice365Object(
            Office365Service.CALENDAR_URL, calendar.getOffice365Id(), accessToken, "calendar");
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }

    super.remove(calendar);
  }

  @Override
  public ICalendar copy(ICalendar calendar, boolean deep) {

    calendar = super.copy(calendar, deep);
    calendar.setOffice365Id(null);
    return calendar;
  }
}
