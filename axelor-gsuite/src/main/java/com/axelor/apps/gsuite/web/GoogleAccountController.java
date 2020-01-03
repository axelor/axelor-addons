/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.gsuite.web;

import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.service.GoogleAbsContactService;
import com.axelor.apps.gsuite.service.GoogleAbsDriveService;
import com.axelor.apps.gsuite.service.GoogleAbsEventService;
import com.axelor.apps.gsuite.service.GoogleContactService;
import com.axelor.apps.gsuite.service.GoogleDriveService;
import com.axelor.apps.gsuite.service.GoogleEventService;
import com.axelor.apps.gsuite.service.GoogleService;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import java.io.IOException;

public class GoogleAccountController {

  @Inject private GoogleService googleService;

  @Inject private GoogleContactService googleContactService;

  @Inject private GoogleEventService googleEventService;

  @Inject private GoogleDriveService googleDriveService;

  @Inject private GoogleAccountRepository googleAccountRepo;

  @Inject private GoogleAbsContactService googleAbsContactService;

  @Inject private GoogleAbsEventService googleAbsEventService;

  @Inject private GoogleAbsDriveService googleAbsDriveService;

  public void syncContacts(ActionRequest request, ActionResponse response) throws AxelorException {

    GoogleAccount account = request.getContext().asType(GoogleAccount.class);
    account = googleAccountRepo.find(account.getId());
    account.setContactSyncFromGoogleLog(null);

    account = googleContactService.update(account);
    if (Strings.isNullOrEmpty(account.getContactSyncToGoogleLog())) {
      response.setFlash(I18n.get("Contacts synchronized successfully."));
    } else {
      response.setFlash(I18n.get("Error in synchronization."));
      response.setAttr("contactSyncToGoogleLog", "hidden", false);
    }
    response.setReload(true);
  }

  public void syncEvents(ActionRequest request, ActionResponse response) throws AxelorException {

    GoogleAccount account = request.getContext().asType(GoogleAccount.class);
    account = googleAccountRepo.find(account.getId());
    account = googleEventService.sync(account);
    if (Strings.isNullOrEmpty(account.getEventSyncToGoogleLog())) {
      response.setFlash(I18n.get("Events synchronized successfully."));
    } else {
      response.setFlash(I18n.get("Error in synchronization."));
    }
    response.setReload(true);
  }

  public void syncDocs(ActionRequest request, ActionResponse response) throws AxelorException {

    GoogleAccount account = request.getContext().asType(GoogleAccount.class);
    account = googleAccountRepo.find(account.getId());
    account = googleDriveService.sync(account);
    if (Strings.isNullOrEmpty(account.getDocSyncToGoogleLog())) {
      response.setFlash(I18n.get("Documents synchronized successfully."));
    } else {
      response.setFlash(I18n.get("Error in synchronization."));
    }
    response.setReload(true);
  }

  public void getAuthUrl(ActionRequest request, ActionResponse response)
      throws AxelorException, IOException {

    GoogleAccount account = request.getContext().asType(GoogleAccount.class);
    String authUrl = googleService.getAuthenticationUrl(account.getId());
    authUrl = "<a href='" + authUrl + "'>Google authentication url</>";
    response.setAttr("authUrl", "title", authUrl);
  }

  public void syncGoogleContacts(ActionRequest request, ActionResponse response)
      throws AxelorException {
    GoogleAccount account = request.getContext().asType(GoogleAccount.class);
    account = googleAccountRepo.find(account.getId());
    account = googleAbsContactService.sync(account);
    if (Strings.isNullOrEmpty(account.getContactSyncFromGoogleLog())) {
      response.setFlash(I18n.get("Contacts synchronized successfully."));
    } else {
      response.setFlash(I18n.get("Error in synchronization."));
      response.setAttr("contactSyncFromGoogleLog", "hidden", false);
    }
    response.setReload(true);
  }

  public void syncGoogleEvents(ActionRequest request, ActionResponse response)
      throws AxelorException {

    GoogleAccount account = request.getContext().asType(GoogleAccount.class);
    account = googleAccountRepo.find(account.getId());
    account = googleAbsEventService.sync(account);
    if (Strings.isNullOrEmpty(account.getEventSyncFromGoogleLog())) {
      response.setFlash(I18n.get("Events from google synchronized successfully."));
    } else {
      response.setFlash(I18n.get("Error in synchronization."));
    }
    response.setReload(true);
  }

  public void syncGoogleDocs(ActionRequest request, ActionResponse response)
      throws AxelorException {

    GoogleAccount account = request.getContext().asType(GoogleAccount.class);
    account = googleAccountRepo.find(account.getId());
    account = googleAbsDriveService.sync(account);
    if (Strings.isNullOrEmpty(account.getDocSyncFromGoogleLog())) {
      response.setFlash(I18n.get("Docs from google synchronized successfully."));
    } else {
      response.setFlash(I18n.get("Error in synchronization."));
    }
    response.setReload(true);
  }
}
