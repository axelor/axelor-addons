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
package com.axelor.apps.invoice.extractor.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.invoice.extractor.service.InvoiceExtractorService;
import com.axelor.apps.invoice.extractor.service.InvoiceExtractorServiceImpl;
import com.axelor.apps.invoice.extractor.service.InvoiceExtractorTemplateService;
import com.axelor.apps.invoice.extractor.service.InvoiceExtractorTemplateServiceImpl;
import com.axelor.apps.invoice.extractor.service.InvoiceFieldService;
import com.axelor.apps.invoice.extractor.service.InvoiceFieldServiceImpl;

public class InvoiceExtractorModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(InvoiceExtractorTemplateService.class).to(InvoiceExtractorTemplateServiceImpl.class);
    bind(InvoiceExtractorService.class).to(InvoiceExtractorServiceImpl.class);
    bind(InvoiceFieldService.class).to(InvoiceFieldServiceImpl.class);
  }
}
