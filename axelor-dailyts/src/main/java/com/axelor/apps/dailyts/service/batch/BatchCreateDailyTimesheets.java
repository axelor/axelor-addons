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
package com.axelor.apps.dailyts.service.batch;

import com.axelor.apps.base.service.weeklyplanning.WeeklyPlanningService;
import com.axelor.apps.dailyts.service.timesheet.DailyTimesheetService;
import com.axelor.apps.hr.db.DailyTimesheet;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.HrBatch;
import com.axelor.apps.hr.db.LeaveRequest;
import com.axelor.apps.hr.db.repo.DailyTimesheetRepository;
import com.axelor.apps.hr.service.batch.BatchStrategy;
import com.axelor.apps.hr.service.leave.LeaveService;
import com.axelor.apps.hr.service.publicHoliday.PublicHolidayHrService;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;

public class BatchCreateDailyTimesheets extends BatchStrategy {

  @Inject protected UserRepository userRepository;
  @Inject protected DailyTimesheetRepository dailyTimesheetRepository;
  @Inject protected DailyTimesheetService dailyTimesheetService;
  @Inject protected WeeklyPlanningService weeklyPlanningService;
  @Inject protected LeaveService leaveService;
  @Inject protected PublicHolidayHrService publicHolidayHrService;

  @Override
  @Transactional
  protected void process() {

    HrBatch hrBatch = batch.getHrBatch();
    Set<Employee> dailyTsEmployeeSet = hrBatch.getDailyTsEmployeeSet();
    LocalDate dailyTsDate = hrBatch.getDailyTsDate();

    if (dailyTsDate == null) {
      dailyTsDate = appBaseService.getTodayDate(null);
    }

    for (Employee dailyTsEmployee : dailyTsEmployeeSet) {

      try {
        long count =
            dailyTimesheetRepository
                .all()
                .filter(
                    "self.dailyTimesheetEmployee = ?1 and self.dailyTimesheetDate = ?2",
                    dailyTsEmployee,
                    dailyTsDate)
                .count();

        if (count == 0
            && dailyTsEmployee != null
            && dailyTsEmployee.getWeeklyPlanning() != null
            && weeklyPlanningService.getWorkingDayValueInDays(
                    dailyTsEmployee.getWeeklyPlanning(), dailyTsDate)
                > 0
            && !isFullDayLeave(dailyTsEmployee, dailyTsDate)
            && !publicHolidayHrService.checkPublicHolidayDay(
                dailyTsDate, dailyTsEmployee.getPublicHolidayEventsPlanning())) {
          DailyTimesheet dailyTimesheet = new DailyTimesheet();
          dailyTimesheet.setDailyTimesheetDate(dailyTsDate);
          dailyTimesheet.setDailyTimesheetEmployee(dailyTsEmployee);
          dailyTimesheetService.updateFromTimesheetAndFavs(dailyTimesheet);
          dailyTimesheetRepository.save(dailyTimesheet);
          incrementDone();
        }
      } catch (AxelorException e) {
        incrementAnomaly();
        TraceBackService.trace(e, "", batch.getId());
      }
    }
  }

  protected boolean isFullDayLeave(Employee dailyTsEmployee, LocalDate dailyTsDate)
      throws AxelorException {

    List<LeaveRequest> leaveRequestList = leaveService.getLeaves(dailyTsEmployee, dailyTsDate);

    if (CollectionUtils.isNotEmpty(leaveRequestList)) {

      for (LeaveRequest leave : leaveRequestList) {

        if (leaveService.computeDuration(leave, dailyTsDate, dailyTsDate).compareTo(BigDecimal.ONE)
            == 0) {
          return true;
        }
      }
    }

    return false;
  }
}
