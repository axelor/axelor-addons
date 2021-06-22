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
package com.axelor.apps.office365.web;

import com.axelor.apps.base.db.AppOffice365;
import com.axelor.apps.base.db.repo.AppOffice365Repository;
import com.axelor.apps.office.db.OfficeAccount;
import com.axelor.apps.office.db.repo.OfficeAccountRepository;
import com.axelor.apps.office365.service.Office365Service;
import com.axelor.apps.office365.translation.ITranslation;
import com.axelor.common.StringUtils;
import com.axelor.i18n.I18n;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public class OfficeAccountController {

  @Inject protected Office365Service office365Service;

  @Inject protected AppOffice365Repository appOffice365Repo;
  @Inject protected OfficeAccountRepository officeAccountRepo;

  public void generateUrl(ActionRequest request, ActionResponse response) throws Exception {

    AppOffice365 appOffice365 = appOffice365Repo.all().fetchOne();
    if (StringUtils.isEmpty(appOffice365.getClientId())
        || StringUtils.isEmpty(appOffice365.getClientSecret())
        || StringUtils.isEmpty(appOffice365.getRedirectUri())) {
      response.setError(I18n.get(ITranslation.OFFICE365_MISSING_CONFIGURATION));
    }

    OfficeAccount officeAccount = request.getContext().asType(OfficeAccount.class);

    Map<String, String> additionalParams = new HashMap<>();
    additionalParams.put("access_type", "offline");
    additionalParams.put("prompt", "consent");

    OAuth20Service authService =
        new ServiceBuilder(appOffice365.getClientId())
            .apiSecret(appOffice365.getClientSecret())
            .callback(appOffice365.getRedirectUri())
            .defaultScope(Office365Service.SCOPE)
            .build(MicrosoftAzureActiveDirectory20Api.instance());
    String authenticationUrl =
        authService
            .createAuthorizationUrlBuilder()
            .state(officeAccount.getId().toString())
            .additionalParams(additionalParams)
            .build();
    authService.close();
    String url =
        String.format(
            "<a href='%s'>%s</a> ",
            authenticationUrl.replace("&", "&amp;"),
            I18n.get(ITranslation.OFFICE365_AUTHETICATE_TITLE));

    response.setValue("isAuthorized", false);
    response.setValue("authenticationUrl", url);
  }

  public void syncContact(ActionRequest request, ActionResponse response) throws Exception {

    OfficeAccount officeAccount = request.getContext().asType(OfficeAccount.class);
    officeAccount = officeAccountRepo.find(officeAccount.getId());
    office365Service.syncContact(officeAccount);
    response.setReload(true);
  }

  public void syncCalendar(ActionRequest request, ActionResponse response) throws Exception {

    OfficeAccount officeAccount = request.getContext().asType(OfficeAccount.class);
    officeAccount = officeAccountRepo.find(officeAccount.getId());
    office365Service.syncCalendar(officeAccount);
    response.setReload(true);
  }

  public void syncMail(ActionRequest request, ActionResponse response) throws Exception {

    OfficeAccount officeAccount = request.getContext().asType(OfficeAccount.class);
    officeAccount = officeAccountRepo.find(officeAccount.getId());
    office365Service.syncMail(officeAccount, Office365Service.MAIL_URL);
    response.setReload(true);
  }
}
