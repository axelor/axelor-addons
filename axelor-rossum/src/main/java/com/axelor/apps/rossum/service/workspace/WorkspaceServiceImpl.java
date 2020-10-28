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
package com.axelor.apps.rossum.service.workspace;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Workspace;
import com.axelor.apps.rossum.db.repo.OrganisationRepository;
import com.axelor.apps.rossum.db.repo.WorkspaceRepository;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
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

public class WorkspaceServiceImpl implements WorkspaceService {

  protected static final String API_URL = "https://api.elis.rossum.ai";
  protected CloseableHttpClient httpClient = HttpClients.createDefault();
  protected CloseableHttpResponse response;

  protected WorkspaceRepository workspaceRepo;
  protected OrganisationRepository organisationRepo;
  protected AppRossumService appRossumService;

  @Inject
  public WorkspaceServiceImpl(
      WorkspaceRepository workspaceRepo,
      OrganisationRepository organisationRepo,
      AppRossumService appRossumService) {
    this.workspaceRepo = workspaceRepo;
    this.organisationRepo = organisationRepo;
    this.appRossumService = appRossumService;
  }

  @Override
  public void updateJsonData(Workspace workspace) throws JSONException {
    String workspaceResult = workspace.getWorkspaceResult();

    JSONObject workspaceObject = new JSONObject(workspaceResult);
    workspaceObject.put("name", workspace.getWorkspaceName());

    workspace.setWorkspaceResult(workspaceObject.toString());
  }

  @Override
  @Transactional
  public void getWorkspaces(AppRossum appRossum)
      throws IOException, JSONException, AxelorException {
    appRossumService.login(appRossum);

    HttpGet httpGet = new HttpGet(String.format(API_URL + "%s", "/v1/workspaces"));
    httpGet.addHeader("Authorization", "token " + appRossum.getToken());
    httpGet.addHeader("Accept", "application/json");

    response = httpClient.execute(httpGet);

    if (response.getEntity() != null) {

      JSONObject obj = new JSONObject(EntityUtils.toString(response.getEntity()));
      JSONArray resultsArray = obj.getJSONArray("results");

      for (Integer i = 0; i < resultsArray.length(); i++) {
        JSONObject resultObject = resultsArray.getJSONObject(i);
        String workspaceUrl = resultObject.getString("url");

        Workspace workspace =
            workspaceRepo.findByUrl(workspaceUrl) != null
                ? workspaceRepo.findByUrl(workspaceUrl)
                : new Workspace();

        Integer workspaceId = resultObject.getInt("id");
        String workspaceName = resultObject.getString("name");
        String organisationUrl = resultObject.getString("organization");

        workspace.setWorkspaceId(workspaceId);
        workspace.setWorkspaceName(workspaceName);
        workspace.setWorkspaceUrl(workspaceUrl);
        workspace.setOrganisationUrl(organisationRepo.findByUrl(organisationUrl));
        workspace.setWorkspaceResult(resultObject.toString());
        workspaceRepo.save(workspace);
      }
    }
  }

  @Override
  public void updateWorkspace(AppRossum appRossum, Workspace workspace)
      throws IOException, JSONException, AxelorException {
    appRossumService.login(appRossum);

    HttpPut httpPut =
        new HttpPut(String.format(API_URL + "%s", "/v1/workspaces/" + workspace.getWorkspaceId()));

    httpPut.addHeader("Authorization", "token " + appRossum.getToken());
    httpPut.addHeader(HTTP.CONTENT_TYPE, "application/json");

    StringEntity stringEntity = new StringEntity(workspace.getWorkspaceResult());
    httpPut.setEntity(stringEntity);

    response = httpClient.execute(httpPut);
    this.getWorkspaces(appRossum);
  }
}
