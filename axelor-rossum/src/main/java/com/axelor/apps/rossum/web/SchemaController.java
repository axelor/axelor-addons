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
package com.axelor.apps.rossum.web;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Schema;
import com.axelor.apps.rossum.db.repo.SchemaRepository;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.apps.rossum.service.schema.SchemaService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.io.IOException;
import org.apache.http.ParseException;
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

  public void createSchema(ActionRequest request, ActionResponse response) {

    try {
      Schema schema = request.getContext().asType(Schema.class);
      Beans.get(SchemaService.class).createSchema(schema);
      response.setReload(true);
    } catch (IOException | JSONException | AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void updateSchemaContent(ActionRequest request, ActionResponse response) {

    try {

      Beans.get(SchemaService.class)
          .updateSchemaContent(
              Beans.get(SchemaRepository.class)
                  .find(request.getContext().asType(Schema.class).getId()));

      response.setReload(true);
    } catch (JSONException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void getSchemas(ActionRequest request, ActionResponse response) {
    try {
      AppRossum appRossum = Beans.get(AppRossumService.class).getAppRossum();
      Beans.get(AppRossumService.class).login(appRossum);
      Beans.get(SchemaService.class).getSchemas(appRossum);

      response.setReload(true);
    } catch (ParseException | IOException | JSONException | AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
