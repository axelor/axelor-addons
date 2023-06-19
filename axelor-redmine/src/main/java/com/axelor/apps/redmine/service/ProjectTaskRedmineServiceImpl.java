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
package com.axelor.apps.redmine.service;

import static com.axelor.apps.base.service.administration.AbstractBatch.FETCH_LIMIT;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.repo.FrequencyRepository;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.FrequencyService;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.businesssupport.service.ProjectTaskBusinessSupportServiceImpl;
import com.axelor.apps.project.db.ProjectTask;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.studio.db.AppBusinessProject;
import com.axelor.studio.db.repo.AppBusinessSupportRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.DoubleSummaryStatistics;
import java.util.List;

public class ProjectTaskRedmineServiceImpl extends ProjectTaskBusinessSupportServiceImpl
    implements ProjectTaskRedmineService {

  @Inject public AppBusinessSupportRepository appBusinessSupportRepository;
  @Inject public ProjectVersionRepository projectVersionRepository;

  @Inject
  public ProjectTaskRedmineServiceImpl(
      ProjectTaskRepository projectTaskRepo,
      FrequencyRepository frequencyRepo,
      FrequencyService frequencyService,
      AppBaseService appBaseService,
      PriceListLineRepository priceListLineRepo,
      PriceListService priceListService,
      PartnerPriceListService partnerPriceListService,
      ProductCompanyService productCompanyService) {
    super(
        projectTaskRepo,
        frequencyRepo,
        frequencyService,
        appBaseService,
        priceListLineRepo,
        priceListService,
        partnerPriceListService,
        productCompanyService);
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  @Override
  public ProjectTask updateTaskToInvoice(
      ProjectTask projectTask, AppBusinessProject appBusinessProject) {
    if (!projectTask.getIsOffered()) {
      return super.updateTaskToInvoice(projectTask, appBusinessProject);
    }
    return projectTask;
  }

  @Override
  public void updateTargetVerionProgress(ProjectTask projectTask) {

    ProjectTask projectTaskDb = null;

    if (projectTask.getId() != null) {
      projectTaskDb = projectTaskRepo.find(projectTask.getId());
    }

    ProjectVersion targetVersion = projectTask.getTargetVersion();
    ProjectVersion targetVersionDb =
        projectTaskDb != null ? projectTaskDb.getTargetVersion() : null;

    if (projectTaskDb == null
        || projectTaskDb.getProgressSelect() != projectTask.getProgressSelect()
        || !projectTaskDb.getStatus().equals(projectTask.getStatus())
        || (targetVersionDb == null && targetVersion != null)
        || (targetVersionDb != null
            && (targetVersion == null || !targetVersionDb.equals(targetVersion)))) {

      if (targetVersion != null
          && (targetVersionDb == null || targetVersionDb.equals(targetVersion))) {
        updateTargetVerionProgress(targetVersion, projectTask, true);
      } else if (targetVersion != null
          && (targetVersionDb != null && !targetVersionDb.equals(targetVersion))) {
        updateTargetVerionProgress(targetVersion, projectTask, true);
        updateTargetVerionProgress(targetVersionDb, projectTask, false);
      } else if (targetVersionDb != null && targetVersion == null) {
        updateTargetVerionProgress(targetVersionDb, projectTask, false);
      }
    }
  }

  @Override
  @Transactional
  public void updateTargetVerionProgress(
      ProjectVersion targetVersion, ProjectTask projectTask, boolean isAdd) {

    DoubleSummaryStatistics stats =
        projectTaskRepo
            .all()
            .filter(
                projectTask.getId() != null
                    ? "self.targetVersion = ?1 and self.id != ?2"
                    : "self.targetVersion = ?1",
                targetVersion,
                projectTask.getId())
            .fetchStream()
            .mapToDouble(tt -> tt.getStatus().getIsCompleted() ? 100 : tt.getProgressSelect())
            .summaryStatistics();

    double sum = stats.getSum();
    long count = stats.getCount();

    if (isAdd) {
      count = count + 1;
      sum =
          sum + (projectTask.getStatus().getIsCompleted() ? 100 : projectTask.getProgressSelect());
    }

    targetVersion.setTotalProgress(
        count != 0
            ? BigDecimal.valueOf(sum / count).setScale(0, BigDecimal.ROUND_HALF_UP)
            : BigDecimal.ZERO);

    projectVersionRepository.save(targetVersion);
  }

  @Override
  @Transactional
  public void updateProjectVersionProgress(ProjectVersion projectVersion) {

    double sum =
        projectTaskRepo
            .all()
            .filter("self.targetVersion = ?1", projectVersion)
            .fetchStream()
            .mapToLong(tt -> tt.getStatus().getIsCompleted() ? 100 : tt.getProgressSelect())
            .average()
            .orElse(0);

    projectVersion.setTotalProgress(BigDecimal.valueOf(sum).setScale(0, BigDecimal.ROUND_HALF_UP));
    projectVersionRepository.save(projectVersion);
  }

  @Override
  @Transactional
  public void updateProjectVersion() {
    List<ProjectVersion> projectVersionList;
    Query<ProjectVersion> query = projectVersionRepository.all();
    int offset = 0;

    while (!(projectVersionList = query.fetch(FETCH_LIMIT, offset)).isEmpty()) {
      offset += projectVersionList.size();
      projectVersionList.forEach(version -> this.updateProjectVersionProgress(version));
      JPA.clear();
    }
  }
}
