/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.redmine.db.repo;

import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.TeamTaskBusinessSupportRepository;
import com.axelor.apps.redmine.service.TeamTaskRedmineService;
import com.axelor.inject.Beans;
import com.axelor.team.db.TeamTask;

public class TeamTaskRedmineRepositiry extends TeamTaskBusinessSupportRepository {

  @Override
  public TeamTask save(TeamTask teamTask) {

    super.save(teamTask);

    if (teamTask.getRedmineId() != 0) {
      teamTask.setFullName(teamTask.getName());
    }

    return teamTask;
  }

  @Override
  public void remove(TeamTask teamTask) {

    ProjectVersion targetVersion = teamTask.getTargetVersion();

    super.remove(teamTask);

    if (targetVersion != null) {
      Beans.get(TeamTaskRedmineService.class)
          .updateTargetVerionProgress(targetVersion, teamTask, false);
    }
  }
}
