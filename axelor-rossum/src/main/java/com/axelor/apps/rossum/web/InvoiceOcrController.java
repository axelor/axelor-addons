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

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.rossum.db.InvoiceOcr;
import com.axelor.apps.rossum.db.repo.InvoiceOcrRepository;
import com.axelor.apps.rossum.exception.IExceptionMessage;
import com.axelor.apps.rossum.service.InvoiceOcrService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.io.IOException;
import javax.persistence.PersistenceException;
import wslite.json.JSONException;

public class InvoiceOcrController {

  public void openInvoice(ActionRequest request, ActionResponse response) {

    InvoiceOcr invoiceOcr =
        Beans.get(InvoiceOcrRepository.class)
            .find(request.getContext().asType(InvoiceOcr.class).getId());

    try {
      InvoiceOcrService invoiceOcerService = Beans.get(InvoiceOcrService.class);
      Invoice invoice =
          invoiceOcerService.generateInvoice(
              invoiceOcr, invoiceOcerService.extractDataFromPDF(invoiceOcr));

      if (invoice != null && invoice.getId() != null) {
        response.setView(
            ActionView.define(I18n.get("Invoice"))
                .model(Invoice.class.getName())
                .add("form", "invoice-form")
                .add("grid", "invoice-grid")
                .context("_showRecord", invoice.getId())
                .map());
      } else {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_NO_VALUE,
            I18n.get(IExceptionMessage.INVOICE_GENERATION_ERROR));
      }
    } catch (ClassNotFoundException
        | AxelorException
        | IOException
        | InterruptedException
        | UnsupportedOperationException
        | JSONException
        | PersistenceException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
