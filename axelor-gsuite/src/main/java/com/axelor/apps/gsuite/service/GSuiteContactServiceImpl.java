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
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaFile;
import com.google.api.client.auth.oauth2.Credential;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.gdata.client.Query;
import com.google.gdata.client.Service.GDataRequest;
import com.google.gdata.client.contacts.ContactsService;
import com.google.gdata.data.Link;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.TextConstruct;
import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.contacts.ContactFeed;
import com.google.gdata.data.extensions.City;
import com.google.gdata.data.extensions.Country;
import com.google.gdata.data.extensions.Email;
import com.google.gdata.data.extensions.FormattedAddress;
import com.google.gdata.data.extensions.FullName;
import com.google.gdata.data.extensions.Name;
import com.google.gdata.data.extensions.PhoneNumber;
import com.google.gdata.data.extensions.PostCode;
import com.google.gdata.data.extensions.Street;
import com.google.gdata.data.extensions.StructuredPostalAddress;
import com.google.gdata.util.ContentType;
import com.google.gdata.util.ResourceNotFoundException;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteContactServiceImpl implements GSuiteContactService {

  private static final String FEED_URL = "https://www.google.com/m8/feeds/contacts/default/full";

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject protected PartnerRepository partnerRepo;

  @Inject private GoogleAccountRepository googleAccountRepo;

  @Inject private PartnerGoogleAccountRepository partnerGoogleAccountRepo;

  @Inject private GSuiteService gSuiteService;

  @Override
  @Transactional
  public GoogleAccount update(GoogleAccount googleAccount) throws AxelorException {

    List<Partner> partners =
        googleAccount.getContactSyncToGoogleDate() != null
            ? partnerRepo
                .all()
                .filter(
                    "self.isContact = true AND self.updatedOn > ?1 OR self.createdOn > ?1",
                    googleAccount.getContactSyncToGoogleDate())
                .fetch()
            : partnerRepo.all().filter("self.isContact = true").fetch();
    try {
      Credential credential = gSuiteService.getCredential(googleAccount.getId());
      String accountName = googleAccount.getName();
      if (credential == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            String.format(I18n.get(IExceptionMessage.AUTH_EXCEPTION_1), googleAccount.getName()));
      }
      for (Partner partner : partners) {
        String contactId = null;
        PartnerGoogleAccount partnerAccount = new PartnerGoogleAccount();
        for (PartnerGoogleAccount account : partner.getPartnerGoogleAccounts()) {
          if (googleAccount.equals(account.getGoogleAccount())) {
            contactId = account.getGoogleContactId();
            partnerAccount = account;
          }
          break;
        }

        contactId =
            updateGoogleContact(partner, credential, new String[] {contactId, accountName}, false);
        contactId = contactId.substring(contactId.lastIndexOf("/") + 1);
        partnerAccount.setGoogleAccount(googleAccount);
        partnerAccount.setGoogleContactId(contactId);
        partnerAccount.setPartner(partner);

        log.debug(
            "Partner : {} --- GoogleId : {}",
            partnerAccount.getPartner().getFullName(),
            partnerAccount.getGoogleContactId());

        partnerGoogleAccountRepo.save(partnerAccount);
      }
      googleAccount.setContactSyncToGoogleDate(LocalDateTime.now());
    } catch (IOException | ServiceException e) {
      googleAccount.setContactSyncToGoogleLog("\n" + ExceptionUtils.getStackTrace(e));
    }

    return googleAccountRepo.save(googleAccount);
  }

  @Override
  public Partner update(Partner partner, boolean remove) throws AxelorException {

    List<GoogleAccount> googleAccounts =
        googleAccountRepo.all().filter("self.authorized = true").fetch();

    log.debug("Updating partner: {}", partner.getName());

    for (GoogleAccount googleAccount : googleAccounts) {

      LocalDateTime syncDate = googleAccount.getContactSyncToGoogleDate();
      if (syncDate != null
          && partner.getCreatedOn() != null
          && partner.getCreatedOn().isAfter(syncDate)) {
        continue;
      }

      log.debug("Updating with google account: {}", googleAccount.getName());
      String contactId = null;
      PartnerGoogleAccount partnerAccount = new PartnerGoogleAccount();
      if (partner.getPartnerGoogleAccounts() != null) {
        for (PartnerGoogleAccount account : partner.getPartnerGoogleAccounts()) {
          if (googleAccount.equals(account.getGoogleAccount())) {
            contactId = account.getGoogleContactId();
            partnerAccount = account;
          }
          break;
        }
      }
      try {
        Credential credential = gSuiteService.getCredential(googleAccount.getId());
        if (credential == null) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              String.format(I18n.get(IExceptionMessage.AUTH_EXCEPTION_1), googleAccount.getName()));
        }
        contactId =
            updateGoogleContact(
                partner, credential, new String[] {contactId, googleAccount.getName()}, remove);
      } catch (IOException | ServiceException e) {
        e.printStackTrace();
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.CONTACT_UPDATE_EXCEPTION),
            e.getLocalizedMessage());
      }

      if (!remove) {
        contactId = contactId.substring(contactId.lastIndexOf("/") + 1);
        partnerAccount.setGoogleAccount(googleAccount);
        partnerAccount.setGoogleContactId(contactId);

        if (partnerAccount.getPartner() == null) {
          partner.addPartnerGoogleAccount(partnerAccount);
        }
      }
    }

    return partner;
  }

  @Override
  public String updateGoogleContact(
      Partner partner, Credential credential, String[] account, boolean remove)
      throws IOException, ServiceException, AxelorException {

    ContactsService service = new ContactsService(ContactsService.CONTACTS_SERVICE);
    service.setOAuth2Credentials(credential);
    ContactEntry contactEntry = searchContactEntry(service, credential, partner, account, false);

    if (contactEntry != null) {
      URL editUrl = new URL(FEED_URL + "/" + account[0]);
      if (remove) {
        contactEntry.delete();
        return null;
      } else {
        editUrl = new URL(contactEntry.getEditLink().getHref());
        contactEntry = updateContactEntry(service, partner, contactEntry);
        contactEntry = service.update(editUrl, contactEntry);
        account[0] = contactEntry.getId();
        updateContactPhoto(service, contactEntry, partner.getPicture());
      }
    } else {
      URL feedUrl = new URL(FEED_URL);
      contactEntry = new ContactEntry();
      contactEntry = updateContactEntry(service, partner, contactEntry);
      try {
        contactEntry = service.insert(feedUrl, contactEntry);
      } catch (NullPointerException e) {
        service = refreshToken(credential, service);
        contactEntry = service.insert(feedUrl, contactEntry);
      }
      account[0] = contactEntry.getId();
      updateContactPhoto(service, contactEntry, partner.getPicture());
    }
    return account[0];
  }

  @Override
  public ContactEntry searchContactEntry(
      ContactsService service,
      Credential credential,
      Partner partner,
      String[] account,
      boolean refreshed)
      throws IOException, ServiceException, AxelorException {

    if (account[0] != null) {
      URL editUrl = new URL(FEED_URL + "/" + account[0]);
      try {
        return service.getEntry(editUrl, ContactEntry.class);
      } catch (NullPointerException e) {
        service = refreshToken(credential, service);
        if (refreshed) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              String.format(I18n.get(IExceptionMessage.AUTH_EXCEPTION_1), account[1]));
        }
        return searchContactEntry(service, credential, partner, account, true);
      } catch (ResourceNotFoundException e) {
        log.debug("Invalid google contact Id: {} for acccount : {}", account[0], account[1]);
      }
    }

    Query query = new Query(new URL(FEED_URL));
    EmailAddress email = partner.getEmailAddress();
    if (email != null && email.getAddress() != null) {
      query.setFullTextQuery(email.getAddress());
      ContactFeed resultFeed = null;

      try {
        resultFeed = service.query(query, ContactFeed.class);
      } catch (NullPointerException e) {
        System.out.println("exception : " + e.getMessage());
        service = refreshToken(credential, service);
        resultFeed = service.query(query, ContactFeed.class);
      }

      for (ContactEntry entry : resultFeed.getEntries()) {
        log.debug("Entry search for  found: {}", entry.getId());
        System.out.println("Handled exception : " + entry.getName().getFullName());
        return entry;
      }
    }
    return null;
  }

  @Override
  public ContactsService refreshToken(Credential credential, ContactsService service)
      throws AxelorException, IOException {

    gSuiteService.refreshToken(credential);
    service.setOAuth2Credentials(credential);
    return service;
  }

  @Override
  public ContactEntry updateContactEntry(
      ContactsService service, Partner partner, ContactEntry contact) {

    Name name = new Name();
    final String NO_YOMI = null;
    name.setFullName(new FullName(partner.getName(), NO_YOMI));
    contact.setName(name);

    Integer titleSelect = partner.getTitleSelect();

    if (titleSelect != 0 && titleSelect != null) {
      String title =
          MetaStore.getSelectionItem("partner.title.type.select", titleSelect.toString())
              .getTitle();
      contact.setTitle(TextConstruct.plainText(title));
    }

    contact.setContent(new PlainTextConstruct(partner.getDescription()));
    updateContactEmails(contact, partner);

    if (contact.getPhoneNumbers() != null) {
      contact.getPhoneNumbers().clear();
    }
    updateContactPhone(contact, partner.getFixedPhone(), "work");
    updateContactPhone(contact, partner.getMobilePhone(), "mobile");
    updateContactAddress(contact, partner.getMainAddress());
    return contact;
  }

  @Override
  public void updateContactEmails(ContactEntry contact, Partner partner) {

    EmailAddress emailAddress = partner.getEmailAddress();

    if (contact.getEmailAddresses() != null) {
      contact.getEmailAddresses().clear();
    }
    if (emailAddress != null && !Strings.isNullOrEmpty(emailAddress.getAddress())) {
      String address = emailAddress.getAddress();
      Email email = new Email();
      email.setAddress(address);
      email.setDisplayName(partner.getName());
      email.setRel("http://schemas.google.com/g/2005#work");
      email.setPrimary(true);
      contact.addEmailAddress(email);
    } else {
      contact.removeExtension(Email.class);
    }
  }

  @Override
  public void updateContactPhone(ContactEntry contact, String phoneNumber, String type) {

    if (!Strings.isNullOrEmpty(phoneNumber)) {
      PhoneNumber number = new PhoneNumber();
      number.setPhoneNumber(phoneNumber);
      number.setRel("http://schemas.google.com/g/2005#" + type);
      if (type.equals("work")) {
        number.setPrimary(true);
      }
      contact.addPhoneNumber(number);
    }
  }

  @Override
  public void updateContactAddress(ContactEntry contact, Address address) {

    StructuredPostalAddress postalAddress = new StructuredPostalAddress();
    if (contact.getStructuredPostalAddresses() != null) {
      contact.getStructuredPostalAddresses().clear();
    }
    if (address != null) {
      postalAddress.setStreet(new Street(address.getAddressL4()));

      if (address.getCity() != null) {
        postalAddress.setCity(new City(address.getCity().getName()));
      }

      String addL6 = address.getAddressL6();
      if (addL6 != null && addL6.length() > 5) {
        String postalCode = addL6.substring(0, 6);
        postalAddress.setPostcode(new PostCode(postalCode));
      }
      com.axelor.apps.base.db.Country country = address.getAddressL7Country();
      if (country != null) {
        Country contactCountry = new Country();
        contactCountry.setValue(country.getName());
        postalAddress.setCountry(contactCountry);
      }
      postalAddress.setFormattedAddress(new FormattedAddress(address.getFullName()));
      postalAddress.setRel("http://schemas.google.com/g/2005#work");
      postalAddress.setPrimary(true);
      contact.addStructuredPostalAddress(postalAddress);
    }
  }

  @Override
  public void updateContactPhoto(ContactsService service, ContactEntry contact, MetaFile picture) {

    Link photoLink = contact.getContactPhotoLink();
    try {
      URL photoUrl = new URL(photoLink.getHref());
      photoLink.setEtag(photoUrl.getPath());
      if (picture != null) {
        File image = MetaFiles.getPath(picture).toFile();
        GDataRequest request =
            service.createRequest(
                GDataRequest.RequestType.UPDATE, photoUrl, new ContentType("image/jpeg"));
        request.setEtag(photoLink.getEtag());
        OutputStream requestStream = request.getRequestStream();
        Files.copy(image, requestStream);
        request.execute();
      } else {
        service.delete(photoUrl, photoLink.getEtag());
      }
    } catch (IOException | ServiceException e) {
      e.printStackTrace();
    }
  }
}
