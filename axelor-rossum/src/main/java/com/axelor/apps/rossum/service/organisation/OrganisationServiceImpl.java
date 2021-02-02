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
package com.axelor.apps.rossum.service.organisation;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Organisation;
import com.axelor.apps.rossum.db.repo.OrganisationRepository;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class OrganisationServiceImpl implements OrganisationService {

  protected static final String API_URL = "https://api.elis.rossum.ai";
  protected CloseableHttpClient httpClient = HttpClients.createDefault();
  protected CloseableHttpResponse response;

  protected OrganisationRepository organisationRepo;
  protected AppRossumService appRossumService;

  @Inject
  public OrganisationServiceImpl(
      OrganisationRepository organisationRepo, AppRossumService appRossumService) {
    this.organisationRepo = organisationRepo;
    this.appRossumService = appRossumService;
  }

  @Override
  @Transactional
  public void getOrganisations(AppRossum appRossum)
      throws IOException, JSONException, AxelorException {
    appRossumService.login(appRossum);

    HttpGet httpGet = new HttpGet(String.format(API_URL + "%s", "/v1/organizations"));
    httpGet.addHeader("Authorization", "token " + appRossum.getToken());
    httpGet.addHeader("Accept", "application/json");

    response = httpClient.execute(httpGet);

    if (response.getEntity() != null) {

      JSONObject obj = new JSONObject(EntityUtils.toString(response.getEntity()));
      JSONArray resultsArray = obj.getJSONArray("results");

      for (Integer i = 0; i < resultsArray.length(); i++) {
        JSONObject resultObject = resultsArray.getJSONObject(i);
        String organisationUrl = resultObject.getString("url");

        Organisation organisation =
            organisationRepo.findByUrl(organisationUrl) != null
                ? organisationRepo.findByUrl(organisationUrl)
                : new Organisation();

        Integer organisationId = resultObject.getInt("id");
        String organisationName = resultObject.getString("name");

        organisation.setOrganisationId(organisationId);
        organisation.setOrganisationName(organisationName);
        organisation.setOrganisationUrl(organisationUrl);
        organisation.setOrganisationResult(resultObject.toString());
        organisationRepo.save(organisation);
      }
    }
  }
}
