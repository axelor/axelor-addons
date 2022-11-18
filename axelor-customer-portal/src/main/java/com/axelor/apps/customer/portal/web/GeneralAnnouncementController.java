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
package com.axelor.apps.customer.portal.web;

import com.axelor.apps.client.portal.db.GeneralAnnouncement;
import com.axelor.apps.client.portal.db.repo.GeneralAnnouncementRepository;
import com.axelor.apps.customer.portal.service.CommonService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.persist.Transactional;

public class GeneralAnnouncementController {

  @Transactional
  public void markRead(ActionRequest request, ActionResponse response) {
    GeneralAnnouncement announce = request.getContext().asType(GeneralAnnouncement.class);
    announce = Beans.get(GeneralAnnouncementRepository.class).find(announce.getId());
    Beans.get(CommonService.class).manageReadRecordIds(announce);
  }
}
