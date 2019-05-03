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

import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.redmine.service.RedmineService;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.RedmineException;

public class BatchImportRedmine extends AbstractBatch {

  @Inject private RedmineService redmineService;

  @Override
  protected void process() {
    try {
      redmineService.importRedmineIssues(
          batch, ticket -> incrementDone(), error -> incrementAnomaly());
    } catch (AxelorException | RedmineException e) {
      throw new RuntimeException(e);
    }
    setResetImport();
  }

  private void setResetImport() {
    if (batch.getRedmineBatch().getUpdateAlreadyImported()) {
      batch.getRedmineBatch().setUpdateAlreadyImported(false);
    }
  }

  @Override
  protected void stop() {
    String comment = I18n.get(IMessage.BATCH_ISSUE_IMPORT_1) + "\n";
    comment +=
        String.format("\t* %s %s \n", batch.getDone(), I18n.get(IMessage.BATCH_ISSUE_IMPORT_2));
    comment +=
        String.format(
            "\t %s",
            batch.getAnomaly(),
            I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.ALARM_ENGINE_BATCH_4));

    super.stop();
    addComment(comment);
  }
}
