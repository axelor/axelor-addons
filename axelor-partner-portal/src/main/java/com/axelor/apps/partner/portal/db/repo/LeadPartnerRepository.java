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
package com.axelor.apps.partner.portal.db.repo;

import com.axelor.apps.crm.db.repo.LeadManagementRepository;
import com.axelor.apps.customer.portal.service.CommonService;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.util.Map;

public class LeadPartnerRepository extends LeadManagementRepository {

  @Inject UserRepository userRepo;

  @Override
  public Map<String, Object> populate(Map<String, Object> json, Map<String, Object> context) {

    Map<String, Object> map = super.populate(json, context);
    if (json != null && json.get("id") != null) {
      map.put(
          "$unread",
          Beans.get(CommonService.class)
              .isUnreadRecord((Long) json.get("id"), (String) context.get("_model")));
    }
    return map;
  }
}
