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
package com.axelor.apps.gsuite.service.batch;

import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.service.GSuiteAOSDriveService;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BatchGSuiteDriveService extends AbstractBatch {

  @Inject protected UserRepository userRepo;
  @Inject protected GSuiteAOSDriveService gsuiteAOSDriveSyncService;

  @Override
  protected void process() {
    List<User> users = userRepo.all().filter("self.googleAccount != null").fetch();
    Set<GoogleAccount> accountSet =
        users.stream().map(User::getGoogleAccount).collect(Collectors.toSet());
    for (GoogleAccount account : accountSet) {
      try {
        gsuiteAOSDriveSyncService.sync(account);
        incrementDone();
      } catch (AxelorException e) {
        TraceBackService.trace(e, "", batch.getId());
        incrementAnomaly();
      }
    }
  }
}
