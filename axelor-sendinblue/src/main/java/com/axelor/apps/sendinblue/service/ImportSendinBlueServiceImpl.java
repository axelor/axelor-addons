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

import com.axelor.apps.base.db.AppMarketing;
import com.axelor.apps.base.db.AppSendinblue;
import com.axelor.apps.sendinblue.db.ImportSendinBlue;
import com.axelor.apps.sendinblue.db.repo.ImportSendinBlueRepository;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang.StringUtils;

public class ImportSendinBlueServiceImpl implements ImportSendinBlueService {

  @Inject ImportSendinBlueRepository importSendinBlueRepo;

  @Inject SendinBlueReportService sendinBlueReportService;
  @Inject SendinBlueContactService sendinBlueContactService;
  @Inject SendinBlueTemplateService sendinBlueTemplateService;
  @Inject SendinBlueCampaignService sendinBlueCampaignService;

  @Override
  public String importSendinBlue(
      AppSendinblue appSendinblue, ImportSendinBlue importSendinBlue, AppMarketing appMarketing)
      throws AxelorException {
    LocalDateTime lastImportDateTime = null;
    Optional<ImportSendinBlue> lastImport =
        importSendinBlueRepo
            .all()
            .fetchStream()
            .max(Comparator.comparing(ImportSendinBlue::getImporttDateT));
    if (lastImport.isPresent()) {
      lastImportDateTime = lastImport.get().getImporttDateT();
    }
    StringBuilder logWriter = new StringBuilder();
    if (appSendinblue.getIsContactImport()) {
      sendinBlueContactService.importContact(importSendinBlue, lastImportDateTime, logWriter);
    }
    if (appSendinblue.getIsTemplateImport()) {
      sendinBlueTemplateService.importTemplate(importSendinBlue, lastImportDateTime, logWriter);
    }
    if (appSendinblue.getIsCampaignImport()) {
      sendinBlueCampaignService.importCampaign(importSendinBlue, lastImportDateTime, logWriter);
    }
    if (appMarketing.getManageSendinBlueApiEmailingReporting()) {
      sendinBlueReportService.importReport(
          appMarketing, importSendinBlue, lastImportDateTime, logWriter);
    }
    return logWriter.toString();
  }

  @Override
  public List<Map<String, Object>> getReport(LocalDate fromDate, LocalDate toDate) {
    List<Map<String, Object>> dataList = new ArrayList<Map<String, Object>>();
    List<String> eventType =
        new ArrayList<>(
            Arrays.asList(
                "requests",
                "delivered",
                "clicks",
                "opens",
                "hardBounces",
                "softBounces",
                "uniqueClicks",
                "uniqueOpens",
                "spamReports",
                "blocked",
                "invalid",
                "unsubscribed"));
    javax.persistence.Query q =
        JPA.em()
            .createQuery(
                "SELECT "
                    + "SUM(requests) AS requests , "
                    + "SUM(delivered) AS delivered ,"
                    + "SUM(clicks) AS clicks ,"
                    + "SUM(opens) AS opens ,"
                    + "SUM(hardBounces) AS hardBounces ,"
                    + "SUM(softBounces) AS softBounces ,"
                    + "SUM(uniqueClicks) AS uniqueClicks ,"
                    + "SUM(uniqueOpens) AS uniqueOpens ,"
                    + "SUM(spamReports) AS spamReports ,"
                    + "SUM(blocked) AS blocked ,"
                    + "SUM(invalid) AS invalid ,"
                    + "SUM(unsubscribed) AS unsubscribed "
                    + "FROM SendinBlueReport "
                    + "WHERE reportDate BETWEEN DATE(:fromDate) AND DATE(:toDate) ");
    q.setParameter("fromDate", fromDate);
    q.setParameter("toDate", toDate);

    if (q.getResultList() != null && !q.getResultList().isEmpty()) {
      Object[] result = (Object[]) q.getResultList().get(0);
      for (int i = 0; i < eventType.size(); i++) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("total", (Long) result[i]);
        dataMap.put("eventType", StringUtils.capitalize(eventType.get(i)));
        dataList.add(dataMap);
      }
    }

    return dataList;
  }

  @Override
  public List<Map<String, Object>> getTagReport(List<Long> ids) {
    List<Map<String, Object>> dataList = new ArrayList<>();
    javax.persistence.Query q =
        JPA.em()
            .createQuery(
                "SELECT new map(event, COUNT(id)) FROM SendinBlueEvent sendinBlueEvent WHERE sendinBlueEvent.tag.id IN :eventTag GROUP BY event");
    q.setParameter("eventTag", ids);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result = q.getResultList();
    for (Map<String, Object> map : result) {
      Map<String, Object> dataMap = new HashMap<>();
      dataMap.put("total", map.get("1"));
      dataMap.put("event", StringUtils.capitalize((String) map.get("0")));
      dataList.add(dataMap);
    }
    return dataList;
  }
}
