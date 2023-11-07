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

import com.axelor.apps.base.db.AppBusinessProject;
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
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Objects;

public class ProjectTaskRedmineServiceImpl extends ProjectTaskBusinessSupportServiceImpl
        implements ProjectTaskRedmineService {

  @Inject public ProjectVersionRepository projectVersionRepository;

  @Inject
  public ProjectTaskRedmineServiceImpl(
          ProjectTaskRepository projectTaskRepo,
          FrequencyRepository frequencyRepo,
          FrequencyService frequencyService,
          AppBaseService appBaseService,
          ProjectRepository projectRepository,
          PriceListLineRepository priceListLineRepo,
          PriceListService priceListService,
          PartnerPriceListService partnerPriceListService,
          ProductCompanyService productCompanyService,
          ProjectVersionRepository projectVersionRepository) {
    super(
            projectTaskRepo,
            frequencyRepo,
            frequencyService,
            appBaseService,
            projectRepository,
            priceListLineRepo,
            priceListService,
            partnerPriceListService,
            productCompanyService);
    this.projectVersionRepository = projectVersionRepository;
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  @Override
  public ProjectTask updateTaskToInvoice(
          ProjectTask projectTask, AppBusinessProject appBusinessProject) {
    if (Boolean.FALSE.equals(projectTask.getIsOffered())) {
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
            || !Objects.equals(projectTaskDb.getProgressSelect(), projectTask.getProgressSelect())
            || !projectTaskDb.getStatus().equals(projectTask.getStatus())
            || (targetVersionDb == null && targetVersion != null)
            || (targetVersionDb != null && (!targetVersionDb.equals(targetVersion)))) {

      updateTargetCondition(projectTask, targetVersion, targetVersionDb);
    }
  }

  private void updateTargetCondition(
          ProjectTask projectTask, ProjectVersion targetVersion, ProjectVersion targetVersionDb) {
    if (targetVersion != null
            && (targetVersionDb == null || targetVersionDb.equals(targetVersion))) {
      updateTargetVerionProgress(targetVersion, projectTask, true);
    } else if (targetVersion != null) {
      updateTargetVerionProgress(targetVersion, projectTask, true);
      updateTargetVerionProgress(targetVersionDb, projectTask, false);
    } else if (targetVersionDb != null) {
      updateTargetVerionProgress(targetVersionDb, projectTask, false);
    }
  }

  @Override
  @Transactional
  public void updateTargetVerionProgress(
          ProjectVersion targetVersion, ProjectTask projectTask, boolean isAdd) {

    DoubleSummaryStatistics stats = calculateStatistics(targetVersion, projectTask);

    double sum = stats.getSum();
    long count = stats.getCount();

    if (isAdd) {
      count = count + 1;
      sum = calculateSum(projectTask, sum);
    }

    targetVersion.setTotalProgress(
            count != 0
                    ? BigDecimal.valueOf(sum / count).setScale(0, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);

    projectVersionRepository.save(targetVersion);
  }

  protected DoubleSummaryStatistics calculateStatistics(
          ProjectVersion targetVersion, ProjectTask projectTask) {
    return projectTaskRepo
            .all()
            .filter(
                    projectTask.getId() != null
                            ? "self.targetVersion = :targetVersion and self.id != :id"
                            : "self.targetVersion = :targetVersion",
                    targetVersion,
                    projectTask.getId())
            .fetchStream()
            .mapToDouble(
                    tt -> {
                      if (Boolean.TRUE.equals(tt.getStatus().getIsCompleted())) {
                        return 100;
                      } else {
                        return tt.getProgressSelect();
                      }
                    })
            .summaryStatistics();
  }

  protected static double calculateSum(ProjectTask projectTask, double sum) {
    sum =
            sum
                    + (Boolean.TRUE.equals(projectTask.getStatus().getIsCompleted())
                    ? 100
                    : projectTask.getProgressSelect());
    return sum;
  }

  @Override
  public void updateProjectVersion() {
    List<ProjectVersion> projectVersionList;
    Query<ProjectVersion> query = projectVersionRepository.all();
    int offset = 0;

    while (!(projectVersionList = query.fetch(FETCH_LIMIT, offset)).isEmpty()) {
      offset += projectVersionList.size();
      projectVersionList.forEach(this::updateProjectVersionProgress);
    }
  }

  @Override
  @Transactional
  public void updateProjectVersionProgress(ProjectVersion projectVersion) {

    projectVersion = projectVersionRepository.find(projectVersion.getId());

    Double sum = calculateSum(projectVersion);
    projectVersion.setTotalProgress(BigDecimal.valueOf(sum).setScale(0, RoundingMode.HALF_UP));
    projectVersionRepository.save(projectVersion);
    JPA.clear();
  }

  protected Double calculateSum(ProjectVersion projectVersion) {
    return projectTaskRepo
            .all()
            .filter("self.targetVersion = :projectVersion", projectVersion)
            .fetchStream()
            .mapToLong(
                    tt -> {
                      if (Boolean.TRUE.equals(tt.getStatus().getIsCompleted())) {
                        return 100;
                      } else {
                        return tt.getProgressSelect();
                      }
                    })
            .average()
            .orElse(0);
  }
}
