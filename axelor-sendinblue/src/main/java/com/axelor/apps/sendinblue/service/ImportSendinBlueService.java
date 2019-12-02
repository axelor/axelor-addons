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
package com.axelor.apps.sendinblue.service;

import com.axelor.apps.base.db.AppMarketing;
import com.axelor.apps.base.db.AppSendinblue;
import com.axelor.apps.sendinblue.db.ImportSendinBlue;
import com.axelor.exception.AxelorException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ImportSendinBlueService {

  String importSendinBlue(
      AppSendinblue appSendinblue, ImportSendinBlue importSendinBlue, AppMarketing appMarketing)
      throws AxelorException;

  List<Map<String, Object>> getReport(LocalDate fromDate, LocalDate toDate);

  List<Map<String, Object>> getTagReport(List<Long> ids);
}
