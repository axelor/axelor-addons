package com.axelor.apps.rossum.web;

import com.axelor.apps.rossum.db.SchemaField;
import com.axelor.apps.rossum.db.repo.SchemaFieldRepository;
import com.axelor.apps.rossum.service.schema.SchemaFieldService;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import wslite.json.JSONException;

public class SchemaFieldController {

  public void updateSchmeaContent(ActionRequest request, ActionResponse response) {
    try {
      SchemaField schemaField =
          Beans.get(SchemaFieldRepository.class)
              .find(request.getContext().asType(SchemaField.class).getId());
      Beans.get(SchemaFieldService.class).updateSchemaContent(schemaField);

      response.setReload(true);
    } catch (JSONException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
