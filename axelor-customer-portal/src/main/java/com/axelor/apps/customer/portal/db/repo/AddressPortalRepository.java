/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2022 Axelor (<http://axelor.com>).
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
package com.axelor.apps.customer.portal.db.repo;

import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.AddressBaseRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.customer.portal.service.stripe.StripePaymentService;
import com.axelor.inject.Beans;

public class AddressPortalRepository extends AddressBaseRepository {

  @Override
  public Address save(Address address) {
    address = super.save(address);
    Partner partner =
        Beans.get(PartnerRepository.class)
            .all()
            .filter(
                "self IN (SELECT partner FROM PartnerAddress WHERE address = :address) AND self.stripeCustomerId IS NOT NULL")
            .bind("address", address)
            .fetchOne();
    if (partner != null) {
      Address defaultAddress = Beans.get(PartnerService.class).getDefaultAddress(partner);
      if (defaultAddress.equals(address)) {
        try {
          Beans.get(StripePaymentService.class).updateCustomer(partner);
        } catch (Exception e) {
        }
      }
    }

    return address;
  }
}
