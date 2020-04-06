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
import com.axelor.apps.base.db.repo.AppGsuiteRepository;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.exception.IExceptionMessage;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
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
import java.io.FileReader;
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

  private GoogleAuthorizationCodeFlow flow = null;

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
      flow =
          new GoogleAuthorizationCodeFlow.Builder(
                  HTTP_TRANSPORT, JSON_FACTORY, getClientSecrets(), Arrays.asList(SCOPES))
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

  @SuppressWarnings("deprecation")
  public Credential refreshToken(Credential credential) throws AxelorException, IOException {

    log.debug("Refresh token: {}", credential.getAccessToken());

    if (!credential.refreshToken()) {
      throw new AxelorException(I18n.get(IExceptionMessage.AUTH_EXCEPTION_2), 4);
    }

    log.debug("Token refreshed: {}", credential.getAccessToken());

    return credential;
  }

  @SuppressWarnings("deprecation")
  public GoogleClientSecrets getClientSecrets() throws AxelorException, IOException {

    AppGsuite appGsuite = Beans.get(AppGsuiteRepository.class).all().fetchOne();
    MetaFile googleConfigFile = appGsuite.getClientSecretFile();
    if (googleConfigFile == null) {
      throw new AxelorException(I18n.get(IExceptionMessage.NO_CONFIGURATION_EXCEPTION), 4);
    }

    File clientSecretFile = MetaFiles.getPath(googleConfigFile).toFile();

    if (clientSecretFile == null || !clientSecretFile.exists()) {
      throw new AxelorException(I18n.get(IExceptionMessage.CONFIGURATION_FILE_EXCEPTION), 4);
    }

    FileReader reader = new FileReader(clientSecretFile);

    return GoogleClientSecrets.load(new JacksonFactory(), reader);
  }

  public Drive getDrive(Long accountId) throws IOException, AxelorException {

    Credential credential = getCredential(accountId);

    return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName("ABS Drive")
        .build();
  }

  public Calendar getCalendar(Credential credential) {

    return new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName("ABS calendar")
        .build();
  }

  public Gmail getGmail(Long accountId) throws IOException, AxelorException {
    Credential credential = getCredential(accountId);
    final String GMAIL_APP_NAME = "AOS Gsuite Gmail";
    return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName(GMAIL_APP_NAME)
        .build();
  }
}
