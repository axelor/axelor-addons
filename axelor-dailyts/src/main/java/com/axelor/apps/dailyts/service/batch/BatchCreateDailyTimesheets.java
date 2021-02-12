/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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

import com.axelor.apps.dailyts.service.timesheet.DailyTimesheetService;
import com.axelor.apps.hr.db.DailyTimesheet;
import com.axelor.apps.hr.db.HrBatch;
import com.axelor.apps.hr.db.repo.DailyTimesheetRepository;
import com.axelor.apps.hr.service.batch.BatchStrategy;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import java.util.Set;

public class BatchCreateDailyTimesheets extends BatchStrategy {

  @Inject UserRepository userRepository;
  @Inject DailyTimesheetRepository dailyTimesheetRepository;
  @Inject DailyTimesheetService dailyTimesheetService;

  @Override
  @Transactional
  protected void process() {

    HrBatch hrBatch = batch.getHrBatch();
    Set<User> dailyTsUserSet = hrBatch.getDailyTsUserSet();
    LocalDate dailyTsDate = hrBatch.getDailyTsDate();

    if (dailyTsDate == null) {
      dailyTsDate = appBaseService.getTodayDate(null);
    }

    for (User dailyTsUser : dailyTsUserSet) {
      long count =
          dailyTimesheetRepository
              .all()
              .filter(
                  "self.dailyTimesheetUser = ?1 and self.dailyTimesheetDate = ?2",
                  dailyTsUser,
                  dailyTsDate)
              .count();

      if (count == 0) {
        DailyTimesheet dailyTimesheet = new DailyTimesheet();
        dailyTimesheet.setDailyTimesheetDate(dailyTsDate);
        dailyTimesheet.setDailyTimesheetUser(dailyTsUser);
        dailyTimesheet.setTimesheet(dailyTimesheetService.getRelatedTimesheet(dailyTimesheet));
        dailyTimesheetRepository.save(dailyTimesheet);
        incrementDone();
      }
    }
  }
}
