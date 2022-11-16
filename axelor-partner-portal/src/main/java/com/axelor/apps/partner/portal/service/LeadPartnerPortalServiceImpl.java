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
package com.axelor.apps.partner.portal.service;

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.crm.db.Lead;
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LeadPartnerPortalServiceImpl implements LeadPartnerPortalService {

  @Override
  public void manageUnreadLead(Lead lead, Partner partner) {

    User partnerUser = partner.getLinkedUser();
    if (partnerUser == null && ObjectUtils.isEmpty(partner.getContactPartnerSet())) {
      return;
    }

    List<User> userList = new ArrayList<>();
    if (partnerUser != null) {
      userList.add(partnerUser);
    }

    userList.addAll(
        partner.getContactPartnerSet().stream()
            .filter(contactPartner -> contactPartner.getLinkedUser() != null)
            .map(Partner::getLinkedUser)
            .collect(Collectors.toList()));
    lead.setUserUnreadIds(
        userList.stream()
            .map(user -> user.getId().toString())
            .collect(Collectors.joining("#$", "#", "$")));
  }
}
