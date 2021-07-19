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

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Country;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.AddressRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.CountryRepository;
import com.axelor.apps.base.db.repo.FunctionRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.AddressService;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.PartnerGoogleAccount;
import com.axelor.apps.gsuite.db.repo.PartnerGoogleAccountRepository;
import com.axelor.apps.gsuite.service.GSuiteService;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.tool.net.URLService;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.Address;
import com.google.api.services.people.v1.model.EmailAddress;
import com.google.api.services.people.v1.model.ListConnectionsResponse;
import com.google.api.services.people.v1.model.Name;
import com.google.api.services.people.v1.model.Organization;
import com.google.api.services.people.v1.model.Person;
import com.google.api.services.people.v1.model.PhoneNumber;
import com.google.api.services.people.v1.model.Photo;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class GSuitePartnerImportServiceImpl implements GSuitePartnerImportService {

  protected PartnerGoogleAccountRepository partnerGoogleAccountRepo;
  protected CountryRepository countryRepo;
  protected AddressService addressService;
  protected PartnerRepository partnerRepo;
  protected AddressRepository addressRepo;
  protected EmailAddressRepository emailRepo;
  protected CompanyRepository companyRepo;
  protected FunctionRepository functionRepo;
  protected GSuiteService gSuiteService;

  @Inject
  public GSuitePartnerImportServiceImpl(
      PartnerGoogleAccountRepository partnerGoogleAccountRepo,
      CountryRepository countryRepo,
      AddressService addressService,
      PartnerRepository partnerRepo,
      AddressRepository addressRepo,
      EmailAddressRepository emailRepo,
      CompanyRepository companyRepo,
      FunctionRepository functionRepo,
      GSuiteService gSuiteService) {
    this.partnerGoogleAccountRepo = partnerGoogleAccountRepo;
    this.countryRepo = countryRepo;
    this.addressService = addressService;
    this.partnerRepo = partnerRepo;
    this.addressRepo = addressRepo;
    this.emailRepo = emailRepo;
    this.companyRepo = companyRepo;
    this.functionRepo = functionRepo;
    this.gSuiteService = gSuiteService;
  }

  @Override
  public void sync(GoogleAccount account) throws AxelorException {

    try {
      PeopleService peopleService = gSuiteService.getPeople(account.getId());
      ListConnectionsResponse profile =
          peopleService
              .people()
              .connections()
              .list("people/me")
              .setPersonFields("names,phoneNumbers,addresses,emailAddresses,photos,organizations")
              .set("sources", "READ_SOURCE_TYPE_CONTACT")
              .execute();
      List<Person> connections = profile.getConnections();
      for (Person person : connections) {
        try {
          createOrUpdatePartner(person, account);
        } catch (AxelorException e) {
          TraceBackService.trace(e);
        }
      }
    } catch (Exception e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }
  }

  @Transactional(rollbackOn = Exception.class)
  protected void createOrUpdatePartner(Person person, GoogleAccount googleAccount)
      throws AxelorException {
    String resourceName = person.getResourceName();
    String[] resourceNameParts = resourceName.split("/");
    String googleContactId = resourceNameParts[1];
    PartnerGoogleAccount partnerGoogleAccount =
        partnerGoogleAccountRepo.findByGoogleContactIdAndAccount(googleContactId, googleAccount);
    Partner partner = partnerGoogleAccount != null ? partnerGoogleAccount.getPartner() : null;
    if (partner == null) {
      partner = new Partner();
      partner.setIsContact(true);
      partner.setPartnerTypeSelect(PartnerRepository.PARTNER_TYPE_INDIVIDUAL);
    }

    setNames(person.getNames(), partner);

    if (StringUtils.isBlank(partner.getName())) {
      return;
    }

    setPhoneNumber(person.getPhoneNumbers(), partner);
    setAddress(person.getAddresses(), partner);

    setEmailAddress(person.getEmailAddresses(), partner);
    setPhoto(person, partner);

    setCompany(person.getOrganizations(), partner);

    if (partnerGoogleAccount == null) {
      partnerGoogleAccount = new PartnerGoogleAccount();
      partnerGoogleAccount.setGoogleAccount(googleAccount);
      partnerGoogleAccount.setGoogleContactId(googleContactId);
      partnerGoogleAccount.setPartner(partner);
      partnerGoogleAccountRepo.save(partnerGoogleAccount);
    }

    partnerRepo.save(partner);
  }

  protected void setCompany(List<Organization> organizations, Partner partner) {
    final String mainPartnerFilter =
        "self.name = :name AND self.isContact = false AND self in (SELECT p from Partner p join p.companySet c where c in :companySet)";

    if (ObjectUtils.isEmpty(organizations)) {
      return;
    }
    for (Organization organization : organizations) {
      Company company = companyRepo.findByName(organization.getName());
      if (company == null) {
        continue;
      }
      partner.addCompanySetItem(company);

      Partner mainPartner =
          partnerRepo
              .all()
              .filter(mainPartnerFilter)
              .bind("name", organization.getName())
              .bind("companySet", partner.getCompanySet())
              .fetchOne();
      partner.setMainPartner(mainPartner);
      String jobTitle = organization.getTitle();
      partner.setJobTitleFunction(functionRepo.findByName(jobTitle));
      partner.setFunctionBusinessCard(jobTitle);
    }
  }

  protected void setPhoto(Person person, Partner partner) throws AxelorException {
    List<Photo> photos = person.getPhotos();
    for (Photo photo : photos) {
      try {
        File file = MetaFiles.createTempFile(partner.getName(), ".png").toFile();
        String url = photo.getUrl().replace("/s100/", "/s1000/"); // increase image pixels
        URLService.fileDownload(file, url, null, null);
        MetaFile picture = Beans.get(MetaFiles.class).upload(file);
        partner.setPicture(picture);
      } catch (IOException e) {
        throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
      }
    }
  }

  protected void setNames(List<Name> googleNames, Partner partner) {
    if (ObjectUtils.isEmpty(googleNames)) {
      return;
    }
    Name name = googleNames.get(0);
    String firstName = name.getGivenName();
    String lastName = name.getFamilyName();
    String displayName = name.getDisplayName();
    if (StringUtils.isBlank(firstName)) {
      firstName = lastName;
    }
    partner.setName(firstName);
    partner.setFirstName(lastName);
    partner.setFullName(displayName);
  }

  protected void setPhoneNumber(List<PhoneNumber> phoneList, Partner partner) {
    if (ObjectUtils.isEmpty(phoneList)) {
      return;
    }
    for (PhoneNumber phoneNumber : phoneList) {
      final String type = phoneNumber.getType();
      if ("main".equalsIgnoreCase(type) || "work".equalsIgnoreCase(type)) {
        partner.setFixedPhone(phoneNumber.getCanonicalForm());
      }
      if ("fax".equalsIgnoreCase(type)) {
        partner.setFax(phoneNumber.getCanonicalForm());
      }
      if ("mobile".equalsIgnoreCase(type)) {
        partner.setMobilePhone(phoneNumber.getCanonicalForm());
      }
    }
  }

  protected void setAddress(List<Address> addressList, Partner partner) {
    if (ObjectUtils.isEmpty(addressList)) {
      return;
    }
    com.axelor.apps.base.db.Address mainAddress = partner.getMainAddress();

    if (mainAddress == null) {
      mainAddress = new com.axelor.apps.base.db.Address();
    }

    for (Address address : addressList) {
      if (StringUtils.isBlank(address.getCountryCode())
          || StringUtils.isBlank(address.getStreetAddress())
          || StringUtils.isBlank(address.getPostalCode())) {
        continue;
      }
      mainAddress.setAddressL4(address.getStreetAddress());
      mainAddress.setZip(address.getPostalCode());
      Country country =
          countryRepo.all().filter("self.alpha2Code = ?1", address.getCountryCode()).fetchOne();
      if (country == null) {
        continue;
      }
      mainAddress.setAddressL7Country(country);
      mainAddress.setAddressL5(address.getPoBox());
      addressService.autocompleteAddress(mainAddress);
      addressRepo.save(mainAddress);
      partner.setMainAddress(mainAddress);
    }
  }

  protected void setEmailAddress(final List<EmailAddress> personEmailAddressList, Partner partner) {
    if (ObjectUtils.isEmpty(personEmailAddressList)) {
      return;
    }
    com.axelor.apps.message.db.EmailAddress emailAddress =
        partner.getEmailAddress() == null
            ? new com.axelor.apps.message.db.EmailAddress()
            : partner.getEmailAddress();
    for (EmailAddress personEmailAddr : personEmailAddressList) {
      if ("work".equalsIgnoreCase(personEmailAddr.getType())) {
        if (StringUtils.notEmpty(emailAddress.getAddress())
            && emailAddress.getAddress().equals(personEmailAddr.getValue())) {
          continue;
        }
        emailAddress.setAddress(personEmailAddr.getValue());
        emailAddress.setPartner(partner);
        emailRepo.save(emailAddress);
      }
    }
  }
}
