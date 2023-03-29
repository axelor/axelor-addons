/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2022 Axelor (<http://axelor.com>).
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
package com.axelor.apps.customer.portal.db.repo;

import com.axelor.apps.base.db.AppCustomerPortal;
import com.axelor.apps.base.db.repo.AppCustomerPortalRepository;
import com.axelor.meta.db.repo.MetaFileRepository;
import com.google.inject.Inject;

public class AppCustomerPortalPortalRepository extends AppCustomerPortalRepository {

  @Inject MetaFileRepository metaFileRepo;

  @Override
  public AppCustomerPortal save(AppCustomerPortal entity) {
    entity = super.save(entity);

    if (entity.getTermMetaFile() != null) {
      entity.getTermMetaFile().setIsShared(true);
      metaFileRepo.save(entity.getTermMetaFile());
    }

    if (entity.getReturnPolicyMetaFile() != null) {
      entity.getReturnPolicyMetaFile().setIsShared(true);
      metaFileRepo.save(entity.getReturnPolicyMetaFile());
    }

    if (entity.getDataPolicyMetaFile() != null) {
      entity.getDataPolicyMetaFile().setIsShared(true);
      metaFileRepo.save(entity.getDataPolicyMetaFile());
    }

    return entity;
  }
}
