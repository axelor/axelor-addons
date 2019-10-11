/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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

import com.axelor.apps.rossum.db.InvoiceOcrTemplate;
import com.axelor.apps.rossum.db.repo.InvoiceOcrTemplateRepository;
import com.axelor.apps.rossum.service.InvoiceOcrTemplateService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.io.IOException;
import wslite.json.JSONException;

public class InvoiceOcrTemplateController {

  public void createTemplate(ActionRequest request, ActionResponse response) {

    InvoiceOcrTemplate invoiceOcrTemplate =
        Beans.get(InvoiceOcrTemplateRepository.class)
            .find(request.getContext().asType(InvoiceOcrTemplate.class).getId());

    try {
      Beans.get(InvoiceOcrTemplateService.class).createTemplate(invoiceOcrTemplate);
      response.setReload(true);
    } catch (AxelorException | IOException | InterruptedException | JSONException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
