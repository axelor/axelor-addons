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
package com.axelor.apps.dailyts.web.timesheet;

import com.axelor.apps.base.service.PeriodService;
import com.axelor.apps.base.service.message.MessageServiceBaseImpl;
import com.axelor.apps.dailyts.service.timesheet.DailyTimesheetService;
import com.axelor.apps.hr.db.DailyTimesheet;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.repo.DailyTimesheetRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.service.timesheet.TimesheetService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class DailyTimesheetController {

  public void updateFromTimesheet(ActionRequest request, ActionResponse response) {

    DailyTimesheet dailyTimesheet = request.getContext().asType(DailyTimesheet.class);
    dailyTimesheet = Beans.get(DailyTimesheetRepository.class).find(dailyTimesheet.getId());
    Beans.get(DailyTimesheetService.class).updateFromTimesheet(dailyTimesheet);
    response.setValue("dailyTimesheetLineList", dailyTimesheet.getDailyTimesheetLineList());
  }

  public void updateFromTimesheetAndFavs(ActionRequest request, ActionResponse response) {

    DailyTimesheet dailyTimesheet = request.getContext().asType(DailyTimesheet.class);
    Beans.get(DailyTimesheetService.class).updateFromTimesheetAndFavs(dailyTimesheet);
    response.setValue("timesheet", dailyTimesheet.getTimesheet());
    response.setValue("dailyTimesheetLineList", dailyTimesheet.getDailyTimesheetLineList());
  }

  public void updateFromActivities(ActionRequest request, ActionResponse response) {

    DailyTimesheet dailyTimesheet = request.getContext().asType(DailyTimesheet.class);
    dailyTimesheet = Beans.get(DailyTimesheetRepository.class).find(dailyTimesheet.getId());
    int count = Beans.get(DailyTimesheetService.class).updateFromActivities(dailyTimesheet);

    if (count > 0) {
      response.setValue("dailyTimesheetLineList", dailyTimesheet.getDailyTimesheetLineList());
    } else {
      response.setReload(true);
    }
  }

  public void updateFromEvents(ActionRequest request, ActionResponse response) {

    DailyTimesheet dailyTimesheet = request.getContext().asType(DailyTimesheet.class);
    dailyTimesheet = Beans.get(DailyTimesheetRepository.class).find(dailyTimesheet.getId());
    int count = Beans.get(DailyTimesheetService.class).updateFromEvents(dailyTimesheet);

    if (count > 0) {
      response.setValue("dailyTimesheetLineList", dailyTimesheet.getDailyTimesheetLineList());
      response.setValue("dailyTotal", dailyTimesheet.getDailyTotal());
    } else {
      response.setReload(true);
    }
  }

  /** Same process as performed during timesheet validation */
  public void confirm(ActionRequest request, ActionResponse response) throws AxelorException {

    try {
      DailyTimesheet dailyTimesheet = request.getContext().asType(DailyTimesheet.class);
      dailyTimesheet = Beans.get(DailyTimesheetRepository.class).find(dailyTimesheet.getId());
      Timesheet timesheet =
          Beans.get(TimesheetRepository.class).find(dailyTimesheet.getTimesheet().getId());

      if (!timesheet.getStatusSelect().equals(TimesheetRepository.STATUS_VALIDATED)) {
        TimesheetService timesheetService = Beans.get(TimesheetService.class);

        timesheetService.checkEmptyPeriod(timesheet);

        if (timesheet.getTimesheetLineList() != null
            && !timesheet.getTimesheetLineList().isEmpty()) {
          Beans.get(TimesheetService.class).computeTimeSpent(timesheet);
        }

        // Call this method to validate dates and fill toDate if it is empty before validating it
        timesheetService.confirm(timesheet);

        Message message = timesheetService.validateAndSendValidationEmail(timesheet);

        if (message != null && message.getStatusSelect() == MessageRepository.STATUS_SENT) {
          response.setFlash(
              String.format(
                  I18n.get("Email sent to %s"),
                  Beans.get(MessageServiceBaseImpl.class).getToRecipients(message)));
        }

        Beans.get(PeriodService.class)
            .checkPeriod(timesheet.getCompany(), timesheet.getToDate(), timesheet.getFromDate());
      }

      // Update daily timesheet status at last after timesheet validation
      Beans.get(DailyTimesheetService.class).confirmDailyTimesheet(dailyTimesheet);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void computeDailyTotal(ActionRequest request, ActionResponse response) {

    DailyTimesheet dailyTimesheet = request.getContext().asType(DailyTimesheet.class);
    response.setValue(
        "dailyTotal", Beans.get(DailyTimesheetService.class).computeDailyTotal(dailyTimesheet));
  }

  public void updateRelatedTimesheet(ActionRequest request, ActionResponse response) {

    DailyTimesheet dailyTimesheet = request.getContext().asType(DailyTimesheet.class);
    response.setValue(
        "timesheet", Beans.get(DailyTimesheetService.class).updateRelatedTimesheet(dailyTimesheet));
  }
}
