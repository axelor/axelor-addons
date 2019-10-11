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
package com.axelor.apps.rossum.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.rossum.db.InvoiceOcr;
import com.axelor.exception.AxelorException;
import java.io.IOException;
import javax.persistence.PersistenceException;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public interface InvoiceOcrService {

  /**
   * Generate Invoice
   *
   * @param invoiceOcr
   * @param result
   * @return
   * @throws AxelorException
   * @throws IOException
   * @throws InterruptedException
   * @throws ClassNotFoundException
   * @throws JSONException
   * @throws UnsupportedOperationException
   * @throws PersistenceException
   */
  public Invoice generateInvoice(InvoiceOcr invoiceOcr, JSONObject result)
      throws AxelorException, IOException, InterruptedException, ClassNotFoundException,
          UnsupportedOperationException, JSONException, PersistenceException;

  /**
   * Compute invoice
   *
   * @param invoice
   * @return
   * @throws ClassNotFoundException
   * @throws AxelorException
   */
  public Invoice compute(Invoice invoice) throws ClassNotFoundException, AxelorException;

  /**
   * This methods extracts data from PDF and save its response for another use.
   *
   * @param invoiceOcr
   * @return
   * @throws AxelorException
   * @throws UnsupportedOperationException
   * @throws IOException
   * @throws InterruptedException
   * @throws JSONException
   */
  public JSONObject extractDataFromPDF(InvoiceOcr invoiceOcr)
      throws AxelorException, UnsupportedOperationException, IOException, InterruptedException,
          JSONException;
}
