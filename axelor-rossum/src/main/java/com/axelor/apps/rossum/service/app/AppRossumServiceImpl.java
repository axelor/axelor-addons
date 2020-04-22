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
package com.axelor.apps.rossum.service.app;

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.base.db.repo.AppRossumRepository;
import com.axelor.apps.rossum.db.Queue;
import com.axelor.apps.rossum.db.repo.InvoiceOcrTemplateRepository;
import com.axelor.apps.rossum.db.repo.OrganisationRepository;
import com.axelor.apps.rossum.db.repo.QueueRepository;
import com.axelor.apps.rossum.db.repo.SchemaRepository;
import com.axelor.apps.rossum.db.repo.WorkspaceRepository;
import com.axelor.apps.rossum.exception.IExceptionMessage;
import com.axelor.apps.rossum.translation.ITranslation;
import com.axelor.apps.tool.date.DurationTool;
import com.axelor.db.Query;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.beust.jcommander.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class AppRossumServiceImpl implements AppRossumService {

  protected AppRossumRepository appRossumRepository;
  protected OrganisationRepository organisationRepo;
  protected WorkspaceRepository workspaceRepo;
  protected QueueRepository queueRepo;
  protected SchemaRepository schemaRepo;

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  protected static final String API_URL = "https://api.elis.rossum.ai";
  protected String username;
  protected String password;
  protected String token;
  protected CloseableHttpClient httpClient = HttpClients.createDefault();
  protected CloseableHttpResponse response;

  @Inject
  public AppRossumServiceImpl(
      AppRossumRepository appRossumRepository,
      OrganisationRepository organisationRepo,
      WorkspaceRepository workspaceRepo,
      QueueRepository queueRepo,
      SchemaRepository schemaRepo) {
    this.appRossumRepository = appRossumRepository;
    this.organisationRepo = organisationRepo;
    this.workspaceRepo = workspaceRepo;
    this.queueRepo = queueRepo;
    this.schemaRepo = schemaRepo;
  }

  @Override
  public AppRossum getAppRossum() {
    return Query.of(AppRossum.class).cacheable().fetchOne();
  }

  @Override
  public JSONObject extractInvoiceDataJson(
      MetaFile metaFile, Integer timeout, Queue queue, String exportTypeSelect)
      throws AxelorException, IOException, InterruptedException, JSONException {

    this.login(getAppRossum());

    JSONObject jsonData = submitInvoiceAndExtractData(metaFile, timeout, queue);

    String uri =
        String.format(
            "%s" + "%s" + "%s" + "%s" + "%s",
            jsonData.getString("queue"),
            "/export?format=",
            exportTypeSelect,
            "&id=",
            jsonData.get("id").toString());

    HttpGet httpGet = new HttpGet(uri);
    httpGet.addHeader("Authorization", "token " + token);
    httpGet.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());

    response = httpClient.execute(httpGet);

    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK
        && response.getEntity() != null) {

      JSONObject resultObject = new JSONObject(EntityUtils.toString(response.getEntity()));

      jsonData = resultObject.getJSONArray("results").getJSONObject(0);
    } else {
      jsonData = null;
    }

    return jsonData;
  }

  @Override
  public File extractInvoiceDataMetaFile(
      MetaFile metaFile, Integer timeout, Queue queue, String exportTypeSelect)
      throws AxelorException, IOException, InterruptedException, JSONException {
    this.login(getAppRossum());

    JSONObject jsonData = submitInvoiceAndExtractData(metaFile, timeout, queue);

    String uri =
        String.format(
            "%s" + "%s" + "%s" + "%s" + "%s",
            jsonData.getString("queue"),
            "/export?format=",
            exportTypeSelect,
            "&id=",
            jsonData.get("id").toString());

    HttpGet httpGet = new HttpGet(uri);
    httpGet.addHeader("Authorization", "token " + token);

    if (exportTypeSelect.equals(InvoiceOcrTemplateRepository.EXPORT_TYPE_SELECT_CSV)) {
      httpGet.addHeader(HTTP.CONTENT_TYPE, "text/csv");
    } else if (exportTypeSelect.equals(InvoiceOcrTemplateRepository.EXPORT_TYPE_SELECT_XML)) {
      httpGet.addHeader(HTTP.CONTENT_TYPE, ContentType.APPLICATION_XML.getMimeType());
    }

    response = httpClient.execute(httpGet);

    File file = null;

    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK
        && response.getEntity() != null) {
      String content = EntityUtils.toString(response.getEntity());

      String defaultPath = AppSettings.get().getPath("file.upload.dir", "");
      String dirPath = defaultPath + "/rossum";

      File dir = new File(dirPath);
      if (!dir.isDirectory()) dir.mkdir();

      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
      String logFileName =
          "Rossum_export_" + LocalDateTime.now().format(formatter) + "." + exportTypeSelect;

      file = new File(dir.getAbsolutePath(), logFileName);
      PrintWriter pw = new PrintWriter(file);

      pw.write(content);
      pw.close();
    }

    return file;
  }

  private JSONObject submitInvoiceAndExtractData(MetaFile metaFile, Integer timeout, Queue queue)
      throws AxelorException, IOException, JSONException, InterruptedException {
    JSONObject result = null;

    if (metaFile != null
        && (metaFile.getFileType().equals(ITranslation.FILE_TYPE_PDF)
            || metaFile.getFileType().equals(ITranslation.FILE_TYPE_PNG)
            || metaFile.getFileType().equals(ITranslation.FILE_TYPE_JPEG))) {

      response = httpClient.execute(httpPost(metaFile, queue));

      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED) {
        result =
            new JSONObject(
                IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset()));
        String annotationsLink = result.getString("annotation");
        log.debug("Submit result: " + result);

        log.debug("Annotation link: " + annotationsLink);

        int sleepMillis = 5000;
        timeout = (int) ((timeout * 1e3) / sleepMillis);
        result = getDocumentWithStatus(annotationsLink, timeout, sleepMillis);
        log.debug("Document successfully processed.");
      } else {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.DOCUMENT_PROCESS_ERROR),
            metaFile.getFileName());
      }
    } else {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.ROSSUM_FILE_ERROR));
    }

    return result;
  }

  private JSONObject getDocumentWithStatus(String annotationsLink, int maxRetries, int sleepMillis)
      throws JSONException, AxelorException, IOException, InterruptedException {

    for (int i = 0; i < maxRetries; i++) {
      log.debug(
          String.format(
              "%s" + "%s" + "%s" + "%s" + "%s",
              "Waiting... ",
              (i * sleepMillis * 1e-3),
              " s / ",
              (maxRetries * sleepMillis * 1e-3),
              " s"));

      HttpGet httpGet = new HttpGet(annotationsLink);
      httpGet.addHeader("Authorization", "token " + token);
      httpGet.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());

      response = httpClient.execute(httpGet);
      JSONObject result =
          new JSONObject(
              IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset()));

      String status = result.getString("status");
      switch (status) {
        case "exported":
          return result;
        case "error":
          log.debug("Result: " + result);

          throw new AxelorException(
              TraceBackRepository.CATEGORY_INCONSISTENCY,
              I18n.get(IExceptionMessage.DOCUMENT_PROCESS_ERROR),
              result.getString("message"));
        default:
          log.debug("Result: " + result);
          Thread.sleep(sleepMillis);
          break;
      }
      httpGet.abort();
    }
    throw new AxelorException(
        TraceBackRepository.CATEGORY_NO_VALUE,
        I18n.get(IExceptionMessage.TIMEOUT_ERROR),
        (int) (maxRetries * sleepMillis * 1e-3));
  }

  private HttpPost httpPost(MetaFile metaFile, Queue queue) {

    String filePath = MetaFiles.getPath(metaFile).toString();
    log.debug("Submitting File: " + filePath);
    log.debug("Content type: " + metaFile.getFileType());

    String url =
        String.format(
            API_URL + "%s" + "%s" + "%s", "/v1/queues/", queue.getQueueId().toString(), "/upload");
    HttpPost httpPost = new HttpPost(url);
    httpPost.setHeader("Authorization", "token " + token);
    httpPost.setHeader(
        "Content-Disposition", String.format("attachment; filename=%s", metaFile.getFileName()));
    httpPost.setHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
    File fileToUse = new File(filePath);

    FileEntity params = new FileEntity(fileToUse);
    httpPost.setEntity(params);

    return httpPost;
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

  @Override
  @Transactional
  public void login(AppRossum appRossum) throws IOException, JSONException, AxelorException {

    if (appRossum == null || appRossum.getUsername() == null || appRossum.getPassword() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.INVALID_USERNAME_OR_PASSWORD));
    }

    token =
        appRossum.getTokenDateTime() != null
                && (DurationTool.computeDuration(appRossum.getTokenDateTime(), LocalDateTime.now())
                        .toHours()
                    <= 162)
            ? appRossum.getToken()
            : null;

    if (Strings.isStringEmpty(token)) {
      username = appRossum.getUsername();
      password = appRossum.getPassword();

      HttpPost httpPost = new HttpPost(String.format(API_URL + "%s", "/v1/auth/login"));

      StringEntity params =
          new StringEntity(
              String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password));
      httpPost.addHeader("content-type", "application/json");
      httpPost.setEntity(params);

      response = httpClient.execute(httpPost);

      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK
          && response.getEntity() != null) {

        JSONObject obj = new JSONObject(EntityUtils.toString(response.getEntity()));

        token = obj.getString("key");
        appRossum.setToken(token);
        appRossum.setTokenDateTime(LocalDateTime.now());
        appRossum.setIsValid(Boolean.TRUE);
        appRossumRepository.save(appRossum);
      } else {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_NO_VALUE,
            I18n.get(IExceptionMessage.INVALID_USERNAME_OR_PASSWORD));
      }
    }
  }

  @Override
  @Transactional
  public void reset(AppRossum appRossum) {
    appRossum.setToken(null);
    appRossum.setTokenDateTime(null);
    appRossum.setIsValid(Boolean.FALSE);

    appRossumRepository.save(appRossum);
    Beans.get(InvoiceOcrTemplateRepository.class).all().update("queue", null);
    queueRepo.all().remove();
    schemaRepo.all().remove();
    workspaceRepo.all().remove();
    organisationRepo.all().remove();
  }
}
