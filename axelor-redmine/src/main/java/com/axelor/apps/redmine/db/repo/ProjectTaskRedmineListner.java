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
package com.axelor.apps.redmine.db.repo;

import com.axelor.apps.project.db.ProjectTask;
import com.axelor.apps.project.db.ProjectVersion;
import com.axelor.apps.redmine.service.ProjectTaskRedmineService;
import com.axelor.inject.Beans;
import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;
import javax.persistence.PreRemove;

public class ProjectTaskRedmineListner {

  @PostPersist
  public void onPostPersist(ProjectTask projectTask) {

    if (projectTask.getRedmineId() != 0) {
      projectTask.setFullName(projectTask.getName());
    }
  }

  @PostUpdate
  public void onPostUpdate(ProjectTask projectTask) {

    if (projectTask.getRedmineId() != 0) {
      projectTask.setFullName(projectTask.getName());
    }
  }

  @PreRemove
  public void remove(ProjectTask projectTask) {

    ProjectVersion targetVersion = projectTask.getTargetVersion();
    if (targetVersion != null) {
      Beans.get(ProjectTaskRedmineService.class)
          .updateTargetVerionProgress(targetVersion, projectTask, false);
    }
  }
}
