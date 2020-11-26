/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
/*
// * Axelor Business Solutions
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
import com.axelor.apps.rossum.db.repo.AnnotationRepository;
import com.axelor.apps.rossum.db.repo.InvoiceOcrTemplateRepository;
import com.axelor.apps.rossum.db.repo.OrganisationRepository;
import com.axelor.apps.rossum.db.repo.QueueRepository;
import com.axelor.apps.rossum.db.repo.SchemaRepository;
import com.axelor.apps.rossum.db.repo.WorkspaceRepository;
import com.axelor.apps.rossum.exception.IExceptionMessage;
import com.axelor.apps.rossum.translation.ITranslation;
import com.axelor.apps.tool.date.DurationTool;
import com.axelor.common.StringUtils;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
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
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class AppRossumServiceImpl implements AppRossumService {

  protected AppRossumRepository appRossumRepository;
  protected OrganisationRepository organisationRepo;
  protected WorkspaceRepository workspaceRepo;
  protected QueueRepository queueRepo;
  protected SchemaRepository schemaRepo;
  protected AnnotationRepository annotationRepo;

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
      SchemaRepository schemaRepo,
      AnnotationRepository annotationRepo) {
    this.appRossumRepository = appRossumRepository;
    this.organisationRepo = organisationRepo;
    this.workspaceRepo = workspaceRepo;
    this.queueRepo = queueRepo;
    this.schemaRepo = schemaRepo;
    this.annotationRepo = annotationRepo;
  }

  @Override
  public AppRossum getAppRossum() {
    return Query.of(AppRossum.class).cacheable().fetchOne();
  }

  @Override
  public Map<MetaFile, Pair<String, File>> extractInvoiceDataMetaFile(
      List<MetaFile> metaFileList, Integer timeout, Queue queue, String exportTypeSelect)
      throws AxelorException, IOException, InterruptedException, JSONException {
    this.login(getAppRossum());

    Map<MetaFile, Pair<String, JSONObject>> metaFileAnnotationLinkJSONPairMap =
        submitInvoiceAndExtractData(metaFileList, timeout, queue);

    Map<MetaFile, Pair<String, File>> metaFileAnnotationLinkFilePairMap = new HashMap<>();

    Set<MetaFile> metaFileSet = metaFileAnnotationLinkJSONPairMap.keySet();

    for (MetaFile metaFile : metaFileSet) {
      JSONObject jsonData = metaFileAnnotationLinkJSONPairMap.get(metaFile).getRight();

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

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyyHHmm");
        String fileName =
            "Rossum_export_"
                + jsonData.get("id").toString()
                + "_"
                + LocalDateTime.now().format(formatter)
                + "."
                + exportTypeSelect;

        file = new File(dir.getAbsolutePath(), fileName);
        PrintWriter pw = new PrintWriter(file);

        pw.write(content);
        pw.close();
      }

      metaFileAnnotationLinkFilePairMap.put(
          metaFile, Pair.of(metaFileAnnotationLinkJSONPairMap.get(metaFile).getLeft(), file));
      httpGet.abort();
    }

    return metaFileAnnotationLinkFilePairMap;
  }

  @Transactional(
      rollbackOn = {
        AxelorException.class,
        IOException.class,
        JSONException.class,
        InterruptedException.class
      })
  protected Map<MetaFile, Pair<String, JSONObject>> submitInvoiceAndExtractData(
      List<MetaFile> metaFileList, Integer timeout, Queue queue)
      throws AxelorException, IOException, JSONException, InterruptedException {

    if (queue == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, "Queue is missing");
    }

    Map<String, MetaFile> annotationsLinkMetaFileMap = new HashMap<>();

    for (MetaFile metaFile : metaFileList) {
      if (metaFile != null
          && (metaFile.getFileType().equals(ITranslation.FILE_TYPE_PDF)
              || metaFile.getFileType().equals(ITranslation.FILE_TYPE_PNG)
              || metaFile.getFileType().equals(ITranslation.FILE_TYPE_JPEG))) {

        response = httpClient.execute(httpPost(metaFile, queue));

        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED) {
          JSONObject result =
              new JSONObject(
                  IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset()));
          String annotationsLink = result.getString("annotation");
          log.debug("Submit result: " + result);

          log.debug("Annotation link: " + annotationsLink);

          annotationsLinkMetaFileMap.put(annotationsLink, metaFile);
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
    }

    int sleepMillis = 5000;
    timeout = (int) ((timeout * 1e3) / sleepMillis);

    Map<MetaFile, Pair<String, JSONObject>> metaFileAnnotationLinkJSONPairMap =
        getDocumentWithStatus(annotationsLinkMetaFileMap, timeout, sleepMillis);
    log.debug("Document successfully processed.");

    return metaFileAnnotationLinkJSONPairMap;
  }

  protected Map<MetaFile, Pair<String, JSONObject>> getDocumentWithStatus(
      Map<String, MetaFile> annotationsLinkMetaFileMap, int maxRetries, int sleepMillis)
      throws JSONException, AxelorException, IOException, InterruptedException {

    Map<MetaFile, Pair<String, JSONObject>> metaFileAnnotationLinkJSONPairMap = new HashMap<>();

    Map<String, Boolean> annotationsLinkExportedMap = new HashMap<>();

    Collection<String> annotationsLinkList = annotationsLinkMetaFileMap.keySet();

    for (String annotationsLink : annotationsLinkList) {
      annotationsLinkExportedMap.put(annotationsLink, false);
    }

    for (int i = 0; i < maxRetries; i++) {
      log.debug(
          String.format(
              "%s" + "%s" + "%s" + "%s" + "%s",
              "Waiting... ",
              (i * sleepMillis * 1e-3),
              " s / ",
              (maxRetries * sleepMillis * 1e-3),
              " s"));

      for (String annotationsLink : annotationsLinkExportedMap.keySet()) {
        if (Boolean.FALSE.equals(annotationsLinkExportedMap.get(annotationsLink))) {
          HttpGet httpGet = new HttpGet(annotationsLink);
          httpGet.addHeader("Authorization", "token " + token);
          httpGet.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());

          response = httpClient.execute(httpGet);
          JSONObject result =
              new JSONObject(
                  IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset()));

          String annotationLinkStatus = result.getString("status");

          if (annotationLinkStatus.equals("confirmed") || annotationLinkStatus.equals("exported")) {
            metaFileAnnotationLinkJSONPairMap.put(
                annotationsLinkMetaFileMap.get(annotationsLink), Pair.of(annotationsLink, result));
            annotationsLinkExportedMap.put(annotationsLink, true);
          } else {
            log.debug("Result: " + result);
          }
          httpGet.abort();
        }
      }
      if (metaFileAnnotationLinkJSONPairMap.size() == annotationsLinkList.size()) {
        return metaFileAnnotationLinkJSONPairMap;
      } else {
        Thread.sleep(sleepMillis);
      }
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

    if (StringUtils.isEmpty(token)) {
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
    annotationRepo.all().remove();
    queueRepo.all().remove();
    schemaRepo.all().remove();
    workspaceRepo.all().remove();
    organisationRepo.all().remove();
  }
}
