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
import com.axelor.apps.project.db.ProjectCategory;
import com.axelor.apps.project.db.repo.ProjectCategoryRepository;
import com.axelor.apps.redmine.db.OpenSuitRedmineSync;
import com.axelor.apps.redmine.db.repo.OpenSuitRedmineSyncRepository;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.mapper.Mapper;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Tracker;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineImportTrackerServiceImp extends RedmineImportService
    implements RedmineImportTrackerService {

  protected OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo;
  protected ProjectCategoryRepository projectCatrgoryRepo;
  protected RedmineDynamicImportService redmineDynamicImportService;
  protected MetaModelRepository metaModelRepo;

  @Inject
  public RedmineImportTrackerServiceImp(
      DMSFileRepository dmsFileRepo,
      AppRedmineRepository appRedmineRepo,
      MetaFiles metaFiles,
      BatchRepository batchRepo,
      UserRepository userRepo,
      OpenSuitRedmineSyncRepository openSuiteRedmineSyncRepo,
      ProjectCategoryRepository projectCatrgoryRepo,
      RedmineDynamicImportService redmineDynamicImportService,
      MetaModelRepository metaModelRepo) {

    super(dmsFileRepo, appRedmineRepo, metaFiles, batchRepo, userRepo);

    this.openSuiteRedmineSyncRepo = openSuiteRedmineSyncRepo;
    this.projectCatrgoryRepo = projectCatrgoryRepo;
    this.redmineDynamicImportService = redmineDynamicImportService;
    this.metaModelRepo = metaModelRepo;
  }

  Logger LOG = LoggerFactory.getLogger(getClass());

  @Override
  public void importTracker(
      Batch batch,
      RedmineManager redmineManager,
      List<com.taskadapter.redmineapi.bean.Tracker> redmineTrackerList,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError,
      List<Object[]> errorObjList) {

    if (redmineTrackerList != null && !redmineTrackerList.isEmpty()) {
      OpenSuitRedmineSync openSuiteRedmineSyncTracker =
          openSuiteRedmineSyncRepo.findBySyncTypeSelect(
              OpenSuitRedmineSyncRepository.SYNC_TYPE_TRACKER);

      this.errorObjList = errorObjList;
      this.dynamicFieldsSyncList = openSuiteRedmineSyncTracker.getDynamicFieldsSyncList();

      if (validateDynamicFieldsSycList(
          dynamicFieldsSyncList,
          METAMODEL_PROJECT_CATEGORY,
          Mapper.toMap(new ProjectCategory()),
          Mapper.toMap(new Tracker()))) {

        this.batch = batch;
        this.redmineManager = redmineManager;
        this.onError = onError;
        this.onSuccess = onSuccess;
        this.redmineIssueManager = redmineManager.getIssueManager();
        this.metaModel = metaModelRepo.findByName(METAMODEL_PROJECT_CATEGORY);

        String syncTypeSelect = openSuiteRedmineSyncTracker.getRedmineToOpenSuiteSyncSelect();

        for (com.taskadapter.redmineapi.bean.Tracker redmineTracker : redmineTrackerList) {
          createOpenSuiteTracker(redmineTracker, syncTypeSelect);
        }
      }
    }

    String resultStr =
        String.format(
            "Redmine Tracker -> ABS Project Category : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  public void createOpenSuiteTracker(
      com.taskadapter.redmineapi.bean.Tracker redmineTracker, String syncTypeSelect) {

    ProjectCategory projectCategory =
        projectCatrgoryRepo.findByRedmineId(redmineTracker.getId()) != null
            ? projectCatrgoryRepo.findByRedmineId(redmineTracker.getId())
            : new ProjectCategory();

    if (projectCategory.getId() == null) {
      addInList = true;
    }

    // Sync type - On create
    if (syncTypeSelect.equals(OpenSuitRedmineSyncRepository.SYNC_ON_CREATE)
        && projectCategory.getId() != null) {
      return;
    }

    projectCategory.setRedmineId(redmineTracker.getId());

    Map<String, Object> projectCategoryMap = Mapper.toMap(projectCategory);
    Map<String, Object> redmineTrackerMap = Mapper.toMap(redmineTracker);

    projectCategoryMap =
        redmineDynamicImportService.createOpenSuiteDynamic(
            dynamicFieldsSyncList,
            projectCategoryMap,
            redmineTrackerMap,
            null,
            metaModel,
            redmineTracker,
            redmineManager);

    projectCategory = Mapper.toBean(projectCategory.getClass(), projectCategoryMap);

    // Create OS object
    this.saveOpenSuiteTracker(projectCategory);
  }

  @Transactional
  public void saveOpenSuiteTracker(ProjectCategory projectCategory) {

    // Fix (getting improper projectCategory)
    if (projectCategory.getId() != null) {
      projectCategory = projectCatrgoryRepo.find(projectCategory.getId());
    }

    if (addInList) {
      projectCategory.addBatchSetItem(batch);
    }
    projectCatrgoryRepo.save(projectCategory);

    //    JPA.em().getTransaction().commit();
    //    if (!JPA.em().getTransaction().isActive()) {
    //      JPA.em().getTransaction().begin();
    //    }
    onSuccess.accept(projectCategory);
    success++;
  }
}
