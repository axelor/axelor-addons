/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2022 Axelor (<http://axelor.com>).
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
package com.axelor.apps.docusign.web;

import com.axelor.app.AppSettings;
import com.axelor.apps.docusign.db.DocuSignAccount;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.docusign.esign.client.auth.OAuth;
import java.net.URISyntaxException;
import java.util.Arrays;
import org.apache.http.client.utils.URIBuilder;

public class DocuSignAccountController {

  public void generateConsent(ActionRequest request, ActionResponse response) {
    DocuSignAccount account = request.getContext().asType(DocuSignAccount.class);
    try {
      URIBuilder uriBuilder =
          new URIBuilder()
              .setScheme("https")
              .setHost(account.getoAuthBasePath())
              .setPath("/oauth/auth")
              .addParameter("response_type", "code")
              .addParameter(
                  "scope",
                  String.join(" ", Arrays.asList(OAuth.Scope_SIGNATURE, OAuth.Scope_IMPERSONATION)))
              .addParameter("client_id", account.getIntegrationKey())
              .addParameter("redirect_uri", AppSettings.get().getBaseURL());
      response.setAttr(
          "$consentURL",
          "title",
          String.format(
              "<a href ='%s' target='_blank'>Click here to open consent screen</a>",
              uriBuilder.build().toString()));
    } catch (URISyntaxException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
