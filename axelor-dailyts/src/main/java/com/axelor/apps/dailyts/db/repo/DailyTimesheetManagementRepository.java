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
package com.axelor.apps.dailyts.db.repo;

import com.axelor.apps.dailyts.service.timesheet.DailyTimesheetService;
import com.axelor.apps.hr.db.DailyTimesheet;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.repo.DailyTimesheetRepository;
import com.axelor.apps.hr.service.timesheet.TimesheetService;
import com.axelor.inject.Beans;

public class DailyTimesheetManagementRepository extends DailyTimesheetRepository {

  @Override
  public DailyTimesheet save(DailyTimesheet dailyTimesheet) {

    dailyTimesheet.setDailyTotal(
        Beans.get(DailyTimesheetService.class).computeDailyTotal(dailyTimesheet));

    Timesheet timesheet = dailyTimesheet.getTimesheet();
    timesheet.setPeriodTotal(Beans.get(TimesheetService.class).computePeriodTotal(timesheet));

    if (timesheet.getToDate() != null
        && timesheet.getToDate().isBefore(dailyTimesheet.getDailyTimesheetDate())) {
      timesheet.setToDate(dailyTimesheet.getDailyTimesheetDate());
    }

    return super.save(dailyTimesheet);
  }
}
