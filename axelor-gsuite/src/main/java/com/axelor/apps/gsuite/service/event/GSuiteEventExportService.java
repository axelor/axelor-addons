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
import java.io.IOException;
import java.util.Iterator;

public interface GSuiteEventExportService {

  public EmailAccount sync(EmailAccount emailAccount) throws AxelorException;

  String updateGoogleEvent(Event event, String[] account, boolean remove) throws IOException;

  com.google.api.services.calendar.model.Event extractEvent(
      Event event, com.google.api.services.calendar.model.Event googleEvent);

  EmailAccount updateCrmEvents(EmailAccount account) throws IOException;

  void checkEvent(
      EmailAccount account, Iterator<com.google.api.services.calendar.model.Event> iterator)
      throws IOException;

  Event createUpdateCrmEvent(Event event, com.google.api.services.calendar.model.Event googleEvent)
      throws IOException;

  void removeEventFromRemote(Event event);
}
