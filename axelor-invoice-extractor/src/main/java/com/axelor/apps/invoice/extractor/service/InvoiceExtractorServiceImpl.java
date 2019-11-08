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
import com.axelor.apps.base.db.Product;
import com.axelor.apps.invoice.extractor.db.InvoiceExtractor;
import com.axelor.apps.invoice.extractor.db.InvoiceField;
import com.axelor.apps.invoice.extractor.translation.ITranslation;
import com.axelor.apps.tool.date.DateTool;
import com.axelor.db.JpaRepository;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.db.MetaField;
import com.beust.jcommander.Strings;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class InvoiceExtractorServiceImpl implements InvoiceExtractorService {

  static String filter;
  
  @Override
  public Map<String, Object> generateInvoice(
      InvoiceExtractor invoiceExtractor,
      JSONArray jsonArray,
      JSONObject jsonObjectData,
      Invoice invoice)
      throws ClassNotFoundException {

    List<InvoiceField> invoiceFieldList =
        invoiceExtractor.getInvoiceExtractorTemplate().getInvoiceFieldList();

    Map<String, Object> invoiceMap = Mapper.toMap(invoice);

    invoiceMap = iterateFields(jsonArray, invoiceFieldList, invoiceMap, jsonObjectData);

    return invoiceMap;
  }

  protected Map<String, Object> iterateFields(
      JSONArray fieldsArray,
      List<InvoiceField> invoiceFieldList,
      Map<String, Object> invoiceMap,
      JSONObject jsonObjectData)
      throws ClassNotFoundException {

    for (InvoiceField invoiceField : invoiceFieldList) {

      if (invoiceField.getMetaField() != null) {
    	  /** For tabuler field get from template  */
        if (invoiceField.getJsonFieldType() != null
            && invoiceField.getJsonFieldType().equals(ITranslation.JSON_INVOICE_TABLES)) {
          filter = "";
          invoiceMap.put(
              invoiceField.getMetaField().getName(), getTableFIeld(invoiceField, jsonObjectData));
        } else if (invoiceField.getTemplateField() != null
            && jsonObjectData
                .get(invoiceField.getTemplateField())
                .getClass()
                .equals(org.json.simple.JSONArray.class)
            && invoiceField.getMetaField().getRelationship() == null) {
          String fieldValue = "";
          JSONArray jsonArray = (JSONArray) jsonObjectData.get(invoiceField.getTemplateField());
        
          /**
           * If Relation is null (String Field) 
           * remove extra white Space from line and convert in string to object
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
  protected Class<Model> getClass(InvoiceField invoiceField) throws ClassNotFoundException {
    return (Class<Model>) Class.forName(invoiceField.getMetaField().getMetaModel().getFullName());
  }

  @SuppressWarnings("unchecked")
  protected Object bindDataToMap(InvoiceField invoiceField, Object value)
      throws ClassNotFoundException {
	  
    Object bean = null;
    String relationship = invoiceField.getMetaField().getRelationship();
    relationship = relationship != null ? relationship : "null";
  
    switch (relationship) {
      case ITranslation.MANY_TO_ONE:
      case ITranslation.ONE_TO_ONE:
        if (Strings.isStringEmpty(filter)) {
          filter = "self";
        }
        bean = addM2OBinding(invoiceField, value);
        break;
      case ITranslation.MANY_TO_MANY:
      case ITranslation.ONE_TO_MANY:
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
      case ITranslation.NULL_TYPE:
        bean = findFieldType(invoiceField, value);
        break;
      default:
        break;
    }
    return bean;
  }

  protected Object findFieldType(InvoiceField invoiceField, Object value) {
	  
    MetaField metaField = invoiceField.getMetaField();

    switch (metaField.getTypeName()) {
      case ITranslation.LOCAL_DATE_TYPE:
        if (invoiceField.getDateFormate() != null
            && invoiceField.getDateFormate().getDateFormate() != null) {

          String DateFormate = invoiceField.getDateFormate().getDateFormate();
          SimpleDateFormat formatter = new SimpleDateFormat(DateFormate);

          try {
            value = DateTool.toLocalDate(formatter.parse(value.toString()));
          } catch (Exception e) {
            TraceBackService.trace(e);
          }
        }
        break;
      case ITranslation.STRING_TYPE:
        value = value.toString();
        break;
      case ITranslation.BIG_DECIMAL_TYPE:
        if (value.getClass().equals(Double.class) || value.getClass().equals(Integer.class)) {
          value = new BigDecimal(String.valueOf(value));
        } else {
          String digit = (String) value;
          value = new BigDecimal(digit.replace(",", "")); // remove comma from digit
        }
        break;
      default:
        break;
    }
    return value;
  }

  protected Object bindValue(
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
      
      /** Record already exist then set in O2M Field*/
      if (query != null
          && filter.contains("self.")
          && query.all().filter(filter).fetchOne() != null
          && invoiceField.getMetaField().getRelationship().equals(ITranslation.MANY_TO_ONE)) {

        bean = query.all().filter(filter).fetchOne(); 
      } 
      
      /**  Prepare Object if not exist in record */
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

  protected Object addM2OBinding(InvoiceField invoiceField, Object value)
      throws ClassNotFoundException {
	  
    Object bean = null;
    Class<Model> klass = null;
    InvoiceField fieldMapping = null;
    Map<String, Object> map = null;
    String mappField = "";
    List<InvoiceField> subInvoiceFieldList = invoiceField.getSubMetaFieldList();
    for (InvoiceField subInvoiceField : subInvoiceFieldList) {

      if (subInvoiceField.getMetaField().getTypeName().equals(String.class.getSimpleName())) {
        fieldMapping = subInvoiceField;
      }
      if (klass == null) {
        klass = getClass(subInvoiceField);
        map = Mapper.toMap(Mapper.toBean(klass, new HashMap<>()));
      }
      
      /**
       * Bind with Template value in O2M (check record if exist or not)
       * If in case Template value is null then bind with default value 
       */
      if (subInvoiceField.getMappingField() == true
          && subInvoiceField.getMetaField().getRelationship() == null) {

        if (subInvoiceField.getDefaultValue() != null && value == null) {		
          filter += "." + subInvoiceField.getMetaField().getName();
          mappField = ITranslation.DEFAULT;
          value = subInvoiceField.getDefaultValue();
          bean = bindValue(klass, subInvoiceField, map, value, invoiceField);
        } else {						
          filter += "." + subInvoiceField.getMetaField().getName();
          mappField = ITranslation.DEFAULT;
          bean = bindValue(klass, subInvoiceField, map, value, invoiceField);
        }
        break;
      }
    }
    
    /** If all bind field is false then take last string field */
    if (!mappField.equals(ITranslation.DEFAULT) && value != null) {
      filter += "." + fieldMapping.getMetaField().getName();
      bean = bindValue(klass, fieldMapping, map, value, invoiceField);
    }

    return bean;
  }

  
  /** 
   * mapping  invoice items define in template 
   *  lines:
   *   	 start: Item\s+Discount\s+Price$
   *   	 end: \s+Total
   *   	 Line: (?P<description>.+)\s+(?P<discount>\d+.\d+)\s+(?P<price>\d+\d+)
   */
  @SuppressWarnings("unchecked")
  protected Object addO2MBinding(InvoiceField invoiceField, Object value, Class<Model> klass)
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
   * mapping table oriented value define in template 
   *   tables:
   *     start: Hotel Details\s+Check In\s+Check Out\s+Rooms
   *     end: Booking ID
   *     body: Field && Value 
   */
  protected Object getTableFIeld(InvoiceField invoiceField, JSONObject jsonObject) {
    Object bean = null;
    JSONObject jsonTableObject = null;
    JSONArray jsonArray = (JSONArray) jsonObject.get(ITranslation.JSON_INVOICE_TABLES);

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
