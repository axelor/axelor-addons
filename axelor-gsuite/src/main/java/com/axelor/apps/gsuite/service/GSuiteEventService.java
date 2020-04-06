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
package com.axelor.apps.gsuite.service;

import com.axelor.apps.crm.db.Event;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.exception.AxelorException;
import java.io.IOException;
import java.util.Iterator;

public interface GSuiteEventService {

  public GoogleAccount sync(GoogleAccount googleAccount) throws AxelorException;

  Event sync(Event event, boolean remove) throws AxelorException;

  String updateGoogleEvent(Event event, String[] account, boolean remove) throws IOException;

  com.google.api.services.calendar.model.Event extractEvent(
      Event event, com.google.api.services.calendar.model.Event googleEvent);

  GoogleAccount updateCrmEvents(GoogleAccount account) throws IOException;

  void checkEvent(
      GoogleAccount account, Iterator<com.google.api.services.calendar.model.Event> iterator)
      throws IOException;

  Event createUpdateCrmEvent(Event event, com.google.api.services.calendar.model.Event googleEvent)
      throws IOException;

  void createEventAccount(GoogleAccount account, Event event, String eventId);
}
