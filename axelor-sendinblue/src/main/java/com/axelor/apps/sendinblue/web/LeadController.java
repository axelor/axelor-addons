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
package com.axelor.apps.sendinblue.web;

import com.axelor.apps.crm.db.Lead;
import com.axelor.apps.sendinblue.service.SendinBlueContactService;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class LeadController {

  @Inject private SendinBlueContactService sendinBlueContactService;

  public void deleteSendinBlueContactStatistics(ActionRequest request, ActionResponse response) {
    Lead lead = request.getContext().asType(Lead.class);
    sendinBlueContactService.deleteSendinBlueContactLeadStatistics(lead);
    response.setReload(true);
  }
}
