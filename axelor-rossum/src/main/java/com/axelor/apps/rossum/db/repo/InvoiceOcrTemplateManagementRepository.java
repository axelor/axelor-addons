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
package com.axelor.apps.rossum.db.repo;

import com.axelor.apps.rossum.db.InvoiceOcrTemplate;
import com.axelor.apps.rossum.service.InvoiceOcrTemplateService;
import com.axelor.inject.Beans;

public class InvoiceOcrTemplateManagementRepository extends InvoiceOcrTemplateRepository {

  @Override
  public InvoiceOcrTemplate save(InvoiceOcrTemplate invoiceOcrTemplate) {

    Beans.get(InvoiceOcrTemplateService.class).setInvoiceOcrTemplateSeq(invoiceOcrTemplate);

    return super.save(invoiceOcrTemplate);
  }
}
