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

import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.exception.AxelorException;
import com.axelor.meta.db.MetaFile;
import com.google.api.client.auth.oauth2.Credential;
import com.google.gdata.client.contacts.ContactsService;
import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.util.ServiceException;
import java.io.IOException;

public interface GSuiteContactService {

  GoogleAccount update(GoogleAccount googleAccount) throws AxelorException;

  Partner update(Partner partner, boolean remove) throws AxelorException;

  String updateGoogleContact(
      Partner partner, Credential credential, String[] account, boolean remove)
      throws IOException, ServiceException, AxelorException;

  ContactEntry searchContactEntry(
      ContactsService service,
      Credential credential,
      Partner partner,
      String[] account,
      boolean refreshed)
      throws IOException, ServiceException, AxelorException;

  ContactsService refreshToken(Credential credential, ContactsService service)
      throws AxelorException, IOException;

  ContactEntry updateContactEntry(ContactsService service, Partner partner, ContactEntry contact);

  void updateContactEmails(ContactEntry contact, Partner partner);

  void updateContactPhone(ContactEntry contact, String phoneNumber, String type);

  void updateContactAddress(ContactEntry contact, Address address);

  void updateContactPhoto(ContactsService service, ContactEntry contact, MetaFile picture);
}
