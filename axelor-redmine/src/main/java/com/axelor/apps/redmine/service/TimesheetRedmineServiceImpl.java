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
package com.axelor.apps.redmine.service;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.publicHoliday.PublicHolidayService;
import com.axelor.apps.base.service.weeklyplanning.WeeklyPlanningService;
import com.axelor.apps.businessproduction.service.TimesheetBusinessProductionServiceImpl;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.service.app.AppHumanResourceService;
import com.axelor.apps.hr.service.config.HRConfigService;
import com.axelor.apps.hr.service.employee.EmployeeService;
import com.axelor.apps.hr.service.leave.LeaveRequestComputeDurationService;
import com.axelor.apps.hr.service.leave.LeaveRequestService;
import com.axelor.apps.hr.service.publicHoliday.PublicHolidayHrService;
import com.axelor.apps.hr.service.timesheet.TimesheetLineService;
import com.axelor.apps.hr.service.user.UserHrService;
import com.axelor.apps.project.db.repo.ProjectPlanningTimeRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.apps.project.service.ProjectService;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.message.service.TemplateMessageService;
import com.google.inject.Inject;
import java.math.BigDecimal;

public class TimesheetRedmineServiceImpl extends TimesheetBusinessProductionServiceImpl {

  @Inject
  public TimesheetRedmineServiceImpl(
      PriceListService priceListService,
      AppHumanResourceService appHumanResourceService,
      HRConfigService hrConfigService,
      TemplateMessageService templateMessageService,
      ProjectRepository projectRepo,
      UserRepository userRepo,
      UserHrService userHrService,
      TimesheetLineService timesheetLineService,
      ProjectPlanningTimeRepository projectPlanningTimeRepository,
      ProjectTaskRepository projectTaskRepo,
      ProductCompanyService productCompanyService,
      TimesheetLineRepository timesheetlineRepo,
      TimesheetRepository timeSheetRepository,
      ProjectService projectService,
      LeaveRequestService leaveRequestService,
      PublicHolidayHrService publicHolidayHrService,
      PublicHolidayService publicHolidayService,
      PartnerPriceListService partnerPriceListService,
      UnitConversionService unitConversionService,
      WeeklyPlanningService weeklyPlanningService,
      LeaveRequestComputeDurationService leaveRequestComputeDurationService,
      EmployeeService employeeService,
      AppBaseService appBaseService) {
    super(
        priceListService,
        appHumanResourceService,
        hrConfigService,
        templateMessageService,
        projectRepo,
        userRepo,
        userHrService,
        timesheetLineService,
        projectPlanningTimeRepository,
        projectTaskRepo,
        productCompanyService,
        timesheetlineRepo,
        timeSheetRepository,
        projectService,
        leaveRequestService,
        publicHolidayHrService,
        publicHolidayService,
        partnerPriceListService,
        unitConversionService,
        weeklyPlanningService,
        leaveRequestComputeDurationService,
        employeeService,
        appBaseService);
  }

  @Override
  public BigDecimal computeDurationForCustomer(TimesheetLine timesheetLine) throws AxelorException {
    return unitConversionService.convert(
        timesheetLine.getDurationUnit(),
        appHumanResourceService.getAppBase().getUnitHours(),
        timesheetLine.getDurationForCustomer(),
        AppBaseService.DEFAULT_NB_DECIMAL_DIGITS,
        timesheetLine.getProduct());
  }
}
