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
import com.axelor.apps.base.db.Country;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.AddressRepository;
import com.axelor.apps.base.db.repo.CountryRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.AddressService;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.PartnerGoogleAccount;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.db.repo.PartnerGoogleAccountRepository;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.common.io.Files;
import com.google.gdata.client.Service.GDataRequest;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteAOSContactServiceImpl implements GSuiteAOSContactService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static String FEED_URL =
      "https://www.google.com/m8/feeds/contacts/default/full?max-results=1000";

  @Inject private GSuiteService gSuiteService;

  @Inject private AddressRepository addressRepo;

  @Inject private MetaFiles metaFiles;

  @Inject private PartnerRepository partnerRepo;

  @Inject private PartnerGoogleAccountRepository partnerGoogleAccountRepo;

  @Inject private GoogleAccountRepository googleAccountRepo;

  @Inject private CountryRepository countryRepo;

  @Inject private AddressService addressService;

  @Override
  @Transactional
  public GoogleAccount sync(GoogleAccount googleAccount) throws AxelorException {

    // TODO to make date sync dynamic
    FEED_URL = FEED_URL + "?updated-min=" + LocalDateTime.now();

    try {
      URL feedUrl = new URL(FEED_URL);

      ContactsService service = gSuiteService.getContact(googleAccount.getId());
      ContactFeed resultFeed = service.getFeed(feedUrl, ContactFeed.class);

      if (resultFeed.getTitle() != null) {
        log.debug(
            "resultfeed::{} -- {}",
            resultFeed.getTitle().getPlainText(),
            resultFeed.getEntries().size());
      }

      sync(resultFeed, service, googleAccount);

    } catch (IOException | ServiceException e) {
      throw new AxelorException(e.getCause(), TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }

    return googleAccountRepo.save(googleAccount);
  }

  @Override
  @Transactional
  public void sync(
      ContactFeed resultFeed, ContactsService contactsService, GoogleAccount googleAccount)
      throws AxelorException {
    for (ContactEntry gsuiteContact : resultFeed.getEntries()) {

      if (gsuiteContact.getName() == null) {
        continue;
      }

      String googleContactId = gsuiteContact.getId();
      googleContactId =
          googleContactId.substring(googleContactId.lastIndexOf("/") + 1, googleContactId.length());

      PartnerGoogleAccount partnerGoogleAccount =
          partnerGoogleAccountRepo.findByGoogleContactId(googleContactId);
      Partner contact = partnerGoogleAccount != null ? partnerGoogleAccount.getPartner() : null;
      if (contact == null) {
        contact = new Partner();
        contact.setIsContact(true);
        contact.setPartnerTypeSelect(PartnerRepository.PARTNER_TYPE_INDIVIDUAL);
      }

      setName(contact, gsuiteContact);
      if (contact.getName() == null) {
        continue;
      }

      setPhoneNumber(contact, gsuiteContact);
      setAddress(contact, gsuiteContact);
      setEmail(contact, gsuiteContact);
      setPicture(contact, gsuiteContact, contactsService, googleAccount);

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

  protected void setPicture(
      Partner contact,
      ContactEntry gsuiteContact,
      ContactsService service,
      GoogleAccount googleAccount)
      throws AxelorException {

    try {
      Link photoLink = gsuiteContact.getContactPhotoLink();
      if (photoLink == null) {
        return;
      }
      GDataRequest request = service.createLinkQueryRequest(photoLink);
      request.execute();
      if (request.getResponseContentType() == null) {
        return;
      }
      InputStream inputStream = request.getResponseStream();
      File tempDir = Files.createTempDir();
      File file = new File(tempDir, contact.getName() + ".jpg");
      FileUtils.copyInputStreamToFile(inputStream, file);
      MetaFile metaFile = metaFiles.upload(file);
      contact.setPicture(metaFile);
    } catch (IOException | ServiceException e) {
      if ("Not Found".equals(e.getMessage())) {
        log.info("Image not found on entry {}", gsuiteContact.getName().getFullName().getValue());
      } else {
        throw new AxelorException(e.getCause(), TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
      }
    }
  }

  protected void setEmail(Partner contact, ContactEntry entry) {
    for (Email email : entry.getEmailAddresses()) {
      if (email != null) {
        EmailAddress emailAddress = new EmailAddress();
        emailAddress.setAddress(email.getAddress());
        contact.setEmailAddress(emailAddress);
      }
    }
  }

  @Transactional
  protected void setAddress(Partner contact, ContactEntry entry) {
    for (StructuredPostalAddress postalAddress : entry.getStructuredPostalAddresses()) {
      if (postalAddress.getFormattedAddress() != null) {
        Address address =
            contact.getMainAddress() == null ? new Address() : contact.getMainAddress();
        address.setAddressL4(
            postalAddress.getStreet() != null ? postalAddress.getStreet().getValue() : null);
        if (ObjectUtils.isEmpty(address.getAddressL4())) {
          continue;
        }
        address.setZip(
            postalAddress.getPostcode() != null ? postalAddress.getPostcode().getValue() : null);
        Country country =
            countryRepo
                .all()
                .filter("self.alpha2Code = ?1", postalAddress.getCountry().getCode())
                .fetchOne();
        address.setAddressL7Country(country);
        address.setAddressL5(
            postalAddress.getPobox() != null ? postalAddress.getPobox().getValue() : null);
        addressService.autocompleteAddress(address);
        address = addressRepo.save(address);
        contact.setMainAddress(address);
      }
    }
  }

  protected void setPhoneNumber(Partner contact, ContactEntry entry) {
    for (PhoneNumber phonenumber : entry.getPhoneNumbers()) {
      if (phonenumber.getPhoneNumber() != null && phonenumber.getPrimary()) {
        contact.setFixedPhone(phonenumber.getPhoneNumber());
        continue;
      }
      contact.setMobilePhone(phonenumber.getPhoneNumber());
    }
  }

  protected void setName(Partner contact, ContactEntry entry) {
    Name name = entry.getName();
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
