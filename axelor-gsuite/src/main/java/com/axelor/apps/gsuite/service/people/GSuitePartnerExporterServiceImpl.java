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
package com.axelor.apps.gsuite.service.people;

import com.axelor.apps.base.db.City;
import com.axelor.apps.base.db.Country;
import com.axelor.apps.base.db.Function;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.app.AppService;
import com.axelor.apps.gsuite.db.PartnerGoogleAccount;
import com.axelor.apps.gsuite.db.repo.PartnerGoogleAccountRepository;
import com.axelor.apps.gsuite.service.GSuiteService;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.db.MetaFile;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.Address;
import com.google.api.services.people.v1.model.EmailAddress;
import com.google.api.services.people.v1.model.Name;
import com.google.api.services.people.v1.model.Organization;
import com.google.api.services.people.v1.model.Person;
import com.google.api.services.people.v1.model.PhoneNumber;
import com.google.api.services.people.v1.model.UpdateContactPhotoRequest;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;

public class GSuitePartnerExporterServiceImpl implements GSuitePartnerExporterService {

  protected GSuiteService gSuiteService;
  protected PartnerRepository partnerRepo;
  protected PartnerGoogleAccountRepository partnerAccountRepo;
  protected EmailAccountRepository emailAccountRepo;

  @Inject
  public GSuitePartnerExporterServiceImpl(
      GSuiteService gSuiteService,
      PartnerRepository partnerRepo,
      PartnerGoogleAccountRepository partnerAccountRepo,
      EmailAccountRepository emailAccountRepo) {
    this.gSuiteService = gSuiteService;
    this.partnerRepo = partnerRepo;
    this.partnerAccountRepo = partnerAccountRepo;
    this.emailAccountRepo = emailAccountRepo;
  }

  @Override
  public void sync(EmailAccount account) throws AxelorException {
    PeopleService service = gSuiteService.getPeople(account.getId());
    LocalDateTime lastSyncDateT = account.getContactSyncToGoogleDate();
    List<Partner> partners = getPartners(lastSyncDateT);
    for (Partner partner : partners) {
      try {
        PartnerGoogleAccount partnerGoogleAccount =
            getRelatedPartnerGoogleAccount(account, partner);
        if (partnerGoogleAccount == null) {
          createContact(service, partner, account);
        } else {
          updateContact(service, partner, partnerGoogleAccount);
        }

      } catch (AxelorException e) {
        TraceBackService.trace(e);
      }
    }
    setSyncDateTime(account);
  }

  protected void updateContact(
      PeopleService service, Partner partner, PartnerGoogleAccount partnerGoogleAccount)
      throws AxelorException {
    try {
      String contactId = partnerGoogleAccount.getGoogleContactId();
      String resourceName = "people/" + contactId;

      Person personToUpdate = getContactPerson(service, resourceName);
      Person updatedPerson = updatePersonFromPartner(personToUpdate, partner);
      service
          .people()
          .updateContact(resourceName, updatedPerson)
          .setUpdatePersonFields("names,phoneNumbers,addresses,emailAddresses,organizations")
          .execute();

      updateContactPhoto(resourceName, partner, service);

    } catch (IOException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }
  }

  protected void createContact(PeopleService service, Partner partner, EmailAccount emailAccount)
      throws AxelorException {
    try {
      Person person = new Person();
      updatePersonFromPartner(person, partner);
      person =
          service
              .people()
              .createContact(person)
              .set("sources", "READ_SOURCE_TYPE_CONTACT")
              .execute();
      updateContactPhoto(person.getResourceName(), partner, service);
      createPartnerGoogleAccount(partner, emailAccount, person);
    } catch (IOException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }
  }

  protected Person updatePersonFromPartner(Person person, Partner partner) {
    setName(person, partner);
    setPhoneNumber(person, partner);
    setAddress(person, partner);
    setEmailAddress(person, partner);
    List<Organization> organizationsList = new ArrayList<>();
    Partner mainPartner = partner.getMainPartner();
    Organization org = new Organization();
    Function jobTitleFunction = partner.getJobTitleFunction();
    if (jobTitleFunction != null) {
      org.setTitle(jobTitleFunction.getName());
    } else {
      org.setTitle(partner.getFunctionBusinessCard());
    }
    if (mainPartner != null) {
      org.setName(mainPartner.getName());
    }
    organizationsList.add(org);
    person.setOrganizations(organizationsList);
    return person;
  }

  protected void setName(Person person, Partner partner) {
    List<Name> names = new ArrayList<>();
    Name name = new Name();
    name.setGivenName(partner.getName());
    name.setFamilyName(partner.getFirstName());
    names.add(name);
    person.setNames(names);
  }

