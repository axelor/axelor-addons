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
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.inject.Beans;
import com.google.inject.persist.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LeadPartnerPortalServiceImpl implements LeadPartnerPortalService {

  @Override
  @Transactional
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

    for (User user : userList) {
      String ids = "";
      if (StringUtils.notBlank(user.getLeadUnreadIds())) {
        ids = String.format("%s,", user.getLeadUnreadIds());
      }

      user.setLeadUnreadIds(String.format("%s%s", ids, lead.getId().toString()));
      Beans.get(UserRepository.class).save(user);
    }
  }
}
