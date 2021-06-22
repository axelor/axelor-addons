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
package com.axelor.apps.office365.web;

import com.axelor.apps.base.db.BaseBatch;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.BaseBatchRepository;
import com.axelor.apps.office365.service.batch.Office365BaseBatchService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;

public class Office365BatchController {

  public void actionSynchronizeContacts(ActionRequest request, ActionResponse response) {

    try {
      BaseBatch baseBatch = request.getContext().asType(BaseBatch.class);
      baseBatch = Beans.get(BaseBatchRepository.class).find(baseBatch.getId());
      Batch batch = Beans.get(Office365BaseBatchService.class).synchronizeContacts(baseBatch);
      response.setFlash(batch.getComments());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }
}
