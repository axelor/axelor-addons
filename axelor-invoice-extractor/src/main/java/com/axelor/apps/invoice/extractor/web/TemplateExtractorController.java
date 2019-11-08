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
import com.axelor.apps.invoice.extractor.exception.IExceptionMessage;
import com.axelor.apps.invoice.extractor.service.TemplateExtractorService;
import com.axelor.apps.invoice.extractor.translation.ITranslation;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.io.Files;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class TemplateExtractorController {

  public void checkYmlFile(ActionRequest request, ActionResponse response) {
    InvoiceExtractorTemplate invoiceTemplate =
        request.getContext().asType(InvoiceExtractorTemplate.class);
    MetaFile templateMetaFile = invoiceTemplate.getTemplateFile();

    if (!Files.getFileExtension(templateMetaFile.getFileName()).equals(ITranslation.YML_FILE)) {
      response.setFlash(IExceptionMessage.YML_FILE_UPLOAD);
      response.setReload(true);
    }
  }

  public void checkPdfFile(ActionRequest request, ActionResponse response) {
    InvoiceExtractorTemplate invoiceTemplate =
        request.getContext().asType(InvoiceExtractorTemplate.class);
    MetaFile PdfMetaFile = invoiceTemplate.getPdfFile();

    if (!Files.getFileExtension(PdfMetaFile.getFileName()).equals(ITranslation.PDF_FILE)) {
      response.setFlash(IExceptionMessage.PDF_FILE_UPLOAD);
      response.setReload(true);
    }
  }

  public void generateField(ActionRequest request, ActionResponse response) {
    InvoiceExtractorTemplate invoiceTemplate =
        request.getContext().asType(InvoiceExtractorTemplate.class);

    TemplateExtractorService templateService = Beans.get(TemplateExtractorService.class);

    MetaFile templateMetaFile = invoiceTemplate.getTemplateFile();
    MetaFile pdfMetaFile = invoiceTemplate.getPdfFile();
    List<InvoiceField> invoiceFields = new ArrayList<>();

    try {
      JSONArray jsonArray = templateService.generateField(pdfMetaFile, templateMetaFile);
      for (Object json : jsonArray) {
        invoiceFields = templateService.printJsonObject((JSONObject) json);
      }

      response.setValue("invoiceFieldList", invoiceFields);

    } catch (AxelorException | IOException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
