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

import com.axelor.app.AppSettings;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.invoice.extractor.db.InvoiceField;
import com.axelor.apps.invoice.extractor.exception.IExceptionMessage;
import com.axelor.apps.invoice.extractor.translation.ITranslation;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaModelRepository;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class TemplateExtractorServiceImpl implements TemplateExtractorService {

  public JSONArray generateField(MetaFile pdf, MetaFile template)
      throws AxelorException, IOException {
    Process process = null;
    String command = null;
    Path metaFile = null;
    String jsonPath = null;
    String line = "";

    try {
      String attachmentPath = AppSettings.get().getPath(ITranslation.FILE_DIRECTORY, "");
      metaFile = MetaFiles.createTempFile(ITranslation.JSON_PRIFIX, ITranslation.JSON_SUFIX);
      jsonPath = metaFile.toString();
      Path pdfPath = MetaFiles.getPath(pdf.getFileName());

      File newFolder = new File(attachmentPath + File.separator + ITranslation.TEMPLATE_FOLDER);
      this.createFolder(template, attachmentPath, newFolder);

      command =
          "invoice2data --template-folder "
              + newFolder
              + " --output-format json "
              + pdfPath
              + " -o "
              + jsonPath;

    } catch (Exception e) {
    	/**  Pdf or Template not set on form view */
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE, I18n.get(IExceptionMessage.PDF_OR_TEMPLATE_ERROR));
    }

    try {
      process = Runtime.getRuntime().exec(command);
    } catch (IOException e) {
      throw new AxelorException(TraceBackRepository.CATEGORY_NO_VALUE, I18n.get(e.toString()));
    }

    BufferedReader rd = new BufferedReader(new InputStreamReader(process.getErrorStream()));
    while ((line = rd.readLine()) != null) {
      if (line.contains(IExceptionMessage.TEMPLATE_ERROR)) {
    	  /** Template not found in folder */
        throw new AxelorException(
            TraceBackRepository.CATEGORY_NO_VALUE, I18n.get(IExceptionMessage.TEMPLATE_ERROR));
      } else if (line.contains(IExceptionMessage.ROOT_ERROR)) {
    	  /** not mention require field in template (e.g. amount,date,issuer) */
        throw new AxelorException(
            TraceBackRepository.CATEGORY_NO_VALUE, I18n.get(IExceptionMessage.ROOT_ERROR));
      }
    }

    try {
      process.waitFor();
    } catch (InterruptedException e) {
      throw new AxelorException(TraceBackRepository.CATEGORY_NO_VALUE, I18n.get(e.toString()));
    }

    return readJsonFile(jsonPath);
  }

  /** move Single Template in folder for remove duplication */
  protected void createFolder(MetaFile template, String attachmentPath, File newFolder)
      throws IOException {

    boolean created = newFolder.mkdirs();

    if (!created) {
      File[] files = newFolder.listFiles();
      for (File file : files) {
        file.delete();
      }
    }

    File source = new File(attachmentPath + File.separator + template.getFileName());
    File destination = new File(newFolder + File.separator + template.getFileName());
    FileUtils.copyFile(source, destination);
  }

  protected JSONArray readJsonFile(String jsonPath) throws AxelorException {
    JSONParser jsonParser = new JSONParser();
    FileReader reader = null;

    try {
      reader = new FileReader(jsonPath);
    } catch (FileNotFoundException e) {
    	/** Json file not found on folder */
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE, I18n.get(IExceptionMessage.JSON_ERROR));
    }

    Object obj = null;
    try {
      obj = jsonParser.parse(reader);
    } catch (Exception e) {
    	/** pdf and template not match */
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE, I18n.get(IExceptionMessage.VALIDATION_ERROR));
    }
    JSONArray array = (JSONArray) obj;
    return array;
  }

  @Override
  public List<InvoiceField> printJsonObject(JSONObject jsonObject) {
    MetaModelRepository metaModel = Beans.get(MetaModelRepository.class);
    MetaModel invoiceMetaModel = metaModel.findByName(Invoice.class.getSimpleName());
    MetaModel invoiceLineMetaModel = metaModel.findByName(InvoiceLine.class.getSimpleName());

    List<InvoiceField> invoiceFields = new ArrayList<>();
    InvoiceField iField = null;

    invoiceFields = setTablesField(jsonObject, invoiceFields, invoiceMetaModel);
    invoiceFields =
        setLinesField(jsonObject, invoiceFields, invoiceLineMetaModel, invoiceMetaModel);

    for (Object keyObj : jsonObject.keySet()) {
      String key = (String) keyObj;
      iField = new InvoiceField();
      if (!key.equals(ITranslation.JSON_INVOICE_LINES)
          && !key.equals(ITranslation.JSON_INVOICE_TABLES)) {
        iField.setMetaModel(invoiceMetaModel);
        iField.setTemplateField(key);
        if (!key.equals(ITranslation.JSON_INVOICE_LINES)
            && !key.equals(ITranslation.JSON_INVOICE_TABLES)) {
          if (jsonObject.get(key).getClass().equals(org.json.simple.JSONArray.class)) {
            String fieldValue = "";
            JSONArray jsonArray = (JSONArray) jsonObject.get(key);

            for (Object obj : jsonArray) {
              if (obj != null) {
                fieldValue = fieldValue.concat(obj.toString());
                iField.setTemplateFieldValue(fieldValue);
              }
            }
          } else {
            iField.setTemplateFieldValue(jsonObject.get(key).toString());
          }
        }
        invoiceFields.add(iField);
      }
    }

    return invoiceFields;
  }

  /**
   *  allows you to parse table-oriented fields
   *  that have a row of column headers followed by a row of values on the next line.
   */
  protected List<InvoiceField> setTablesField(
      JSONObject jsonObject, List<InvoiceField> invoiceFields, MetaModel invoiceMetaModel) {
    JSONArray tableArray = (JSONArray) jsonObject.get(ITranslation.JSON_INVOICE_TABLES);
    if (tableArray != null) {
      JSONObject tableObj = (JSONObject) tableArray.iterator().next();
      for (Object keyTables : tableObj.keySet()) {
        InvoiceField tableField = new InvoiceField();
        String tableKey = (String) keyTables;
        tableField.setMetaModel(invoiceMetaModel);
        tableField.setTemplateField(tableKey);
        tableField.setJsonFieldType(ITranslation.JSON_INVOICE_TABLES);
        tableField.setTemplateFieldValue(tableObj.get(tableKey).toString());

        invoiceFields.add(tableField);
      }
    }
    return invoiceFields;
  }
  
  /**
   *   The lines key allows you to parse invoice items
   *   Regexes start and end to figure out where in the stream the item table is located.
   */
  protected List<InvoiceField> setLinesField(
      JSONObject jsonObject,
      List<InvoiceField> invoiceFields,
      MetaModel invoiceLineMetaModel,
      MetaModel invoiceMetaModel) {

    InvoiceField linesField = new InvoiceField();
    JSONArray lineArray = (JSONArray) jsonObject.get(ITranslation.JSON_INVOICE_LINES);
    if (lineArray != null) {
      InvoiceField lineField = null;
      List<InvoiceField> lines = new ArrayList<>();
      JSONObject objLine = (JSONObject) lineArray.get(0);
      for (Object keyLines : objLine.keySet()) {
        lineField = new InvoiceField();
        String keyLine = (String) keyLines;
        lineField.setMetaModel(invoiceLineMetaModel);
        lineField.setTemplateField(keyLine);
        lineField.setJsonFieldType(ITranslation.JSON_INVOICE_LINES);
        lineField.setTemplateFieldValue(objLine.get(keyLine).toString());

        lines.add(lineField);
      }
      linesField.setMetaModel(invoiceMetaModel);
      linesField.setTemplateField(ITranslation.JSON_INVOICE_LINES);
      linesField.setSubMetaFieldList(lines);

      invoiceFields.add(linesField);
    }
    return invoiceFields;
  }
}
