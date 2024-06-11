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
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businessproject.service.TimesheetProjectServiceImpl;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.service.timesheet.TimesheetLineService;
import com.google.inject.Inject;
import java.math.BigDecimal;

public class TimesheetRedmineServiceImpl extends TimesheetProjectServiceImpl {

  private UnitConversionService unitConversionService;
  private AppBaseService appBaseService;

  @Inject
  public TimesheetRedmineServiceImpl(
      TimesheetLineService timesheetLineService,
      UnitConversionService unitConversionService,
      AppBaseService appBaseService) {
    super(timesheetLineService);
    this.unitConversionService = unitConversionService;
    this.appBaseService = appBaseService;
  }

  @Override
  public BigDecimal computeDurationForCustomer(TimesheetLine timesheetLine) throws AxelorException {
    return unitConversionService.convert(
        timesheetLine.getDurationUnit(),
        appBaseService.getAppBase().getUnitHours(),
        timesheetLine.getDurationForCustomer(),
        AppBaseService.DEFAULT_NB_DECIMAL_DIGITS,
        timesheetLine.getProduct());
  }
}
