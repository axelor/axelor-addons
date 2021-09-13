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

import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.google.inject.Inject;

public class EmailAccountManagementRepository extends EmailAccountRepository {

  @Inject private UserRepository userRepo;

  @Override
  public EmailAccount save(EmailAccount entity) {
    EmailAccount account = super.save(entity);
    User user = account.getUser();
    if (user != null) {
      user.setEmailAccount(account);
      userRepo.save(user);
    }
    return account;
  }

  @Override
  public void remove(EmailAccount account) {
    User user = account.getUser();
    if (user != null) {
      user.setEmailAccount(null);
      userRepo.save(user);
    }
    super.remove(account);
  }
}
