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
package com.axelor.apps.redmine.service.batch;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.redmine.service.db.imports.projects.RedmineDbImportProjectService;
import com.axelor.apps.redmine.service.log.RedmineErrorLogService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.PersistenceException;
import org.apache.commons.collections.CollectionUtils;

public class RedmineBatchDbImportProjects extends AbstractBatch {

  protected AppRedmineRepository appRedmineRepo;
  protected RedmineDbImportProjectService redmineDbImportProjectService;
  protected RedmineErrorLogService redmineErrorLogService;

  @Inject
  public RedmineBatchDbImportProjects(
      AppRedmineRepository appRedmineRepo,
      RedmineDbImportProjectService redmineDbImportProjectService,
      RedmineErrorLogService redmineErrorLogService) {

    this.appRedmineRepo = appRedmineRepo;
    this.redmineDbImportProjectService = redmineDbImportProjectService;
    this.redmineErrorLogService = redmineErrorLogService;
  }

  protected String projectResultStr;

  @Override
  protected void process() {

    AppRedmine appRedmine = appRedmineRepo.all().fetchOne();

    try {

      LOG.debug("Getting connction with redmine database..");

      Class.forName(appRedmine.getRedmineDbDriver());

      try (Connection connection =
          DriverManager.getConnection(
              appRedmine.getRedmineDbUrl(),
              appRedmine.getRedmineDbUser(),
              appRedmine.getRedmineDbPassword())) {

        connection.setAutoCommit(false);

        Batch lastBatch =
            batchRepo
                .all()
                .filter(
                    "self.id != ?1 and self.redmineBatch.id = ?2 and self.endDate != null",
                    batch.getId(),
                    batch.getRedmineBatch().getId())
                .order("-updatedOn")
                .fetchOne();

        ZonedDateTime lastBatchEndDate = lastBatch != null ? lastBatch.getEndDate() : null;
        List<Object[]> errorObjList = new ArrayList<>();

        LOG.debug("Start process to import redmine issues..");

        projectResultStr =
            redmineDbImportProjectService.redmineProjectsImportProcess(
                connection,
                lastBatchEndDate,
                appRedmine,
                batch,
                success -> incrementDone(),
                error -> incrementAnomaly(),
                errorObjList);

        if (CollectionUtils.isNotEmpty(errorObjList)) {
          generateErrorLog(errorObjList);
        }
      }
    } catch (Exception e) {
      incrementAnomaly();
      TraceBackService.trace(e, "", batch.getId());
      throw new PersistenceException(e.getLocalizedMessage());
    }
  }

  @Override
  protected void stop() {

    super.stop();
    addComment(projectResultStr);
  }

  public void generateErrorLog(List<Object[]> errorObjList) {

    LOG.debug("Generate error log file..");

    String sheetName = "ErrorLog";
    String fileName = "RedmineImportErrorLog_";
    Object[] headers = new Object[] {"Object", "Redmine reference", "Error"};

    MetaFile errorFile =
        redmineErrorLogService.generateErrorLog(sheetName, fileName, headers, errorObjList);

    batchRepo.find(batch.getId()).setErrorLogFile(errorFile);
  }
}
