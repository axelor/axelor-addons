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

import com.axelor.apps.base.db.AppMarketing;
import com.axelor.apps.base.db.AppSendinblue;
import com.axelor.apps.base.db.repo.AppMarketingRepository;
import com.axelor.apps.base.db.repo.AppSendinblueRepository;
import com.axelor.apps.sendinblue.db.ImportSendinBlue;
import com.axelor.apps.sendinblue.service.AppSendinBlueService;
import com.axelor.apps.sendinblue.service.ImportSendinBlueService;
import com.axelor.apps.sendinblue.translation.ITranslation;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportSendinBlueController {

  @Inject protected AppSendinBlueService appSendinBlueService;
  @Inject protected ImportSendinBlueService importSendinBlueService;

  private Logger LOG = LoggerFactory.getLogger(getClass());

  public void importSendinBlue(ActionRequest request, ActionResponse response)
      throws AxelorException {
    appSendinBlueService.getApiKeyAuth();
    ImportSendinBlue importSendinBlue = request.getContext().asType(ImportSendinBlue.class);
    AppSendinblue appSendinblue = Beans.get(AppSendinblueRepository.class).all().fetchOne();
    AppMarketing appMarketing = Beans.get(AppMarketingRepository.class).all().fetchOne();
    if (appSendinblue.getIsContactImport()
        || appSendinblue.getIsTemplateImport()
        || appSendinblue.getIsCampaignImport()
        || appMarketing.getManageSendinBlueApiEmailingReporting()) {
      String log =
          importSendinBlueService.importSendinBlue(appSendinblue, importSendinBlue, appMarketing);
      response.setValue("importLog", log.trim());
      LOG.debug("Import Completed");
    } else {
      response.setAlert(I18n.get(ITranslation.IMPORT_CONFIGURATION_ERROR));
    }
  }

  public void report(ActionRequest request, ActionResponse response) throws AxelorException {
    LocalDate fromDate =
        LocalDate.parse(
            request.getContext().get("fromDate").toString(), DateTimeFormatter.ISO_DATE);
    LocalDate toDate =
        LocalDate.parse(request.getContext().get("toDate").toString(), DateTimeFormatter.ISO_DATE);
    List<Map<String, Object>> dataList = importSendinBlueService.getReport(fromDate, toDate);
    response.setData(dataList);
  }

  public void tagReport(ActionRequest request, ActionResponse response) throws AxelorException {
    List<Map<String, Object>> dataList = null;
    @SuppressWarnings("unchecked")
    List<LinkedHashMap<String, Integer>> obj =
        (List<LinkedHashMap<String, Integer>>) request.getContext().get("eventTag");
    if (obj != null) {
      Stream<Long> idStream = obj.stream().map(mapObj -> mapObj.get("id").longValue());
      List<Long> ids = idStream.collect(Collectors.toList());
      dataList = importSendinBlueService.getTagReport(ids);
    }
    response.setData(dataList);
  }
}
