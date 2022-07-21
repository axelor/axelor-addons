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
package com.axelor.apps.redmine.service.batch;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.meta.db.MetaFile;
import com.taskadapter.redmineapi.CustomFieldManager;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.UserManager;
import java.util.List;
import java.util.Map;

public interface RedmineBatchCommonService {

  public RedmineManager getRedmineManager(AppRedmine appRedmine);

  public void validateCustomFieldConfigProject(
      CustomFieldManager customFieldManager, AppRedmine appRedmine);

  public void validateCustomFieldConfigIssue(
      CustomFieldManager customFieldManager, AppRedmine appRedmine);

  public void validateCustomFieldConfigTimeEntry(
      CustomFieldManager customFieldManager, AppRedmine appRedmine);

  public Map<Integer, String> getRedmineUserMap(UserManager redmineUserManager);

  public Map<String, String> getRedmineUserLoginMap(UserManager redmineUserManager);

  public MetaFile generateErrorLog(List<Object[]> errorObjList);
}
