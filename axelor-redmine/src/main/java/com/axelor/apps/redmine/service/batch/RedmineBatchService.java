/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.redmine.service.batch;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.service.administration.AbstractBatchService;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;

public class RedmineBatchService extends AbstractBatchService {

  @Override
  protected Class<? extends Model> getModelClass() {
    return RedmineBatch.class;
  }

  @Override
  public Batch run(Model batchModel) throws AxelorException {
    Batch batch;
    RedmineBatch redmineBatch = (RedmineBatch) batchModel;

    batch = redmineSyncProcess(redmineBatch);

    return batch;
  }

  public Batch redmineSyncProcess(RedmineBatch redmineBatch) {
    return Beans.get(BatchSyncAllRedmine.class).run(redmineBatch);
  }
}
