/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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

import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.project.db.ProjectTask;

public interface ProjectTaskRedmineService {

  public void updateTargetVerionProgress(ProjectTask projectTask);

  public void updateProjectVersionProgress(
      ProjectVersion projectVersion, String taskClosedStatusSelect);

  public void updateTargetVerionProgress(
      ProjectVersion targetVersion, ProjectTask projectTask, boolean isAdd);
}
