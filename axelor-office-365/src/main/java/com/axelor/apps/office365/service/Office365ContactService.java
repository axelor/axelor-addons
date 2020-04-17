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
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PartnerAddress;
import com.axelor.apps.base.db.repo.AddressRepository;
import com.axelor.apps.base.db.repo.PartnerAddressRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.common.StringUtils;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class Office365ContactService {

  @Inject private PartnerService partnerService;

  @Inject private PartnerRepository partnerRepo;
  @Inject private EmailAddressRepository emailAddressRepo;
  @Inject private AddressRepository addressRepo;
  @Inject private PartnerAddressRepository partnerAddressRepo;

  @SuppressWarnings("unchecked")
  public void createContact(JSONObject jsonObject) {

    if (jsonObject != null) {
      try {
        String officeContactId = jsonObject.getOrDefault("id", "").toString();
        Partner partner = partnerRepo.findByOffice365Id(officeContactId);

        if (partner == null) {
          partner = new Partner();
          partner.setOffice365Id(officeContactId);
          partner.setIsContact(true);
          partner.setPartnerTypeSelect(PartnerRepository.PARTNER_TYPE_INDIVIDUAL);
        }
        setPartnerValues(partner, jsonObject);
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public void setPartnerValues(Partner partner, JSONObject jsonObject) throws JSONException {

    partner.setFirstName(
        (jsonObject.getOrDefault("givenName", "").toString()
                + " "
                + jsonObject.getOrDefault("middleName", "").toString())
            .trim()
            .replaceAll("null", ""));
    partner.setName(jsonObject.getOrDefault("surname", "").toString().replaceAll("null", ""));
    partner.setFullName(
        jsonObject.getOrDefault("displayName", "").toString().replaceAll("null", ""));
    partner.setCompanyStr(jsonObject.getOrDefault("companyName", "").toString());
    partner.setDepartment(jsonObject.getOrDefault("department", "").toString());
    partner.setMobilePhone(jsonObject.getOrDefault("mobilePhone", "").toString());
    partner.setDescription(jsonObject.getOrDefault("personalNotes", "").toString());
    if (jsonObject.getOrDefault("birthday", null) != null) {
      String birthDateStr = jsonObject.getOrDefault("birthday", "").toString();
      if (!StringUtils.isBlank((birthDateStr)) && !birthDateStr.equals("null")) {
        partner.setBirthdate(LocalDate.parse(birthDateStr.substring(0, birthDateStr.indexOf("T"))));
      }
    }
    partner.setJobTitle(jsonObject.getOrDefault("jobTitle", "").toString());
    partner.setNickName(jsonObject.getOrDefault("nickName", "").toString());

    switch (jsonObject.getOrDefault("title", null).toString().toLowerCase()) {
      case "miss":
        partner.setTitleSelect(PartnerRepository.PARTNER_TITLE_MS);
        break;
      case "dr":
        partner.setTitleSelect(PartnerRepository.PARTNER_TITLE_DR);
        break;
      case "prof":
        partner.setTitleSelect(PartnerRepository.PARTNER_TITLE_PROF);
        break;
      default:
        partner.setTitleSelect(PartnerRepository.PARTNER_TITLE_M);
    }

    JSONArray phones = jsonObject.getJSONArray("homePhones");
    partner.setFixedPhone(
        phones != null && phones.get(0) != null ? phones.get(0).toString() : null);

    manageEmailAddress(jsonObject, partner);
    managePartnerAddress(jsonObject, partner);
    partnerRepo.save(partner);
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
        Address defaultAddress = partner != null ? partnerService.getDefaultAddress(partner) : null;
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
      managePartnerAddress(businessAddressObj, partner, "self.isBusinessAddr = true", true);

      JSONObject otherAddressObj = jsonObject.getJSONObject("otherAddress");
      managePartnerAddress(otherAddressObj, partner, "self.isOtherAddr = true", false);

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
        partnerAddress.setIsBusinessAddr(true);
      } else {
        partnerAddress.setIsOtherAddr(true);
      }
      partnerAddressRepo.save(partnerAddress);
    }
  }

  @SuppressWarnings("unchecked")
  private void manageAddress(Address address, Partner partner, JSONObject jsonAddressObj) {

    try {
      address.setAddressL2(jsonAddressObj.getOrDefault("street", "").toString());
      address.setAddressL3(jsonAddressObj.getOrDefault("city", "").toString());
      address.setAddressL4(jsonAddressObj.getOrDefault("state", "").toString());
      address.setAddressL5(jsonAddressObj.getOrDefault("countryOrRegion", "").toString());
      address.setZip(jsonAddressObj.getOrDefault("postalCode", "").toString());
      addressRepo.save(address);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }
}
