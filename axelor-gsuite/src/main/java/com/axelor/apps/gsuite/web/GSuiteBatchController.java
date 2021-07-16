/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.gsuite.web;

import com.axelor.apps.base.db.AppGsuite;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.gsuite.db.GSuiteBatch;
import com.axelor.apps.gsuite.db.repo.GSuiteBatchRepository;
import com.axelor.apps.gsuite.service.app.AppGSuiteService;
import com.axelor.apps.gsuite.service.batch.GSuiteBatchService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class GSuiteBatchController {

  public void setActionSelect(ActionRequest request, ActionResponse response) {
    AppGsuite appGsuite = Beans.get(AppGSuiteService.class).getAppGSuite();
    List<Integer> actionSelect = new ArrayList<>();
    actionSelect.add(0);
    if (appGsuite.getIsEmailSyncAllowed()) {
      actionSelect.add(GSuiteBatchRepository.ACTION_EMAIL_SYNC);
    }
    if (appGsuite.getIsEventSyncAllowed()) {
      actionSelect.add(GSuiteBatchRepository.ACTION_EVENT_SYNC);
    }
    if (appGsuite.getIsTaskSyncAllowed()) {
      actionSelect.add(GSuiteBatchRepository.ACTION_TASK_SYNC);
    }
    if (appGsuite.getIsContactSyncAllowed()) {
      actionSelect.add(GSuiteBatchRepository.ACTION_CONTACT_SYNC);
    }
    if (appGsuite.getIsDriveSyncAllowed()) {
      actionSelect.add(GSuiteBatchRepository.ACTION_DRIVE_SYNC);
    }
    response.setAttr("actionSelect", "selection-in", actionSelect);
  }

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
      Batch batch = Beans.get(GSuiteBatchService.class).taskSyncBatch(gSuiteBatch);
      response.setFlash(batch.getComments());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }

  public void actionContactSyncBatch(ActionRequest request, ActionResponse response) {
    try {
      GSuiteBatch gSuiteBatch = request.getContext().asType(GSuiteBatch.class);
      gSuiteBatch = Beans.get(GSuiteBatchRepository.class).find(gSuiteBatch.getId());
      Batch batch = Beans.get(GSuiteBatchService.class).contactSyncBatch(gSuiteBatch);
      response.setFlash(batch.getComments());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }

  public void actionDriveSyncBatch(ActionRequest request, ActionResponse response) {
    try {
      GSuiteBatch gSuiteBatch = request.getContext().asType(GSuiteBatch.class);
      gSuiteBatch = Beans.get(GSuiteBatchRepository.class).find(gSuiteBatch.getId());
      Batch batch = Beans.get(GSuiteBatchService.class).driveSyncBatch(gSuiteBatch);
      response.setFlash(batch.getComments());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }
}
