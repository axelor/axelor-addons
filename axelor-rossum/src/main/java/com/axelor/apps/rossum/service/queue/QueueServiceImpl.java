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
package com.axelor.apps.rossum.service.queue;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Queue;
import com.axelor.apps.rossum.db.repo.QueueRepository;
import com.axelor.apps.rossum.db.repo.SchemaRepository;
import com.axelor.apps.rossum.db.repo.WorkspaceRepository;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.exception.AxelorException;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.nio.charset.Charset;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
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

    if (!Strings.isNullOrEmpty(queueResult)) {
      JSONObject queueObject = new JSONObject(queueResult);
      queueObject.put("name", queue.getQueueName());
      queueObject.put("automation_level", queue.getAutomationLevelSelect());
      queueObject.put(
          "automation_enabled",
          queue.getAutomationLevelSelect().equals("never") ? Boolean.FALSE : Boolean.TRUE);
      queueObject.put("use_confirmed_state", queue.getUseConfirmedState());
      queue.setQueueResult(queueObject.toString());
    }
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
  @Transactional(rollbackOn = {IOException.class, JSONException.class, AxelorException.class})
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
        Boolean useConfirmedState = resultObject.getBoolean("use_confirmed_state");

        Queue queue =
            queueRepo.findByUrl(queueUrl) != null ? queueRepo.findByUrl(queueUrl) : new Queue();
        queue.setQueueId(queueId);
        queue.setQueueName(queueName);
        queue.setQueueUrl(queueUrl);
        queue.setWorkspaceUrl(workspaceRepo.findByUrl(workspaceUrl));
        queue.setSchemaUrl(schemaRepo.findByUrl(schemaUrl));
        queue.setQueueResult(resultObject.toString());
        queue.setAutomationLevelSelect(automationLevelSelect);
        queue.setUseConfirmedState(useConfirmedState);
        queueRepo.save(queue);
      }
    }
  }

  @Override
  @Transactional(rollbackOn = {IOException.class, JSONException.class, AxelorException.class})
  public void createQueue(Queue queue) throws IOException, JSONException, AxelorException {
    AppRossum appRossum = appRossumService.getAppRossum();

    appRossumService.login(appRossum);

    JSONObject jsonObject = new JSONObject();
    jsonObject.put("name", queue.getQueueName());
    jsonObject.put("workspace", queue.getWorkspaceUrl().getWorkspaceUrl());
    jsonObject.put("schema", queue.getSchemaUrl().getSchemaUrl());

    HttpPost httpPost = new HttpPost(String.format(API_URL + "%s", "/v1/queues"));
    httpPost.addHeader("Authorization", "token " + appRossum.getToken());
    httpPost.addHeader(HTTP.CONTENT_TYPE, "application/json");

    StringEntity stringEntity = new StringEntity(jsonObject.toString());
    httpPost.setEntity(stringEntity);

    response = httpClient.execute(httpPost);

    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED) {
      JSONObject resultObject =
          new JSONObject(
              IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset()));

      String queueUrl = resultObject.getString("url");

      Integer queueId = resultObject.getInt("id");
      String queueName = resultObject.getString("name");
      String workspaceUrl = resultObject.getString("workspace");
      String schemaUrl = resultObject.getString("schema");
      String automationLevelSelect = resultObject.getString("automation_level");
      Boolean useConfirmedState = resultObject.getBoolean("use_confirmed_state");

      queue.setQueueId(queueId);
      queue.setQueueName(queueName);
      queue.setQueueUrl(queueUrl);
      queue.setWorkspaceUrl(workspaceRepo.findByUrl(workspaceUrl));
      queue.setSchemaUrl(schemaRepo.findByUrl(schemaUrl));
      queue.setQueueResult(resultObject.toString());
      queue.setAutomationLevelSelect(automationLevelSelect);
      queue.setUseConfirmedState(useConfirmedState);
      queueRepo.save(queue);
    }
  }
}
