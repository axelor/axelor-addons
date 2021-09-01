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
package com.axelor.apps.gsuite.service;

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.AppGsuite;
import com.axelor.apps.gsuite.exception.IExceptionMessage;
import com.axelor.apps.gsuite.service.app.AppGSuiteService;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.PeopleServiceScopes;
import com.google.api.services.tasks.Tasks;
import com.google.api.services.tasks.TasksScopes;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class GSuiteService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String[] SCOPES =
      new String[] {
        DriveScopes.DRIVE,
        CalendarScopes.CALENDAR,
        GmailScopes.MAIL_GOOGLE_COM,
        TasksScopes.TASKS,
        PeopleServiceScopes.CONTACTS
      };

  private static final JacksonFactory JSON_FACTORY = new JacksonFactory();
  private final FileDataStoreFactory dataStoreFactory;

  protected EmailAccountRepository emailAccountRepo;
  protected AppGSuiteService appGSuiteService;

  private GoogleAuthorizationCodeFlow flow = null;

  private static final String APP_NAME = AppSettings.get().get("application.name", "Axelor gsuite");

  @Inject
  public GSuiteService(EmailAccountRepository googleAccountRepo, AppGSuiteService appGSuiteService)
      throws AxelorException {
    try {
      this.emailAccountRepo = googleAccountRepo;
      this.appGSuiteService = appGSuiteService;
      dataStoreFactory =
          new FileDataStoreFactory(new File(AppSettings.get().get("file.upload.dir")));
    } catch (IOException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
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
                    getHttpTransport(),
                    JSON_FACTORY,
                    appGsuite.getClientId(),
                    appGsuite.getClientSecret(),
                    Arrays.asList(SCOPES))
                .setDataStoreFactory(dataStoreFactory)
                .build();
      } catch (IOException e) {
        throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
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
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }

    EmailAccount account = emailAccountRepo.find(accountId);
    account.setIsValid(true);

    emailAccountRepo.save(account);
  }

  public Credential getCredential(Long accountId) throws AxelorException {

    GoogleAuthorizationCodeFlow flow = getFlow();

    Credential credential;
    try {
      credential = flow.loadCredential(accountId.toString());
    } catch (IOException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
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
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }

    return credential;
  }

  public Drive getDrive(Long accountId) throws AxelorException {

    Credential credential = getCredential(accountId);

    return new Drive.Builder(getHttpTransport(), JSON_FACTORY, credential)
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

  public Calendar getCalendar(Credential credential) throws AxelorException {

    return new Calendar.Builder(getHttpTransport(), JSON_FACTORY, credential)
        .setApplicationName(APP_NAME)
        .build();
  }

  public PeopleService getPeople(Long accountId) throws AxelorException {
    Credential credential = getCredential(accountId);
    if (credential == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          String.format(
              I18n.get(IExceptionMessage.AUTH_EXCEPTION_1),
              emailAccountRepo.find(accountId).getName()));
    }

    return new PeopleService.Builder(getHttpTransport(), JSON_FACTORY, credential)
        .setApplicationName(APP_NAME)
        .build();
  }

  public Gmail getGmail(Long accountId) throws AxelorException {
    Credential credential = getCredential(accountId);
    if (credential == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          String.format(
              I18n.get(IExceptionMessage.AUTH_EXCEPTION_1),
              emailAccountRepo.find(accountId).getName()));
    }
    return new Gmail.Builder(getHttpTransport(), JSON_FACTORY, credential)
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
              emailAccountRepo.find(accountId).getName()));
    }
    return new Tasks.Builder(getHttpTransport(), JSON_FACTORY, credential)
        .setApplicationName(APP_NAME)
        .build();
  }

  private NetHttpTransport getHttpTransport() throws AxelorException {
    try {
      return GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }
  }
}
