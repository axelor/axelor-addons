package com.axelor.apps.rossum.service.annotation;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Annotation;
import com.axelor.apps.rossum.db.repo.AnnotationRepository;
import com.axelor.apps.rossum.db.repo.QueueRepository;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class AnnotaionServiceImpl implements AnnotationService {

  protected AppRossumService appRossumService;
  protected QueueRepository queueRepo;
  protected AnnotationRepository annotationRepo;

  @Inject
  public AnnotaionServiceImpl(
      AppRossumService appRossumService,
      QueueRepository queueRepo,
      AnnotationRepository annotationRepo) {
    this.appRossumService = appRossumService;
    this.queueRepo = queueRepo;
    this.annotationRepo = annotationRepo;
  }

  @SuppressWarnings("static-access")
  @Override
  @Transactional(rollbackOn = {IOException.class, JSONException.class, AxelorException.class})
  public void getAnnotations(AppRossum appRossum)
      throws IOException, JSONException, AxelorException {
    appRossumService.login(appRossum);

    HttpGet httpGet =
        new HttpGet(String.format(appRossumService.API_URL + "%s", "/v1/annotations"));
    httpGet.addHeader("Authorization", "token " + appRossum.getToken());
    httpGet.addHeader("Accept", "application/json");

    CloseableHttpClient httpClient = appRossumService.httpClient;

    CloseableHttpResponse response = httpClient.execute(httpGet);

    if (response.getEntity() != null) {

      JSONObject obj = new JSONObject(EntityUtils.toString(response.getEntity()));
      JSONArray resultsArray = obj.getJSONArray("results");

      for (Integer i = 0; i < resultsArray.length(); i++) {
        JSONObject resultObject = resultsArray.getJSONObject(i);
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
        annotationRepo.save(annotation);
      }
    }
  }
}
