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
package com.axelor.apps.redmine.imports.service;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.redmine.db.OpenSuitRedmineSync;
import com.axelor.apps.redmine.db.repo.OpenSuitRedmineSyncRepository;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.mapper.Mapper;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Version;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineImportVersionServiceImpl extends RedmineImportService
    implements RedmineImportVersionService {

  protected OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo;
  protected RedmineDynamicImportService redmineDynamicImportService;
  protected ProjectVersionRepository projectVersionRepo;
  protected MetaModelRepository metaModelRepo;

  @Inject
  public RedmineImportVersionServiceImpl(
      DMSFileRepository dmsFileRepo,
      AppRedmineRepository appRedmineRepo,
      MetaFiles metaFiles,
      BatchRepository batchRepo,
      UserRepository userRepo,
      OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo,
      RedmineDynamicImportService redmineDynamicImportService,
      ProjectVersionRepository projectVersionRepo,
      MetaModelRepository metaModelRepo) {

    super(dmsFileRepo, appRedmineRepo, metaFiles, batchRepo, userRepo);

    this.openSuiteRedmineSyncRepo = openSuiteRedmineSyncRepo;
    this.redmineDynamicImportService = redmineDynamicImportService;
    this.projectVersionRepo = projectVersionRepo;
    this.metaModelRepo = metaModelRepo;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());
  private LocalDateTime lastBatchUpdatedOn;

  @Override
  public void importVersion(
      Batch batch,
      LocalDateTime lastBatchUpdatedOn,
      RedmineManager redmineManager,
      List<com.taskadapter.redmineapi.bean.Version> redmineVersionList,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError,
      List<Object[]> errorObjList) {

    if (redmineVersionList != null && !redmineVersionList.isEmpty()) {
      OpenSuitRedmineSync openSuiteRedmineSyncVersion =
          openSuiteRedmineSyncRepo.findBySyncTypeSelect(
              OpenSuitRedmineSyncRepository.SYNC_TYPE_VERSION);

      this.errorObjList = errorObjList;
      this.dynamicFieldsSyncList = openSuiteRedmineSyncVersion.getDynamicFieldsSyncList();

      if (validateDynamicFieldsSycList(
          dynamicFieldsSyncList,
          METAMODEL_PROJECT_VERSION,
          Mapper.toMap(new ProjectVersion()),
          Mapper.toMap(new Version()))) {

        this.batch = batch;
        this.redmineManager = redmineManager;
        this.onError = onError;
        this.onSuccess = onSuccess;
        this.redmineProjectManager = redmineManager.getProjectManager();
        this.metaModel = metaModelRepo.findByName(METAMODEL_PROJECT_VERSION);
        this.lastBatchUpdatedOn = lastBatchUpdatedOn;

        String syncTypeSelect = openSuiteRedmineSyncVersion.getRedmineToOpenSuiteSyncSelect();

        for (com.taskadapter.redmineapi.bean.Version redmineVersion : redmineVersionList) {
          createOpenSuiteProjectVersion(redmineVersion, syncTypeSelect);
        }
      }
    }

    String resultStr =
        String.format("Redmine Version -> ABS Version : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  public void createOpenSuiteProjectVersion(Version redmineVersion, String syncTypeSelect) {

    ProjectVersion projectVersion =
        projectVersionRepo.findByRedmineId(redmineVersion.getId()) != null
            ? projectVersionRepo.findByRedmineId(redmineVersion.getId())
            : new ProjectVersion();

    // Sync type - On create
    if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_CREATE)
        && projectVersion.getId() != null) {
      return;
    }

    // Sync type - On update
    if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_UPDATE)
        && lastBatchUpdatedOn != null
        && projectVersion.getId() != null) {

      // If updates are made on both sides and os side is latest updated then abort import
      LocalDateTime redmineUpdatedOn =
          redmineVersion
              .getUpdatedOn()
              .toInstant()
              .atZone(ZoneId.systemDefault())
              .toLocalDateTime();

      if (lastBatchUpdatedOn.isAfter(redmineUpdatedOn)) {
        return;
      }

      if (projectVersion.getUpdatedOn().isAfter(lastBatchUpdatedOn)
          && projectVersion.getUpdatedOn().isAfter(redmineUpdatedOn)) {
        return;
      }
    }

    projectVersion.setRedmineId(redmineVersion.getId());

    Map<String, Object> projectVersionMap = Mapper.toMap(projectVersion);
    Map<String, Object> redmineVersionMap = Mapper.toMap(redmineVersion);
    Map<String, Object> redmineVersionCustomFieldsMap =
        setRedmineCustomFieldsMap(redmineVersion.getCustomFields());

    projectVersionMap =
        redmineDynamicImportService.createOpenSuiteDynamic(
            dynamicFieldsSyncList,
            projectVersionMap,
            redmineVersionMap,
            redmineVersionCustomFieldsMap,
            metaModel,
            redmineVersion,
            redmineManager);

    projectVersion = Mapper.toBean(projectVersion.getClass(), projectVersionMap);

    setLocalDateTime(projectVersion, redmineVersion.getCreatedOn(), "setCreatedOn");
    setLocalDateTime(projectVersion, redmineVersion.getUpdatedOn(), "setUpdatedOn");

    // Create OS object
    this.saveOpenSuiteVersion(projectVersion, redmineVersion);
  }

  @Transactional
  public void saveOpenSuiteVersion(ProjectVersion projectVersion, Version redmineVersion) {

    try {
      projectVersionRepo.save(projectVersion);

      //      JPA.em().getTransaction().commit();
      //      if (!JPA.em().getTransaction().isActive()) {
      //        JPA.em().getTransaction().begin();
      //      }
      onSuccess.accept(projectVersion);
      success++;
    } catch (Exception e) {
      onError.accept(e);
      fail++;
      //      JPA.em().getTransaction().rollback();
      //      JPA.em().getTransaction().begin();
      TraceBackService.trace(e, "", batch.getId());
    }
  }
}
