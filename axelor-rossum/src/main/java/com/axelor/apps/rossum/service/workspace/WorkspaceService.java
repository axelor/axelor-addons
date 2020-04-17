package com.axelor.apps.rossum.service.workspace;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Workspace;
import com.axelor.exception.AxelorException;
import java.io.IOException;
import wslite.json.JSONException;

public interface WorkspaceService {

  public void updateJsonData(Workspace workspace) throws JSONException;

  public void getWorkspaces(AppRossum appRossum) throws IOException, JSONException, AxelorException;

  public void updateWorkspace(AppRossum appRossum, Workspace workspace)
      throws IOException, JSONException, AxelorException;
}
