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
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.rossum.db.InvoiceField;
import com.axelor.apps.rossum.db.InvoiceOcr;
import com.axelor.apps.rossum.db.repo.InvoiceOcrRepository;
import com.axelor.apps.rossum.exception.IExceptionMessage;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.apps.rossum.translation.ITranslation;
import com.axelor.db.JpaRepository;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.persistence.PersistenceException;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class InvoiceOcrServiceImpl implements InvoiceOcrService {

  protected AppRossumService rossumApiService;
  protected InvoiceOcrRepository invoiceOcrRepository;
  protected InvoiceRepository invoiceRepository;
  protected InvoiceLineRepository invoiceLineRepository;
  protected InvoiceService invoiceService;
  protected InvoiceLineService invoiceLineService;
  protected MetaFiles metaFiles;

  private static String filter;

  @Inject
  public InvoiceOcrServiceImpl(
      AppRossumService rossumApiService,
      InvoiceOcrRepository invoiceOcrRepository,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      InvoiceService invoiceService,
      InvoiceLineService invoiceLineService,
      MetaFiles metaFiles) {
    this.rossumApiService = rossumApiService;
    this.invoiceOcrRepository = invoiceOcrRepository;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.invoiceService = invoiceService;
    this.invoiceLineService = invoiceLineService;
    this.metaFiles = metaFiles;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public JSONObject extractDataFromPDF(InvoiceOcr invoiceOcr)
      throws UnsupportedOperationException, AxelorException, IOException, InterruptedException,
          JSONException {

    JSONObject result;

    if (Strings.isNullOrEmpty(invoiceOcr.getRawData())) {
      result =
          rossumApiService.extractInvoiceDataJson(
              invoiceOcr.getInvoiceFile(), invoiceOcr.getTimeout(), null, null);
      result = rossumApiService.generateUniqueKeyFromJsonData(result);
      invoiceOcr.setRawData(result.toString());
      invoiceOcrRepository.save(invoiceOcr);
    } else {
      result = new JSONObject(invoiceOcr.getRawData());
    }
    return result;
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public Invoice generateInvoice(InvoiceOcr invoiceOcr, JSONObject result)
      throws JSONException, ClassNotFoundException, PersistenceException, AxelorException {

    Map<String, Object> invoiceMap = new HashMap<>();
    Invoice invoice = null;
    if (result != null) {
      invoice = new Invoice();
      invoice.setCompany(Beans.get(UserService.class).getUser().getActiveCompany());
      invoice.setCurrency(invoice.getCompany().getCurrency());
      invoice.setOperationTypeSelect(InvoiceRepository.OPERATION_TYPE_CLIENT_SALE);
      invoice.setOperationSubTypeSelect(InvoiceRepository.OPERATION_SUB_TYPE_DEFAULT);
      invoiceMap = Mapper.toMap(invoice);

      List<InvoiceField> invoiceFieldList = invoiceOcr.getInvoiceTemplate().getInvoiceFieldList();

      JSONArray fieldsArray = result.getJSONArray(ITranslation.GET_FIELDS);
      invoiceMap = iterateFields(fieldsArray, invoiceFieldList, invoiceMap);

      JSONArray tablesArray = result.getJSONArray(ITranslation.GET_TABLES);
      invoiceMap = iterateTables(tablesArray, invoiceFieldList, invoiceMap);
      invoice = Mapper.toBean(Invoice.class, invoiceMap);
      if (!Strings.isNullOrEmpty(invoice.getInvoiceId())
          && invoiceRepository
                  .all()
                  .filter("self.invoiceId = ?1", invoice.getInvoiceId())
                  .fetchOne()
              != null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.INVOICE_ID_EXIST_ERROR));
      }
      invoiceRepository.save(invoice);
      metaFiles.attach(
          invoiceOcr.getInvoiceFile(), invoiceOcr.getInvoiceFile().getFileName(), invoice);
      this.compute(invoice);
    }
    return invoice;
  }

  protected Map<String, Object> iterateFields(
      JSONArray fieldsArray, List<InvoiceField> invoiceFieldList, Map<String, Object> invoiceMap)
      throws ClassNotFoundException, JSONException, AxelorException {
    for (int i = 0; i < fieldsArray.length(); i++) {
      JSONObject jsonObject = (JSONObject) fieldsArray.get(i);
      InvoiceField invoiceField =
          getInvoiceField(invoiceFieldList, jsonObject.getString(ITranslation.GET_NAME));

      if (invoiceField != null) {
        if (jsonObject.get("content").getClass().equals(JSONArray.class)) {
          JSONArray contentArray = (JSONArray) jsonObject.get(ITranslation.GET_CONTENT);
          invoiceMap = iterateFields(contentArray, invoiceFieldList, invoiceMap);
        } else {
          filter = "";
          invoiceMap.put(
              invoiceField.getMetaField().getName(),
              bindDataToMap(invoiceField, jsonObject.get(ITranslation.GET_VALUE)));
        }
      }
    }
    return invoiceMap;
  }

  protected Map<String, Object> iterateTables(
      JSONArray tablesArray, List<InvoiceField> invoiceFieldList, Map<String, Object> invoiceMap)
      throws ClassNotFoundException, AxelorException, JSONException {

    InvoiceField invoiceField = getInvoiceField(invoiceFieldList, ITranslation.TYPE_COLUMN_TYPE);

    if (invoiceField != null) {
      invoiceMap.put(
          invoiceField.getMetaField().getName(), bindDataToMap(invoiceField, tablesArray));
    }
    return invoiceMap;
  }

  protected InvoiceField getInvoiceField(
      List<InvoiceField> invoiceFieldList, String templateField) {
    return invoiceFieldList
        .stream()
        .filter(
            line ->
                line.getMetaField() != null
                    && line.getIsbindWithTemplateField()
                    && line.getTemplateField().equals(templateField))
        .findFirst()
        .orElse(null);
  }

  @SuppressWarnings("unchecked")
  protected Class<Model> getClass(InvoiceField invoiceField) throws ClassNotFoundException {
    return (Class<Model>) Class.forName(invoiceField.getMetaField().getMetaModel().getFullName());
  }

  @SuppressWarnings("unchecked")
  protected Object bindDataToMap(InvoiceField invoiceField, Object value)
      throws ClassNotFoundException, AxelorException, JSONException {
    Class<Model> klass;

    String relationship = invoiceField.getMetaField().getRelationship();
    relationship = relationship != null ? relationship : "";
    switch (relationship) {
      case "ManyToOne":
      case "OneToOne":
        if (Strings.isNullOrEmpty(filter)) {
          filter = "self";
        }
        value = addM2OBinding(invoiceField, value);
        break;
      case "ManyToMany":
      case "OneToMany":
        klass =
            (Class<Model>)
                Class.forName(
                    invoiceField.getMetaField().getPackageName()
                        + "."
                        + invoiceField.getMetaField().getTypeName());

        value = addO2MBinding(invoiceField, value, klass);
        break;
      default:
        klass = getClass(invoiceField);
        Map<String, Object> map = Mapper.toMap(Mapper.toBean(klass, new HashMap<>()));
        value = bindValue(klass, invoiceField, map, value);
        break;
    }
    return value;
  }

  protected Object addM2OBinding(InvoiceField invoiceField, Object value)
      throws ClassNotFoundException, AxelorException, JSONException {

    if (invoiceField.getSubMetaFieldList() != null
        && !invoiceField.getSubMetaFieldList().isEmpty()) {
      InvoiceField subInvoiceField = invoiceField.getSubMetaFieldList().iterator().next();
      filter += "." + subInvoiceField.getMetaField().getName();
      Class<Model> klass = getClass(subInvoiceField);
      Map<String, Object> map = Mapper.toMap(Mapper.toBean(klass, new HashMap<>()));

      if (subInvoiceField.getMetaField().getRelationship() == null) {
        value = bindValue(klass, subInvoiceField, map, value);
      } else {
        map.put(subInvoiceField.getMetaField().getName(), bindDataToMap(subInvoiceField, value));
        value = Mapper.toBean(klass, map);
      }
    } else {
      value = invoiceField.getDefaultValue() != null ? invoiceField.getDefaultValue() : null;
    }
    return value;
  }

  protected Object addO2MBinding(InvoiceField invoiceField, Object value, Class<Model> klass)
      throws ClassNotFoundException, AxelorException, JSONException {
    List<Model> objectList = new ArrayList<>();
    List<InvoiceField> subMetaFieldList = invoiceField.getSubMetaFieldList();

    if (value.getClass().equals(JSONArray.class)) {
      JSONArray tablesArray = (JSONArray) value;

      /**
       * Rossum API doesn't support multiple invoices no only one tablesArray object will be
       * generated.
       */
      JSONObject object = tablesArray.getJSONObject(0);
      JSONArray columnTypesArray = object.getJSONArray(ITranslation.GET_COLUMN_TYPES);
      JSONArray rowsArray = object.getJSONArray(ITranslation.GET_ROWS);

      for (int j = 0; j < rowsArray.length(); j++) {
        JSONObject rowsObject = rowsArray.getJSONObject(j);
        if (rowsObject.get(ITranslation.GET_TYPE).equals(ITranslation.GET_HEADER)) {
          continue;
        }

        Map<String, Object> map = Mapper.toMap(Mapper.toBean(klass, new HashMap<>()));
        for (int k = 0; k < columnTypesArray.length(); k++) {
          String keyname = columnTypesArray.getString(k);

          Set<String> columnKeyset = new HashSet<>();

          /**
           * For some files, extraction of tables contains two same key i.e. table_column_quantity.
           * As per API Documentation, it should be table_column_quantity and table_column_uom (i.e.
           * Unit of measure). So, this code is for changing duplicate table_column_quantity to
           * table_column_uom.
           */
          if (columnKeyset != null
              && keyname.endsWith("_quantity")
              && columnKeyset.contains(keyname)) {
            keyname = keyname.replace("_quantity", "_uom");
          } else if (keyname.endsWith("_quantity")) {
            columnKeyset.add(keyname);
          }
          JSONObject jsonObject = rowsObject.getJSONArray(ITranslation.GET_CELLS).getJSONObject(k);

          /**
           * For All keys of table column from API gives converted value. But, for
           * 'table_column_uom' i.e. Unit's column it doesn't convert value. So, using 'content' for
           * its actual value.
           */
          Object tableValue;
          if (keyname.endsWith("_uom")) {
            tableValue = jsonObject.get(ITranslation.GET_CONTENT);
          } else {
            tableValue = jsonObject.get(ITranslation.GET_VALUE);
          }

          InvoiceField subInvoiceField = getInvoiceField(subMetaFieldList, keyname);
          if (subInvoiceField != null) {
            filter = "";
            map.put(
                subInvoiceField.getMetaField().getName(),
                bindDataToMap(subInvoiceField, tableValue));
          }
        }
        objectList.add(Mapper.toBean(klass, map));
      }
    }
    return objectList;
  }

  /**
   * It will check data with existing data from repositories in case of M2O or O2O for all
   * Metafields of Invoice whose relationship is not null.
   *
   * @throws AxelorException
   */
  protected Object bindValue(
      Class<Model> klass, InvoiceField subInvoiceField, Map<String, Object> map, Object value)
      throws AxelorException {

    MetaField metaField = subInvoiceField.getMetaField();
    Object bean = getValueFromType(metaField.getTypeName(), value);

    JpaRepository<Model> query = null;
    if (metaField.getTypeName().equals(String.class.getSimpleName())
        && !klass.equals(Invoice.class)) {
      query = JpaRepository.of(klass);
      if (filter.contains("self.")) {
        filter += " LIKE " + "'%" + value + "%'";
      }

      if (query != null && filter.contains("self.")) {
        bean = query.all().filter(filter).fetchOne();
        if (bean == null) {
          map.put(metaField.getName(), value);

          List<InvoiceField> subMetaFieldlist =
              subInvoiceField.getParentInvoiceField() != null
                  ? subInvoiceField.getParentInvoiceField().getSubMetaFieldList()
                  : subInvoiceField.getSubMetaFieldList();

          for (InvoiceField line : subMetaFieldlist) {
            if (line.getMetaField() != null
                && line.getDefaultValue() != null
                && !line.getIsbindWithTemplateField()) {
              if (line.getDefaultValue().contains("$.")) {
                String lineValue = line.getDefaultValue().replace("$.", "");
                map.put(
                    line.getMetaField().getName(),
                    getValueFromType(line.getMetaField().getTypeName(), map.get(lineValue)));
              } else {
                map.put(
                    line.getMetaField().getName(),
                    getValueFromType(line.getMetaField().getTypeName(), line.getDefaultValue()));
              }
            }
          }
          Mapper mapper = Mapper.of(klass);
          for (Property property : mapper.getProperties()) {
            if (property.isRequired() && map.get(property.getName()) == null) {
              throw new AxelorException(
                  TraceBackRepository.CATEGORY_MISSING_FIELD,
                  IExceptionMessage.REQUIRED_FIELD_MISSING,
                  Model.class.getSimpleName());
            }
          }
          bean = Mapper.toBean(klass, map);
        }
      }
    }

    return bean;
  }

  protected Object getValueFromType(String type, Object value) {
    switch (type) {
      case "String":
        value = (String) value;
        break;
      case "BigDecimal":
        if (value.toString().contains(",")) {
          value = value.toString().replaceAll(",", ".");
        }
        value = new BigDecimal(value.toString());
        break;
      case "Boolean":
        value = value.equals("true") ? true : false;
        break;
      default:
        break;
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public Invoice compute(Invoice invoice) throws ClassNotFoundException, AxelorException {

    if (invoice.getPartner().getFullName() == null) {
      Beans.get(PartnerService.class).setPartnerFullName(invoice.getPartner());
    }
    if (invoice.getBankDetails() == null
        && invoice.getPartner().getBankDetailsList() != null
        && !invoice.getPartner().getBankDetailsList().isEmpty()) {
      invoice.setBankDetails(invoice.getPartner().getBankDetailsList().iterator().next());
    }
    if (invoice.getAddress() == null
        && invoice.getPartner().getPartnerAddressList() != null
        && !invoice.getPartner().getPartnerAddressList().isEmpty()) {
      invoice.setAddress(
          invoice.getPartner().getPartnerAddressList().iterator().next().getAddress());
    }
    if (invoice.getAddressStr() == null && invoice.getAddress() != null) {
      invoice.setAddressStr(invoice.getAddress().getFullName());
    }

    Map<String, Object> invoiceMap = Mapper.toMap(invoice);
    List<MetaField> metaFieldList =
        Beans.get(MetaFieldRepository.class)
            .all()
            .filter(
                "self.metaModel.name = ?1 AND self.relationship = ?2",
                I18n.get("Invoice"),
                "OneToMany")
            .fetch();

    for (MetaField metaField : metaFieldList) {
      String mappedBy = metaField.getMappedBy();
      if (mappedBy != null && mappedBy.equals(metaField.getMetaModel().getName())) {
        List<Model> list = (List<Model>) invoiceMap.get(metaField.getName());

        for (Model line : list) {
          Map<String, Object> lineMap = Mapper.toMap(line);
          if (lineMap.get(mappedBy) == null) {
            lineMap.put(mappedBy, invoice);
            line = Mapper.toBean(line.getClass(), lineMap);
            JpaRepository.of((Class<Model>) line.getClass()).save(line);
          }
        }
      }
    }

    if (invoice.getInvoiceLineList() != null) {
      this.computeInvoiceLine(invoice);
      invoiceService.compute(invoice);
    }

    return invoice;
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  private void computeInvoiceLine(Invoice invoice) throws AxelorException {

    for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {
      invoiceLine.setTypeSelect(InvoiceLineRepository.TYPE_NORMAL);
      if (invoiceLine.getProduct() != null) {
        invoiceLine.setProductName(invoiceLine.getProduct().getName());
        Map<String, Object> invoiceLineMap =
            invoiceLineService.fillProductInformation(invoice, invoiceLine);
        invoiceLine = Mapper.toBean(invoiceLine.getClass(), invoiceLineMap);
      }
      invoiceLineRepository.save(invoiceLine);
    }
  }
}
