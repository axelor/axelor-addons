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

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.AppGsuite;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.exception.IExceptionMessage;
import com.axelor.apps.gsuite.service.app.AppGSuiteService;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.tasks.Tasks;
import com.google.api.services.tasks.TasksScopes;
import com.google.gdata.client.contacts.ContactsService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class GSuiteService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String[] SCOPES =
      new String[] {
        "https://www.google.com/m8/feeds/",
        DriveScopes.DRIVE,
        CalendarScopes.CALENDAR,
        GmailScopes.MAIL_GOOGLE_COM,
        TasksScopes.TASKS_READONLY
      };

  private HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
  private JacksonFactory JSON_FACTORY = new JacksonFactory();
  private final FileDataStoreFactory DATA_STORE_FACTORY;

  protected GoogleAccountRepository googleAccountRepo;
  protected AppGSuiteService appGSuiteService;

  private GoogleAuthorizationCodeFlow flow = null;

  private static final String APP_NAME = AppSettings.get().get("application.name", "Axelor gsuite");

  @Inject
  public GSuiteService(GoogleAccountRepository googleAccountRepo, AppGSuiteService appGSuiteService)
      throws AxelorException {
    try {
      this.googleAccountRepo = googleAccountRepo;
      this.appGSuiteService = appGSuiteService;
      DATA_STORE_FACTORY =
          new FileDataStoreFactory(new File(AppSettings.get().get("file.upload.dir")));
    } catch (IOException e) {
      throw new AxelorException(e.getCause(), TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }
  }

  public String getAuthenticationUrl(Long accountId) throws AxelorException {

    String redirectUrl = AppSettings.get().getBaseURL() + "/ws/gsuite-sync-code";

    AuthorizationCodeRequestUrl authorizationUrl =
        getFlow()
            .newAuthorizationUrl()
            .setRedirectUri(redirectUrl)
            .setApprovalPrompt("force")
            .setState(accountId.toString())
            .setAccessType("offline");

    return authorizationUrl.build();
  }

  public GoogleAuthorizationCodeFlow getFlow() throws AxelorException {

    if (flow == null) {
      AppGsuite appGsuite = appGSuiteService.getAppGSuite();

      if (ObjectUtils.isEmpty(appGsuite.getClientId())
          || ObjectUtils.isEmpty(appGsuite.getClientSecret())) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            IExceptionMessage.NO_CONFIGURATION_EXCEPTION);
      }

      try {
        flow =
            new GoogleAuthorizationCodeFlow.Builder(
                    HTTP_TRANSPORT,
                    JSON_FACTORY,
                    appGsuite.getClientId(),
                    appGsuite.getClientSecret(),
                    Arrays.asList(SCOPES))
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .build();
      } catch (IOException e) {
        throw new AxelorException(e.getCause(), TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
      }
    }

    return flow;
  }

  @Transactional
  public void setGoogleCredential(Long accountId, String code) throws AxelorException {

    String redirectUrl = AppSettings.get().getBaseURL() + "/ws/gsuite-sync-code";

    LOG.debug("Redirect url: {}", redirectUrl);

    GoogleAuthorizationCodeFlow flow = getFlow();

    TokenResponse response;
    try {
      response = flow.newTokenRequest(code).setRedirectUri(redirectUrl).execute();
      flow.createAndStoreCredential(response, accountId.toString());
    } catch (IOException e) {
      throw new AxelorException(e.getCause(), TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }

    GoogleAccount account = googleAccountRepo.find(accountId);
    account.setIsAuthorized(true);

    googleAccountRepo.save(account);
  }

  public Credential getCredential(Long accountId) throws AxelorException {

    GoogleAuthorizationCodeFlow flow = getFlow();

    Credential credential;
    try {
      credential = flow.loadCredential(accountId.toString());
    } catch (IOException e) {
      throw new AxelorException(e.getCause(), TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }

    return credential;
  }

  public Credential refreshToken(Credential credential) throws AxelorException {

    try {

      if (!credential.refreshToken()) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, IExceptionMessage.AUTH_EXCEPTION_2);
      }

      LOG.debug("Token refreshed");

    } catch (IOException e) {
      throw new AxelorException(e.getCause(), TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }

    return credential;
  }

  public Drive getDrive(Long accountId) throws AxelorException {

    Credential credential = getCredential(accountId);

    return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setHttpRequestInitializer(
            new HttpRequestInitializer() {
              @Override
              public void initialize(HttpRequest request) throws IOException {
                credential.initialize(request);
                request.setConnectTimeout(300 * 60000); // 300 minutes connect timeout
                request.setReadTimeout(300 * 60000); // 300 minutes read timeout
              }
            })
        .setApplicationName(APP_NAME)
        .build();
  }

  public Calendar getCalendar(Credential credential) {

    return new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName(APP_NAME)
        .build();
  }

  public ContactsService getContact(Long accountId) throws AxelorException {
    Credential credential = getCredential(accountId);
    if (credential == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          String.format(
              I18n.get(IExceptionMessage.AUTH_EXCEPTION_1),
              googleAccountRepo.find(accountId).getName()));
    }
    return getContact(credential);
  }

  public ContactsService getContact(Credential credential) {
    ContactsService contactsService = new ContactsService(ContactsService.CONTACTS_SERVICE);
    contactsService.setOAuth2Credentials(credential);
    return contactsService;
  }

  public Gmail getGmail(Long accountId) throws AxelorException {
    Credential credential = getCredential(accountId);
    if (credential == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          String.format(
              I18n.get(IExceptionMessage.AUTH_EXCEPTION_1),
              googleAccountRepo.find(accountId).getName()));
    }
    return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName(APP_NAME)
        .build();
  }

  public Tasks getTask(Long accountId) throws AxelorException {
    Credential credential = getCredential(accountId);
    if (credential == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          String.format(
              I18n.get(IExceptionMessage.AUTH_EXCEPTION_1),
              googleAccountRepo.find(accountId).getName()));
    }
    return new Tasks.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName(APP_NAME)
        .build();
  }
}
