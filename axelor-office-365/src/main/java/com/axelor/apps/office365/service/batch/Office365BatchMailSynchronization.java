/* Axelor Business Solutions
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
import com.axelor.apps.office.db.OfficeAccount;
import com.axelor.apps.office.db.repo.OfficeAccountRepository;
import com.axelor.apps.office365.service.Office365Service;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class Office365BatchMailSynchronization extends AbstractBatch {

  @Inject Office365Service office365Service;

  @Override
  protected void process() {

    List<OfficeAccount> officeAccounts =
        Beans.get(AppOffice365Repository.class).all().fetchOne().getOfficeAccountSet().stream()
            .filter(account -> account.getIsAuthorized())
            .collect(Collectors.toList());

    if (ObjectUtils.isEmpty(officeAccounts)) {
      officeAccounts =
          Beans.get(OfficeAccountRepository.class).all().filter("self.isAuthorized = true").fetch();
    }

    for (OfficeAccount officeAccount : officeAccounts) {
      try {
        office365Service.syncMail(officeAccount, Office365Service.MAIL_URL);
        incrementDone();
      } catch (Exception e) {
        TraceBackService.trace(e, "Mail synchronization", batch.getId());
        incrementAnomaly();
      }
    }

    batch.setComments(
        String.format("Success : %s , Anomaly : %s", batch.getDone(), batch.getAnomaly()));
  }
}
