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
package com.axelor.apps.office365.service;

import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.City;
import com.axelor.apps.base.db.Country;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PartnerAddress;
import com.axelor.apps.base.db.repo.AddressRepository;
import com.axelor.apps.base.db.repo.CityRepository;
import com.axelor.apps.base.db.repo.CountryRepository;
import com.axelor.apps.base.db.repo.PartnerAddressRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.office.db.OfficeAccount;
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class Office365ContactService {

  private static final String COMPANY_OFFICE_ID_PREFIX = "company_";

  @Inject private PartnerService partnerService;
  @Inject private Office365Service office365Service;

  @Inject private PartnerRepository partnerRepo;
  @Inject private EmailAddressRepository emailAddressRepo;
  @Inject private AddressRepository addressRepo;
  @Inject private PartnerAddressRepository partnerAddressRepo;

  @SuppressWarnings("unchecked")
  public void createContact(
      JSONObject jsonObject, OfficeAccount officeAccount, LocalDateTime lastSyncOn) {

    if (jsonObject == null) {
      return;
    }

    try {
      String officeContactId = jsonObject.getOrDefault("id", "").toString();
      Partner partner = partnerRepo.findByOffice365Id(officeContactId);
      if (partner == null) {
        partner = new Partner();
        partner.setOffice365Id(officeContactId);
        partner.setOfficeAccount(officeAccount);
        partner.setIsContact(true);
        partner.setPartnerTypeSelect(PartnerRepository.PARTNER_TYPE_INDIVIDUAL);

      } else if (!office365Service.needUpdation(
          jsonObject, lastSyncOn, partner.getCreatedOn(), partner.getUpdatedOn())) {
        return;
      }

      setPartnerValues(partner, jsonObject, officeAccount.getOwnerUser(), officeAccount);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  @Transactional
  public void setPartnerValues(
      Partner partner, JSONObject jsonObject, User user, OfficeAccount officeAccount)
      throws JSONException {

    managePartnerName(jsonObject, partner);
    manageCompany(jsonObject, partner, user, officeAccount);

    partner.setDepartment(office365Service.processJsonValue("department", jsonObject));
    partner.setMobilePhone(office365Service.processJsonValue("mobilePhone", jsonObject));
    partner.setDescription(office365Service.processJsonValue("personalNotes", jsonObject));
    partner.setJobTitle(office365Service.processJsonValue("jobTitle", jsonObject));
    partner.setNickName(office365Service.processJsonValue("nickName", jsonObject));
    partner.setUser(user);

    LocalDateTime dob =
        office365Service.processLocalDateTimeValue(jsonObject, "birthday", ZoneId.systemDefault());
    if (dob != null) {
      partner.setBirthdate(dob.toLocalDate());
    }

    JSONArray phones = jsonObject.getJSONArray("homePhones");
    partner.setFixedPhone(
        phones != null && phones.get(0) != null ? phones.get(0).toString() : null);

    manageEmailAddress(jsonObject, partner);
    managePartnerAddress(jsonObject, partner);
    partnerRepo.save(partner);
  }

  @Transactional
  public void createOffice365Contact(
      Partner partner, OfficeAccount officeAccount, String accessToken) {

    try {
      JSONObject contactJsonObject = setOffice365ContactValues(partner);
      String office365Id =
          office365Service.createOffice365Object(
              Office365Service.CONTACT_URL,
              contactJsonObject,
              accessToken,
              partner.getOffice365Id(),
              "contacts");
      partner.setOffice365Id(office365Id);
      partner.setOfficeAccount(officeAccount);
      partnerRepo.save(partner);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  @Transactional
  public void manageCompany(
      JSONObject jsonObject, Partner partner, User user, OfficeAccount officeAccount) {

    String companyName = office365Service.processJsonValue("companyName", jsonObject);
    if (StringUtils.isBlank(companyName)) {
      return;
    }

    String office365Id = partner.getOffice365Id();
    Partner company = partnerRepo.findByOffice365Id(COMPANY_OFFICE_ID_PREFIX + office365Id);
    if (company == null) {
      company = new Partner();
      company.setPartnerTypeSelect(PartnerRepository.PARTNER_TYPE_COMPANY);
      company.setIsCustomer(true);
      company.setOfficeAccount(officeAccount);
      company.setOffice365Id(COMPANY_OFFICE_ID_PREFIX + office365Id);
    }

    company.setName(companyName);
    company.setFullName(companyName);
    company.setUser(user);
    if (company.getContactPartnerSet() == null
        || !company.getContactPartnerSet().contains(company)) {
      company.addContactPartnerSetItem(partner);
    }
    partner.setMainPartner(company);

    partnerRepo.save(company);
  }

  @SuppressWarnings("unchecked")
  private void managePartnerName(JSONObject jsonObject, Partner partner) {

    String nameStr =
        office365Service.processJsonValue("givenName", jsonObject)
            + " "
            + office365Service.processJsonValue("middleName", jsonObject);
    switch (partner.getPartnerTypeSelect()) {
      case PartnerRepository.PARTNER_TYPE_COMPANY:
        partner.setName(nameStr + " " + office365Service.processJsonValue("surname", jsonObject));
        break;
      default:
        partner.setFirstName(nameStr);
        partner.setName(office365Service.processJsonValue("surname", jsonObject));
        break;
    }

    switch (jsonObject.getOrDefault("title", "").toString().toLowerCase()) {
      case "ms.":
        partner.setTitleSelect(PartnerRepository.PARTNER_TITLE_MS);
        break;
      case "dr.":
        partner.setTitleSelect(PartnerRepository.PARTNER_TITLE_DR);
        break;
      case "prof.":
        partner.setTitleSelect(PartnerRepository.PARTNER_TITLE_PROF);
        break;
      case "m.":
        partner.setTitleSelect(PartnerRepository.PARTNER_TITLE_M);
        break;
    }

    partner.setFullName(office365Service.processJsonValue("displayName", jsonObject));
  }

  @SuppressWarnings("unchecked")
  private void manageEmailAddress(JSONObject jsonObject, Partner partner) {

    try {
      JSONArray emailAddresses = jsonObject.getJSONArray("emailAddresses");
      for (Object object : emailAddresses) {
        JSONObject obj = (JSONObject) object;
        EmailAddress emailAddress =
            emailAddressRepo.findByAddress(obj.getOrDefault("address", "").toString());
        if (emailAddress == null) {
          emailAddress = new EmailAddress();
          emailAddress.setAddress(obj.getOrDefault("address", "").toString());
          emailAddress.setName(obj.getOrDefault("name", "").toString());
          emailAddress.setPartner(partner);
          partner.setEmailAddress(emailAddress);
        } else {
          emailAddress.setName(obj.getOrDefault("name", "").toString());
          emailAddress.setPartner(partner);
          partner.setEmailAddress(emailAddress);
        }
        emailAddressRepo.save(emailAddress);
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  private void managePartnerAddress(JSONObject jsonObject, Partner partner) {

    try {
      JSONObject homeAddressObj = jsonObject.getJSONObject("homeAddress");
      if (homeAddressObj != null && homeAddressObj.size() > 0) {
        Address defaultAddress = null;
        if (partner.getIsContact()) {
          defaultAddress = partner.getMainAddress();
        } else {
          defaultAddress = partner != null ? partnerService.getDefaultAddress(partner) : null;
        }

        PartnerAddress partnerAddress = null;
        if (defaultAddress == null) {
          defaultAddress = new Address();
          partnerAddress = new PartnerAddress();
          partnerAddress.setAddress(defaultAddress);
          partnerAddress.setPartner(partner);
          partner.addPartnerAddressListItem(partnerAddress);
        }
        manageAddress(defaultAddress, partner, homeAddressObj);
        if (partnerAddress != null) {
          defaultAddress = partnerAddress.getAddress();
          partnerAddress.setIsDefaultAddr(true);
          partnerAddressRepo.save(partnerAddress);
          partner.setMainAddress(defaultAddress);
        }
      }

      JSONObject businessAddressObj = jsonObject.getJSONObject("businessAddress");
      managePartnerAddress(businessAddressObj, partner, "self.isInvoicingAddr = true", true);

      JSONObject otherAddressObj = jsonObject.getJSONObject("otherAddress");
      managePartnerAddress(otherAddressObj, partner, "self.isDeliveryAddr = true", false);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  private void managePartnerAddress(
      JSONObject jsonObject, Partner partner, String filter, boolean isBusinessAddr) {

    PartnerAddress partnerAddress = null;
    if (jsonObject != null && jsonObject.size() > 0) {
      Address address = null;
      if (partner != null) {
        partnerAddress =
            partnerAddressRepo.all().filter(filter + " AND self.partner = ?", partner).fetchOne();
        address = partnerAddress != null ? partnerAddress.getAddress() : null;
      }
      if (address == null) {
        address = new Address();
      }
      if (partnerAddress == null) {
        partnerAddress = new PartnerAddress();
        partnerAddress.setAddress(address);
        partnerAddress.setPartner(partner);
        partner.addPartnerAddressListItem(partnerAddress);
      }
      manageAddress(address, partner, jsonObject);
      if (isBusinessAddr) {
        partnerAddress.setIsInvoicingAddr(true);
      } else {
        partnerAddress.setIsDeliveryAddr(true);
      }
      partnerAddressRepo.save(partnerAddress);
    }
  }

  @SuppressWarnings("unchecked")
  private void manageAddress(Address address, Partner partner, JSONObject jsonAddressObj) {

    try {
      address.setAddressL2(office365Service.processJsonValue("state", jsonAddressObj));
      address.setAddressL4(office365Service.processJsonValue("street", jsonAddressObj));

      String cityStr = office365Service.processJsonValue("city", jsonAddressObj);
      if (StringUtils.notBlank(cityStr)) {
        City city = Beans.get(CityRepository.class).findByName(cityStr);
        if (city != null) address.setCity(city);
      }

      String countryStr = jsonAddressObj.getOrDefault("countryOrRegion", "").toString();
      if (StringUtils.notBlank(countryStr)) {
        Country country = Beans.get(CountryRepository.class).findByName(countryStr);
        if (country != null) address.setAddressL7Country(country);
      }

      address.setZip(jsonAddressObj.getOrDefault("postalCode", "").toString());
      addressRepo.save(address);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  private JSONObject setOffice365ContactValues(Partner partner) throws JSONException {

    JSONObject contactJsonObject = new JSONObject();
    String fullName = Beans.get(PartnerService.class).computeSimpleFullName(partner);

    if (partner.getPartnerTypeSelect() == PartnerRepository.PARTNER_TYPE_COMPANY) {
      office365Service.putObjValue(contactJsonObject, "givenName", fullName);
    } else {
      office365Service.putObjValue(contactJsonObject, "givenName", partner.getFirstName());
      office365Service.putObjValue(contactJsonObject, "surname", partner.getName());
    }
    office365Service.putObjValue(contactJsonObject, "displayName", fullName);
    office365Service.putObjValue(contactJsonObject, "department", partner.getDepartment());
    office365Service.putObjValue(contactJsonObject, "mobilePhone", partner.getMobilePhone());
    office365Service.putObjValue(contactJsonObject, "personalNotes", partner.getDescription());

    String companyName =
        partner.getMainPartner() != null ? partner.getMainPartner().getName() : null;
    office365Service.putObjValue(contactJsonObject, "companyName", companyName);

    String title = null;
    switch (partner.getTitleSelect()) {
      case 1:
        title = "M.";
        break;
      case 2:
        title = "Ms.";
        break;
      case 3:
        title = "Dr";
        break;
      case 4:
        title = "Prof.";
        break;
    }
    office365Service.putObjValue(contactJsonObject, "title", title);

    JSONArray phoneJsonArr = new JSONArray();
    if (StringUtils.notBlank(partner.getFixedPhone())) {
      phoneJsonArr.add(partner.getFixedPhone());
    }
    if (ObjectUtils.notEmpty(phoneJsonArr)) {
      contactJsonObject.put("homePhones", (Object) phoneJsonArr);
    }

    manageOffice365EmailAddress(contactJsonObject, partner);
    manageOffice365PartnerAddress(contactJsonObject, partner);

    return contactJsonObject;
  }

  private void manageOffice365EmailAddress(JSONObject contactJsonObject, Partner partner)
      throws JSONException {

    EmailAddress emailAddress = partner.getEmailAddress();
    if (emailAddress == null || StringUtils.isBlank(emailAddress.getAddress())) {
      return;
    }

    JSONArray emailJsonArr = new JSONArray();
    JSONObject emailJsonObj = new JSONObject();
    office365Service.putObjValue(emailJsonObj, "address", emailAddress.getAddress());
    String emailName = emailAddress.getName();
    office365Service.putObjValue(
        emailJsonObj, "name", StringUtils.notBlank(emailName) ? emailName : partner.getFullName());
    emailJsonArr.add(emailJsonObj);
    contactJsonObject.put("emailAddresses", (Object) emailJsonArr);
  }

  private void manageOffice365PartnerAddress(JSONObject contactJsonObject, Partner partner)
      throws JSONException {

    if (partner.getIsContact()) {
      manageOffice365Address(contactJsonObject, "homeAddress", partner.getMainAddress());
    }

    List<PartnerAddress> partnerAddressList = partner.getPartnerAddressList();
    if (ObjectUtils.isEmpty(partnerAddressList)) {
      return;
    }

    for (PartnerAddress partnerAddress : partnerAddressList) {
      if (partnerAddress.getIsInvoicingAddr()
          && !contactJsonObject.containsKey("businessAddress")) {
        manageOffice365Address(contactJsonObject, "businessAddress", partnerAddress.getAddress());
      } else if (partnerAddress.getIsDeliveryAddr()
          && !contactJsonObject.containsKey("otherAddress")) {
        manageOffice365Address(contactJsonObject, "otherAddress", partnerAddress.getAddress());
      } else if (partnerAddress.getIsDefaultAddr()) {
        manageOffice365Address(contactJsonObject, "homeAddress", partnerAddress.getAddress());
      } else if (!contactJsonObject.containsKey("homeAddress")) {
        manageOffice365Address(contactJsonObject, "homeAddress", partnerAddress.getAddress());
      }
    }
  }

  private void manageOffice365Address(JSONObject contactJsonObject, String key, Address address)
      throws JSONException {

    if (address == null) {
      return;
    }

    JSONObject homeAddJsonObject = new JSONObject();

    String l3 = address.getAddressL3();
    String l4 = address.getAddressL4();
    String l5 = address.getAddressL5();
    String addressStr =
        (!Strings.isNullOrEmpty(l3) ? " " + l3 : "")
            + (!Strings.isNullOrEmpty(l4) ? " " + l4 : "")
            + (!Strings.isNullOrEmpty(l5) ? " " + l5 : "");
    office365Service.putObjValue(homeAddJsonObject, "street", addressStr);

    City city = address.getCity();
    if (city != null) {
      office365Service.putObjValue(homeAddJsonObject, "city", address.getCity().getName());
    }
    Country country = address.getAddressL7Country();
    if (country != null) {
      office365Service.putObjValue(
          homeAddJsonObject, "countryOrRegion", address.getAddressL7Country().getName());
    }
    office365Service.putObjValue(homeAddJsonObject, "postalCode", address.getZip());
    contactJsonObject.put(key, (Object) homeAddJsonObject);
  }
}
