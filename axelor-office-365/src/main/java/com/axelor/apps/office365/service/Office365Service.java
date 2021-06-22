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

import com.axelor.apps.base.db.ICalendar;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.office.db.OfficeAccount;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import java.net.MalformedURLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import wslite.json.JSONException;
import wslite.json.JSONObject;

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

  static final List<String> SCOPES = Arrays.asList(SCOPE.split(" "));

  String processJsonValue(String key, JSONObject jsonObject);

  void putObjValue(JSONObject jsonObject, String key, String value) throws JSONException;

  LocalDateTime processLocalDateTimeValue(JSONObject jsonObject, String key, ZoneId zoneId);

  boolean needUpdation(
      JSONObject jsonObject,
      LocalDateTime lastSyncOn,
      LocalDateTime createdOn,
      LocalDateTime updatedOn);

  String createOffice365Object(
      String urlStr, JSONObject jsonObject, String accessToken, String office365Id, String key);

  void putUserEmailAddress(User user, JSONObject jsonObject, String key) throws JSONException;

  void syncContact(OfficeAccount officeAccount) throws AxelorException, MalformedURLException;

  void syncCalendar(OfficeAccount officeAccount) throws AxelorException, MalformedURLException;

  void syncCalendar(ICalendar calendar) throws AxelorException, MalformedURLException;

  User getUser(String name, String email);

  void syncMail(OfficeAccount officeAccount, String urlStr)
      throws AxelorException, MalformedURLException;

  void syncUserMail(EmailAddress emailAddress, List<String> emailIds);
}
