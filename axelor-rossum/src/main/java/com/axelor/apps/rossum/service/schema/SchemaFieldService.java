package com.axelor.apps.rossum.service.schema;

import com.axelor.apps.rossum.db.Schema;
import com.axelor.apps.rossum.db.SchemaField;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public interface SchemaFieldService {

  public void updateSchemaContent(SchemaField schemaField) throws JSONException;

  public JSONObject findAndUpdateSchemaContent(
      String id, Boolean canExport, JSONObject resultObject, String parentSchemaFieldId)
      throws JSONException;

  public SchemaField findAndUpdateSchemaField(
      Schema schema, JSONObject childObject, String parentSchemaFieldId) throws JSONException;
}
