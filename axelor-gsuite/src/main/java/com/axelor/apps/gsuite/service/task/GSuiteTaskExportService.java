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
package com.axelor.apps.gsuite.service.task;

import com.axelor.apps.crm.db.Event;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.exception.AxelorException;
import com.google.api.services.tasks.Tasks.TasksOperations;
import com.google.api.services.tasks.model.Task;
import java.io.IOException;

public interface GSuiteTaskExportService {

  GoogleAccount sync(GoogleAccount googleAccount) throws AxelorException;

  String createUpdateGoogleTask(
      TasksOperations taskOperations, Event task, String tasklistId, String taskId)
      throws IOException;

  Task extractEvent(Event task, Task googleTask);
}
