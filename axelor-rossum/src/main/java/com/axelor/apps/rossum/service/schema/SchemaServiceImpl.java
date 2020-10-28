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
package com.axelor.apps.rossum.service.schema;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Schema;
import com.axelor.apps.rossum.db.SchemaField;
import com.axelor.apps.rossum.db.repo.SchemaRepository;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.exception.AxelorException;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
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

public class SchemaServiceImpl implements SchemaService {

  protected static final String API_URL = "https://api.elis.rossum.ai";
  protected CloseableHttpClient httpClient = HttpClients.createDefault();
  protected CloseableHttpResponse response;

  protected SchemaRepository schemaRepo;
  protected AppRossumService appRossumService;
  protected SchemaFieldService schemaFieldService;

  protected static final String SCHEMA_CHILDREN = "children";

  @Inject
  public SchemaServiceImpl(
      SchemaRepository schemaRepo,
      AppRossumService appRossumService,
      SchemaFieldService schemaFieldService) {
    this.schemaRepo = schemaRepo;
    this.appRossumService = appRossumService;
    this.schemaFieldService = schemaFieldService;
  }

  @Override
  public void updateJsonData(Schema schema) throws JSONException {
    String schemeResult = schema.getSchemaResult();

    if (!Strings.isNullOrEmpty(schemeResult)) {
      JSONObject schemaObject = new JSONObject(schemeResult);
      schemaObject.put("name", schema.getSchemaName());

      schema.setSchemaResult(schemaObject.toString());
    }
  }

  @Override
  @Transactional(rollbackOn = {IOException.class, JSONException.class, AxelorException.class})
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
          this.generateSchemaFields(schema);
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

  @Override
  @Transactional(rollbackOn = {IOException.class, JSONException.class, AxelorException.class})
  public void createSchema(Schema schema) throws IOException, JSONException, AxelorException {
    AppRossum appRossum = appRossumService.getAppRossum();
    appRossumService.login(appRossum);

    JSONObject jsonObject = new JSONObject();
    jsonObject.put("name", schema.getSchemaName());
    jsonObject.put("template_name", schema.getSchemaTemplateSelect());

    HttpPost httpPost = new HttpPost(String.format(API_URL + "%s", "/v1/schemas/from_template"));
    httpPost.addHeader("Authorization", "token " + appRossum.getToken());
    httpPost.addHeader(HTTP.CONTENT_TYPE, "application/json");

    StringEntity stringEntity = new StringEntity(jsonObject.toString());
    httpPost.setEntity(stringEntity);

    response = httpClient.execute(httpPost);

    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED) {
      JSONObject resultObject =
          new JSONObject(
              IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset()));

      String schemaUrl = resultObject.getString("url");
      Integer schemaId = resultObject.getInt("id");
      String schemaName = resultObject.getString("name");

      schema = schemaRepo.find(schema.getId());
      schema.setSchemaId(schemaId);
      schema.setSchemaName(schemaName);
      schema.setSchemaUrl(schemaUrl);
      schema.setSchemaResult(resultObject.toString());
      schemaRepo.save(schema);
    }
  }

  public void generateSchemaFields(Schema schema) throws JSONException {

    JSONObject jsonObject = new JSONObject(schema.getSchemaResult());
    JSONArray contentArray = jsonObject.getJSONArray("content");

    for (int i = 0; i < contentArray.size(); i++) {
      JSONObject contentObject = contentArray.getJSONObject(i);
      JSONArray childrenArray = contentObject.getJSONArray(SCHEMA_CHILDREN);

      for (int j = 0; j < childrenArray.size(); j++) {
        JSONObject childObject = childrenArray.getJSONObject(j);
        String schemaFieldId = childObject.getString("id");

        if (!Strings.isNullOrEmpty(schemaFieldId)) {
          if (schemaFieldId.equals("tax_details") || schemaFieldId.equals("line_items")) {

            JSONArray subChildArray =
                childObject.getJSONObject(SCHEMA_CHILDREN).getJSONArray(SCHEMA_CHILDREN);

            for (int k = 0; k < subChildArray.size(); k++) {
              JSONObject subChildObject = subChildArray.getJSONObject(k);
              schemaFieldService.findAndUpdateSchemaField(schema, subChildObject, schemaFieldId);
            }

          } else {
            schemaFieldService.findAndUpdateSchemaField(schema, childObject, null);
          }
        }
      }
    }
  }

  @Override
  @Transactional(rollbackOn = {JSONException.class})
  public void updateSchemaContent(Schema schema) throws JSONException {
    List<SchemaField> schemaFieldList = schema.getSchemaFieldList();

    if (CollectionUtils.isNotEmpty(schemaFieldList)) {
      JSONObject resultObject = new JSONObject(schema.getSchemaResult());

      for (SchemaField schemaField : schemaFieldList) {
        resultObject =
            schemaFieldService.findAndUpdateSchemaContent(
                schemaField.getSchemaFieldId(),
                schemaField.getCanExport(),
                resultObject,
                schemaField.getParentSchemaFieldId());
      }

      schema.setSchemaResult(resultObject.toString());
      schemaRepo.save(schema);
    }
  }
}
