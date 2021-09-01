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
package com.axelor.apps.gsuite.service.event;

import com.axelor.apps.crm.db.Event;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.exception.AxelorException;
import com.google.api.services.calendar.Calendar;
import java.time.LocalDateTime;

public interface GSuiteEventImportService {

  EmailAccount sync(EmailAccount emailAccount) throws AxelorException;

  public EmailAccount sync(
      EmailAccount emailAccount, LocalDateTime startDateT, LocalDateTime endDateT)
      throws AxelorException;

  public void syncEvents(
      EmailAccount emailAccount,
      Calendar calendar,
      LocalDateTime startDateT,
      LocalDateTime endDateT)
      throws AxelorException;

  public void setEventDates(
      com.google.api.services.calendar.model.Event googleEvent, Event aosEvent);
}
