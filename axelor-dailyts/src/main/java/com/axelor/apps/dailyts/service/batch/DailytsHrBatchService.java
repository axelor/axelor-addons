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

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.hr.db.HrBatch;
import com.axelor.apps.hr.db.repo.HrBatchRepository;
import com.axelor.apps.hr.service.batch.HrBatchService;
import com.axelor.db.Model;
import com.axelor.inject.Beans;

public class DailytsHrBatchService extends HrBatchService {

  @Override
  public Batch run(Model batchModel) throws AxelorException {

    HrBatch hrBatch = (HrBatch) batchModel;

    if (hrBatch.getActionSelect() == HrBatchRepository.ACTION_CREATE_DAILY_TIMESHEETS) {
      return createDailyTimesheets(hrBatch);
    }

    return super.run(batchModel);
  }

  public Batch createDailyTimesheets(HrBatch hrBatch) {
    return Beans.get(BatchCreateDailyTimesheets.class).run(hrBatch);
  }
}
