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
import com.axelor.apps.base.db.repo.AppOffice365Repository;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.office365.translation.ITranslation;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.common.io.ByteSource;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import wslite.http.HTTPClient;
import wslite.http.HTTPMethod;
import wslite.http.HTTPRequest;
import wslite.http.HTTPResponse;
import wslite.json.JSONArray;
import wslite.json.JSONObject;

public class Office365ServiceImpl implements Office365Service {

  @Inject Office365ContactService contactService;
  @Inject Office365CalendarService calendarService;
  @Inject Office365MailService mailService;

  @Inject private DMSFileRepository dmsFileRepo;

  @Transactional
  public String getAccessTocken(AppOffice365 appOffice365) throws AxelorException {

    try {
      OAuth20Service authService =
          new ServiceBuilder(appOffice365.getClientId())
              .apiSecret(appOffice365.getClientSecret())
              .callback(appOffice365.getRedirectUri())
              .defaultScope(Office365Service.SCOPE)
              .build(MicrosoftAzureActiveDirectory20Api.instance());
      OAuth2AccessToken accessToken;
      if (StringUtils.isBlank(appOffice365.getRefreshToken())) {
        throw new AxelorException(
            AppOffice365.class,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(ITranslation.OFFICE365_TOKEN_ERROR));
      }
      accessToken = authService.refreshAccessToken(appOffice365.getRefreshToken());
      appOffice365.setRefreshToken(accessToken.getRefreshToken());
      Beans.get(AppOffice365Repository.class).save(appOffice365);
      return accessToken.getTokenType() + " " + accessToken.getAccessToken();
    } catch (Exception e) {
      throw new AxelorException(
          AppOffice365.class, TraceBackRepository.CATEGORY_INCONSISTENCY, e.getMessage());
    }
  }

