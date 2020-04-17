package com.axelor.apps.rossum.service.schema;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Schema;
import com.axelor.apps.rossum.db.repo.SchemaRepository;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import org.apache.commons.httpclient.HttpStatus;
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

public class SchemaServiceImpl implements SchemaService {

  protected static final String API_URL = "https://api.elis.rossum.ai";
  protected CloseableHttpClient httpClient = HttpClients.createDefault();
  protected CloseableHttpResponse response;

  protected SchemaRepository schemaRepo;
  protected AppRossumService appRossumService;

  @Inject
  public SchemaServiceImpl(SchemaRepository schemaRepo, AppRossumService appRossumService) {
    this.schemaRepo = schemaRepo;
    this.appRossumService = appRossumService;
  }

  @Override
  public void updateJsonData(Schema schema) throws JSONException {
    String schemeResult = schema.getSchemaResult();

    JSONObject schemaObject = new JSONObject(schemeResult);
    schemaObject.put("name", schema.getSchemaName());

    schema.setSchemaResult(schemaObject.toString());
  }

  @Override
  @Transactional
  public void getSchemas(AppRossum appRossum) throws IOException, JSONException, AxelorException {
    appRossumService.login(appRossum);

    HttpGet httpGet = new HttpGet(String.format(API_URL + "%s", "/v1/schemas"));
    httpGet.addHeader("Authorization", "token " + appRossum.getToken());
    httpGet.addHeader("Accept", "application/json");

    response = httpClient.execute(httpGet);

    if (response.getEntity() != null) {

      JSONObject obj = new JSONObject(EntityUtils.toString(response.getEntity()));
      JSONArray resultsArray = obj.getJSONArray("results");

      for (Integer i = 0; i < resultsArray.length(); i++) {
        JSONObject resultObject = resultsArray.getJSONObject(i);
        String schemaUrl = resultObject.getString("url");

        Schema schema =
            schemaRepo.findByUrl(schemaUrl) != null
                ? schemaRepo.findByUrl(schemaUrl)
                : new Schema();

        httpGet = new HttpGet(schemaUrl);
        httpGet.addHeader("Authorization", "token " + appRossum.getToken());
        httpGet.addHeader("Accept", "application/json");

        response = httpClient.execute(httpGet);

        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK
            && response.getEntity() != null) {

          resultObject = new JSONObject(EntityUtils.toString(response.getEntity()));
          Integer schemaId = resultObject.getInt("id");
          String schemaName = resultObject.getString("name");

          schema.setSchemaId(schemaId);
          schema.setSchemaName(schemaName);
          schema.setSchemaUrl(schemaUrl);
          schema.setSchemaResult(resultObject.toString());
          schemaRepo.save(schema);
        }
      }
    }
  }

  @Override
  public void updateSchema(AppRossum appRossum, Schema schema)
      throws IOException, JSONException, AxelorException {
    appRossumService.login(appRossum);

    HttpPut httpPut =
        new HttpPut(String.format(API_URL + "%s", "/v1/schemas/" + schema.getSchemaId()));
    httpPut.addHeader("Authorization", "token " + appRossum.getToken());
    httpPut.addHeader(HTTP.CONTENT_TYPE, "application/json");

    StringEntity stringEntity = new StringEntity(schema.getSchemaResult());
    httpPut.setEntity(stringEntity);

    response = httpClient.execute(httpPut);
    this.getSchemas(appRossum);
  }
}
