/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.rossum.service;

import com.axelor.apps.rossum.db.Organisation;
import com.axelor.apps.rossum.db.RossumAccount;
import com.axelor.apps.rossum.db.repo.OrganisationRepository;
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

  protected CloseableHttpClient httpClient = HttpClients.createDefault();
  protected CloseableHttpResponse response;

  protected OrganisationRepository organisationRepo;
  protected RossumAccountService rossumAccountService;

  @Inject
  public OrganisationServiceImpl(
      OrganisationRepository organisationRepo, RossumAccountService rossumAccountService) {
    this.organisationRepo = organisationRepo;
    this.rossumAccountService = rossumAccountService;
  }

  @Override
  @Transactional
  public void getOrganisations(RossumAccount rossumAccount)
      throws IOException, JSONException, AxelorException {
    rossumAccountService.login(rossumAccount);

    HttpGet httpGet =
        new HttpGet(String.format(RossumAccountService.API_URL + "%s", "/v1/organizations"));
    httpGet.addHeader("Authorization", "token " + rossumAccount.getToken());
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
        organisation.setRossumAccount(rossumAccount);
        organisationRepo.save(organisation);
      }
    }
  }
}
