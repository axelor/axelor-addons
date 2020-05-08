package com.axelor.apps.rossum.service.schema;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Schema;
import com.axelor.exception.AxelorException;
import java.io.IOException;
import wslite.json.JSONException;

public interface SchemaService {

  public void updateJsonData(Schema schema) throws JSONException;

  public void getSchemas(AppRossum appRossum) throws IOException, JSONException, AxelorException;

  public void updateSchema(AppRossum appRossum, Schema schema)
      throws IOException, JSONException, AxelorException;

  public void createSchema(Schema schema) throws IOException, JSONException, AxelorException;

  public void updateSchemaContent(Schema schema) throws JSONException;
}
