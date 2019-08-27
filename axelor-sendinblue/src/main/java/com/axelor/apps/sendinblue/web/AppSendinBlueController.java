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
package com.axelor.apps.sendinblue.web;

import com.axelor.apps.base.db.AppSendinblue;
import com.axelor.apps.sendinblue.service.AppSendinBlueService;
import com.axelor.apps.sendinblue.translation.ITranslation;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppSendinBlueController {

  @Inject protected AppSendinBlueService appSendinBlueService;

  private Logger LOG = LoggerFactory.getLogger(getClass());

  public void authenticateSendinBlue(ActionRequest request, ActionResponse response) {
    try {
      appSendinBlueService.getApiKeyAuth();
      response.setFlash(I18n.get(ITranslation.AUTHENTICATE_MESSAGE));
    } catch (AxelorException e) {
      response.setFlash(e.getLocalizedMessage());
    }
  }

  public void addContactFields(ActionRequest request, ActionResponse response)
      throws AxelorException {
    AppSendinblue appSendinblue = request.getContext().asType(AppSendinblue.class);
    if (appSendinblue.getPartnerFieldSet().isEmpty() && appSendinblue.getLeadFieldSet().isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE, I18n.get(ITranslation.EXPORT_FIELD_ERROR));
    }
    appSendinBlueService.exportFields(appSendinblue);
    LOG.debug("Contact Fields Export Completed");
  }

  public void deleteSendinBlueAggregatedStatistics(ActionRequest request, ActionResponse response) {
    Long total = appSendinBlueService.deleteSendinBlueAggregatedStatistics();
    if (total > 0) {
      response.setFlash(I18n.get(ITranslation.AGGREGATE_STATISTICS_MESSAGE));
    } else {
      response.setFlash(I18n.get(ITranslation.AGGREGATE_STATISTICS_ERROR));
    }
  }
}
