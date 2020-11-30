/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
package com.axelor.apps.rossum.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.rossum.db.InvoiceOcrTemplate;
import com.axelor.exception.AxelorException;
import java.io.IOException;
import java.net.URISyntaxException;
import wslite.json.JSONException;

public interface InvoiceOcrTemplateService {

  /**
   * Creation of Invoice Ocr Template Data using Rossum API
   *
   * @param invoiceOcrTemplate
   * @throws AxelorException
   * @throws IOException
   * @throws InterruptedException
   * @throws JSONException
   */
  public void createTemplate(InvoiceOcrTemplate invoiceOcrTemplate)
      throws AxelorException, IOException, InterruptedException, JSONException;

  public Invoice generateInvoiceFromCSV(InvoiceOcrTemplate invoiceOcrTemplate)
      throws IOException, AxelorException;

  public InvoiceOcrTemplate setInvoiceOcrTemplateSeq(InvoiceOcrTemplate invoiceOcrTemplate);

  public String getDocumentUrl(InvoiceOcrTemplate invoiceOcrTemplate)
      throws AxelorException, URISyntaxException, IOException, JSONException;

  public void fetchUpdatedDetails(InvoiceOcrTemplate invoiceOcrTemplate)
      throws AxelorException, IOException, JSONException;

  public void validateRossumData(InvoiceOcrTemplate invoiceOcrTemplate)
      throws AxelorException, IOException, JSONException;

  public void recogniseData(InvoiceOcrTemplate invoiceOcrTemplate);
}
