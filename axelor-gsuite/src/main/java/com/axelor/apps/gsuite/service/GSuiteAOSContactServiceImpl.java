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
import com.axelor.apps.base.db.repo.AddressRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.PartnerGoogleAccount;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.db.repo.PartnerGoogleAccountRepository;
import com.axelor.apps.gsuite.exception.IExceptionMessage;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.api.client.auth.oauth2.Credential;
import com.google.common.io.Files;
import com.google.gdata.client.contacts.ContactsService;
import com.google.gdata.data.Link;
import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.contacts.ContactFeed;
import com.google.gdata.data.extensions.Email;
import com.google.gdata.data.extensions.Name;
import com.google.gdata.data.extensions.PhoneNumber;
import com.google.gdata.data.extensions.StructuredPostalAddress;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.time.LocalDateTime;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteAOSContactServiceImpl implements GSuiteAOSContactService {

  private static String FEED_URL =
      "https://www.google.com/m8/feeds/contacts/default/full?max-results=1000";

  @Inject private GSuiteService gSuiteService;

  @Inject private AddressRepository addressRepo;

  @Inject private MetaFiles metaFiles;

  @Inject private PartnerRepository partnerRepo;

  @Inject private PartnerGoogleAccountRepository partnerGoogleAccountRepo;

  @Inject private GoogleAccountRepository googleAccountRepo;

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  @Transactional
  public GoogleAccount sync(GoogleAccount googleAccount) throws AxelorException {
    if (googleAccount == null) {
      return null;
    }
    googleAccount.setContactSyncFromGoogleLog(null);
    if (googleAccount.getContactSyncFromGoogleDate() != null) {
      FEED_URL = FEED_URL + "?updated-min=" + googleAccount.getContactSyncFromGoogleDate();
    }
    try {
      Credential credential = gSuiteService.getCredential(googleAccount.getId());
      if (credential == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            String.format(I18n.get(IExceptionMessage.AUTH_EXCEPTION_1), googleAccount.getName()));
      }
      sync(credential, googleAccount);
      googleAccount.setContactSyncFromGoogleDate(LocalDateTime.now());
    } catch (IOException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }

    return googleAccountRepo.save(googleAccount);
  }

  @Override
  @Transactional
  public void sync(Credential credential, GoogleAccount googleAccount) {

    try {
      URL feedUrl = new URL(FEED_URL);
      ContactsService contactsService = new ContactsService(ContactsService.CONTACTS_SERVICE);
      contactsService.setOAuth2Credentials(credential);
      ContactFeed resultFeed;
      resultFeed = contactsService.getFeed(feedUrl, ContactFeed.class);
      if (resultFeed.getTitle() != null) {
        log.debug(
            "resultfeed::{} -- {}",
            resultFeed.getTitle().getPlainText(),
            resultFeed.getEntries().size());
      }
      sync(resultFeed, contactsService, googleAccount);
    } catch (IOException | ServiceException e) {
      googleAccount.setContactSyncFromGoogleLog("\n" + ExceptionUtils.getStackTrace(e));
    }
  }

  @Override
  @Transactional
  public void sync(
      ContactFeed resultFeed, ContactsService contactsService, GoogleAccount googleAccount) {
    for (ContactEntry entry : resultFeed.getEntries()) {
      if (entry.getName() == null) {
        continue;
      }
      String googleContactId =
          entry.getId().substring(entry.getId().lastIndexOf("/") + 1, entry.getId().length());
      PartnerGoogleAccount partnerGoogleAccount =
          partnerGoogleAccountRepo.findByGoogleContactId(googleContactId);
      Partner contact = partnerGoogleAccount != null ? partnerGoogleAccount.getPartner() : null;
      if (contact == null) {
        contact = new Partner();
        contact.setIsContact(true);
        contact.setPartnerTypeSelect(PartnerRepository.PARTNER_TYPE_INDIVIDUAL);
      }
      setName(contact, entry);
      if (contact.getName() == null) {
        continue;
      }
      setPhoneNumber(contact, entry);
      setAddress(contact, entry);
      setEmail(contact, entry);
      setPicture(contact, entry, contactsService, googleAccount);
      partnerRepo.save(contact);
      if (partnerGoogleAccount == null) {
        partnerGoogleAccount = new PartnerGoogleAccount();
      }
      partnerGoogleAccount.setGoogleAccount(googleAccount);
      partnerGoogleAccount.setGoogleContactId(googleContactId);
      partnerGoogleAccount.setPartner(contact);
      partnerGoogleAccountRepo.save(partnerGoogleAccount);
    }
  }

  @SuppressWarnings("deprecation")
  public void setPicture(
      Partner contact,
      ContactEntry entry,
      ContactsService contactsService,
      GoogleAccount googleAccount) {
    Link photoLink = entry.getContactPhotoLink();
    if (photoLink != null) {
      try {
        InputStream inputStream = contactsService.getStreamFromLink(photoLink);
        File tempDir = Files.createTempDir();
        File file = new File(tempDir, contact.getName() + ".jpg");
        FileUtils.copyInputStreamToFile(inputStream, file);
        MetaFile metaFile = metaFiles.upload(file);
        contact.setPicture(metaFile);
      } catch (IOException | ServiceException e) {
        googleAccount.setContactSyncFromGoogleLog("\n" + ExceptionUtils.getStackTrace(e));
      }
    }
  }

  public void setEmail(Partner contact, ContactEntry entry) {
    for (Email email : entry.getEmailAddresses()) {
      if (email != null) {
        EmailAddress emailAddress = new EmailAddress();
        emailAddress.setAddress(email.getAddress());
        contact.setEmailAddress(emailAddress);
      }
    }
  }

  @Transactional
  public void setAddress(Partner contact, ContactEntry entry) {
    for (StructuredPostalAddress postalAddress : entry.getStructuredPostalAddresses()) {
      if (postalAddress.getFormattedAddress() != null) {
        Address address = new Address();
        address.setAddressL4(
            postalAddress.getStreet() != null ? postalAddress.getStreet().getValue() : null);
        address.setAddressL6(
            postalAddress.getPostcode() != null ? postalAddress.getPostcode().getValue() : null);
        com.axelor.apps.base.db.Country country =
            new com.axelor.apps.base.db.Country(postalAddress.getCountry().getValue());
        address.setAddressL7Country(country);
        address = addressRepo.find(addressRepo.save(address).getId());
        contact.setMainAddress(address);
      }
    }
  }

  public void setPhoneNumber(Partner contact, ContactEntry entry) {
    for (PhoneNumber phonenumber : entry.getPhoneNumbers()) {
      if (phonenumber.getPhoneNumber() != null && phonenumber.getPrimary()) {
        contact.setFixedPhone(phonenumber.getPhoneNumber());
        continue;
      }
      contact.setMobilePhone(phonenumber.getPhoneNumber());
    }
  }

  public void setName(Partner contact, ContactEntry entry) {
    Name name = null;
    name = entry.getName();
    if (name.hasFullName()) {
      String fullName = name.getFullName().getValue();
      if (name.getFullName().hasYomi()) {
        fullName += " (" + name.getFullName().getYomi() + ")";
      }
      contact.setFullName(fullName);
    }
    if (name.getGivenName() != null && name.hasGivenName()) {
      String firstName = name.getGivenName().getValue();
      if (name.getGivenName().hasYomi()) {
        firstName += " (" + name.getGivenName().getYomi() + ")";
      }
      contact.setFirstName(firstName);
    }
    if (name.hasFamilyName()) {
      String lastName = name.getFamilyName().getValue();
      if (name.getFamilyName().hasYomi()) {
        lastName += " (" + name.getFamilyName().getYomi() + ")";
      }
      contact.setName(lastName);
    }
  }
}
