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

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.base.db.repo.AppRossumRepository;
import com.axelor.apps.rossum.exception.IExceptionMessage;
import com.axelor.apps.rossum.translation.ITranslation;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class RossumApiServiceImpl implements RossumApiService {

  protected AppRossumRepository appRossumRepository;

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private String host;
  private String secretKey;
  private CloseableHttpClient httpClient = HttpClients.createDefault();
  private CloseableHttpResponse response;

  @Inject
  public RossumApiServiceImpl(AppRossumRepository appRossumRepository) {
    this.appRossumRepository = appRossumRepository;
  }

  @Override
  public JSONObject extractInvoiceData(MetaFile metaFile, Integer timeout)
      throws AxelorException, IOException, InterruptedException, UnsupportedOperationException,
          JSONException {
    AppRossum appRossum = appRossumRepository.all().fetchOne();

    if (appRossum == null
        || appRossum.getRossumUrl() == null
        || appRossum.getRossumApiKey() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.MISSING_URL_OR_API_KEY));
    }

    host = appRossum.getRossumUrl();
    secretKey = appRossum.getRossumApiKey();

    if (metaFile != null && metaFile.getFileType().equals(ITranslation.FILE_TYPE_PDF)) {
      return submitInvoiceAndExtractData(metaFile, timeout);
    }
    throw new AxelorException(
        TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get(IExceptionMessage.PDF_FILE_ERROR));
  }

  private JSONObject submitInvoiceAndExtractData(MetaFile metaFile, Integer timeout)
      throws AxelorException, ClientProtocolException, IOException, UnsupportedOperationException,
          JSONException, InterruptedException {

    JSONObject result = null;
    response = httpClient.execute(httpPost(metaFile));
    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
      result =
          new JSONObject(
              IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset()));
      String documentId = result.getString("id");
      log.debug("Submit result: " + result);

      if (result.getString("status").equals("processing")) {

        log.debug("Document id: " + documentId);

        int sleepMillis = 5000;
        timeout = (int) ((timeout * 1e3) / sleepMillis);
        result = getDocumentWithStatus(documentId, timeout, sleepMillis);
        log.debug("Document successfully processed.");
      }
    } else {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.DOCUMENT_PROCESS_ERROR));
    }
    return result;
  }

  private JSONObject getDocumentWithStatus(String documentId, int maxRetries, int sleepMillis)
      throws JSONException, AxelorException, ClientProtocolException, IOException,
          InterruptedException {

    for (int i = 0; i < maxRetries; i++) {
      log.debug(
          "Waiting... "
              + (i * sleepMillis * 1e-3)
              + " s / "
              + (maxRetries * sleepMillis * 1e-3)
              + " s");

      response = httpClient.execute(httpGet(documentId));
      JSONObject result =
          new JSONObject(
              IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset()));

      String status = result.getString("status");
      switch (status) {
        case "ready":
          return result;
        case "error":
          log.debug("Result: " + result);

          throw new AxelorException(
              TraceBackRepository.CATEGORY_INCONSISTENCY,
              I18n.get(IExceptionMessage.DOCUMENT_PROCESS_ERROR),
              result.getString("message"));
        default:
          if (i == 0) {
            log.debug("Result: " + result);
          }
          Thread.sleep(sleepMillis);
          break;
      }
    }
    throw new AxelorException(
        TraceBackRepository.CATEGORY_NO_VALUE,
        I18n.get(IExceptionMessage.TIMEOUT_ERROR),
        (int) (maxRetries * sleepMillis * 1e-3));
  }

  private HttpPost httpPost(MetaFile metaFile) {

    String filePath = MetaFiles.getPath(metaFile).toString();
    log.debug("Submitting File: " + filePath);
    log.debug("Content type: " + ITranslation.FILE_TYPE_PDF);

    String url = host + "/document";
    HttpPost httpPost = new HttpPost(url);
    httpPost.addHeader("Authorization", "secret_key " + secretKey);
    httpPost.addHeader("Accept", "application/json");
    File fileToUse = new File(filePath);

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
    builder.addPart("file", new FileBody(fileToUse));
    httpPost.setEntity(builder.build());

    return httpPost;
  }

  private HttpGet httpGet(String documentId) {

    String url = host + "/document/" + documentId;
    HttpGet httpGet = new HttpGet(url);
    httpGet.addHeader("Authorization", "secret_key " + secretKey);
    httpGet.addHeader("Accept", "application/json");

    return httpGet;
  }

  @Override
  public JSONObject generateUniqueKeyFromJsonData(JSONObject jsonObject) throws JSONException {
    Integer keyOccurance = 0;
    Map<String, Object> jsonObjectMap = Mapper.toMap(jsonObject);

    JSONArray fieldsArray = jsonObject.getJSONArray(ITranslation.GET_FIELDS);
    if (fieldsArray != null) {
      Map<String, Integer> fieldsArrayKeyMap = new HashMap<>();

      for (int i = 0; i < fieldsArray.length(); i++) {
        String name = fieldsArray.getJSONObject(i).getString(ITranslation.GET_NAME);
        if (fieldsArrayKeyMap != null && name != null && fieldsArrayKeyMap.containsKey(name)) {
          Integer newKeyOccurance = (Integer) fieldsArrayKeyMap.get(name) + 1;
          fieldsArray.getJSONObject(i).put(ITranslation.GET_NAME, name + "_" + newKeyOccurance);
          fieldsArrayKeyMap.put(name, newKeyOccurance);
        } else {
          fieldsArrayKeyMap.put(name, keyOccurance);
        }
      }
      jsonObjectMap.put(ITranslation.GET_FIELDS, fieldsArray);
    }

    JSONArray tablesArray = jsonObject.getJSONArray(ITranslation.GET_TABLES);
    if (tablesArray != null
        && tablesArray.getJSONObject(0).getJSONArray(ITranslation.GET_COLUMN_TYPES) != null) {
      JSONArray columnTypesArray =
          tablesArray.getJSONObject(0).getJSONArray(ITranslation.GET_COLUMN_TYPES);
      Map<String, Integer> columnTypesArrayKeyMap = new HashMap<>();

      for (Integer i = 0; i < columnTypesArray.length(); i++) {
        String name = columnTypesArray.getString(i);
        if (columnTypesArrayKeyMap != null
            && name != null
            && columnTypesArrayKeyMap.containsKey(name)) {
          columnTypesArray.set(i, name + "_" + columnTypesArrayKeyMap.get(name));
        } else {
          columnTypesArrayKeyMap.put(name, keyOccurance);
        }
      }
      jsonObjectMap.put(ITranslation.GET_TABLES, tablesArray);
    }

    jsonObject.putAll(jsonObjectMap);
    return jsonObject;
  }
}
