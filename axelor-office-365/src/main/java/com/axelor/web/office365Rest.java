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
package com.axelor.web;

import com.axelor.apps.base.db.AppOffice365;
import com.axelor.apps.base.db.repo.AppOffice365Repository;
import com.axelor.apps.office.db.OfficeAccount;
import com.axelor.apps.office.db.repo.OfficeAccountRepository;
import com.axelor.apps.office365.service.Office365Service;
import com.axelor.exception.service.TraceBackService;
import com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.net.URI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("/office365")
public class office365Rest {

  private static final String APP_VIEW_SUFFIX = "/#/ds/office365.account/edit/";
  private static final String ERROR_VIEW_SUFFIX = "/#/ds/admin.root.maintenance.trace.back/list/1";
  private static final String WEB_SERVICE_SUFFIX = "/ws/";

  @Inject private AppOffice365Repository appOffice365Repo;
  @Inject protected OfficeAccountRepository officeAccountRepo;

  @Path("/authenticate")
  @GET
  @Transactional
  public Response authenticate(
      @QueryParam("code") String code,
      @QueryParam("error") String error,
      @QueryParam("error_description") String errorDescription,
      @QueryParam("state") String state,
      @Context UriInfo uriInfo) {

    String baseUri = uriInfo.getBaseUri().toString();
    try {
      AppOffice365 appOffice365 = appOffice365Repo.all().fetchOne();

      if (error != null) {
        baseUri = baseUri.replace(WEB_SERVICE_SUFFIX, "");
      } else {
        OAuth20Service authService =
            new ServiceBuilder(appOffice365.getClientId())
                .apiSecret(appOffice365.getClientSecret())
                .callback(appOffice365.getRedirectUri())
                .defaultScope(Office365Service.SCOPE)
                .build(MicrosoftAzureActiveDirectory20Api.instance());
        OAuth2AccessToken accessToken = authService.getAccessToken(code);

        Long officeAccountId = Long.parseLong(state);
        OfficeAccount officeAccount = officeAccountRepo.find(officeAccountId);
        officeAccount.setRefreshToken(accessToken.getRefreshToken());
        officeAccount.setIsAuthorized(true);
        officeAccountRepo.save(officeAccount);

        baseUri = baseUri.replace(WEB_SERVICE_SUFFIX, APP_VIEW_SUFFIX + officeAccountId);
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
      baseUri = baseUri.replace(WEB_SERVICE_SUFFIX, ERROR_VIEW_SUFFIX);
    }
    return Response.seeOther(URI.create(baseUri)).build();
  }
}
