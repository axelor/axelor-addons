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
package com.axelor.web.service;

import com.axelor.apps.gsuite.service.GSuiteService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;
import java.io.IOException;
import java.net.URI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@RequestScoped
@Path("/gsuite-sync-code")
public class GSuiteSyncCode {

  @Inject private GSuiteService gSuiteService;

  @GET
  public Response setCode(
      @QueryParam("state") String accountId,
      @QueryParam("code") String code,
      @Context UriInfo uriInfo)
      throws IOException, AxelorException {

    if (code != null) {
      gSuiteService.setGoogleCredential(Long.parseLong(accountId), code.trim());
    }

    String baseUri = uriInfo.getBaseUri().toString();
    String formUri = baseUri.replace("/ws/", "/#/ds/google.google.account/edit/" + accountId);

    return Response.seeOther(URI.create(formUri)).build();
  }
}
