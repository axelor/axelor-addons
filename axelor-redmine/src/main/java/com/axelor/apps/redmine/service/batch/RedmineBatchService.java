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
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.administration.AbstractBatchService;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.apps.redmine.imports.service.RedmineImportService;
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
    RedmineImportService.result = "";

    switch (redmineBatch.getRedmineActionSelect()) {
      case RedmineBatchRepository.ACTION_SELECT_IMPORT_PROJECT:
        batch = redmineImportProjects(redmineBatch);
        break;
      case RedmineBatchRepository.ACTION_SELECT_IMPORT_ISSUE:
        batch = redmineImportIssues(redmineBatch);
        break;
      default:
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BASE_BATCH_1),
            redmineBatch.getRedmineActionSelect(),
            redmineBatch.getCode());
    }

    return batch;
  }

  public Batch redmineImportProjects(RedmineBatch redmineBatch) {
    return Beans.get(BatchImportAllRedmineProject.class).run(redmineBatch);
  }

  public Batch redmineImportIssues(RedmineBatch redmineBatch) {
    return Beans.get(BatchImportAllRedmineIssue.class).run(redmineBatch);
  }
}
