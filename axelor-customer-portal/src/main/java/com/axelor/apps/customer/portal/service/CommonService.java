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
package com.axelor.apps.customer.portal.service;

import com.axelor.apps.base.service.user.UserService;
import com.axelor.auth.db.AuditableModel;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.db.EntityHelper;
import com.axelor.db.JPA;
import com.axelor.db.mapper.Mapper;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.stream.Collectors;

public class CommonService {

  @Inject UserRepository userRepo;

  public String getUnreadRecordIds(AuditableModel model) {

    return Beans.get(UserRepository.class).all().filter("self.id != :id")
        .bind("id", Beans.get(UserService.class).getUser().getId()).fetch().stream()
        .map(user -> user.getId().toString())
        .collect(Collectors.joining("$#", "#", "$"));
  }

  @Transactional
  public void manageReadRecordIds(AuditableModel model) {

    User currentUser = Beans.get(UserService.class).getUser();
    if (model.getCreatedBy().equals(currentUser)) {
      return;
    }

    String fieldName = "userUnreadIds";
    Mapper mapper = Mapper.of(model.getClass());
    String ids = (String) mapper.get(model, fieldName);
    if (ids == null) {
      return;
    }

    mapper.set(
        model, fieldName, ids.replace(String.format("#%s$", currentUser.getId().toString()), ""));
    JPA.save(EntityHelper.getEntity(model));
  }

  public boolean isUnreadRecord(String ids) {
    User currentUser = Beans.get(UserService.class).getUser();
    if (StringUtils.notBlank(ids) && ids.contains(String.format("#%s$", currentUser.getId()))) {
      return true;
    }

    return false;
  }
}
