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
package com.axelor.apps.redmine.web;

import com.axelor.apps.base.db.AppBusinessSupport;
import com.axelor.apps.base.db.repo.AppBusinessSupportRepository;
import com.axelor.apps.businesssupport.db.ProjectVersion;
import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.redmine.service.ProjectTaskRedmineService;
import com.axelor.common.StringUtils;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;

public class AppBusinessSupportController {

  @Inject public ProjectTaskRedmineService projectTaskRedmineService;

  public void updateProjectVersionProgress(ActionRequest request, ActionResponse response) {

    AppBusinessSupport appBusinessSupport = request.getContext().asType(AppBusinessSupport.class);
    AppBusinessSupport appBusinessSupportDb =
        Beans.get(AppBusinessSupportRepository.class).find(appBusinessSupport.getId());
    String taskClosedStatusSelect = appBusinessSupport.getTaskClosedStatusSelect();
    String taskClosedStatusSelectDb = appBusinessSupportDb.getTaskClosedStatusSelect();

    boolean isTaskClosedStatusSelectEmpty = StringUtils.isEmpty(taskClosedStatusSelect);
    boolean isTaskClosedStatusSelectDbEmpty = StringUtils.isEmpty(taskClosedStatusSelectDb);

    if ((!isTaskClosedStatusSelectEmpty && isTaskClosedStatusSelectDbEmpty)
        || (isTaskClosedStatusSelectEmpty && !isTaskClosedStatusSelectDbEmpty)
        || (!isTaskClosedStatusSelectEmpty
            && !isTaskClosedStatusSelectDbEmpty
            && !taskClosedStatusSelect.equals(taskClosedStatusSelectDb))) {

      List<ProjectVersion> projectVersionList =
          Beans.get(ProjectVersionRepository.class).all().fetch();

      if (CollectionUtils.isNotEmpty(projectVersionList)) {
        projectVersionList.stream()
            .forEach(
                version ->
                    projectTaskRedmineService.updateProjectVersionProgress(
                        version, taskClosedStatusSelect));
      }
    }
  }
}
