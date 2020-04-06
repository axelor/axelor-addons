package com.axelor.apps.gsuite.web;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.gsuite.db.GSuiteBatch;
import com.axelor.apps.gsuite.db.repo.GSuiteBatchRepository;
import com.axelor.apps.gsuite.service.batch.GSuiteBatchService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class GSuiteBatchController {

  public void actionEmailSyncBatch(ActionRequest request, ActionResponse response) {
    try {
      GSuiteBatch gSuiteBatch = request.getContext().asType(GSuiteBatch.class);
      gSuiteBatch = Beans.get(GSuiteBatchRepository.class).find(gSuiteBatch.getId());
      Batch batch = Beans.get(GSuiteBatchService.class).emailSyncBatch(gSuiteBatch);
      response.setFlash(batch.getComments());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }

  public void actionEventSyncBatch(ActionRequest request, ActionResponse response) {
    try {
      GSuiteBatch gSuiteBatch = request.getContext().asType(GSuiteBatch.class);
      gSuiteBatch = Beans.get(GSuiteBatchRepository.class).find(gSuiteBatch.getId());
      Batch batch = Beans.get(GSuiteBatchService.class).eventSyncBatch(gSuiteBatch);
      response.setFlash(batch.getComments());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }

  public void actionTaskSyncBatch(ActionRequest request, ActionResponse response) {
    try {
      GSuiteBatch gSuiteBatch = request.getContext().asType(GSuiteBatch.class);
      gSuiteBatch = Beans.get(GSuiteBatchRepository.class).find(gSuiteBatch.getId());
      //	      Batch batch = Beans.get(GSuiteBatchService.class).emailSyncBatch(gSuiteBatch);
      //	      response.setFlash(batch.getComments());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }
}
