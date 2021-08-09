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
import com.axelor.apps.office.db.ContactFolder;
import com.axelor.apps.office.db.MailFolder;
import com.axelor.apps.office.db.OfficeAccount;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public interface Office365Service {

  static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  static final String SCOPE =
      "openid offline_access Contacts.ReadWrite Calendars.ReadWrite Mail.ReadWrite";

  static final String GRAPH_URL = "https://graph.microsoft.com/v1.0/";

  static final String SIGNED_USER_URL = GRAPH_URL + "me";
  static final String CONTACT_FOLDER_URL = GRAPH_URL + "me/contactFolders";
  static final String CONTACT_CHILD_FOLDER_URL = GRAPH_URL + "/me/contactFolders/%s/childFolders";
  static final String FOLDER_CONTACTS_URL = GRAPH_URL + "me/contactFolders/%s/contacts";
  static final String CONTACT_URL = GRAPH_URL + "me/contacts";
  static final String CALENDAR_URL = GRAPH_URL + "me/calendars";
  static final String EVENT_URL = GRAPH_URL + "me/calendars/%s/events";
  static final String CALENDAR_VIEW_URL = GRAPH_URL + "me/calendars/%s/calendarView";
  static final String DELETE_EVENT_URL = GRAPH_URL + "me/events";
  static final String MAIL_FOLDER_URL = GRAPH_URL + "me/mailFolders";
  static final String MAIL_CHILD_FOLDER_URL = GRAPH_URL + "me/mailFolders/%s/childFolders";
  static final String FOLDER_MAILS_URL = GRAPH_URL + "me/mailFolders/%s/messages";
  static final String MAIL_URL = GRAPH_URL + "me/messages";
  static final String MAIL_ATTACHMENT_URL = GRAPH_URL + "me/messages/%s/attachments";
  static final String MAIL_USER_URL = GRAPH_URL + "users/%s/messages";
  static final String MAIL_ID_URL = GRAPH_URL + "users/%s/messages/%s";

  static final String RECORD_DELETED = "deleted";

  static final List<String> SCOPES = Arrays.asList(SCOPE.split(" "));

  public String processJsonValue(String key, JSONObject jsonObject);

  public void putObjValue(JSONObject jsonObject, String key, String value) throws JSONException;

  public LocalDateTime processLocalDateTimeValue(JSONObject jsonObject, String key, ZoneId zoneId);

  public boolean needUpdation(
      JSONObject jsonObject,
      LocalDateTime lastSyncOn,
      LocalDateTime createdOn,
      LocalDateTime updatedOn);

  public String createOffice365Object(
      String urlStr,
      JSONObject jsonObject,
      String accessToken,
      String office365Id,
      String key,
      String type);

  public void deleteOffice365Object(
      String urlStr, String office365Id, String accessToken, String type);

  public String getAccessTocken(OfficeAccount officeAccount) throws AxelorException;

  public JSONArray fetchData(
      String urlStr,
      String accessToken,
      boolean isListResult,
      Map<String, String> queryParams,
      String type);

  public JSONArray fetchIncrementalData(
      String urlStr,
      String accessToken,
      String type,
      String deltaTocken,
      LocalDateTime startDate,
      LocalDateTime endDate);

  public void putUserEmailAddress(User user, JSONObject jsonObject, String key)
      throws JSONException;

  public void syncContact(OfficeAccount officeAccount)
      throws AxelorException, MalformedURLException;

  public void syncContacts(
      String accessToken, ContactFolder contactFolder, List<Long> removedContactIdList);

  public void syncCalendar(OfficeAccount officeAccount)
      throws AxelorException, MalformedURLException;

  public void syncCalendar(ICalendar calendar) throws AxelorException, MalformedURLException;

  public void syncEvent(
      ICalendar iCalendar,
      OfficeAccount officeAccount,
      String accessToken,
      LocalDateTime lastSyncOn,
      LocalDateTime now,
      List<Long> removedEventIdList)
      throws MalformedURLException;

  public void syncMail(OfficeAccount officeAccount, String urlStr)
      throws AxelorException, MalformedURLException;

  public void syncMails(
      OfficeAccount officeAccount,
      String accessToken,
      String parentFolderId,
      MailFolder mailFolder,
      List<String> emails);

  public void syncUserMail(EmailAddress emailAddress, List<String> emailIds);
}
