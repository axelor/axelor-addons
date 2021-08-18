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
package com.axelor.apps.office365.service.batch;

import com.axelor.apps.base.db.repo.AppOffice365Repository;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.apps.office365.service.Office365Service;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class Office365BatchCalendarSynchronization extends AbstractBatch {

  @Inject Office365Service office365Service;

  @Override
  protected void process() {

    List<EmailAccount> emailAccounts =
        Beans.get(AppOffice365Repository.class).all().fetchOne().getEmailAccountSet().stream()
            .filter(account -> account.getIsValid())
            .collect(Collectors.toList());

    if (ObjectUtils.isEmpty(emailAccounts)) {
      emailAccounts =
          Beans.get(EmailAccountRepository.class)
              .all()
              .filter("self.isValid = true AND self.serverTypeSelect = :typeSelect")
              .bind("typeSelect", EmailAccountRepository.SERVER_TYPE_OFFICE365)
              .fetch();
    }

    for (EmailAccount emailAccount : emailAccounts) {
      try {
        office365Service.syncCalendar(emailAccount);
        incrementDone();
      } catch (Exception e) {
        TraceBackService.trace(e, "Calendar synchronization", batch.getId());
        incrementAnomaly();
      }
    }

    batch.setComments(
        String.format("Success : %s , Anomaly : %s", batch.getDone(), batch.getAnomaly()));
  }
}
