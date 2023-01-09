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

import com.axelor.apps.account.service.AccountingSituationInitService;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.customer.portal.service.stripe.StripePaymentService;
import com.axelor.apps.hr.db.repo.PartnerHRRepository;
import com.axelor.inject.Beans;
import com.axelor.studio.app.service.AppService;
import com.google.inject.Inject;

public class PartnerPortalRepository extends PartnerHRRepository {

  @Inject
  public PartnerPortalRepository(
      AppService appService, AccountingSituationInitService accountingSituationInitService) {
    super(appService, accountingSituationInitService);
  }

  @Override
  public Partner save(Partner partner) {
    partner = super.save(partner);
    try {
      Beans.get(StripePaymentService.class).updateCustomer(partner);
    } catch (Exception e) {
    }

    return partner;
  }
}
