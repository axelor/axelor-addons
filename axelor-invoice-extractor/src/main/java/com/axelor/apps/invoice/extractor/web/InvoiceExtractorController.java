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
import com.axelor.apps.invoice.extractor.service.InvoiceExtractorService;
import com.axelor.apps.invoice.extractor.service.InvoiceExtractorTemplateService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import org.apache.commons.collections.CollectionUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class InvoiceExtractorController {

  public void generateInvoice(ActionRequest request, ActionResponse response) {
    try {
      InvoiceExtractor invoiceExtractor = request.getContext().asType(InvoiceExtractor.class);

      MetaFile pdfFile = invoiceExtractor.getInvoiceFile();
      MetaFile template = invoiceExtractor.getInvoiceExtractorTemplate().getTemplateFile();

      InvoiceExtractorService invoiceExtractorService = Beans.get(InvoiceExtractorService.class);

      JSONArray jsonArray =
          Beans.get(InvoiceExtractorTemplateService.class).generateField(pdfFile, template);
      if (CollectionUtils.isEmpty(jsonArray)) {
        return;
      }

      Invoice invoice =
          invoiceExtractorService.generateInvoice(invoiceExtractor, (JSONObject) jsonArray.get(0));

      response.setView(
          ActionView.define(I18n.get("Invoice"))
              .model(Invoice.class.getName())
              .add("form", "invoice-form")
              .context("_showRecord", invoice.getId())
              .map());

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
