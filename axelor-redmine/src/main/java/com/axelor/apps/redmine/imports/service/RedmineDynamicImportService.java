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
package com.axelor.apps.redmine.imports.service;

import com.axelor.apps.redmine.db.DynamicFieldsSync;
import com.axelor.meta.db.MetaModel;
import com.taskadapter.redmineapi.RedmineManager;
import java.util.List;
import java.util.Map;

public interface RedmineDynamicImportService {

  Map<String, Object> createOpenSuiteDynamic(
      List<DynamicFieldsSync> dynamicFieldsSyncList,
      Map<String, Object> osMap,
      Map<String, Object> redmineMap,
      Map<String, Object> redmineCustomFieldsMap,
      MetaModel metaModel,
      Object redmineObj,
      RedmineManager redmineManager);
}
