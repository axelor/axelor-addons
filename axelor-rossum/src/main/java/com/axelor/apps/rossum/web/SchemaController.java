package com.axelor.apps.rossum.web;

import com.axelor.apps.rossum.db.Schema;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.apps.rossum.service.schema.SchemaService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.io.IOException;
import wslite.json.JSONException;

public class SchemaController {

  public void updateJsonData(ActionRequest request, ActionResponse response) {

    try {
      Schema schema = request.getContext().asType(Schema.class);

      Beans.get(SchemaService.class).updateJsonData(schema);
      response.setValues(schema);
    } catch (JSONException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void updateSchema(ActionRequest request, ActionResponse response) {

    try {
      Schema schema = request.getContext().asType(Schema.class);
      Beans.get(SchemaService.class)
          .updateSchema(Beans.get(AppRossumService.class).getAppRossum(), schema);

      response.setReload(true);
    } catch (IOException | JSONException | AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
