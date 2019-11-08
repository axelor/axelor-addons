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

import com.axelor.apps.invoice.extractor.db.InvoiceExtractor;
import com.axelor.apps.invoice.extractor.exception.IExceptionMessage;
import com.axelor.apps.invoice.extractor.translation.ITranslation;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.io.Files;
import java.time.LocalDateTime;

public class InvoiceExtractorController {

  public void checkPdfFile(ActionRequest request, ActionResponse response) {
    InvoiceExtractor invoiceTemplate = request.getContext().asType(InvoiceExtractor.class);
    MetaFile PdfMetaFile = invoiceTemplate.getInvoiceFile();

    if (!Files.getFileExtension(PdfMetaFile.getFileName()).equals(ITranslation.PDF_FILE)) {
      response.setFlash(IExceptionMessage.PDF_FILE_UPLOAD);
      response.setReload(true);
    }
  }

  public void setInvoice(ActionRequest request, ActionResponse response) {

    InvoiceExtractor invoiceExtractor = request.getContext().asType(InvoiceExtractor.class);

    response.setView(
        ActionView.define("Invoice Extractor")
            .model(com.axelor.apps.account.db.Invoice.class.getName())
            .add("form", "invoice-form")
            .param("forceEdit", "true")
            .context("invoiceFormId", invoiceExtractor.getId().toString())
            .context("_operationTypeSelect", 3)
            .context("todayDate", LocalDateTime.now())
            .map());
  }
}
