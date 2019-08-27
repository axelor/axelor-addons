/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.sendinblue.service;

import com.axelor.apps.base.db.AppSendinblue;
import com.axelor.apps.base.db.repo.AppSendinblueRepository;
import com.axelor.apps.sendinblue.db.repo.SendinBlueCampaignStatRepository;
import com.axelor.apps.sendinblue.db.repo.SendinBlueEventRepository;
import com.axelor.apps.sendinblue.db.repo.SendinBlueReportRepository;
import com.axelor.apps.sendinblue.translation.ITranslation;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import sendinblue.ApiClient;
import sendinblue.ApiException;
import sendinblue.Configuration;
import sendinblue.auth.ApiKeyAuth;
import sibApi.AccountApi;
import sibModel.GetAccount;

public class AppSendinBlueServiceImpl implements AppSendinBlueService {

  @Inject SendinBlueCampaignStatRepository sendinBlueCampaignStatRepo;

  @Inject AppSendinBlueService appSendinBlueService;
  @Inject SendinBlueFieldService sendinBlueFieldService;

  public ApiKeyAuth getApiKeyAuth() throws AxelorException {
    AppSendinblue appSendinblue = Beans.get(AppSendinblueRepository.class).all().fetchOne();
    String apiKeyStr = appSendinblue.getApiKey();

    if (StringUtils.isBlank(apiKeyStr)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE, I18n.get(ITranslation.CONFIGURATION_ERROR));
    }

    ApiClient defaultClient = Configuration.getDefaultApiClient();
    ApiKeyAuth apiKey = (ApiKeyAuth) defaultClient.getAuthentication("api-key");
    apiKey.setApiKey(apiKeyStr);

    AccountApi apiInstance = new AccountApi();
    try {
      GetAccount result = apiInstance.getAccount();
      if (result == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(ITranslation.AUTHENTICATE_ERROR));
      }
    } catch (ApiException e) {
      if (e.getMessage().contains("Unauthorized")) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(ITranslation.AUTHENTICATE_ERROR));
      }
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, e.getLocalizedMessage());
    }

    return apiKey;
  }

  public void exportFields(AppSendinblue appSendinblue) throws AxelorException {
    sendinBlueFieldService.exportFields(appSendinblue);
  }

  @Transactional
  public Long deleteSendinBlueAggregatedStatistics() {
    Long total = Beans.get(SendinBlueReportRepository.class).all().remove();
    total += Beans.get(SendinBlueEventRepository.class).all().remove();
    return total;
  }
}
