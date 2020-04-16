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
package com.axelor.apps.gsuite.web;

import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.exception.IExceptionMessage;
import com.axelor.apps.gsuite.service.GSuiteAOSContactService;
import com.axelor.apps.gsuite.service.GSuiteAOSDriveService;
import com.axelor.apps.gsuite.service.GSuiteAOSEventService;
import com.axelor.apps.gsuite.service.GSuiteAOSMessageService;
import com.axelor.apps.gsuite.service.GSuiteContactService;
import com.axelor.apps.gsuite.service.GSuiteDriveService;
import com.axelor.apps.gsuite.service.GSuiteEventService;
import com.axelor.apps.gsuite.service.GSuiteService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class GoogleAccountController {

  @Inject private GSuiteService gSuiteService;

  @Inject private GSuiteContactService gSuiteContactService;

  @Inject private GSuiteEventService gSuiteEventService;

  @Inject private GSuiteDriveService gSuiteDriveService;

  @Inject private GoogleAccountRepository googleAccountRepo;

  @Inject private GSuiteAOSContactService gSuiteAOSContactService;

  @Inject private GSuiteAOSEventService gSuiteAOSEventService;

  @Inject private GSuiteAOSDriveService gSuiteAOSDriveService;

  @Inject private GSuiteAOSMessageService gSuiteAOSMessageService;

  public void getAuthUrl(ActionRequest request, ActionResponse response)
      throws AxelorException, IOException {
    GoogleAccount account = request.getContext().asType(GoogleAccount.class);

    account = googleAccountRepo.find(account.getId());
    String authUrl = gSuiteService.getAuthenticationUrl(account.getId());
    response.setAttr(
        "authUrl",
        "title",
        String.format(
            "<a href='%s'>Google authentication url (click to authenticate account)</a>", authUrl));
  }

  public void syncContacts(ActionRequest request, ActionResponse response) throws AxelorException {

    GoogleAccount account = request.getContext().asType(GoogleAccount.class);
    account = googleAccountRepo.find(account.getId());
    account.setContactSyncFromGoogleLog(null);

    account = gSuiteContactService.update(account);
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
    account = gSuiteEventService.sync(account);
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
    account = gSuiteDriveService.sync(account);
    if (Strings.isNullOrEmpty(account.getDocSyncToGoogleLog())) {
      response.setFlash(I18n.get("Documents synchronized successfully."));
    } else {
      response.setFlash(I18n.get("Error in synchronization."));
    }
    response.setReload(true);
  }

  public void syncGoogleContacts(ActionRequest request, ActionResponse response)
      throws AxelorException {
    GoogleAccount account = request.getContext().asType(GoogleAccount.class);
    account = googleAccountRepo.find(account.getId());
    account = gSuiteAOSContactService.sync(account);
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
    account = gSuiteAOSEventService.sync(account);
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
    account = gSuiteAOSDriveService.sync(account);
    if (Strings.isNullOrEmpty(account.getDocSyncFromGoogleLog())) {
      response.setFlash(I18n.get("Docs from google synchronized successfully."));
    } else {
      response.setFlash(I18n.get("Error in synchronization."));
    }
    response.setReload(true);
  }

  public void syncGoogleMails(ActionRequest request, ActionResponse response) {
    GoogleAccount account = request.getContext().asType(GoogleAccount.class);
    account = googleAccountRepo.find(account.getId());
    try {
      account = gSuiteAOSMessageService.sync(account);
      response.setFlash(I18n.get(IExceptionMessage.GMAIL_SYNC_SUCCESS));
    } catch (AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    } finally {
      response.setReload(true);
    }
  }
}
