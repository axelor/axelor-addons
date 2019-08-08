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
import com.axelor.apps.sendinblue.db.ExportSendinBlue;
import com.axelor.apps.sendinblue.db.repo.ExportSendinBlueRepository;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;

public class ExportSendinBlueServiceImpl implements ExportSendinBlueService {

  @Inject ExportSendinBlueRepository exportSendinBlueRepo;

  @Inject SendinBlueContactService sendinBlueContactService;
  @Inject SendinBlueTemplateService sendinBlueTemplateService;
  @Inject SendinBlueCampaignService sendinBlueCampaignService;

  @Transactional
  @Override
  public String exportSendinBlue(AppSendinblue appSendinblue, ExportSendinBlue exportSendinBlue)
      throws AxelorException {
    LocalDateTime lastExportDateTime = null;
    Optional<ExportSendinBlue> lastExport =
        exportSendinBlueRepo
            .all()
            .fetchStream()
            .max(Comparator.comparing(ExportSendinBlue::getExportDateT));
    if (lastExport.isPresent()) {
      lastExportDateTime = lastExport.get().getExportDateT();
    }
    StringBuilder logWriter = new StringBuilder();
    if (appSendinblue.getIsContactExport()) {
      sendinBlueContactService.exportContact(
          appSendinblue, exportSendinBlue, lastExportDateTime, logWriter);
    }
    if (appSendinblue.getIsTemplateExport()) {
      sendinBlueTemplateService.exportTemplate(exportSendinBlue, lastExportDateTime, logWriter);
    }
    if (appSendinblue.getIsCampaignExport()) {
      sendinBlueCampaignService.exportCampaign(exportSendinBlue, lastExportDateTime, logWriter);
    }
    return logWriter.toString();
  }
}
