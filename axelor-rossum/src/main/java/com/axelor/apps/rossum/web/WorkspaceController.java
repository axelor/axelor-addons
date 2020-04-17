package com.axelor.apps.rossum.web;

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
}
