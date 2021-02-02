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
package com.axelor.apps.dailyts.service.timesheet;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.businessproject.service.TimesheetLineProjectServiceImpl;
import com.axelor.apps.businessproject.service.app.AppBusinessProjectService;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.db.repo.TimesheetHRRepository;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.service.timesheet.TimesheetService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimesheetlineDailytsServiceImpl extends TimesheetLineProjectServiceImpl {

  @Inject
  public TimesheetlineDailytsServiceImpl(
      TimesheetService timesheetService,
      TimesheetHRRepository timesheetHRRepository,
      TimesheetRepository timesheetRepository,
      EmployeeRepository employeeRepository,
      ProjectRepository projectRepo,
      TeamTaskRepository teamTaskaRepo,
      TimesheetLineRepository timesheetLineRepo) {
    super(
        timesheetService,
        timesheetHRRepository,
        timesheetRepository,
        employeeRepository,
        projectRepo,
        teamTaskaRepo,
        timesheetLineRepo);
  }

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public TimesheetLine createTimesheetLine(
      Project project,
      Product product,
      User user,
      LocalDate date,
      Timesheet timesheet,
      BigDecimal hours,
      String comments) {

    // override process from TimesheetLineServiceImpl (add timesheet != null check)

    TimesheetLine timesheetLine = new TimesheetLine();

    timesheetLine.setDate(date);
    timesheetLine.setComments(comments);
    timesheetLine.setProduct(product);
    timesheetLine.setProject(project);
    timesheetLine.setUser(user);
    timesheetLine.setHoursDuration(hours);

    try {
      timesheetLine.setDuration(computeHoursDuration(timesheet, hours, false));
    } catch (AxelorException e) {
      log.error(e.getLocalizedMessage());
      TraceBackService.trace(e);
    }

    if (timesheet != null) {
      timesheet.addTimesheetLineListItem(timesheetLine);
    }

    // override process from TimesheetLineProjectServiceImpl (as it is)

    if (Beans.get(AppBusinessProjectService.class).isApp("business-project")
        && project != null
        && (project.getIsInvoicingTimesheet()
            || (project.getParentProject() != null
                && project.getParentProject().getIsInvoicingTimesheet()))) {
      timesheetLine.setToInvoice(true);
    }

    return timesheetLine;
  }
}
