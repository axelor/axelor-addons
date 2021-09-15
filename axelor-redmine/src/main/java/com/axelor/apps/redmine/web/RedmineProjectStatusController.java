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
package com.axelor.apps.redmine.web;

import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.project.db.ProjectStatus;
import com.axelor.apps.project.db.repo.ProjectStatusRepository;
import com.axelor.apps.redmine.service.ProjectTaskRedmineService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;

public class RedmineProjectStatusController {

  @Inject public ProjectTaskRedmineService projectTaskRedmineService;

  public void updateProjectVersionProgress(ActionRequest request, ActionResponse response) {
    ProjectStatus projectStatus = request.getContext().asType(ProjectStatus.class);
    ProjectStatus projectStatusDb =
        projectStatus.getId() != null
            ? Beans.get(ProjectStatusRepository.class).find(projectStatus.getId())
            : null;

    if (projectStatusDb != null
        && !projectStatus.getIsCompleted().equals(projectStatusDb.getIsCompleted())) {
      List<ProjectVersion> projectVersionList =
          Beans.get(ProjectVersionRepository.class).all().fetch();

      if (CollectionUtils.isNotEmpty(projectVersionList)) {
        projectVersionList.stream()
            .forEach(version -> projectTaskRedmineService.updateProjectVersionProgress(version));
      }
    }
  }
}
