/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.administration.AbstractBatchService;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
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

    switch (redmineBatch.getActionSelect()) {
      case RedmineBatchRepository.ACTION_IMPORT:
        batch = importAll(redmineBatch);
        break;
      case RedmineBatchRepository.ACTION_EXPORT:
        batch = exportAll(redmineBatch);
        break;

      default:
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BASE_BATCH_1),
            redmineBatch.getActionSelect(),
            redmineBatch.getCode());
    }

    return batch;
  }

  public Batch importAll(RedmineBatch redmineBatch) {
    return Beans.get(BatchImportAllRedmine.class).run(redmineBatch);
  }

  public Batch exportAll(RedmineBatch redmineBatch) {
    return Beans.get(BatchExportAllRedmine.class).run(redmineBatch);
  }
}
