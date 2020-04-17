package com.axelor.apps.rossum.service.queue;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Queue;
import com.axelor.apps.rossum.db.repo.QueueRepository;
import com.axelor.apps.rossum.db.repo.SchemaRepository;
import com.axelor.apps.rossum.db.repo.WorkspaceRepository;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class QueueServiceImpl implements QueueService {

  protected static final String API_URL = "https://api.elis.rossum.ai";
  protected CloseableHttpClient httpClient = HttpClients.createDefault();
  protected CloseableHttpResponse response;

  protected QueueRepository queueRepo;
  protected WorkspaceRepository workspaceRepo;
  protected SchemaRepository schemaRepo;
  protected AppRossumService appRossumService;

  @Inject
  public QueueServiceImpl(
      QueueRepository queueRepo,
      WorkspaceRepository workspaceRepo,
      SchemaRepository schemaRepo,
      AppRossumService appRossumService) {
    this.queueRepo = queueRepo;
    this.workspaceRepo = workspaceRepo;
    this.schemaRepo = schemaRepo;
    this.appRossumService = appRossumService;
  }

  @Override
  public void updateJsonData(Queue queue) throws JSONException {
    String queueResult = queue.getQueueResult();

    JSONObject queueObject = new JSONObject(queueResult);
    queueObject.put("name", queue.getQueueName());
    queueObject.put("automation_level", queue.getAutomationLevelSelect());

    queue.setQueueResult(queueObject.toString());
  }

  @Override
  public void updateQueue(AppRossum appRossum, Queue queue)
      throws IOException, JSONException, AxelorException {
    appRossumService.login(appRossum);

    HttpPut httpPut =
        new HttpPut(String.format(API_URL + "%s", "/v1/queues/" + queue.getQueueId()));
    httpPut.addHeader("Authorization", "token " + appRossum.getToken());
    httpPut.addHeader(HTTP.CONTENT_TYPE, "application/json");

    JSONObject queueUpdateObject = new JSONObject(queue.getQueueResult());
    queueUpdateObject.remove("connector");
    queueUpdateObject.remove("inbox");

    StringEntity stringEntity = new StringEntity(queueUpdateObject.toString());
    httpPut.setEntity(stringEntity);

    response = httpClient.execute(httpPut);
    this.getQueues(appRossum);
  }

  @Override
  @Transactional
  public void getQueues(AppRossum appRossum) throws IOException, JSONException, AxelorException {
    appRossumService.login(appRossum);

    HttpGet httpGet = new HttpGet(String.format(API_URL + "%s", "/v1/queues"));
    httpGet.addHeader("Authorization", "token " + appRossum.getToken());
    httpGet.addHeader("Accept", "application/json");

    response = httpClient.execute(httpGet);

    if (response.getEntity() != null) {

      JSONObject obj = new JSONObject(EntityUtils.toString(response.getEntity()));
      JSONArray resultsArray = obj.getJSONArray("results");

      for (Integer i = 0; i < resultsArray.length(); i++) {
        JSONObject resultObject = resultsArray.getJSONObject(i);
        String queueUrl = resultObject.getString("url");

        Integer queueId = resultObject.getInt("id");
        String queueName = resultObject.getString("name");
        String workspaceUrl = resultObject.getString("workspace");
        String schemaUrl = resultObject.getString("schema");
        String automationLevelSelect = resultObject.getString("automation_level");

        Queue queue =
            queueRepo.findByUrl(queueUrl) != null ? queueRepo.findByUrl(queueUrl) : new Queue();
        queue.setQueueId(queueId);
        queue.setQueueName(queueName);
        queue.setQueueUrl(queueUrl);
        queue.setWorkspaceUrl(workspaceRepo.findByUrl(workspaceUrl));
        queue.setSchemaUrl(schemaRepo.findByUrl(schemaUrl));
        queue.setQueueResult(resultObject.toString());
        queue.setAutomationLevelSelect(automationLevelSelect);
        queueRepo.save(queue);
      }
    }
  }
}
