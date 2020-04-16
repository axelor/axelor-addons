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
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.drive.Drive;
import com.google.api.services.gmail.Gmail;
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

  private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String[] SCOPES =
      new String[] {
        "https://www.google.com/m8/feeds/",
        "https://www.googleapis.com/auth/drive",
        "https://www.googleapis.com/auth/calendar",
        "https://mail.google.com/"
      };

  private HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
  private JacksonFactory JSON_FACTORY = new JacksonFactory();
  private final FileDataStoreFactory DATA_STORE_FACTORY;

  @Inject private GoogleAccountRepository googleAccountRepo;
  @Inject private AppGSuiteService appGSuiteService;

  private GoogleAuthorizationCodeFlow flow = null;

  private static final String APP_NAME = AppSettings.get().get("application.name", "Axelor gsuite");

  public GSuiteService() throws IOException {
    DATA_STORE_FACTORY =
        new FileDataStoreFactory(new File(AppSettings.get().get("file.upload.dir")));
  }

  public String getAuthenticationUrl(Long accountId) throws AxelorException, IOException {

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

  public GoogleAuthorizationCodeFlow getFlow() throws IOException, AxelorException {

    if (flow == null) {
      AppGsuite appGsuite = appGSuiteService.getAppGSuite();

      if (ObjectUtils.isEmpty(appGsuite.getClientId())
          || ObjectUtils.isEmpty(appGsuite.getClientSecret())) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            IExceptionMessage.NO_CONFIGURATION_EXCEPTION);
      }

      flow =
          new GoogleAuthorizationCodeFlow.Builder(
                  HTTP_TRANSPORT,
                  JSON_FACTORY,
                  appGsuite.getClientId(),
                  appGsuite.getClientSecret(),
                  Arrays.asList(SCOPES))
              .setDataStoreFactory(DATA_STORE_FACTORY)
              .build();
    }

    return flow;
  }

  @Transactional
  public void setGoogleCredential(Long accountId, String code) throws IOException, AxelorException {

    String redirectUrl = AppSettings.get().getBaseURL() + "/ws/gsuite-sync-code";

    log.debug("Redirect url: {}", redirectUrl);

    GoogleAuthorizationCodeFlow flow = getFlow();

    TokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectUrl).execute();

    flow.createAndStoreCredential(response, accountId.toString());

    GoogleAccount account = googleAccountRepo.find(accountId);
    account.setIsAuthorized(true);

    googleAccountRepo.save(account);
  }

  public Credential getCredential(Long accountId) throws IOException, AxelorException {

    GoogleAuthorizationCodeFlow flow = getFlow();

    Credential credential = flow.loadCredential(accountId.toString());

    return credential;
  }

  public Credential refreshToken(Credential credential) throws AxelorException, IOException {

    log.debug("Refresh token: {}", credential.getAccessToken());

    if (!credential.refreshToken()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, IExceptionMessage.AUTH_EXCEPTION_2);
    }

    log.debug("Token refreshed: {}", credential.getAccessToken());

    return credential;
  }

  public Drive getDrive(Long accountId) throws IOException, AxelorException {

    Credential credential = getCredential(accountId);

    return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName(APP_NAME)
        .build();
  }

  public Calendar getCalendar(Credential credential) {

    return new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName(APP_NAME)
        .build();
  }

  public Gmail getGmail(Long accountId) throws IOException, AxelorException {
    Credential credential = getCredential(accountId);
    return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName(APP_NAME)
        .build();
  }
}
