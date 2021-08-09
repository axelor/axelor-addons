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

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.office365.service.Office365ContactService;
import com.axelor.apps.office365.service.Office365Service;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import javax.persistence.PreRemove;
import org.apache.commons.lang3.StringUtils;

public class Office365PartnerListener {

  @PreRemove
  private void onPartnerPreRemove(Partner partner) {

    if (partner.getOffice365Id() != null
        && partner.getOfficeAccount() != null
        && !StringUtils.startsWith(
            partner.getOffice365Id(), Office365ContactService.COMPANY_OFFICE_ID_PREFIX)) {
      try {
        Office365Service office365Service = Beans.get(Office365Service.class);
        String accessToken = office365Service.getAccessTocken(partner.getOfficeAccount());
        office365Service.deleteOffice365Object(
            Office365Service.CONTACT_URL, partner.getOffice365Id(), accessToken, "contact");
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }
  }
}
