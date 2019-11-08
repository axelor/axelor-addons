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
import com.axelor.apps.invoice.extractor.db.InvoiceExtractorTemplate;
import com.axelor.apps.invoice.extractor.db.InvoiceField;
import com.axelor.apps.invoice.extractor.service.InvoiceFieldService;
import com.axelor.inject.Beans;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.util.List;
import org.apache.poi.ss.formula.functions.T;

public class InvoiceFieldController {

  public void setMetaModel(ActionRequest request, ActionResponse response) {

    Class<T> klass = (Class<T>) request.getContext().getParent().getContextClass();

    if (klass.equals(InvoiceExtractorTemplate.class)) {
      response.setValue(
          "metaModel",
          Beans.get(MetaModelRepository.class).findByName(Invoice.class.getSimpleName()));

    } else {
      InvoiceField parent = request.getContext().getParent().asType(InvoiceField.class);
      response.setValue(
          "metaModel",
          Beans.get(MetaModelRepository.class).findByName(parent.getMetaField().getTypeName()));
    }
  }

  public void setRequiredField(ActionRequest request, ActionResponse response)
      throws ClassNotFoundException {

    InvoiceField invoiceField = request.getContext().asType(InvoiceField.class);

    List<InvoiceField> invoiceFieldList =
        Beans.get(InvoiceFieldService.class).getRequireField(invoiceField);

    response.setValue("subMetaFieldList", invoiceFieldList);
  }
}
