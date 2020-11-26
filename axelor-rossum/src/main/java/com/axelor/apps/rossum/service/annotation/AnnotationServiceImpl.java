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
package com.axelor.apps.rossum.service.annotation;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Annotation;
import com.axelor.apps.rossum.db.repo.AnnotationRepository;
import com.axelor.apps.rossum.db.repo.QueueRepository;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.exception.AxelorException;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class AnnotationServiceImpl implements AnnotationService {

  protected AppRossumService appRossumService;
  protected QueueRepository queueRepo;
  protected AnnotationRepository annotationRepo;

  @Inject
  public AnnotationServiceImpl(
      AppRossumService appRossumService,
      QueueRepository queueRepo,
      AnnotationRepository annotationRepo) {
    this.appRossumService = appRossumService;
    this.queueRepo = queueRepo;
    this.annotationRepo = annotationRepo;
  }

  @SuppressWarnings("static-access")
  @Override
  public void getAnnotations(AppRossum appRossum)
      throws IOException, JSONException, AxelorException {
    appRossumService.login(appRossum);

    String uri = String.format(appRossumService.API_URL + "%s", "/v1/annotations");
    String token = appRossum.getToken();

    CloseableHttpClient httpClient = appRossumService.httpClient;

    this.checkNext(uri, token, null, httpClient);
  }

  protected void checkNext(
      String uri, String token, JSONObject object, CloseableHttpClient httpClient)
      throws JSONException, ClientProtocolException, IOException {

    uri =
        Strings.isNullOrEmpty(uri)
            ? object.getJSONObject("pagination").isNull("next")
                ? null
                : object.getJSONObject("pagination").getString("next")
            : uri;

    if (!Strings.isNullOrEmpty(uri)) {

      HttpGet httpGet = new HttpGet(uri);
      httpGet.addHeader("Authorization", "token " + token);
      httpGet.addHeader("Accept", "application/json");
      CloseableHttpResponse response = httpClient.execute(httpGet);

      if (response.getEntity() != null) {
        JSONObject obj = new JSONObject(EntityUtils.toString(response.getEntity()));
        this.createOrUpdateAnnotations(obj);
        this.checkNext(null, token, obj, httpClient);
      }
      httpGet.completed();
    }
  }

  protected void createOrUpdateAnnotations(JSONObject obj) throws JSONException {
    JSONArray resultsArray = obj.getJSONArray("results");

    for (Integer i = 0; i < resultsArray.length(); i++) {
      JSONObject resultObject = resultsArray.getJSONObject(i);
      this.getAnnotationFromJSONObject(resultObject);
    }
  }

  @Override
  public void exportAnnotation(Annotation annotation, String invOcrTemplateName)
      throws IOException, JSONException, AxelorException {
    AppRossum appRossum = appRossumService.getAppRossum();
    appRossumService.login(appRossum);

    JSONObject annotationObject = new JSONObject(annotation.getAnnotationResult());
    annotationObject.get("document");
  }

  @SuppressWarnings("static-access")
  @Override
  public void createOrUpdateAnnotationFromLink(String annotationLink)
      throws IOException, JSONException, AxelorException {
    AppRossum appRossum = appRossumService.getAppRossum();
    appRossumService.login(appRossum);

    CloseableHttpClient httpClient = appRossumService.httpClient;
    HttpGet httpGet = new HttpGet(annotationLink);
    httpGet.addHeader("Authorization", "token " + appRossum.getToken());
    httpGet.addHeader("Accept", "application/json");
    CloseableHttpResponse response = httpClient.execute(httpGet);

    if (response.getEntity() != null) {
      JSONObject obj = new JSONObject(EntityUtils.toString(response.getEntity()));
      this.getAnnotationFromJSONObject(obj);
    }
  }

  @Transactional(rollbackOn = {IOException.class, JSONException.class, AxelorException.class})
  protected Annotation getAnnotationFromJSONObject(JSONObject resultObject) throws JSONException {
    String annotationUrl = resultObject.getString("url");

    Integer annotationId = resultObject.getInt("id");
    String statusSelect = resultObject.getString("status");
    String queueUrl = resultObject.getString("queue");

    Annotation annotation =
        annotationRepo.findByUrl(annotationUrl) != null
            ? annotationRepo.findByUrl(annotationUrl)
            : new Annotation();

    annotation.setAnnotationId(annotationId);
    annotation.setAnnotationUrl(annotationUrl);
    annotation.setStatusSelect(statusSelect);
    annotation.setQueueUrl(queueRepo.findByUrl(queueUrl));
    annotation.setAnnotationResult(resultObject.toString());
    return annotationRepo.save(annotation);
  }
}
