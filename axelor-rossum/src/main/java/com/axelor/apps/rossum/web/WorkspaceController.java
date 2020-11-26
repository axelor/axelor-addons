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
import com.axelor.apps.rossum.db.Workspace;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.apps.rossum.service.workspace.WorkspaceService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.io.IOException;
import org.apache.http.ParseException;
import wslite.json.JSONException;

public class WorkspaceController {

  public void updateJsonData(ActionRequest request, ActionResponse response) {

    try {
      Workspace workspace = request.getContext().asType(Workspace.class);

      Beans.get(WorkspaceService.class).updateJsonData(workspace);
      response.setValues(workspace);
    } catch (JSONException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void updateWorkSpace(ActionRequest request, ActionResponse response) {

    try {
      Workspace workspace = request.getContext().asType(Workspace.class);

      Beans.get(WorkspaceService.class)
          .updateWorkspace(Beans.get(AppRossumService.class).getAppRossum(), workspace);

      response.setReload(true);
    } catch (IOException | JSONException | AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void getWorkspaces(ActionRequest request, ActionResponse response) {
    try {
      AppRossum appRossum = Beans.get(AppRossumService.class).getAppRossum();
      Beans.get(AppRossumService.class).login(appRossum);
      Beans.get(WorkspaceService.class).getWorkspaces(appRossum);

      response.setReload(true);
    } catch (ParseException | IOException | JSONException | AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
