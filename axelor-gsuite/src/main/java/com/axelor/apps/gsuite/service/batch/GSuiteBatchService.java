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
package com.axelor.apps.gsuite.service.batch;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.service.administration.AbstractBatchService;
import com.axelor.apps.gsuite.db.GSuiteBatch;
import com.axelor.apps.gsuite.db.repo.GSuiteBatchRepository;
import com.axelor.apps.gsuite.exception.IExceptionMessage;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;

public class GSuiteBatchService extends AbstractBatchService {

  @Override
  protected Class<? extends Model> getModelClass() {
    return GSuiteBatch.class;
  }

  @Override
  public Batch run(Model model) throws AxelorException {
    GSuiteBatch batch = (GSuiteBatch) model;
    switch (batch.getActionSelect()) {
      case GSuiteBatchRepository.ACTION_EMAIL_SYNC:
        return emailSyncBatch(batch);
      case GSuiteBatchRepository.ACTION_EVENT_SYNC:
        return eventSyncBatch(batch);
      case GSuiteBatchRepository.ACTION_TASK_SYNC:
        return eventSyncBatch(batch);
      case GSuiteBatchRepository.ACTION_CONTACT_SYNC:
        return contactSyncBatch(batch);
      case GSuiteBatchRepository.ACTION_DRIVE_SYNC:
        return driveSyncBatch(batch);
      default:
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.GSUITE_BATCH_1),
            batch.getActionSelect(),
            batch.getCode());
    }
  }

  public Batch emailSyncBatch(GSuiteBatch batch) {
    return Beans.get(BatchGSuiteEmailSyncService.class).run(batch);
  }

  public Batch eventSyncBatch(GSuiteBatch batch) {
    return Beans.get(BatchGSuiteEventSyncService.class).run(batch);
  }

  public Batch taskSyncBatch(GSuiteBatch batch) {
    return Beans.get(BatchGSuiteTaskSyncService.class).run(batch);
  }

  public Batch contactSyncBatch(GSuiteBatch batch) {
    return Beans.get(BatchGSuiteContactService.class).run(batch);
  }

  public Batch driveSyncBatch(GSuiteBatch batch) {
    return Beans.get(BatchGSuiteDriveService.class).run(batch);
  }
}
