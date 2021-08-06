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
package com.axelor.apps.gsuite.service;

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.PartnerServiceImpl;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.db.JPA;
import com.google.inject.Inject;
import java.util.List;

public class PartnerGSuiteServiceImpl extends PartnerServiceImpl {

  @Inject
  public PartnerGSuiteServiceImpl(PartnerRepository partnerRepo, AppBaseService appBaseService) {
    super(partnerRepo, appBaseService);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<Long> findMailsFromPartner(Partner partner) {
    String query = String.format(
        "SELECT DISTINCT(email.id) FROM Message as email JOIN email.relatedList relatedList WHERE email.mediaTypeSelect = 2 "
            + "AND (email IN (SELECT message FROM MultiRelated as related WHERE related.relatedToSelect = 'com.axelor.apps.base.db.Partner' AND related.relatedToSelectId = %s)"
            + "OR (relatedList.relatedToSelect = 'com.axelor.apps.base.db.Partner' AND relatedList.relatedToSelectId = %s))",
        partner.getId(),
        partner.getId());

    if (partner.getEmailAddress() != null) {
      query += "OR (email.fromEmailAddress.id = " + partner.getEmailAddress().getId() + ")";
    }
    return JPA.em().createQuery(query).getResultList();
  }
}
