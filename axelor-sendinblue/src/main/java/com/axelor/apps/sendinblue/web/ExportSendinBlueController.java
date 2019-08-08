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
import com.axelor.apps.base.db.repo.AppSendinblueRepository;
import com.axelor.apps.sendinblue.db.ExportSendinBlue;
import com.axelor.apps.sendinblue.service.AppSendinBlueService;
import com.axelor.apps.sendinblue.service.ExportSendinBlueService;
import com.axelor.apps.sendinblue.translation.ITranslation;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportSendinBlueController {

  @Inject protected AppSendinBlueService appSendinBlueService;
  @Inject protected ExportSendinBlueService exportSendinBlueService;

  private Logger LOG = LoggerFactory.getLogger(getClass());

  public void exportSendinBlue(ActionRequest request, ActionResponse response)
      throws AxelorException {
    appSendinBlueService.getApiKeyAuth();
    ExportSendinBlue exportSendinBlue = request.getContext().asType(ExportSendinBlue.class);
    AppSendinblue appSendinblue = Beans.get(AppSendinblueRepository.class).all().fetchOne();
    if (appSendinblue.getIsContactExport()
        || appSendinblue.getIsTemplateExport()
        || appSendinblue.getIsCampaignExport()) {
      String log = exportSendinBlueService.exportSendinBlue(appSendinblue, exportSendinBlue);
      response.setValue("exportLog", log.trim());
      LOG.debug("Export Completed");
    } else {
      response.setAlert(I18n.get(ITranslation.EXPORT_CONFIGURATION_ERROR));
    }
  }
}
