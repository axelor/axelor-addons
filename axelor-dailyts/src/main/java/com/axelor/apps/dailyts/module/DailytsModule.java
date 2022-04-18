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
package com.axelor.apps.dailyts.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.dailyts.db.repo.DailyTimesheetManagementRepository;
import com.axelor.apps.dailyts.db.repo.MailMessageDailytsRepository;
import com.axelor.apps.dailyts.db.repo.TimesheetDailytsRepository;
import com.axelor.apps.dailyts.db.repo.TimesheetLineDailytsRepository;
import com.axelor.apps.dailyts.service.batch.DailytsHrBatchService;
import com.axelor.apps.dailyts.service.timesheet.DailyTimesheetService;
import com.axelor.apps.dailyts.service.timesheet.DailyTimesheetServiceImpl;
import com.axelor.apps.hr.db.repo.DailyTimesheetRepository;
import com.axelor.apps.hr.db.repo.TimesheetHRRepository;
import com.axelor.apps.hr.db.repo.TimesheetLineHRRepository;
import com.axelor.apps.hr.service.batch.HrBatchService;
import com.axelor.mail.db.repo.MailMessageRepository;

public class DailytsModule extends AxelorModule {

  @Override
  protected void configure() {

    bind(HrBatchService.class).to(DailytsHrBatchService.class);
    bind(DailyTimesheetService.class).to(DailyTimesheetServiceImpl.class);
    bind(TimesheetLineHRRepository.class).to(TimesheetLineDailytsRepository.class);
    bind(MailMessageRepository.class).to(MailMessageDailytsRepository.class);
    bind(DailyTimesheetRepository.class).to(DailyTimesheetManagementRepository.class);
    bind(TimesheetHRRepository.class).to(TimesheetDailytsRepository.class);
  }
}
