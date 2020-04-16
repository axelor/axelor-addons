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
}
