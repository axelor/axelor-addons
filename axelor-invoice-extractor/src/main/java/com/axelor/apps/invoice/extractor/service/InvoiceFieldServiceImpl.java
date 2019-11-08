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
package com.axelor.apps.invoice.extractor.service;

import com.axelor.apps.invoice.extractor.db.InvoiceField;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class InvoiceFieldServiceImpl implements InvoiceFieldService {

  public static final String MANY_TO_MANY = "ManyToMany";
  public static final String MANY_TO_ONE = "ManyToOne";
  public static final String ONE_TO_MANY = "OneToMany";
  public static final String ONE_TO_ONE = "OneToOne";

  @Inject MetaFieldRepository metaFieldRepo;

  @Override
  public List<InvoiceField> getRequireField(InvoiceField invoiceField)
      throws ClassNotFoundException {

    List<InvoiceField> invoiceFieldList = invoiceField.getSubMetaFieldList();
    List<InvoiceField> requiredInvoiceField = new ArrayList<InvoiceField>();
    Boolean flag = true;

    if (invoiceField.getMetaField() != null
        && invoiceField.getMetaField().getRelationship() != null) {

      switch (invoiceField.getMetaField().getRelationship()) {
        case MANY_TO_ONE:
        case ONE_TO_ONE:
        case MANY_TO_MANY:
          for (Property property : getFieldPropertys(invoiceField)) {
            if (property.isRequired()
                || property.isNameColumn()
                || property.getName().equals("name")) {
              InvoiceField requiredField = new InvoiceField();
              MetaField iField = getReuqireField(invoiceField, property);
              if (iField != null) {
                requiredField.setMetaField(iField);
                requiredInvoiceField.add(requiredField);
              }
            }
          }
          return requiredInvoiceField;

        case ONE_TO_MANY:
          if (invoiceFieldList == null) {
            invoiceFieldList = new ArrayList<InvoiceField>();
          }
          for (Property property : getFieldPropertys(invoiceField)) {
            flag = true;

            if (property.isRequired()) {
              InvoiceField requiredField = new InvoiceField();
              MetaField iField = getReuqireField(invoiceField, property);

              if (invoiceFieldList != null && iField != null) {
                for (InvoiceField subField : invoiceFieldList) {
                  if (subField.getMetaField() != null
                      && subField.getMetaField().getName() == iField.getName()) {
                    flag = false;
                  }
                }
              }
              if (flag) {
                requiredField.setMetaField(iField);
                requiredInvoiceField.add(requiredField);
              }
            }
          }

          for (InvoiceField addField : requiredInvoiceField) {
            invoiceFieldList.add(addField);
          }
          return invoiceFieldList;

        default:
          return null;
      }

    } else return null;
  }

  protected MetaField getReuqireField(InvoiceField invoiceField, Property property)
      throws ClassNotFoundException {
    return (MetaField)
        metaFieldRepo
            .all()
            .filter(
                "self.metaModel.name = ?1 AND self.name = ?2",
                invoiceField.getMetaField().getTypeName(),
                property.getName())
            .fetchOne();
  }

  protected Property[] getFieldPropertys(InvoiceField invoiceField) throws ClassNotFoundException {
    String modelName =
        invoiceField.getMetaField().getPackageName()
            + "."
            + invoiceField.getMetaField().getTypeName();
    Class klass = Class.forName(modelName);
    Mapper mapper = Mapper.of(klass);
    return mapper.getProperties();
  }
}