  private JSONObject fetchData(URL url, String accessToken) {

    JSONObject jsonObject = null;
    try {
      HTTPResponse response;
      HTTPClient httpclient = new HTTPClient();
      HTTPRequest request = new HTTPRequest();
      Map<String, Object> headers = new HashMap<>();
      headers.put("Accept", "application/json");
      headers.put("Authorization", accessToken);
      request.setHeaders(headers);
      request.setUrl(url);
      request.setMethod(HTTPMethod.GET);
      response = httpclient.execute(request);
      if (response.getStatusCode() == 200) {
        jsonObject = new JSONObject(response.getContentAsString());
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    return jsonObject;
  }

  public void syncContact(AppOffice365 appOffice365) throws AxelorException, MalformedURLException {

    String accessToken = getAccessTocken(appOffice365);
    URL url = new URL(Office365Service.CONTACT_URL);
    JSONObject jsonObject = fetchData(url, accessToken);
    @SuppressWarnings("unchecked")
    JSONArray jsonArray = (JSONArray) jsonObject.getOrDefault("value", new ArrayList<>());
    for (Object object : jsonArray) {
      jsonObject = (JSONObject) object;
      contactService.createContact(jsonObject);
    }
  }

  @SuppressWarnings("unchecked")
  public void syncCalendar(AppOffice365 appOffice365)
      throws AxelorException, MalformedURLException {

    String accessToken = getAccessTocken(appOffice365);
    URL url = new URL(Office365Service.CALENDAR_URL);
    JSONObject jsonObject = fetchData(url, accessToken);
    JSONArray calendarArray = (JSONArray) jsonObject.getOrDefault("value", new ArrayList<>());
    if (calendarArray != null) {
      for (Object object : calendarArray) {
        jsonObject = (JSONObject) object;
        ICalendar iCalendar = calendarService.createCalendar(jsonObject);
        syncEvent(iCalendar, accessToken);
      }
    }
  }

  private void syncEvent(ICalendar iCalendar, String accessToken) throws MalformedURLException {

    if (iCalendar == null || StringUtils.isBlank(iCalendar.getOffice365Id())) {
      return;
    }

    String eventUrl = String.format(Office365Service.EVENT_URL, iCalendar.getOffice365Id());
    URL url = new URL(eventUrl);

    JSONObject jsonObject = fetchData(url, accessToken);
    @SuppressWarnings("unchecked")
    JSONArray eventArray = (JSONArray) jsonObject.getOrDefault("value", null);
    if (eventArray != null) {
      for (Object object : eventArray) {
        jsonObject = (JSONObject) object;
        calendarService.createEvent(jsonObject, iCalendar);
      }
    }
  }

  @SuppressWarnings("unchecked")
  public void syncMail(AppOffice365 appOffice365, String urlStr)
      throws AxelorException, MalformedURLException {

    String accessToken = getAccessTocken(appOffice365);
    URL url = new URL(urlStr);
    JSONObject jsonObject = fetchData(url, accessToken);
    JSONArray messageArray = (JSONArray) jsonObject.getOrDefault("value", new ArrayList<>());
    if (messageArray != null) {
      for (Object object : messageArray) {
        jsonObject = (JSONObject) object;
        mailService.createMessage(jsonObject);
      }
    }
  }

  @Override
  public void syncUserMail(EmailAddress emailAddress, List<String> emailIds) {

    AppOffice365 appOffice365 = Beans.get(AppOffice365Repository.class).all().fetchOne();
    if (CollectionUtils.isNotEmpty(emailIds)) {
      for (String emailId : emailIds) {
        try {
          this.createUserMail(
              appOffice365,
              String.format(Office365Service.MAIL_ID_URL, emailAddress.getAddress(), emailId));
        } catch (MalformedURLException | AxelorException e) {
          TraceBackService.trace(e);
        }
      }
      return;
    }

    try {
      Beans.get(Office365Service.class)
          .syncMail(
              appOffice365,
              String.format(Office365Service.MAIL_USER_URL, emailAddress.getAddress()));
    } catch (MalformedURLException | AxelorException e) {
      TraceBackService.trace(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public void manageAttachment(Message message, Map<String, Object> mailObj) {

    List<Map<String, Object>> attachmentList =
        (List<Map<String, Object>>) mailObj.get("attachments");
    if (ObjectUtils.isEmpty(attachmentList)) {
      return;
    }

    try {
      AppOffice365 appOffice365 = Beans.get(AppOffice365Repository.class).all().fetchOne();
      String accessToken = getAccessTocken(appOffice365);

      for (Map<String, Object> attachment : attachmentList) {
        try {
          String attachmentId = (String) attachment.get("id");
          URL url =
              new URL(
                  String.format(
                      Office365Service.MAIL_ATTACHMENT_URL + "/%s",
                      message.getOffice365Id(),
                      attachmentId));
          JSONObject attachmentJsonObject = fetchData(url, accessToken);
          if (attachmentJsonObject == null) {
            continue;
          }

          String contentType = (String) attachmentJsonObject.getOrDefault("contentType", "");
          if ("application/octet-stream".equals(contentType)) {
            String office365Id = (String) attachmentJsonObject.getOrDefault("id", "");
            String name = (String) attachmentJsonObject.getOrDefault("name", "");
            String contentBytes = (String) attachmentJsonObject.getOrDefault("contentBytes", "");
            byte[] bytes = Base64.getDecoder().decode(contentBytes);
            DMSFile dmsFile =
                dmsFileRepo
                    .all()
                    .filter("self.office365Id = :office365Id")
                    .bind("office365Id", office365Id)
                    .fetchOne();

            if (dmsFile != null) {
              File file = new File(name);
              FileUtils.writeByteArrayToFile(file, bytes);
              dmsFile.setMetaFile(Beans.get(MetaFiles.class).upload(file));
            } else {
              InputStream is = ByteSource.wrap(bytes).openStream();
              dmsFile = Beans.get(MetaFiles.class).attach(is, name, message);
              dmsFile.setOffice365Id(office365Id);
            }
          }
        } catch (Exception e) {
          TraceBackService.trace(e);
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  private void createUserMail(AppOffice365 appOffice365, String urlStr)
      throws AxelorException, MalformedURLException {

    String accessToken = getAccessTocken(appOffice365);
    URL url = new URL(urlStr);
    JSONObject jsonObject = fetchData(url, accessToken);
    mailService.createMessage(jsonObject);
  }
}
