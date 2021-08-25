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
import com.axelor.apps.base.db.ModelEmailLink;
import com.axelor.apps.base.db.repo.AppGsuiteRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.common.ObjectUtils;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class AppGSuiteServiceImpl implements AppGSuiteService {

  @Inject protected AppGsuiteRepository appRepo;
  @Inject protected AppBaseService appBaseService;

  @Override
  public AppGsuite getAppGSuite() {
    return appRepo.all().cacheable().fetchOne();
  }

  /**
   * To get list of email address from ModelLink in AppBase
   *
   * @param fromToTypeSelect 0 to include all and others based on {@link
   *     ModelEmailLink#addressTypeSelect}
   */
  @Override
  public Set<String> getRelatedEmailAddressSet(Integer fromToTypeSelect) {
    Set<String> addressSet = new HashSet<>();
    List<ModelEmailLink> emailLinkList = appBaseService.getAppBase().getEmailLinkList();
    if (ObjectUtils.isEmpty(emailLinkList)) {
      return addressSet;
    }
    try {
      for (ModelEmailLink modelEmailLink : emailLinkList) {
        if (fromToTypeSelect == 0
            || fromToTypeSelect.equals(modelEmailLink.getAddressTypeSelect())) {
          addressSet.addAll(getEmailAddresses(modelEmailLink));
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
    return addressSet;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected Set<String> getEmailAddresses(ModelEmailLink modelEmailLink)
      throws ClassNotFoundException {
    String field = modelEmailLink.getEmailField();
    String className = modelEmailLink.getMetaModel().getFullName();
    Class<Model> klass = (Class<Model>) Class.forName(className);
    List<Map> dataMapList = JPA.all(klass).select(field).fetch(0, 0);
    Set<String> addresses =
        dataMapList.stream()
            .map(map -> map.get(field))
            .filter(Objects::nonNull)
            .map(Object::toString)
            .collect(Collectors.toSet());
    return addresses;
  }
}
