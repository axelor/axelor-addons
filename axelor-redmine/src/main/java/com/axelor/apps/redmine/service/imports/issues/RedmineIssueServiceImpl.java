/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2022 Axelor (<http://axelor.com>).
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
package com.axelor.apps.redmine.service.imports.issues;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.apps.redmine.service.common.RedmineCommonService;
import com.axelor.apps.redmine.service.common.RedmineErrorLogService;
import com.axelor.apps.redmine.service.imports.fetch.RedmineFetchDataService;
import com.axelor.apps.redmine.service.imports.projects.pojo.MethodParameters;
import com.axelor.apps.redmine.service.imports.utils.FetchRedmineInfo;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.User;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineIssueServiceImpl implements RedmineIssueService {

  protected RedmineImportIssueService redmineImportIssueService;
  protected RedmineFetchDataService redmineFetchDataService;
  protected RedmineErrorLogService redmineErrorLogService;
  protected BatchRepository batchRepo;
  protected RedmineBatchRepository redmineBatchRepo;
  protected AppRedmineRepository appRedmineRepository;

  @Inject
  public RedmineIssueServiceImpl(
      RedmineImportIssueService redmineImportIssueService,
      RedmineFetchDataService redmineFetchDataService,
      RedmineErrorLogService redmineErrorLogService,
      BatchRepository batchRepo,
      RedmineBatchRepository redmineBatchRepo,
      AppRedmineRepository appRedmineRepository) {

    this.redmineImportIssueService = redmineImportIssueService;
    this.redmineFetchDataService = redmineFetchDataService;
    this.redmineErrorLogService = redmineErrorLogService;
    this.batchRepo = batchRepo;
    this.redmineBatchRepo = redmineBatchRepo;
    this.appRedmineRepository = appRedmineRepository;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());

  @Override
  @Transactional
  public void redmineImportIssue(
      Batch batch,
      RedmineManager redmineManager,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {

    RedmineCommonService.setResult("");
    AppRedmine appRedmine = appRedmineRepository.all().fetchOne();

    // LOGGER FOR REDMINE IMPORT ERROR DATA

    List<Object[]> errorObjList = new ArrayList<Object[]>();

    // FETCH IMPORT DATA

    RedmineBatch redmineBatch = batch.getRedmineBatch();

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
    LocalDateTime lastBatchUpdatedOn = lastBatch != null ? lastBatch.getUpdatedOn() : null;
    String failedRedmineIssuesIds = redmineBatch.getFailedRedmineIssuesIds();

    HashMap<Integer, String> redmineUserMap = new HashMap<Integer, String>();

    LOG.debug("Fetching issues from redmine..");

    try {
      List<User> redmineUserList = new ArrayList<>();
      Map<Integer, Boolean> includedIdsMap = new HashMap<>();

      LOG.debug("Fetching Axelor users from Redmine...");

      FetchRedmineInfo.fillUsersList(
          redmineManager,
          includedIdsMap,
          redmineUserList,
          FetchRedmineInfo.getFillUsersListParams(appRedmine));

      for (User user : redmineUserList) {
        redmineUserMap.put(user.getId(), user.getMail());
      }

      // CREATE MAP FOR PASS TO THE METHODS

      HashMap<String, Object> paramsMap = new HashMap<String, Object>();

      MethodParameters methodParameters =
          new MethodParameters(
              onError,
              onSuccess,
              batch,
              errorObjList,
              lastBatchUpdatedOn,
              redmineUserMap,
              redmineManager);

      // IMPORT PROCESS

      redmineImportIssueService.importIssue(
          redmineFetchDataService.fetchIssueImportData(
              redmineManager, lastBatchEndDate, failedRedmineIssuesIds),
          methodParameters);
      failedRedmineIssuesIds = batch.getRedmineBatch().getFailedRedmineIssuesIds();

      // ATTACH ERROR LOG WITH BATCH

      if (errorObjList != null && errorObjList.size() > 0) {
        MetaFile errorMetaFile = redmineErrorLogService.redmineErrorLogService(errorObjList);

        redmineBatch = redmineBatchRepo.find(redmineBatch.getId());
        redmineBatch.setFailedRedmineIssuesIds(failedRedmineIssuesIds);
        redmineBatchRepo.save(redmineBatch);

        if (errorMetaFile != null) {
          batch = batchRepo.find(batch.getId());
          batch.setErrorLogFile(errorMetaFile);
          batchRepo.save(batch);
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }
}
