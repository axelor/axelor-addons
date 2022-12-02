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

import com.axelor.apps.client.portal.db.ClientResource;
import com.axelor.apps.client.portal.db.repo.ClientResourceRepository;
import com.axelor.apps.customer.portal.service.CommonService;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaFileRepository;
import java.util.Map;

public class ClientResourceManagementRepository extends ClientResourceRepository {

  @Override
  public ClientResource save(ClientResource clientResource) {

    if (clientResource.getVersion().equals(0)) {
      Beans.get(CommonService.class).manageUnreadRecord(clientResource);
    }
    clientResource = super.save(clientResource);
    MetaFile metaFile = clientResource.getResourceFile();
    if (metaFile == null) {
      return clientResource;
    }

    metaFile.setIsShared(true);
    Beans.get(MetaFileRepository.class).save(metaFile);
    return clientResource;
  }

  @Override
  public Map<String, Object> populate(Map<String, Object> json, Map<String, Object> context) {

    Map<String, Object> map = super.populate(json, context);
    if (json != null && json.get("id") != null) {
      map.put(
          "$unread",
          Beans.get(CommonService.class)
              .isUnreadRecord((Long) json.get("id"), (String) context.get("_model")));
    }

    return map;
  }
}
