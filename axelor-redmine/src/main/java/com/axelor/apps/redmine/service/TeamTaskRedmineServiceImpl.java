/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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

import com.axelor.apps.base.db.AppBusinessProject;
import com.axelor.apps.base.db.repo.AppBusinessSupportRepository;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.businesssupport.service.TeamTaskBusinessSupportServiceImpl;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.DoubleSummaryStatistics;

public class TeamTaskRedmineServiceImpl extends TeamTaskBusinessSupportServiceImpl
    implements TeamTaskRedmineService {

  @Inject public TeamTaskRepository teamTaskRepository;
  @Inject public AppBusinessSupportRepository appBusinessSupportRepository;
  @Inject public ProjectVersionRepository projectVersionRepository;

  @Inject
  public TeamTaskRedmineServiceImpl(
      TeamTaskRepository teamTaskRepo,
      PriceListLineRepository priceListLineRepository,
      PriceListService priceListService,
      ProductCompanyService productCompanyService,
      AppBaseService appBaseService) {
    super(
        teamTaskRepo,
        priceListLineRepository,
        priceListService,
        productCompanyService,
        appBaseService);
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  @Override
  public TeamTask updateTask(TeamTask teamTask, AppBusinessProject appBusinessProject)
      throws AxelorException {
    if (!teamTask.getIsOffered()) {
      return super.updateTask(teamTask, appBusinessProject);
    }
    teamTask = computeDefaultInformation(teamTask);

    return teamTaskRepo.save(teamTask);
  }

  @Override
  public void updateTargetVerionProgress(TeamTask teamTask) {

    TeamTask teamTaskDb = null;

    if (teamTask.getId() != null) {
      teamTaskDb = teamTaskRepository.find(teamTask.getId());
    }

    ProjectVersion targetVersion = teamTask.getTargetVersion();
    ProjectVersion targetVersionDb = teamTaskDb != null ? teamTaskDb.getTargetVersion() : null;

    if (teamTaskDb == null
        || teamTaskDb.getProgressSelect() != teamTask.getProgressSelect()
        || !teamTaskDb.getStatus().equals(teamTask.getStatus())
        || (targetVersionDb == null && targetVersion != null)
        || (targetVersionDb != null
            && (targetVersion == null || !targetVersionDb.equals(targetVersion)))) {

      if (targetVersion != null
          && (targetVersionDb == null || targetVersionDb.equals(targetVersion))) {
        updateTargetVerionProgress(targetVersion, teamTask, true);
      } else if (targetVersion != null
          && (targetVersionDb != null && !targetVersionDb.equals(targetVersion))) {
        updateTargetVerionProgress(targetVersion, teamTask, true);
        updateTargetVerionProgress(targetVersionDb, teamTask, false);
      } else if (targetVersionDb != null && targetVersion == null) {
        updateTargetVerionProgress(targetVersionDb, teamTask, false);
      }
    }
  }

  @Override
  @Transactional
  public void updateTargetVerionProgress(
      ProjectVersion targetVersion, TeamTask teamTask, boolean isAdd) {

    String taskClosedStatusSelect =
        appBusinessSupportRepository.all().fetchOne().getTaskClosedStatusSelect();

    DoubleSummaryStatistics stats =
        teamTaskRepository
            .all()
            .filter(
                teamTask.getId() != null
                    ? "self.targetVersion = ?1 and self.id != ?2"
                    : "self.targetVersion = ?1",
                targetVersion,
                teamTask.getId())
            .fetchStream()
            .mapToDouble(
                tt ->
                    !StringUtils.isEmpty(taskClosedStatusSelect)
                            && taskClosedStatusSelect.contains(tt.getStatus())
                        ? 100
                        : tt.getProgressSelect())
            .summaryStatistics();

    double sum = stats.getSum();
    long count = stats.getCount();

    if (isAdd) {
      count = count + 1;
      sum =
          sum
              + (!StringUtils.isEmpty(taskClosedStatusSelect)
                      && taskClosedStatusSelect.contains(teamTask.getStatus())
                  ? 100
                  : teamTask.getProgressSelect());
    }

    targetVersion.setTotalProgress(
        count != 0
            ? BigDecimal.valueOf(sum / count).setScale(0, BigDecimal.ROUND_HALF_UP)
            : BigDecimal.ZERO);

    projectVersionRepository.save(targetVersion);
  }

  @Override
  @Transactional
  public void updateProjectVersionProgress(
      ProjectVersion projectVersion, String taskClosedStatusSelect) {

    double sum =
        teamTaskRepo
            .all()
            .filter("self.targetVersion = ?1", projectVersion)
            .fetchStream()
            .mapToLong(
                tt ->
                    !StringUtils.isEmpty(taskClosedStatusSelect)
                            && taskClosedStatusSelect.contains(tt.getStatus())
                        ? 100
                        : tt.getProgressSelect())
            .average()
            .orElse(0);

    projectVersion.setTotalProgress(BigDecimal.valueOf(sum).setScale(0, BigDecimal.ROUND_HALF_UP));
    projectVersionRepository.save(projectVersion);
  }
}
