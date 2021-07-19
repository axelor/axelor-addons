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
package com.axelor.apps.gsuite.db.repo;

import com.axelor.apps.gsuite.db.EventGoogleAccount;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.PartnerGoogleAccount;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.google.inject.Inject;
import java.util.List;

public class GoogleAccountManagementRepository extends GoogleAccountRepository {

  @Inject private EventGoogleAccountRepository eventGoogleAccountRepo;

  @Inject private PartnerGoogleAccountRepository partnerGoogleAccountRepo;

  @Inject private UserRepository userRepo;

  @Override
  public GoogleAccount save(GoogleAccount entity) {
    GoogleAccount account = super.save(entity);
    User user = account.getOwnerUser();
    user.setGoogleAccount(account);
    userRepo.save(user);
    return account;
  }

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
    User user = account.getOwnerUser();
    user.setGoogleAccount(null);
    userRepo.save(user);
    super.remove(account);
  }
}
