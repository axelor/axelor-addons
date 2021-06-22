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
package com.axelor.apps.office365.service.batch;

import com.axelor.apps.base.db.ICalendar;
import com.axelor.apps.base.service.batch.BatchCalendarSynchronization;
import com.axelor.apps.office365.service.Office365Service;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class Office365BatchCalendarSynchronization extends BatchCalendarSynchronization {

  @Inject Office365Service office365Service;

  @Override
  protected void process() {

    super.process();

    final List<ICalendar> calendars =
        batch.getBaseBatch().getCalendarList().stream()
            .filter(calendar -> calendar.getOfficeAccount() != null)
            .collect(Collectors.toList());

    for (ICalendar calendar : calendars) {
      try {
        office365Service.syncCalendar(calendar);
        incrementDone();
      } catch (Exception e) {
        TraceBackService.trace(e, "Calendar synchronization", batch.getId());
        incrementAnomaly();
      }
    }

    batch.setComments(
        String.format("Success : %s , Anomaly : %s", batch.getDone(), batch.getAnomaly()));
  }
}