  protected void setPhoneNumber(Person person, Partner partner) {
    List<PhoneNumber> phoneNumbers = new ArrayList<>();
    if (partner.getFixedPhone() != null) {
      phoneNumbers.add(new PhoneNumber().setType("main").setValue(partner.getFixedPhone()));
    }
    if (partner.getFax() != null) {
      phoneNumbers.add(new PhoneNumber().setType("fax").setValue(partner.getFax()));
    }
    if (partner.getMobilePhone() != null) {
      phoneNumbers.add(new PhoneNumber().setType("mobile").setValue(partner.getMobilePhone()));
    }
    person.setPhoneNumbers(phoneNumbers);
  }

  protected void setAddress(Person person, Partner partner) {
    com.axelor.apps.base.db.Address mainAddress = partner.getMainAddress();
    if (ObjectUtils.isEmpty(mainAddress)) {
      return;
    }
    List<Address> addresses = new ArrayList<>();
    Address address = new Address();
    address.setStreetAddress(mainAddress.getAddressL4());
    Country addressL7Country = mainAddress.getAddressL7Country();
    address.setCountry(addressL7Country.getName());
    address.setCountryCode(addressL7Country.getAlpha2Code());
    address.setPostalCode(mainAddress.getZip());
    address.setPoBox(mainAddress.getAddressL5());
    City city = mainAddress.getCity();
    if (city != null) {
      address.setCity(city.getName());
    }
    addresses.add(address);
    person.setAddresses(addresses);
  }

  protected void setEmailAddress(Person person, Partner partner) {
    com.axelor.apps.message.db.EmailAddress partnerEmailAddress = partner.getEmailAddress();
    if (ObjectUtils.isEmpty(partnerEmailAddress)) {
      return;
    }
    List<EmailAddress> emailAddressList = new ArrayList<>();
    EmailAddress emailAddress = new EmailAddress();
    emailAddress.setValue(partnerEmailAddress.getAddress());
    emailAddress.setType("work");
    emailAddressList.add(emailAddress);
    person.setEmailAddresses(emailAddressList);
  }

  protected void updateContactPhoto(String resourceName, Partner partner, PeopleService service)
      throws AxelorException {
    MetaFile picture = partner.getPicture();
    try {
      if (picture != null
          && Files.exists(Paths.get(AppService.getFileUploadDir(), picture.getFilePath()))) {
        byte[] fileContent =
            FileUtils.readFileToByteArray(
                Paths.get(AppService.getFileUploadDir(), picture.getFilePath()).toFile());
        service
            .people()
            .updateContactPhoto(
                resourceName, new UpdateContactPhotoRequest().encodePhotoBytes(fileContent))
            .execute();
      } else {
        service.people().deleteContactPhoto(resourceName).execute();
      }
    } catch (IOException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }
  }

  protected PartnerGoogleAccount getRelatedPartnerGoogleAccount(
      EmailAccount account, Partner partner) {
    PartnerGoogleAccount partnerGoogleAccount = null;
    for (PartnerGoogleAccount partnerGAccount : partner.getPartnerGoogleAccounts()) {
      if (partnerGAccount.getEmailAccount() != null
          && partnerGAccount.getEmailAccount().equals(account)
          && StringUtils.notBlank(partnerGAccount.getGoogleContactId())) {
        partnerGoogleAccount = partnerGAccount;
        break;
      }
    }
    return partnerGoogleAccount;
  }

  protected Person getContactPerson(PeopleService service, String resourceName) throws IOException {
    return service
        .people()
        .get(resourceName)
        .setPersonFields("names,phoneNumbers,addresses,emailAddresses,photos,organizations")
        .set("sources", "READ_SOURCE_TYPE_CONTACT")
        .execute();
  }

  @Transactional(rollbackOn = Exception.class)
  protected void createPartnerGoogleAccount(
      Partner partner, EmailAccount emailAccount, Person person) {
    String resourceName = person.getResourceName();
    String[] resourceNameParts = resourceName.split("/");
    String googleContactId = resourceNameParts[1];
    PartnerGoogleAccount partnerGoogleAccount = new PartnerGoogleAccount();
    partnerGoogleAccount.setEmailAccount(emailAccount);
    partnerGoogleAccount.setGoogleContactId(googleContactId);
    partnerGoogleAccount.setPartner(partner);
    partnerAccountRepo.save(partnerGoogleAccount);
  }

  protected List<Partner> getPartners(LocalDateTime lastSyncDateT) {
    StringBuilder filter = new StringBuilder("self.isContact = true");
    Object[] params = new Object[1];
    if (lastSyncDateT != null) {
      filter.append(" AND self.updatedOn > ?1 OR self.createdOn > ?1");
      params[0] = lastSyncDateT;
    }
    return partnerRepo.all().filter(filter.toString(), params).fetch();
  }

  @Transactional
  protected void setSyncDateTime(EmailAccount account) {
    account.setContactSyncToGoogleDate(LocalDateTime.now());
    emailAccountRepo.save(account);
  }
}
