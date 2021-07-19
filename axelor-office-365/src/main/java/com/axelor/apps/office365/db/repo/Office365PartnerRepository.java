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
package com.axelor.apps.office365.db.repo;

import com.axelor.apps.account.service.AccountingSituationService;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.service.app.AppService;
import com.axelor.apps.hr.db.repo.PartnerHRRepository;
import com.axelor.apps.office365.service.Office365ContactService;
import com.axelor.apps.office365.service.Office365Service;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;

public class Office365PartnerRepository extends PartnerHRRepository {

  @Inject Office365Service office365Service;

  @Inject
  public Office365PartnerRepository(
      AppService appService, AccountingSituationService accountingSituationService) {
    super(appService, accountingSituationService);
  }

  @Override
  public void remove(Partner partner) {

    if (partner.getOffice365Id() != null
        && partner.getOfficeAccount() != null
        && !StringUtils.startsWith(
            partner.getOffice365Id(), Office365ContactService.COMPANY_OFFICE_ID_PREFIX)) {
      try {
        String accessToken = office365Service.getAccessTocken(partner.getOfficeAccount());
        office365Service.deleteOffice365Object(
            Office365Service.CONTACT_URL, partner.getOffice365Id(), accessToken, "contact");
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }

    super.remove(partner);
  }

  @Override
  public Partner copy(Partner partner, boolean deep) {

    partner = super.copy(partner, deep);
    partner.setOffice365Id(null);
    return partner;
  }
}
