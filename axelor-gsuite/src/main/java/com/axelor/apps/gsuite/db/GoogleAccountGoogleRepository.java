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
package com.axelor.apps.gsuite.db;

import com.axelor.apps.gsuite.db.repo.EventGoogleAccountRepository;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.db.repo.PartnerGoogleAccountRepository;
import com.google.inject.Inject;
import java.util.List;

public class GoogleAccountGoogleRepository extends GoogleAccountRepository {

  @Inject private EventGoogleAccountRepository eventGoogleAccountRepo;

  @Inject private PartnerGoogleAccountRepository partnerGoogleAccountRepo;

  @Override
  public void remove(GoogleAccount account) {

    List<PartnerGoogleAccount> partnerAccounts =
        partnerGoogleAccountRepo.all().filter("self.googleAccount = ?1", account).fetch();
    for (PartnerGoogleAccount partnerAccount : partnerAccounts) {
      partnerGoogleAccountRepo.remove(partnerAccount);
    }
    List<EventGoogleAccount> eventAccounts =
        eventGoogleAccountRepo.all().filter("self.googleAccount = ?1", account).fetch();
    for (EventGoogleAccount eventAccount : eventAccounts) {
      eventGoogleAccountRepo.remove(eventAccount);
    }
    super.remove(account);
  }
}
