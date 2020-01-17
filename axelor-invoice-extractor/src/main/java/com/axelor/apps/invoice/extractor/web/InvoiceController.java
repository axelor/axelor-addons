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
package com.axelor.apps.invoice.extractor.web;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.invoice.extractor.db.InvoiceExtractor;
import com.axelor.apps.invoice.extractor.db.repo.InvoiceExtractorRepository;
import com.axelor.apps.invoice.extractor.service.InvoiceExtractorService;
import com.axelor.apps.invoice.extractor.service.TemplateExtractorService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.io.IOException;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class InvoiceController {

  public void generateInvoice(ActionRequest request, ActionResponse response)
      throws ClassNotFoundException {

    Map<String, Object> invoiceMap = null;

    Invoice invoice = request.getContext().asType(Invoice.class);
    InvoiceExtractor invoiceExtractor =
        Beans.get(InvoiceExtractorRepository.class)
            .find(Long.valueOf((String) request.getContext().get("invoiceExtractorId")));

    MetaFile pdfFile = invoiceExtractor.getInvoiceFile();
    MetaFile template = invoiceExtractor.getInvoiceExtractorTemplate().getTemplateFile();

    try {
      JSONArray jsonArray =
          Beans.get(TemplateExtractorService.class).generateField(pdfFile, template);
      InvoiceExtractorService invoiceExtractorService = Beans.get(InvoiceExtractorService.class);
      for (Object json : jsonArray) {
        invoiceMap =
            invoiceExtractorService.generateInvoice(
                invoiceExtractor, jsonArray, (JSONObject) json, invoice);
      }

      response.setValues(invoiceMap);

    } catch (AxelorException | IOException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
