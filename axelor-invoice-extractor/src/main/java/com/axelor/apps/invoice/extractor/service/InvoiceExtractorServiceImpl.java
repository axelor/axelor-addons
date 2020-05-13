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

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.PaymentCondition;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.invoice.extractor.db.InvoiceExtractor;
import com.axelor.apps.invoice.extractor.db.InvoiceField;
import com.axelor.apps.tool.date.DateTool;
import com.axelor.db.JpaRepository;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaSelectItem;
import com.axelor.meta.db.repo.MetaSelectItemRepository;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class InvoiceExtractorServiceImpl implements InvoiceExtractorService {

  public static final String YML_FILE = "yml";

  private static final String MANY_TO_MANY = "ManyToMany";
  private static final String MANY_TO_ONE = "ManyToOne";
  private static final String ONE_TO_MANY = "OneToMany";
  private static final String ONE_TO_ONE = "OneToOne";
  private static final String LOCAL_DATE_TYPE = "LocalDate";
  private static final String STRING_TYPE = "String";
  private static final String BIG_DECIMAL_TYPE = "BigDecimal";
  private static final String INTEGER_TYPE = "Integer";
  private static final String DEFAULT = "default";

  public static String filter;

  @Inject private InvoiceRepository invoiceRepo;
  @Inject private MetaSelectItemRepository metaSelectItemRepo;

  @SuppressWarnings("unchecked")
  @Override
  @Transactional
  public Invoice generateInvoice(InvoiceExtractor invoiceExtractor, JSONObject jsonObjectData)
      throws ClassNotFoundException, AxelorException {

    List<InvoiceField> invoiceFieldList =
        invoiceExtractor.getInvoiceExtractorTemplate().getInvoiceFieldList();

    Map<String, Object> invoiceMap = new HashMap<String, Object>();

    invoiceMap = iterateFields(invoiceFieldList, invoiceMap, jsonObjectData);
    if (invoiceMap.isEmpty()) {
      return null;
    }

    List<InvoiceLine> invoiceLines = (List<InvoiceLine>) invoiceMap.get("invoiceLineList");

    InvoiceGenerator invoiceGenerator = this.createInvoiceGenerator(invoiceMap);
    Invoice invoice = invoiceGenerator.generate();

    Map<String, Object> newInvoiceMap = Mapper.toMap(invoice);
    invoiceMap.forEach((key, value) -> newInvoiceMap.merge(key, value, (v1, v2) -> v2));

    Invoice finalInvoice = Mapper.toBean(Invoice.class, newInvoiceMap);
    invoiceGenerator.populate(finalInvoice, invoiceLines);
    return invoiceRepo.save(finalInvoice);
  }

  private InvoiceGenerator createInvoiceGenerator(Map<String, Object> invoiceMap)
      throws AxelorException {

    Company company = null;
    if (invoiceMap.get("company") != null) {
      company = (Company) invoiceMap.get("company");
    } else {
      company = this.getActiveCompany();
    }

    Partner partner = (Partner) invoiceMap.get("partner");
    Currency currency =
        invoiceMap.get("currency") != null
            ? (Currency) invoiceMap.get("currency")
            : partner != null && partner.getCurrency() != null
                ? partner.getCurrency()
                : company.getCurrency();

    PaymentCondition paymentCondition = (PaymentCondition) invoiceMap.get("paymentCondition");
    PaymentMode paymentModel = (PaymentMode) invoiceMap.get("paymentMode");

    InvoiceGenerator invoiceGenerator =
        new InvoiceGenerator(
            InvoiceRepository.OPERATION_TYPE_CLIENT_SALE,
            company,
            paymentCondition,
            paymentModel,
            null,
            partner,
            null,
            currency,
            Beans.get(PartnerPriceListService.class)
                .getDefaultPriceList(partner, PriceListRepository.TYPE_SALE),
            null,
            null,
            null,
            null,
            null) {

          @Override
          public Invoice generate() throws AxelorException {
            Invoice invoice = super.createInvoiceHeader();
            return invoice;
          }
        };

    return invoiceGenerator;
  }

  private Company getActiveCompany() {
    Company company = Beans.get(UserService.class).getUser().getActiveCompany();
    if (company == null) {
      List<Company> companyList = Beans.get(CompanyRepository.class).all().fetch();
      if (companyList.size() == 1) {
        company = companyList.get(0);
      }
    }
    return company;
  }

  private Map<String, Object> iterateFields(
      List<InvoiceField> invoiceFieldList,
      Map<String, Object> invoiceMap,
      JSONObject jsonObjectData)
      throws ClassNotFoundException {

    for (InvoiceField invoiceField : invoiceFieldList) {

      if (invoiceField.getMetaField() != null) {
        /** For tabuler field get from template */
        if (invoiceField.getJsonFieldType() != null
            && invoiceField
                .getJsonFieldType()
                .equals(InvoiceExtractorTemplateServiceImpl.JSON_INVOICE_TABLES)) {
          filter = "";
          invoiceMap.put(
              invoiceField.getMetaField().getName(), getTableField(invoiceField, jsonObjectData));

        } else if (invoiceField.getTemplateField() != null
            && jsonObjectData
                .get(invoiceField.getTemplateField())
                .getClass()
                .equals(org.json.simple.JSONArray.class)
            && invoiceField.getMetaField().getRelationship() == null) {
          String fieldValue = "";
          JSONArray jsonArray = (JSONArray) jsonObjectData.get(invoiceField.getTemplateField());

          /**
           * If Relation is null(String Field) remove extra white Space from line and convert object
           * in to string
           */
          for (Object obj : jsonArray) {
            if (obj != null) {
              fieldValue = fieldValue.concat(obj.toString());
            }
          }
          filter = "";
          invoiceMap.put(
              invoiceField.getMetaField().getName(), bindDataToMap(invoiceField, fieldValue));

        } else if (invoiceField.getTemplateField() == null
            && invoiceField.getDefaultValue() != null) {

          /** Set extra fields which is not get from template */
          invoiceMap.put(
              invoiceField.getMetaField().getName(),
              bindDataToMap(invoiceField, invoiceField.getDefaultValue()));

        } else {
          filter = "";
          invoiceMap.put(
              invoiceField.getMetaField().getName(),
              bindDataToMap(invoiceField, jsonObjectData.get(invoiceField.getTemplateField())));
        }
      }
    }
    return invoiceMap;
  }

  @SuppressWarnings("unchecked")
  private Class<Model> getClass(InvoiceField invoiceField) throws ClassNotFoundException {
    return (Class<Model>) Class.forName(invoiceField.getMetaField().getMetaModel().getFullName());
  }

  @SuppressWarnings("unchecked")
  private Object bindDataToMap(InvoiceField invoiceField, Object value)
      throws ClassNotFoundException {

    Object bean = null;
    String relationship = invoiceField.getMetaField().getRelationship();
    relationship = relationship != null ? relationship : null;

    if (relationship == null) {
      bean = findFieldType(invoiceField, value);
      return bean;
    }

    switch (relationship) {
      case MANY_TO_ONE:
      case ONE_TO_ONE:
        if (Strings.isNullOrEmpty(filter)) {
          filter = "self";
        }
        bean = addM2OBinding(invoiceField, value);
        break;

      case MANY_TO_MANY:
      case ONE_TO_MANY:
        bean =
            addO2MBinding(
                invoiceField,
                value,
                (Class<Model>)
                    Class.forName(
                        invoiceField.getMetaField().getPackageName()
                            + "."
                            + invoiceField.getMetaField().getTypeName()));
        break;
    }
    return bean;
  }

  private Object findFieldType(InvoiceField invoiceField, Object value) {

    MetaField metaField = invoiceField.getMetaField();

    switch (metaField.getTypeName()) {
      case LOCAL_DATE_TYPE:
        if (invoiceField.getDateFormat() != null) {

          String DateFormat = invoiceField.getDateFormat().getDateFormat();
          SimpleDateFormat formatter = new SimpleDateFormat(DateFormat);

          try {
            value = DateTool.toLocalDate(formatter.parse(value.toString()));
          } catch (Exception e) {
            TraceBackService.trace(e);
          }
        }
        break;

      case STRING_TYPE:
        Object strVal = getSelectionValue(metaField, value);
        if (strVal != null) {
          value = strVal;
        } else {
          value = value.toString();
        }
        break;

      case BIG_DECIMAL_TYPE:
        if (value.getClass().equals(Double.class) || value.getClass().equals(Integer.class)) {
          value = new BigDecimal(value.toString());
        } else {
          String digit = value.toString();
          value = new BigDecimal(digit.replace(",", ".")); // remove comma from digit
        }
        break;

      case INTEGER_TYPE:
        Object intVal = getSelectionValue(metaField, value);
        if (intVal != null) {
          value = intVal;
        } else {
          value = Integer.parseInt(value.toString());
        }
        break;
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private Object getSelectionValue(MetaField metaField, Object value) {
    try {
      Class<? extends Model> klass =
          (Class<? extends Model>) Class.forName(metaField.getMetaModel().getFullName());
      Mapper mapper = Mapper.of(klass);
      Property property = mapper.getProperty(metaField.getName());
      if (!Strings.isNullOrEmpty(property.getSelection())) {
        List<MetaSelectItem> metaSelectItems =
            metaSelectItemRepo
                .all()
                .filter("self.select.name = ?", property.getSelection())
                .fetch();

        for (MetaSelectItem metaSelectItem : metaSelectItems) {
          if ((value instanceof String
              && metaSelectItem.getTitle().equalsIgnoreCase(value.toString()))) {
            return metaSelectItem.getValue();
          }
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
    return null;
  }

  private Object bindValue(
      Class<Model> klass,
      InvoiceField subInvoiceField,
      Map<String, Object> map,
      Object value,
      InvoiceField invoiceField)
      throws ClassNotFoundException {

    Object bean = null;
    MetaField metaField = subInvoiceField.getMetaField();
    bean = findFieldType(subInvoiceField, value);
    List<InvoiceField> subInvoiceFieldList = invoiceField.getSubMetaFieldList();
    JpaRepository<Model> query = null;

    if (metaField.getTypeName().equals(String.class.getSimpleName())) {
      query = JpaRepository.of(klass);

      if (filter.contains("self")) {
        filter += " = " + "'" + value + "'";
      }

      /** Record already exist then set in O2M Field */
      if (query != null
          && filter.contains("self.")
          && query.all().filter(filter).fetchOne() != null
          && invoiceField.getMetaField().getRelationship().equals(MANY_TO_ONE)) {

        bean = query.all().filter(filter).fetchOne();
      }

      /** Prepare Object if not exist in record */
      else {
        if (klass.equals(Product.class)) {
          map.put("code", value + "-1");
        }
        for (InvoiceField subField : subInvoiceFieldList) {
          if (subField.getMetaField().getRelationship() != null) {
            filter = "";
            map.put(subField.getMetaField().getName(), bindDataToMap(subField, null));
          } else {
            if (subField.getDefaultValue() != null) {
              map.put(
                  subField.getMetaField().getName(),
                  bindDataToMap(subField, subField.getDefaultValue()));
            }
          }
        }
        map.put(subInvoiceField.getMetaField().getName(), bindDataToMap(subInvoiceField, value));

        bean = Mapper.toBean(klass, map);
      }
    }

    return bean;
  }

  private Object addM2OBinding(InvoiceField invoiceField, Object value)
      throws ClassNotFoundException {

    Object bean = null;
    Class<Model> klass = null;
    InvoiceField nameColumn = null;
    Map<String, Object> map = null;
    Mapper mapper = null;
    String mappField = "";
    List<InvoiceField> subInvoiceFieldList = invoiceField.getSubMetaFieldList();
    for (InvoiceField subInvoiceField : subInvoiceFieldList) {

      if (klass == null) {
        klass = getClass(subInvoiceField);
        map = Mapper.toMap(Mapper.toBean(klass, new HashMap<>()));
        mapper = Mapper.of(klass);
      }

      Property prot = mapper.getProperty(subInvoiceField.getMetaField().getName());
      if (prot.isNameColumn() || prot.getName().equals("name")) {
        nameColumn = subInvoiceField;
      }

      /**
       * Bind with Template value in O2M (check record if exist or not) If in case Template value is
       * null then bind with default value
       */
      if (subInvoiceField.getMappingField() == true
          && subInvoiceField.getMetaField().getRelationship() == null
          && (subInvoiceField.getDefaultValue() != null || value != null)) {

        if (value != null) {
          filter += "." + subInvoiceField.getMetaField().getName();
          mappField = DEFAULT;
          bean = bindValue(klass, subInvoiceField, map, value, invoiceField);
        } else {
          filter += "." + subInvoiceField.getMetaField().getName();
          mappField = DEFAULT;
          value = subInvoiceField.getDefaultValue();
          bean = bindValue(klass, subInvoiceField, map, value, invoiceField);
        }
        break;
      }
    }

    /** If all bind field is false then take namecolumn or name field */
    if (!mappField.equals(DEFAULT)
        && nameColumn != null
        && (nameColumn.getDefaultValue() != null || value != null)) {

      if (value != null) {
        filter += "." + nameColumn.getMetaField().getName();
        bean = bindValue(klass, nameColumn, map, value, invoiceField);
      } else {
        filter += "." + nameColumn.getMetaField().getName();
        value = nameColumn.getDefaultValue();
        bean = bindValue(klass, nameColumn, map, value, invoiceField);
      }
    }

    return bean;
  }

  /**
   * mapping invoice items define in template lines: start: Item\s+Discount\s+Price$ end: \s+Total
   * Line: (?P<description>.+)\s+(?P<discount>\d+.\d+)\s+(?P<price>\d+\d+)
   */
  @SuppressWarnings("unchecked")
  private Object addO2MBinding(InvoiceField invoiceField, Object value, Class<Model> klass)
      throws ClassNotFoundException {
    List<Model> objectList = new ArrayList<>();
    List<InvoiceField> subMetaFieldList = invoiceField.getSubMetaFieldList();

    if (value != null && value.getClass().equals(org.json.simple.JSONArray.class)) {
      List<Map<String, Object>> subFieldMapList = (List<Map<String, Object>>) value;

      for (int fieldList = 0; fieldList < subFieldMapList.size(); fieldList++) {
        Map<String, Object> map = Mapper.toMap(Mapper.toBean(klass, new HashMap<>()));
        Map<String, Object> subFieldMap = subFieldMapList.get(fieldList);
        for (InvoiceField subInvoiceField : subMetaFieldList) {

          if (subInvoiceField != null
              && subInvoiceField.getMetaField() != null
              && (subInvoiceField.getTemplateField() != null
                  || subInvoiceField.getDefaultValue() != null)) {

            if (subInvoiceField.getTemplateField() != null
                && subInvoiceField.getDefaultValue() == null) {
              filter = "";
              map.put(
                  subInvoiceField.getMetaField().getName(),
                  bindDataToMap(
                      subInvoiceField, subFieldMap.get(subInvoiceField.getTemplateField())));
            } else {
              filter = "";
              map.put(
                  subInvoiceField.getMetaField().getName(),
                  bindDataToMap(subInvoiceField, subInvoiceField.getDefaultValue()));
            }
          }
        }
        objectList.add(Mapper.toBean(klass, map));
      }
    } else {
      Map<String, Object> map = Mapper.toMap(Mapper.toBean(klass, new HashMap<>()));

      for (InvoiceField subInvoiceField : subMetaFieldList) {
        if (subInvoiceField != null && subInvoiceField.getMetaField().getRelationship() != null) {
          filter = "";
          map.put(subInvoiceField.getMetaField().getName(), bindDataToMap(subInvoiceField, null));
        }
        if (subInvoiceField.getDefaultValue() != null) {
          map.put(
              subInvoiceField.getMetaField().getName(),
              bindDataToMap(subInvoiceField, subInvoiceField.getDefaultValue()));
        }
      }
      objectList.add(Mapper.toBean(klass, map));
    }

    return objectList;
  }

  /**
   * mapping table oriented value define in template tables: start: Hotel Details\s+Check In\s+Check
   * Out\s+Rooms end: Booking ID body: Field && Value
   */
  private Object getTableField(InvoiceField invoiceField, JSONObject jsonObject) {
    Object bean = null;
    JSONObject jsonTableObject = null;
    JSONArray jsonArray =
        (JSONArray) jsonObject.get(InvoiceExtractorTemplateServiceImpl.JSON_INVOICE_TABLES);

    for (Object object : jsonArray) {
      jsonTableObject = (JSONObject) object;
    }
    try {
      bean = bindDataToMap(invoiceField, jsonTableObject.get(invoiceField.getTemplateField()));
    } catch (ClassNotFoundException e) {
      TraceBackService.trace(e);
    }

    return bean;
  }
}
