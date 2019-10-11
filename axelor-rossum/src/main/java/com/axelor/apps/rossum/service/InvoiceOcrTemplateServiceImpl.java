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

import com.axelor.apps.base.db.repo.AppRossumRepository;
import com.axelor.apps.rossum.db.InvoiceField;
import com.axelor.apps.rossum.db.InvoiceOcrTemplate;
import com.axelor.apps.rossum.db.repo.InvoiceFieldRepository;
import com.axelor.apps.rossum.db.repo.InvoiceOcrTemplateRepository;
import com.axelor.apps.rossum.translation.ITranslation;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaField;
import com.beust.jcommander.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class InvoiceOcrTemplateServiceImpl implements InvoiceOcrTemplateService {

  protected RossumApiService rossumApiService;
  protected InvoiceOcrTemplateRepository invoiceOcrTemplateRepository;
  protected InvoiceFieldRepository invoiceFieldRepository;
  protected AppRossumRepository appRossumRepository;
  protected MetaFiles metaFiles;
  protected MetaField metaField;

  @Inject
  public InvoiceOcrTemplateServiceImpl(
      RossumApiService rossumApiService,
      InvoiceOcrTemplateRepository invoiceOcrTemplateRepository,
      InvoiceFieldRepository invoiceFieldRepository,
      AppRossumRepository appRossumRepository,
      MetaFiles metaFiles,
      MetaField metaField) {
    this.rossumApiService = rossumApiService;
    this.invoiceOcrTemplateRepository = invoiceOcrTemplateRepository;
    this.invoiceFieldRepository = invoiceFieldRepository;
    this.appRossumRepository = appRossumRepository;
    this.metaFiles = metaFiles;
    this.metaField = metaField;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void createTemplate(InvoiceOcrTemplate invoiceOcrTemplate)
      throws AxelorException, IOException, InterruptedException, JSONException {

    JSONObject resultData;

    if (Strings.isStringEmpty(invoiceOcrTemplate.getRawData())) {
      resultData =
          rossumApiService.extractInvoiceData(
              invoiceOcrTemplate.getTemplateFile(), invoiceOcrTemplate.getTimeout());
      resultData = rossumApiService.generateUniqueKeyFromJsonData(resultData);
      invoiceOcrTemplate.setRawData(resultData.toString());
      invoiceOcrTemplate.setName(invoiceOcrTemplate.getTemplateFile().getFileName());
      metaFiles.attach(
          invoiceOcrTemplate.getTemplateFile(),
          invoiceOcrTemplate.getTemplateFile().getFileName(),
          invoiceOcrTemplate);
      invoiceOcrTemplateRepository.save(invoiceOcrTemplate);
    } else {
      resultData = new JSONObject(invoiceOcrTemplate.getRawData());
    }
    filterInvoiceData(resultData, invoiceOcrTemplate);
  }

  @Override
  public void filterInvoiceData(JSONObject resultObject, InvoiceOcrTemplate invoiceOcrTemplate)
      throws IOException, JSONException {

    JSONArray fieldsArray = resultObject.getJSONArray(ITranslation.GET_FIELDS);
    generateInvoiceFields(invoiceOcrTemplate, null, fieldsArray);

    JSONArray tablesArray = resultObject.getJSONArray(ITranslation.GET_TABLES);
    if (tablesArray != null) {
      /** Just to take first object from tables Array as Rossum doesn't support multiple invoices */
      JSONObject object = tablesArray.getJSONObject(0);
      JSONArray columnTypesArray = object.getJSONArray(ITranslation.GET_COLUMN_TYPES);
      JSONArray headerCellArray =
          object
              .getJSONArray(ITranslation.GET_ROWS)
              .getJSONObject(0)
              .getJSONArray(ITranslation.GET_CELLS);

      InvoiceField field =
          setInvoiceField(
              invoiceOcrTemplate,
              null,
              ITranslation.TYPE_COLUMN_TYPE,
              ITranslation.GET_COLUMN_TYPES);

      /**
       * For some files, extraction of tables contains two same key i.e. table_column_quantity. As
       * per API Documentation, it should be table_column_quantity and table_column_uom (i.e. Unit
       * of measure). So, this code is for changing duplicate table_column_quantity to
       * table_column_uom.
       */
      Set<String> columnKeyset = new HashSet<>();
      for (int i = 0; i < columnTypesArray.length(); i++) {
        String keyname = columnTypesArray.getString(i);
        if (columnKeyset != null
            && columnKeyset.contains(keyname)
            && keyname.endsWith("_quantity")) {
          keyname = keyname.replace("_quantity", "_uom");
        }
        columnKeyset.add(keyname);
        setInvoiceField(
            null,
            field,
            keyname,
            headerCellArray.getJSONObject(i).getString(ITranslation.GET_VALUE));
      }
    }
  }

  protected void generateInvoiceFields(
      InvoiceOcrTemplate invoiceOcrTemplate, InvoiceField invoiceField, JSONArray jsonArray)
      throws JSONException {

    if (jsonArray != null) {
      for (int i = 0; i < jsonArray.length(); i++) {
        JSONObject fieldsObject = (JSONObject) jsonArray.get(i);

        Object content = fieldsObject.get(ITranslation.GET_CONTENT);
        String keyName = fieldsObject.getString(ITranslation.GET_NAME);

        if (content.getClass().equals(String.class)) {
          setInvoiceField(invoiceOcrTemplate, invoiceField, I18n.get(keyName), content.toString());
        } else if (content.getClass().equals(JSONArray.class)) {
          JSONArray contentArray = (JSONArray) content;
          InvoiceField invField =
              setInvoiceField(invoiceOcrTemplate, null, I18n.get(keyName), I18n.get(keyName));
          generateInvoiceFields(null, invField, contentArray);
        }
      }
    }
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public InvoiceField setInvoiceField(
      InvoiceOcrTemplate invoiceOcrTemplate,
      InvoiceField invoiceField,
      String keyName,
      String content) {
    InvoiceField invField = new InvoiceField();
    invField.setTemplateField(keyName);
    invField.setTemplateValue(content);
    invField.setIsbindWithTemplateField(true);
    invField.setInvoiceOcrTemplate(invoiceOcrTemplate);
    invField.setParentInvoiceField(invoiceField);
    return invoiceFieldRepository.save(invField);
  }
}
