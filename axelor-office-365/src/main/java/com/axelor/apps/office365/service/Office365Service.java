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
package com.axelor.apps.office365.service;

import com.axelor.apps.base.db.AppOffice365;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.exception.AxelorException;
import java.net.MalformedURLException;
import java.util.List;

public interface Office365Service {

  static final String SCOPE =
      "openid offline_access Contacts.ReadWrite Calendars.ReadWrite Mail.ReadWrite";

  static final String GRAPH_URL = "https://graph.microsoft.com/v1.0/";

  static final String SIGNED_USER_URL = GRAPH_URL + "me";
  static final String CONTACT_URL = GRAPH_URL + "me/contacts";
  static final String CALENDAR_URL = GRAPH_URL + "me/calendars";
  static final String EVENT_URL = GRAPH_URL + "me/calendars/%s/events";
  static final String MAIL_URL = GRAPH_URL + "me/messages";
  static final String MAIL_USER_URL = GRAPH_URL + "users/%s/messages";
  static final String MAIL_ID_URL = GRAPH_URL + "users/%s/messages/%s";

  void syncContact(AppOffice365 appOffice365) throws AxelorException, MalformedURLException;

  void syncCalendar(AppOffice365 appOffice365) throws AxelorException, MalformedURLException;

  void syncMail(AppOffice365 appOffice365, String urlStr)
      throws AxelorException, MalformedURLException;

  void syncUserMail(EmailAddress emailAddress, List<String> emailIds);
}
