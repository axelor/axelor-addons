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
package com.axelor.apps.gsuite.db.repo;

import com.axelor.apps.crm.db.Event;
import com.axelor.apps.crm.db.repo.EventManagementRepository;
import com.axelor.apps.gsuite.service.event.GSuiteEventExportService;
import com.google.inject.Inject;

public class GSuiteEventRepository extends EventManagementRepository {

  @Inject GSuiteEventExportService gSuiteEventExportService;

  @Override
  public void remove(Event entity) {
    removeGSuiteEvent(entity, true);
  }

  public void removeGSuiteEvent(Event event, boolean removeRemote) {
    if (removeRemote) {
      gSuiteEventExportService.removeEventFromRemote(event);
    }
    super.remove(event);
  }
}
