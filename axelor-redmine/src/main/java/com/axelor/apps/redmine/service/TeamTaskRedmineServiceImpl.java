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
package com.axelor.apps.redmine.service;

import com.axelor.apps.base.db.AppBusinessProject;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.businesssupport.service.TeamTaskBusinessSupportServiceImpl;
import com.axelor.exception.AxelorException;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class TeamTaskRedmineServiceImpl extends TeamTaskBusinessSupportServiceImpl {

  @Inject
  public TeamTaskRedmineServiceImpl(
      TeamTaskRepository teamTaskRepo,
      PriceListLineRepository priceListLineRepository,
      PriceListService priceListService) {
    super(teamTaskRepo, priceListLineRepository, priceListService);
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  @Override
  public TeamTask updateTask(TeamTask teamTask, AppBusinessProject appBusinessProject) {
    if (!teamTask.getIsOffered()) {
      return super.updateTask(teamTask, appBusinessProject);
    }
    teamTask = computeDefaultInformation(teamTask);

    return teamTaskRepo.save(teamTask);
  }
}
