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
package com.axelor.apps.redmine.exports.service;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.redmine.db.OpenSuitRedmineSync;
import com.axelor.apps.redmine.db.repo.OpenSuitRedmineSyncRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Version;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineExportVersionServiceImpl extends RedmineExportService
    implements RedmineExportVersionService {

  protected OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo;
  protected ProjectVersionRepository projectVersionRepo;
  protected RedmineDynamicExportService redmineDynamicExportService;
  protected MetaModelRepository metaModelRepository;

  @Inject
  public RedmineExportVersionServiceImpl(
      OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo,
      ProjectVersionRepository projectVersionRepo,
      RedmineDynamicExportService redmineDynamicExportService,
      MetaModelRepository metaModelRepository) {

    this.openSuiteRedmineSyncRepo = openSuiteRedmineSyncRepo;
    this.projectVersionRepo = projectVersionRepo;
    this.redmineDynamicExportService = redmineDynamicExportService;
    this.metaModelRepository = metaModelRepository;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());
  private LocalDateTime lastBatchUpdatedOn;

  @Override
  public void exportVersion(
      Batch batch,
      LocalDateTime lastBatchUpdatedOn,
      RedmineManager redmineManager,
      List<ProjectVersion> projectVersionList,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError,
      List<Object[]> errorObjList) {

    if (projectVersionList != null && !projectVersionList.isEmpty()) {
      OpenSuitRedmineSync openSuiteRedmineSyncVersion =
          openSuiteRedmineSyncRepo.findBySyncTypeSelect(
              OpenSuitRedmineSyncRepository.SYNC_TYPE_VERSION);

      this.errorObjList = errorObjList;
      this.dynamicFieldsSyncList = openSuiteRedmineSyncVersion.getDynamicFieldsSyncList();

      if (validateDynamicFieldsSyncList(
          dynamicFieldsSyncList,
          METAMODEL_PROJECT_VERSION,
          Mapper.toMap(new ProjectVersion()),
          Mapper.toMap(new Version()))) {

        this.redmineManager = redmineManager;
        this.batch = batch;
        this.onError = onError;
        this.onSuccess = onSuccess;
        this.redmineProjectManager = redmineManager.getProjectManager();
        this.metaModel = metaModelRepository.findByName(METAMODEL_PROJECT_VERSION);
        this.lastBatchUpdatedOn = lastBatchUpdatedOn;

        String syncTypeSelect = openSuiteRedmineSyncVersion.getOpenSuiteToRedmineSyncSelect();

        for (ProjectVersion projectVersion : projectVersionList) {
          createRedmineVersion(projectVersion, syncTypeSelect);
        }
      }
    }

    String resultStr =
        String.format(
            "ABS Project Version -> Redmine Version : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  public void createRedmineVersion(ProjectVersion projectVersion, String syncTypeSelect) {

    try {
      com.taskadapter.redmineapi.bean.Version redmineVersion = null;

      if (projectVersion.getRedmineId() == null || projectVersion.getRedmineId().equals(0)) {
        redmineVersion = new com.taskadapter.redmineapi.bean.Version();
      } else {
        redmineVersion = redmineProjectManager.getVersionById(projectVersion.getRedmineId());
      }

      // Sync type - On create
      if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_CREATE)
          && (projectVersion.getRedmineId() != null && !projectVersion.getRedmineId().equals(0))) {
        return;
      }

      // Sync type - On update
      if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_UPDATE)
          && redmineVersion.getUpdatedOn() != null
          && lastBatchUpdatedOn != null) {

        // If updates are made on both sides and redmine side is latest updated then abort export
        LocalDateTime redmineUpdatedOn =
            redmineVersion
                .getUpdatedOn()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        if (redmineUpdatedOn.isAfter(lastBatchUpdatedOn)
            && redmineUpdatedOn.isAfter(projectVersion.getUpdatedOn())) {
          return;
        }
      }

      Map<String, Object> projectVersionMap = Mapper.toMap(projectVersion);
      Map<String, Object> redmineVersionMap = Mapper.toMap(redmineVersion);
      Map<String, Object> redmineVersionCustomFieldsMap = new HashMap<>();

      redmineVersionMap =
          redmineDynamicExportService.createRedmineDynamic(
              dynamicFieldsSyncList,
              projectVersionMap,
              redmineVersionMap,
              redmineVersionCustomFieldsMap,
              metaModel,
              projectVersion,
              redmineManager);

      Mapper redmineVersionMapper = Mapper.of(redmineVersion.getClass());
      Iterator<Entry<String, Object>> redmineVersionMapItr =
          redmineVersionMap.entrySet().iterator();

      while (redmineVersionMapItr.hasNext()) {
        Map.Entry<String, Object> entry = redmineVersionMapItr.next();
        redmineVersionMapper.set(redmineVersion, entry.getKey(), entry.getValue());
      }

      // Fix - Required in redmine version
      redmineVersion.setStatus(Version.STATUS_OPEN);

      // Create or update redmine object
      this.saveRedmineVersion(redmineVersion, projectVersion, redmineVersionCustomFieldsMap);
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);

      if (e.getMessage().equals(REDMINE_SERVER_404_NOT_FOUND)) {
        setErrorLog(
            ProjectVersion.class.getSimpleName(),
            projectVersion.getId().toString(),
            null,
            null,
            I18n.get(IMessage.REDMINE_SYNC_ERROR_RECORD_NOT_FOUND));
      }
    }
  }

  @Transactional
  public void saveRedmineVersion(
      Version redmineVersion,
      ProjectVersion projectVersion,
      Map<String, Object> redmineVersionCustomFieldsMap) {

    try {
      redmineVersion.setTransport(redmineManager.getTransport());

      if (redmineVersion.getId() == null) {
        redmineVersion = redmineVersion.create();
        projectVersion.setRedmineId(redmineVersion.getId());
        projectVersionRepo.save(projectVersion);
      }

      // Set custom fields
      setRedmineCustomFieldValues(
          redmineVersion.getCustomFields(), redmineVersionCustomFieldsMap, projectVersion.getId());

      redmineVersion.update();

      onSuccess.accept(projectVersion);
      success++;
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
      fail++;
    }
  }
}
