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
package com.axelor.apps.gsuite.service.app;

import com.axelor.apps.base.db.AppGsuite;
import com.axelor.apps.base.db.repo.AppGsuiteRepository;
import com.axelor.apps.gsuite.db.ModelEmailLink;
import com.axelor.common.ObjectUtils;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.db.MetaModel;
import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;

public class AppGSuiteServiceImpl implements AppGSuiteService {

  @Inject AppGsuiteRepository appRepo;

  @Override
  public AppGsuite getAppGSuite() {
    return appRepo.all().cacheable().fetchOne();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Set<String> getRelatedEmailAddressSet() {
    Set<String> addressSet = new HashSet<>();
    List<ModelEmailLink> modelEmailLinkList = getAppGSuite().getEmailAddressFilterList();

    if (CollectionUtils.isNotEmpty(modelEmailLinkList)) {
      for (ModelEmailLink modelEmailLink : modelEmailLinkList) {
        try {
          String metaField = modelEmailLink.getMetaField();
          List<Map> addressMapList =
              getRelatedEmailByModel(
                  (Class<MetaModel>) Class.forName(modelEmailLink.getMetaModel().getFullName()),
                  metaField);
          for (Map map : addressMapList) {
            String address = (String) map.get(metaField);
            if (ObjectUtils.notEmpty(address)) {
              addressSet.add(address);
            }
          }

        } catch (ClassNotFoundException e) {
          TraceBackService.trace(e);
        }
      }
    }

    return addressSet;
  }

  @SuppressWarnings("rawtypes")
  private <T extends Model> List<Map> getRelatedEmailByModel(
      Class<T> modelConcerned, String fieldName) {
    Query<T> query = Query.of(modelConcerned);
    return query.cacheable().select(fieldName).fetch(0, 0);
  }
}
