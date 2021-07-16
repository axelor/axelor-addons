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
package com.axelor.apps.gsuite.web;

import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.service.GSuiteService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class GoogleAccountController {

  public void getAuthUrl(ActionRequest request, ActionResponse response)
      throws AxelorException, IOException {
    GoogleAccount account = request.getContext().asType(GoogleAccount.class);
    account = Beans.get(GoogleAccountRepository.class).find(account.getId());
    String authUrl = Beans.get(GSuiteService.class).getAuthenticationUrl(account.getId());
    response.setAttr(
        "authUrl",
        "title",
        String.format(
            "<a href='%s'>Google authentication url (click to authenticate account)</a>", authUrl));
  }
}
