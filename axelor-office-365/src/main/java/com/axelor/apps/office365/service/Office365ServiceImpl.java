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
import com.axelor.apps.base.db.ICalendar;
import com.axelor.apps.base.db.ModelEmailLink;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.AppOffice365Repository;
import com.axelor.apps.base.db.repo.ICalendarEventRepository;
import com.axelor.apps.base.db.repo.ICalendarRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.office.db.ContactFolder;
import com.axelor.apps.office.db.MailFolder;
import com.axelor.apps.office.db.Office365Token;
import com.axelor.apps.office.db.repo.MailFolderRepository;
import com.axelor.apps.office.db.repo.Office365TokenRepository;
import com.axelor.apps.office365.translation.ITranslation;
import com.axelor.apps.tool.QueryBuilder;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class Office365ServiceImpl implements Office365Service {

  @Inject Office365ContactService contactService;
  @Inject Office365CalendarService calendarService;
  @Inject Office365MailService mailService;

  @Inject AppOffice365Repository appOffice365Repo;
  @Inject EmailAccountRepository emailAccountRepo;
  @Inject UserRepository userRepo;
  @Inject ICalendarRepository iCalendarRepo;

  static String query = "(self.office365Id IS NULL AND self.createdOn < :start)";
  static String lastSyncQuery =
      "(self.office365Id IS NULL OR (COALESCE(self.updatedOn, self.createdOn) BETWEEN :lastSync AND :start))";
  private String deltaToken = null;

  @Override
  @SuppressWarnings("unchecked")
  public String processJsonValue(String key, JSONObject jsonObject) {

    Object value = jsonObject.getOrDefault(key, "");
    if (value == null) {
      return null;
    }
    return value.toString().replaceAll("null", "").trim();
  }

  @Override
  public void putObjValue(JSONObject jsonObject, String key, String value) throws JSONException {

    if (StringUtils.isBlank(value)) {
      return;
    }

    jsonObject.put(key, value);
  }

  @Override
  public LocalDateTime processLocalDateTimeValue(JSONObject jsonObject, String key, ZoneId zoneId) {

    String dateStr = processJsonValue(key, jsonObject);
    if (StringUtils.isBlank(dateStr)) {
      return null;
    }

    return LocalDateTime.ofInstant(Instant.parse(dateStr), zoneId);
  }

  @Override
  public boolean needUpdation(
      JSONObject jsonObject,
      LocalDateTime lastSyncOn,
      LocalDateTime createdOn,
      LocalDateTime updatedOn) {

    LocalDateTime creationDT =
        processLocalDateTimeValue(jsonObject, "createdDateTime", ZoneId.systemDefault());
    LocalDateTime lastModificationDT =
        processLocalDateTimeValue(jsonObject, "lastModifiedDateTime", ZoneId.systemDefault());
    if ((lastModificationDT != null
            && ((lastSyncOn != null && lastModificationDT.isBefore(lastSyncOn))
                || (updatedOn != null && updatedOn.isAfter(lastModificationDT))
                || (updatedOn == null && createdOn.isAfter(lastModificationDT))))
        || (lastModificationDT == null
            && creationDT != null
            && lastSyncOn != null
            && creationDT.isBefore(lastSyncOn))) {
      return false;
    }

    return true;
  }

  protected Response executeAction(Builder builder) throws IOException {

    Request request = builder.build();
    OkHttpClient httpClient = new OkHttpClient();
    return httpClient.newCall(request).execute();
  }

  public String createOffice365Object(
      String urlStr,
      JSONObject jsonObject,
      String accessToken,
      String office365Id,
      String key,
      String type) {

    try {
      if (office365Id != null) {
        urlStr += "/" + office365Id;
      }
      URL url = new URL(urlStr);
      RequestBody body =
          RequestBody.create(
              jsonObject.toString(), MediaType.parse("application/json; charset=utf-8"));
      Builder builder =
          new Request.Builder()
              .url(url)
              .addHeader("Accept", "application/json")
              .addHeader("Authorization", accessToken);

      if (office365Id != null) {
        builder = builder.patch(body);
      } else {
        builder = builder.post(body);
      }

      Response response = executeAction(builder);
      if (response.isSuccessful()) {
        if (office365Id == null) {
          office365Id =
              StringUtils.substringBetween(
                  response.networkResponse().header("Location"), "/" + key + "('", "')");
        }

        jsonObject.put("Office365Id", office365Id);
        Office365Service.LOG.debug(
            String.format(I18n.get(ITranslation.OFFICE365_OBJECT_SYNC_SUCESS), type, jsonObject));
      } else {
        Office365Service.LOG.debug(
            String.format(
                I18n.get(ITranslation.OFFICE365_OBJECT_SYNC_FAILURE),
                type,
                jsonObject,
                response.code(),
                response.message(),
                response.body().string()));
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    return office365Id;
  }

  public void deleteOffice365Object(
      String urlStr, String office365Id, String accessToken, String type) {

    try {
      if (office365Id != null) {
        urlStr += "/" + office365Id;
      }
      URL url = new URL(urlStr);
      Builder builder =
          new Request.Builder().url(url).addHeader("Authorization", accessToken).delete();
      Response response = executeAction(builder);
      if (!response.isSuccessful() && response.code() != 404) {
        TraceBackService.trace(
            new AxelorException(
                TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
                String.format(
                    I18n.get(ITranslation.OFFICE365_OBJECT_REMOVAL_FAILURE),
                    office365Id,
                    response.code(),
                    response.message(),
                    response.body().string())));
      } else {
        Office365Service.LOG.debug(
            String.format(
                I18n.get(ITranslation.OFFICE365_OBJECT_REMOVAL_SUCESS), type, office365Id));
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  public void putUserEmailAddress(User user, JSONObject jsonObject, String key)
      throws JSONException {

    if (user == null || (user.getPartner() == null && StringUtils.isNotBlank(user.getEmail()))) {
      return;
    }

    String emailAddressStr = null, emailName = user.getName();
    if (user.getPartner() != null && user.getPartner().getEmailAddress() != null) {
      EmailAddress emailAddress = user.getPartner().getEmailAddress();
      emailAddressStr = emailAddress.getAddress();
      if (StringUtils.isNotBlank(emailAddress.getName())) {
        emailName = emailAddress.getName();
      }
    } else if (StringUtils.isNotBlank(user.getEmail())) {
      emailAddressStr = user.getEmail();
    }

    if (StringUtils.isBlank(emailAddressStr)) {
      return;
    }

    JSONObject emailJsonObj = new JSONObject();
    putObjValue(emailJsonObj, "address", emailAddressStr);
    putObjValue(emailJsonObj, "name", emailName);
    jsonObject.put(key, (Object) emailJsonObj);
  }

  @Transactional
  @Override
  public String getAccessTocken(EmailAccount emailAccount) throws AxelorException {

    try {
      AppOffice365 appOffice365 = appOffice365Repo.all().fetchOne();
      OAuth20Service authService =
          new ServiceBuilder(appOffice365.getClientId())
              .apiSecret(appOffice365.getClientSecret())
              .callback(appOffice365.getRedirectUri())
              .defaultScope(Office365Service.SCOPE)
              .build(MicrosoftAzureActiveDirectory20Api.instance());
      OAuth2AccessToken accessToken;
      if (StringUtils.isBlank(emailAccount.getRefreshToken())) {
        throw new AxelorException(
            AppOffice365.class,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(ITranslation.OFFICE365_TOKEN_ERROR));
      }

      accessToken = authService.refreshAccessToken(emailAccount.getRefreshToken());
      emailAccount.setRefreshToken(accessToken.getRefreshToken());
      appOffice365Repo.save(appOffice365);

      Office365Service.LOG.debug(I18n.get(ITranslation.OFFICE365_ACCESS_TOKEN_SUCESS));
      return accessToken.getTokenType() + " " + accessToken.getAccessToken();
    } catch (Exception e) {
      throw new AxelorException(
          AppOffice365.class, TraceBackRepository.CATEGORY_INCONSISTENCY, e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  public JSONArray fetchData(
      String urlStr,
      String accessToken,
      boolean isListResult,
      Map<String, String> queryParams,
      String type) {

    JSONArray jsonArray = new JSONArray();
    Integer top = 500;
    Integer count = 0;
    Integer skip = 0;
    try {
      Builder builder =
          new Request.Builder()
              .addHeader("Accept", "application/json")
              .addHeader("Authorization", accessToken);

      URIBuilder ub = new URIBuilder(urlStr);
      ub.addParameter("$count", "true");
      if (ObjectUtils.notEmpty(queryParams)) {
        for (Entry<String, String> queryParamEntrySet : queryParams.entrySet()) {
          ub.addParameter(queryParamEntrySet.getKey(), queryParamEntrySet.getValue());
        }
      }

      do {
        try {
          ub.addParameter("top", top.toString());
          ub.addParameter("skip", skip.toString());
          builder = builder.url(new URL(ub.toString()));
          Response response = executeAction(builder);
          if (response.isSuccessful()) {
            JSONObject jsonObject = new JSONObject(response.body().string());
            if (isListResult) {
              count = (int) jsonObject.getOrDefault("@odata.count", 0);
              jsonArray.addAll((JSONArray) jsonObject.getOrDefault("value", new JSONArray()));
            } else {
              jsonArray.add(jsonObject);
            }
          } else {
            Office365Service.LOG.debug(
                String.format(
                    I18n.get(ITranslation.OFFICE365_OBJECT_FETCH_FAILURE),
                    type,
                    response.code(),
                    response.message(),
                    response.body().string()));
          }
        } catch (Exception e) {
          TraceBackService.trace(e);
        }
        skip += top;
        count -= skip;
      } while (count > 0);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    Office365Service.LOG.debug(
        String.format(
            I18n.get(ITranslation.OFFICE365_OBJECT_FETCH_SUCESS), type, jsonArray.size()));
    return jsonArray;
  }

  @SuppressWarnings("unchecked")
  public JSONArray fetchIncrementalData(
      String urlStr,
      String accessToken,
      String type,
      String deltaTocken,
      LocalDateTime startDate,
      LocalDateTime endDate) {

    JSONArray jsonArray = new JSONArray();
    try {
      urlStr += "/delta";
      URIBuilder ub = new URIBuilder(urlStr);
      if (StringUtils.isBlank(deltaTocken) && startDate != null && endDate != null) {
        ub.addParameter("startdatetime", startDate.toString());
        ub.addParameter("enddatetime", endDate.toString());
      }
      if (StringUtils.isNotBlank(deltaTocken)) {
        ub.addParameter("$deltatoken", deltaTocken);
      }

      Builder builder =
          new Request.Builder()
              .addHeader("Accept", "application/json")
              .addHeader("Authorization", accessToken);
      String skipToken = null;
      do {
        try {
          builder = builder.url(new URL(ub.toString()));
          Response response = executeAction(builder);
          if (response.isSuccessful()) {
            JSONObject jsonObject = new JSONObject(response.body().string());
            jsonArray.addAll((JSONArray) jsonObject.getOrDefault("value", new JSONArray()));

            String nextLink = (String) jsonObject.getOrDefault("@odata.nextLink", null);
            String deltaLink = (String) jsonObject.getOrDefault("@odata.deltaLink", null);
            if (StringUtils.isNotBlank(nextLink)) {
              skipToken = StringUtils.substringAfterLast(nextLink, "$skiptoken=");
              ub = new URIBuilder(urlStr);
              ub.addParameter("$skiptoken", skipToken);
            }
            if (StringUtils.isNotBlank(deltaLink)) {
              this.deltaToken = StringUtils.substringAfterLast(deltaLink, "$deltatoken=");
              skipToken = null;
            }
          } else {
            Office365Service.LOG.debug(
                String.format(
                    I18n.get(ITranslation.OFFICE365_OBJECT_FETCH_FAILURE),
                    type,
                    response.code(),
                    response.message(),
                    response.body().string()));
          }
        } catch (Exception e) {
          TraceBackService.trace(e);
        }
      } while (StringUtils.isNotBlank(skipToken));
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    Office365Service.LOG.debug(
        String.format(
            I18n.get(ITranslation.OFFICE365_OBJECT_FETCH_SUCESS), type, jsonArray.size()));
    return jsonArray;
  }

  @Transactional
  public void syncContact(EmailAccount emailAccount) throws AxelorException, MalformedURLException {

    LocalDateTime start = Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime();
    String accessToken = getAccessTocken(emailAccount);
    List<Long> removedContactIdList = new ArrayList<>();
    contactService.syncContactFolders(emailAccount, accessToken, removedContactIdList);

    String queryStr = query;
    QueryBuilder<Partner> partnerQuery = QueryBuilder.of(Partner.class);
    if (emailAccount.getLastContactSyncOn() != null) {
      queryStr = lastSyncQuery;
      partnerQuery = partnerQuery.bind("lastSync", emailAccount.getLastContactSyncOn());
    }
    if (ObjectUtils.notEmpty(removedContactIdList)) {
      queryStr += " AND self.id NOT IN :removedIds";
      partnerQuery.bind("removedIds", removedContactIdList);
    }

    List<Partner> partnerList =
        partnerQuery
            .add(queryStr)
            .add("(self.emailAccount = :emailAccount OR self.user.emailAccount = :emailAccount)")
            .bind("start", start)
            .bind("emailAccount", emailAccount)
            .build()
            .fetch();
    if (ObjectUtils.notEmpty(partnerList)) {
      for (Partner partner : partnerList) {
        contactService.createOffice365Contact(partner, emailAccount, accessToken);
      }
    }

    emailAccount.setLastContactSyncOn(
        Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime());
    emailAccountRepo.save(emailAccount);

    Office365Service.LOG.debug(I18n.get(ITranslation.OFFICE365_CONTACT_SYNC));
  }

  public void syncContacts(
      String accessToken, ContactFolder contactFolder, List<Long> removedContactIdList) {

    JSONArray jsonArray = null;
    if (StringUtils.isNotBlank(contactFolder.getOffice365Id())) {
      this.deltaToken = null;
      jsonArray =
          fetchIncrementalData(
              String.format(Office365Service.FOLDER_CONTACTS_URL, contactFolder.getOffice365Id()),
              accessToken,
              "contacts",
              contactFolder.getContactDeltaToken(),
              null,
              null);
      int totalFetchRecords = jsonArray != null ? jsonArray.size() : 0;
      contactFolder.setContactDeltaToken(deltaToken);
      contactFolder.addTokenListItem(
          manageDeltaToken(
              deltaToken, Office365TokenRepository.FOLDER_TYPE_CONTACT, totalFetchRecords));
    } else {
      jsonArray = fetchData(Office365Service.CONTACT_URL, accessToken, true, null, "contacts");
    }

    if (jsonArray == null) {
      return;
    }

    for (Object object : jsonArray) {
      JSONObject jsonObject = (JSONObject) object;

      if (jsonObject.containsKey("@removed")) {
        contactService.removeContact(jsonObject, removedContactIdList, contactFolder);
      } else {
        contactService.createContact(jsonObject, contactFolder);
      }
    }
  }

  @Transactional
  public void syncCalendar(EmailAccount emailAccount)
      throws AxelorException, MalformedURLException {

    LocalDateTime start = Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime();
    String accessToken = getAccessTocken(emailAccount);
    List<Long> removedEventIdList = new ArrayList<>();
    calendarService.syncOfficeCalendar(emailAccount, accessToken, removedEventIdList, start);
    calendarService.syncCalendar(emailAccount, accessToken, removedEventIdList, start);

    LocalDateTime end = Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime();
    long syncDuration = Duration.between(start, end).toNanos();
    emailAccount.setLastCalendarSyncOn(end);
    emailAccount.setCalendarSyncDuration(syncDuration);
    emailAccountRepo.save(emailAccount);
    Office365Service.LOG.debug(I18n.get(ITranslation.OFFICE365_CALENDAR_SYNC));
  }

  @Transactional
  @Override
  public void syncCalendar(ICalendar calendar) throws AxelorException, MalformedURLException {

    EmailAccount emailAccount = calendar.getEmailAccount();
    if (emailAccount == null) {
      return;
    }

    LocalDateTime start = Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime();
    String accessToken = getAccessTocken(calendar.getEmailAccount());

    List<Long> removedEventIdList = new ArrayList<>();
    if (StringUtils.isNotBlank(calendar.getOffice365Id())) {
      JSONArray calendarArray =
          fetchData(
              Office365Service.CALENDAR_URL + "/" + calendar.getOffice365Id(),
              accessToken,
              false,
              null,
              "calendars");
      if (calendarArray != null) {
        JSONObject calendarJsonObj = (JSONObject) calendarArray.get(0);
        calendar = calendarService.createCalendar(calendarJsonObj, emailAccount);
        syncEvent(calendar, emailAccount, accessToken, start, removedEventIdList);
      }
    } else {
      calendarService.createOffice365Calendar(calendar, emailAccount, accessToken);
    }

    User currentUser = Beans.get(UserService.class).getUser();
    calendarService.syncOffice365Event(
        calendar, emailAccount, currentUser, accessToken, start, removedEventIdList);

    LocalDateTime lastSync = Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime();
    calendar.setLastSynchronizationDateT(lastSync);
    iCalendarRepo.save(calendar);

    emailAccount.setLastCalendarSyncOn(lastSync);
    emailAccountRepo.save(emailAccount);
    Office365Service.LOG.debug(I18n.get(ITranslation.OFFICE365_CALENDAR_SYNC));
  }

  @Override
  public void syncEvent(
      ICalendar iCalendar,
      EmailAccount emailAccount,
      String accessToken,
      LocalDateTime now,
      List<Long> removedEventIdList)
      throws MalformedURLException {

    if (iCalendar == null
        || StringUtils.isBlank(iCalendar.getOffice365Id())
        || (!iCalendar.getIsOfficeEditableCalendar()
            && StringUtils.isNotBlank(iCalendar.getCalendarViewDeltaToken()))) {
      return;
    }

    LocalDateTime lastSyncOn = emailAccount.getLastCalendarSyncOn();
    LocalDateTime startDate = null, endDate = null;
    String calendarViewDeltaToken = iCalendar.getCalendarViewDeltaToken();
    if (StringUtils.isBlank(calendarViewDeltaToken)) {
      if (iCalendar.getSynchronizationDuration() > 0) {
        startDate = now.minusWeeks(iCalendar.getSynchronizationDuration());
        endDate = now.plusWeeks(iCalendar.getSynchronizationDuration());
      } else {
        startDate = now.minusWeeks(Office365CalendarService.DEAFULT_SYNC_DURATION);
        endDate = now.plusWeeks(Office365CalendarService.DEAFULT_SYNC_DURATION);
      }
    }
    String eventUrl = String.format(Office365Service.CALENDAR_VIEW_URL, iCalendar.getOffice365Id());
    this.deltaToken = null;
    JSONArray eventArray =
        fetchIncrementalData(
            eventUrl, accessToken, "events", calendarViewDeltaToken, startDate, endDate);
    int totalFetchRecords = eventArray != null ? eventArray.size() : 0;
    if (StringUtils.isNotBlank(this.deltaToken)) {
      iCalendar.setCalendarViewDeltaToken(this.deltaToken);
      iCalendar.addTokenListItem(
          manageDeltaToken(
              deltaToken, Office365TokenRepository.FOLDER_TYPE_CALENDAR, totalFetchRecords));
    }
    if (eventArray == null) {
      return;
    }

    List<JSONObject> recurrenceObjectList = new ArrayList<>();
    for (Object object : eventArray) {
      JSONObject jsonObject = (JSONObject) object;
      if (jsonObject.containsKey("@removed")) {
        calendarService.removeOfficeEvent(jsonObject, removedEventIdList, iCalendar);
      } else {
        String eventType = processJsonValue("type", jsonObject);
        if (ICalendarEventRepository.EVENT_OCCURRENCE.equals(eventType)) {
          recurrenceObjectList.add(jsonObject);
          continue;
        }
        calendarService.createEvent(jsonObject, emailAccount, iCalendar, lastSyncOn, now);
      }
    }
    for (JSONObject jsonObject : recurrenceObjectList) {
      calendarService.updateReccurrenceEvents(jsonObject);
    }
  }

  @Transactional
  public void syncMail(EmailAccount emailAccount, String urlStr)
      throws AxelorException, MalformedURLException {

    LocalDateTime start = Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime();
    String accessToken = getAccessTocken(emailAccount);
    List<String> emails = getConfiguredModelEmails();
    mailService.syncMailFolder(emailAccount, accessToken, emails);

    if (appOffice365Repo.all().fetchOne().getExportMail()) {
      List<Message> messageList =
          Beans.get(MessageRepository.class)
              .all()
              .filter(
                  "(:emailAccount MEMBER OF self.emailAccountSet OR self.senderUser.emailAccount = :emailAccount) AND "
                      + "self.statusSelect != :statusDraft AND self.mediaTypeSelect = :mediaTypeSelect AND self.createdOn < :start AND "
                      + "self NOT IN (SELECT officeMail.message FROM OfficeMail officeMail WHERE officeMail.mailFolder.emailAccount = :emailAccount)")
              .bind("emailAccount", emailAccount)
              .bind("statusDraft", MessageRepository.STATUS_DRAFT)
              .bind("mediaTypeSelect", MessageRepository.MEDIA_TYPE_EMAIL)
              .bind("start", start)
              .fetch();
      if (ObjectUtils.notEmpty(messageList)) {
        for (Message message : messageList) {
          if (!mailService.isEmailIncluded(message, emails)) {
            continue;
          }
          mailService.createOffice365Mail(message, emailAccount, accessToken);
        }
      }
    }

    emailAccount.setLastMailSyncOn(
        Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime());
    emailAccountRepo.save(emailAccount);

    Office365Service.LOG.debug(I18n.get(ITranslation.OFFICE365_MAIL_SYNC));
  }

  public void syncMails(
      EmailAccount emailAccount,
      String accessToken,
      String parentFolderId,
      MailFolder mailFolder,
      List<String> emails) {

    JSONArray jsonArray =
        fetchIncrementalData(
            String.format(Office365Service.FOLDER_MAILS_URL, parentFolderId),
            accessToken,
            "mails",
            mailFolder.getMessageDeltaToken(),
            null,
            null);
    int totalFetchRecords = jsonArray != null ? jsonArray.size() : 0;
    mailFolder.setMessageDeltaToken(deltaToken);
    mailFolder.addTokenListItem(
        manageDeltaToken(deltaToken, Office365TokenRepository.FOLDER_TYPE_MAIL, totalFetchRecords));

    if (jsonArray == null) {
      return;
    }

    for (Object object : jsonArray) {
      JSONObject jsonObject = (JSONObject) object;

      if (jsonObject.containsKey("@removed")) {
        continue;
      } else {
        mailService.createMessage(jsonObject, emailAccount, mailFolder, accessToken, emails);
      }
    }
  }

  protected Office365Token manageDeltaToken(
      String token, int folderTypeSelect, int totalFetchRecords) {

    Office365Token office365Token = new Office365Token();
    office365Token.setToken(token);
    office365Token.setFolderTypeSelect(folderTypeSelect);
    office365Token.setTotalFetchRecords(totalFetchRecords);
    office365Token.setFetchOn(Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime());
    return Beans.get(Office365TokenRepository.class).save(office365Token);
  }

  @Override
  public void syncUserMail(EmailAddress emailAddress, List<String> emailIds) {

    if (emailAddress == null) {
      return;
    }
    User user = userRepo.findByEmail(emailAddress.getAddress());
    if (user == null) {
      return;
    }
    EmailAccount emailAccount = user.getEmailAccount();

    if (ObjectUtils.notEmpty(emailIds)) {
      for (String emailId : emailIds) {
        try {
          this.createUserMail(
              emailAccount,
              String.format(Office365Service.MAIL_ID_URL, emailAddress.getAddress(), emailId));
        } catch (MalformedURLException | AxelorException e) {
          TraceBackService.trace(e);
        }
      }
      return;
    }

    try {

      if (emailAccount != null) {
        Beans.get(Office365Service.class)
            .syncMail(
                emailAccount,
                String.format(Office365Service.MAIL_USER_URL, emailAddress.getAddress()));
      }
    } catch (MalformedURLException | AxelorException e) {
      TraceBackService.trace(e);
    }
  }

  protected void createUserMail(EmailAccount emailAccount, String urlStr)
      throws AxelorException, MalformedURLException {

    if (emailAccount == null) {
      return;
    }

    List<String> emails = getConfiguredModelEmails();
    String accessToken = getAccessTocken(emailAccount);
    JSONArray jsonArr = fetchData(urlStr, accessToken, false, null, "user");
    if (jsonArr.isEmpty()) {
      return;
    }
    mailService.createMessage((JSONObject) jsonArr.get(0), emailAccount, null, accessToken, emails);
  }

  protected List<String> getConfiguredModelEmails() {

    List<ModelEmailLink> emailLinkList = appOffice365Repo.all().fetchOne().getEmailLinkList();
    if (ObjectUtils.isEmpty(emailLinkList)) {
      return null;
    }

    List<String> emails = new ArrayList<>();
    for (ModelEmailLink modelEmailLink : emailLinkList) {
      try {
        String field = modelEmailLink.getEmailField();
        String query =
            String.format("SELECT %s FROM %s", field, modelEmailLink.getMetaModel().getName());
        emails.addAll(JPA.em().createQuery(query, String.class).getResultList());
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }

    return emails;
  }

  @Transactional
  public Message sendOffice365Mail(Message message) {

    try {
      String mailFolderName = I18n.get(ITranslation.OFFICE_MAIL_FOLDER_DRAFTS);
      MailFolder mailFolder =
          Beans.get(MailFolderRepository.class)
              .findByName(mailFolderName, message.getMailAccount());
      if (mailFolder == null) {
        mailFolder = new MailFolder();
        mailFolder.setName(mailFolderName);
        mailFolder.setEmailAccount(message.getMailAccount());
        Beans.get(MailFolderRepository.class).save(mailFolder);
      }

      String accessToken = getAccessTocken(message.getMailAccount());
      String office365Id =
          mailService.createOffice365Mail(message, message.getMailAccount(), accessToken);
      if (StringUtils.isBlank(office365Id)) {
        return message;
      }

      URL url = new URL(String.format(Office365Service.MAIL_SEND_URL, office365Id));
      RequestBody body = RequestBody.create("", MediaType.parse("application/json; charset=utf-8"));
      Builder builder =
          new Request.Builder().url(url).addHeader("Authorization", accessToken).post(body);
      Response response = executeAction(builder);
      if (response.isSuccessful()) {
        manageSentMessage(message, accessToken, office365Id);
      } else {
        TraceBackService.trace(
            new AxelorException(
                TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
                String.format(
                    I18n.get(ITranslation.OFFICE365_MAIL_SEND_FAILURE),
                    message,
                    response.code(),
                    response.message(),
                    response.body().string())));
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    return message;
  }

  protected void manageSentMessage(Message message, String accessToken, String office365Id) {

    try {
      message.setStatusSelect(MessageRepository.STATUS_SENT);
      Office365Service.LOG.debug(
          String.format(I18n.get(ITranslation.OFFICE365_MAIL_SEND_SUCESS), message));

      String mailFolderName = I18n.get(ITranslation.OFFICE_MAIL_FOLDER_SENT_ITEMS);
      String urlStr;
      MailFolder mailFolder =
          Beans.get(MailFolderRepository.class)
              .findByName(mailFolderName, message.getMailAccount());
      if (mailFolder != null && StringUtils.isNotBlank(mailFolder.getOffice365Id())) {
        urlStr =
            String.format(
                "%s/%s/messages/%s",
                Office365Service.MAIL_FOLDER_URL, mailFolder.getOffice365Id(), office365Id);
      } else {
        urlStr = String.format("%s/%s", Office365Service.MAIL_URL, office365Id);
      }

      JSONArray jsonArr = fetchData(urlStr, accessToken, false, null, "mails");
      if (!jsonArr.isEmpty()) {
        JSONObject jsonObject = (JSONObject) jsonArr.get(0);
        String messageId = processJsonValue("internetMessageId", jsonObject);
        message.setMessageId(messageId);
      }
      Beans.get(MessageRepository.class).save(message);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }
}
