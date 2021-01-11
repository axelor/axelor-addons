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
package com.axelor.apps.redmine.service.api.imports;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.redmine.service.api.imports.projects.RedmineImportProjectService;
import com.axelor.apps.redmine.service.log.RedmineErrorLogService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineProjectServiceImpl implements RedmineProjectService {

  protected RedmineImportProjectService redmineImportProjectService;
  protected RedmineProjectFetchDataService redmineProjectFetchImportDataService;
  protected RedmineErrorLogService redmineErrorLogService;
  protected BatchRepository batchRepo;

  @Inject
  public RedmineProjectServiceImpl(
      RedmineImportProjectService redmineImportProjectService,
      RedmineProjectFetchDataService redmineProjectFetchImportDataService,
      RedmineErrorLogService redmineErrorLogService,
      BatchRepository batchRepo) {

    this.redmineImportProjectService = redmineImportProjectService;
    this.redmineProjectFetchImportDataService = redmineProjectFetchImportDataService;
    this.redmineErrorLogService = redmineErrorLogService;
    this.batchRepo = batchRepo;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());

  @Override
  @Transactional
  public void redmineImportProject(
      Batch batch,
      RedmineManager redmineManager,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {

    RedmineImportService.result = "";

    // LOGGER FOR REDMINE IMPORT ERROR DATA

    List<Object[]> errorObjList = new ArrayList<Object[]>();

    // FETCH IMPORT DATA

    Batch lastBatch =
        batchRepo
            .all()
            .filter(
                "self.id != ?1 and self.redmineBatch.id = ?2 and self.endDate != null",
                batch.getId(),
                batch.getRedmineBatch().getId())
            .order("-updatedOn")
            .fetchOne();

    LocalDateTime lastBatchUpdatedOn = lastBatch != null ? lastBatch.getUpdatedOn() : null;

    List<com.taskadapter.redmineapi.bean.Project> importProjectList = null;
    HashMap<Integer, String> redmineUserMap = new HashMap<Integer, String>();

    LOG.debug("Fetching projects from redmine..");

    try {
      importProjectList = redmineProjectFetchImportDataService.fetchImportData(redmineManager);

      List<User> redmineUserList = redmineManager.getUserManager().getUsers();

      for (User user : redmineUserList) {
        redmineUserMap.put(user.getId(), user.getMail());
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }

    // CREATE MAP FOR PASS TO THE METHODS

    HashMap<String, Object> paramsMap = new HashMap<String, Object>();

    paramsMap.put("onError", onError);
    paramsMap.put("onSuccess", onSuccess);
    paramsMap.put("batch", batch);
    paramsMap.put("errorObjList", errorObjList);
    paramsMap.put("lastBatchUpdatedOn", lastBatchUpdatedOn);
    paramsMap.put("redmineUserMap", redmineUserMap);
    paramsMap.put("redmineProjectManager", redmineManager.getProjectManager());

    // IMPORT PROCESS

    redmineImportProjectService.importProject(importProjectList, paramsMap);

    // ATTACH ERROR LOG WITH BATCH

    if (errorObjList != null && errorObjList.size() > 0) {
      String sheetName = "ErrorLog";
      String fileName = "RedmineImportErrorLog_";
      Object[] headers = new Object[] {"Object", "Redmine reference", "Error"};

      MetaFile errorMetaFile =
          redmineErrorLogService.generateErrorLog(sheetName, fileName, headers, errorObjList);

      if (errorMetaFile != null) {
        batch = batchRepo.find(batch.getId());
        batch.setErrorLogFile(errorMetaFile);
        batchRepo.save(batch);
      }
    }
  }
}
