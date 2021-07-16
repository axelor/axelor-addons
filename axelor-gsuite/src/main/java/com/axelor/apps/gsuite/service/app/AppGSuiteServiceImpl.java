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
package com.axelor.apps.gsuite.service.app;

import com.axelor.apps.base.db.AppGsuite;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.AppGsuiteRepository;
import com.axelor.apps.crm.db.Lead;
import com.axelor.common.ObjectUtils;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppGSuiteServiceImpl implements AppGSuiteService {

  @Inject AppGsuiteRepository appRepo;

  @Override
  public AppGsuite getAppGSuite() {
    return appRepo.all().cacheable().fetchOne();
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Set<String> getRelatedEmailAddressSet() {
    List<Map> addresses = new ArrayList<>();
    Set<String> addressSet = new HashSet<>();
    AppGsuite app = getAppGSuite();

    if (app.getIsLeadIncluded()) {
      List<Map> leadAddresses = getRelatedEmailByModel(Lead.class, null);
      addresses.addAll(leadAddresses);
    }

    if (app.getIsPartnerIncluded()) {
      List<Map> partnerAddresses = getRelatedEmailByModel(Partner.class, "self.isContact = false");
      addresses.addAll(partnerAddresses);
    }

    if (app.getIsContactIncluded()) {
      List<Map> contactAddresses = getRelatedEmailByModel(Partner.class, "self.isContact = true");
      addresses.addAll(contactAddresses);
    }

    for (Map map : addresses) {
      String address = (String) map.get("emailAddress.address");
      if (!ObjectUtils.isEmpty(address)) {
        addressSet.add(address);
      }
    }

    return addressSet;
  }

  @SuppressWarnings("rawtypes")
  private <T extends Model> List<Map> getRelatedEmailByModel(
      Class<T> modelConcerned, String filter) {
    Query<T> query = Query.of(modelConcerned);
    if (!ObjectUtils.isEmpty(filter)) {
      query.filter(filter);
    }
    return query.cacheable().select("emailAddress.address").fetch(0, 0);
  }
}
