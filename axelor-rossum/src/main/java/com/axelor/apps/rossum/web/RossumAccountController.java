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
package com.axelor.apps.rossum.web;

import com.axelor.apps.rossum.db.RossumAccount;
import com.axelor.apps.rossum.db.repo.RossumAccountRepository;
import com.axelor.apps.rossum.service.AnnotationService;
import com.axelor.apps.rossum.service.OrganisationService;
import com.axelor.apps.rossum.service.QueueService;
import com.axelor.apps.rossum.service.RossumAccountService;
import com.axelor.apps.rossum.service.SchemaService;
import com.axelor.apps.rossum.service.WorkspaceService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.io.IOException;
import org.apache.http.ParseException;
import wslite.json.JSONException;

public class RossumAccountController {

  public void login(ActionRequest request, ActionResponse response) {

    try {
      RossumAccount rossumAccount =
          Beans.get(RossumAccountRepository.class)
              .find(request.getContext().asType(RossumAccount.class).getId());
      if (rossumAccount != null) {
        Beans.get(RossumAccountService.class).login(rossumAccount);
        Beans.get(OrganisationService.class).getOrganisations(rossumAccount);
        Beans.get(WorkspaceService.class).getWorkspaces(rossumAccount);
        Beans.get(SchemaService.class).getSchemas(rossumAccount);
        Beans.get(QueueService.class).getQueues(rossumAccount);
        Beans.get(AnnotationService.class).getAnnotations(rossumAccount);
      }
      response.setReload(true);
    } catch (ParseException | IOException | JSONException | AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void reset(ActionRequest request, ActionResponse response) {
    RossumAccount rossumAccount =
        Beans.get(RossumAccountRepository.class)
            .find(request.getContext().asType(RossumAccount.class).getId());

    if (rossumAccount != null) {
      Beans.get(RossumAccountService.class).reset(rossumAccount);
      response.setReload(true);
    }
  }
}
