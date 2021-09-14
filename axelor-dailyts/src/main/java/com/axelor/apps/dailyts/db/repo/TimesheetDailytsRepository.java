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

import com.axelor.apps.hr.db.DailyTimesheet;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.DailyTimesheetRepository;
import com.axelor.apps.hr.db.repo.TimesheetHRRepository;
import com.google.inject.Inject;
import java.util.List;

public class TimesheetDailytsRepository extends TimesheetHRRepository {

  @Inject protected TimesheetLineDailytsRepository timesheetLineDailytsRepo;
  @Inject protected DailyTimesheetRepository dailyTimesheetRepo;

  @Override
  public Timesheet save(Timesheet timesheet) {

    List<TimesheetLine> timesheetLineList = timesheet.getTimesheetLineList();

    if (timesheetLineList != null) {

      for (TimesheetLine timesheetLine : timesheetLineList) {
        timesheetLine.setDailyTimesheet(timesheetLineDailytsRepo.getRelatedDailyTs(timesheetLine));
      }
    }

    timesheet = super.save(timesheet);

    List<DailyTimesheet> dailyTsList =
        dailyTimesheetRepo.all().filter("self.timesheet = ?1", timesheet).fetch();

    if (dailyTsList != null) {

      for (DailyTimesheet dailyTimesheet : dailyTsList) {
        dailyTimesheetRepo.save(dailyTimesheet);
      }
    }

    return timesheet;
  }
}
