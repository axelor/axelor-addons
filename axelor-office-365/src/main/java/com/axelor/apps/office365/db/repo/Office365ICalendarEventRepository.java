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
package com.axelor.apps.office365.db.repo;

import com.axelor.apps.base.db.ICalendar;
import com.axelor.apps.base.db.ICalendarEvent;
import com.axelor.apps.base.db.repo.ICalendarEventManagementRepository;
import com.axelor.apps.crm.db.Event;
import com.axelor.apps.office365.service.Office365Service;
import com.axelor.db.Query;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;

public class Office365ICalendarEventRepository extends ICalendarEventManagementRepository {

  @Inject Office365Service office365Service;

  @Override
  public void remove(ICalendarEvent event) {

    ICalendar calendar = event.getCalendar();
    if (event.getOffice365Id() != null
        && calendar != null
        && calendar.getOfficeAccount() != null
        && !calendar.getIsOfficeEditableCalendar()) {
      try {
        String accessToken =
            office365Service.getAccessTocken(event.getCalendar().getOfficeAccount());
        office365Service.deleteOffice365Object(
            Office365Service.DELETE_EVENT_URL, event.getOffice365Id(), accessToken, "calendar");
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }
    super.remove(event);
  }

  @Override
  public ICalendarEvent findByOffice365Id(String office365Id) {

    ICalendarEvent event = super.findByOffice365Id(office365Id);
    if (event != null && Event.class.isAssignableFrom(event.getClass())) {
      Event crmEvent = (Event) event;
      if (crmEvent.getParentEvent() == null) {
        return crmEvent;
      } else {
        return Query.of(Event.class)
            .filter(
                "self.office365Id = :office365Id AND self.parentEvent IS NULL AND COALESCE(self.archived, false) = false")
            .bind("office365Id", office365Id)
            .fetchOne();
      }
    }

    return event;
  }

  @Override
  public ICalendarEvent copy(ICalendarEvent iCalendarEvent, boolean deep) {

    iCalendarEvent = super.copy(iCalendarEvent, deep);
    iCalendarEvent.setOffice365Id(null);
    return iCalendarEvent;
  }
}
