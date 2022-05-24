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
package com.axelor.apps.dailyts.db.repo;

import com.axelor.apps.dailyts.service.timesheet.DailyTimesheetService;
import com.axelor.apps.hr.db.DailyTimesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.DailyTimesheetRepository;
import com.axelor.apps.hr.db.repo.TimesheetLineHRRepository;
import com.google.inject.Inject;

public class TimesheetLineDailytsRepository extends TimesheetLineHRRepository {

  @Inject protected DailyTimesheetRepository dailyTimesheetRepo;
  @Inject protected DailyTimesheetService dailyTimesheetService;

  @Override
  public TimesheetLine save(TimesheetLine timesheetLine) {

    DailyTimesheet previousDailyTs = timesheetLine.getDailyTimesheet();
    DailyTimesheet currentDailyTs = getRelatedDailyTs(timesheetLine);
    timesheetLine.setDailyTimesheet(currentDailyTs);

    timesheetLine = super.save(timesheetLine);

    if (currentDailyTs != null) {
      currentDailyTs.setTimesheet(dailyTimesheetService.updateRelatedTimesheet(currentDailyTs));
      dailyTimesheetRepo.save(currentDailyTs);
    }

    if (previousDailyTs != null && previousDailyTs != currentDailyTs) {
      previousDailyTs.setTimesheet(dailyTimesheetService.updateRelatedTimesheet(previousDailyTs));
      dailyTimesheetRepo.save(previousDailyTs);
    }
    computeFullNameDailyTs(timesheetLine);

    return timesheetLine;
  }

  protected void computeFullNameDailyTs(TimesheetLine timesheetLine) {

    timesheetLine.setFullName(
        (timesheetLine.getTimesheet() != null
                ? timesheetLine.getTimesheet().getFullName() + " "
                : "")
            + timesheetLine.getDate()
            + " "
            + timesheetLine.getId());
  }

  protected DailyTimesheet getRelatedDailyTs(TimesheetLine timesheetLine) {

    return dailyTimesheetRepo
        .all()
        .filter(
            "self.dailyTimesheetUser = ?1 AND self.dailyTimesheetDate = ?2 AND self.timesheet = ?3",
            timesheetLine.getUser(),
            timesheetLine.getDate(),
            timesheetLine.getTimesheet())
        .order("-dailyTimesheetDate")
        .fetchOne();
  }
}
