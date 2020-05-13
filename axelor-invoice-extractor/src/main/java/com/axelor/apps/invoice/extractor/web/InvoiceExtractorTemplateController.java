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

import com.axelor.apps.invoice.extractor.db.InvoiceExtractorTemplate;
import com.axelor.apps.invoice.extractor.db.InvoiceField;
import com.axelor.apps.invoice.extractor.service.InvoiceExtractorTemplateService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.util.ArrayList;
import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class InvoiceExtractorTemplateController {

  public void generateField(ActionRequest request, ActionResponse response) {
    try {
      InvoiceExtractorTemplate invoiceTemplate =
          request.getContext().asType(InvoiceExtractorTemplate.class);

      MetaFile templateMetaFile = invoiceTemplate.getTemplateFile();
      MetaFile pdfMetaFile = invoiceTemplate.getPdfFile();

      InvoiceExtractorTemplateService extractorTemplateService =
          Beans.get(InvoiceExtractorTemplateService.class);
      JSONArray jsonArray = extractorTemplateService.generateField(pdfMetaFile, templateMetaFile);

      List<InvoiceField> invoiceFields = new ArrayList<>();
      for (Object json : jsonArray) {
        invoiceFields = extractorTemplateService.printJsonObject((JSONObject) json);
      }
      response.setValue("invoiceFieldList", invoiceFields);

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
