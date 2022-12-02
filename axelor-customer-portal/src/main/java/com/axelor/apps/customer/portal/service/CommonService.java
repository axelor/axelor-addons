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
import com.axelor.apps.client.portal.db.UnreadRecord;
import com.axelor.apps.client.portal.db.repo.UnreadRecordRepository;
import com.axelor.auth.db.AuditableModel;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.stream.Collectors;

public class CommonService {

  @Inject UserRepository userRepo;
  @Inject UnreadRecordRepository unreadRecordRepo;

  @Transactional
  public void manageUnreadRecord(AuditableModel model) {

    UnreadRecord unreadRecord =
        unreadRecordRepo
            .all()
            .filter(
                "self.relatedToSelect = :relatedToSelect AND self.relatedToSelectId = :relatedToSelectId")
            .bind("relatedToSelect", model.getClass().getCanonicalName())
            .bind("relatedToSelectId", model.getId())
            .fetchOne();
    if (unreadRecord == null) {
      unreadRecord = new UnreadRecord();
      unreadRecord.setRelatedToSelect(model.getClass().getCanonicalName());
      unreadRecord.setRelatedToSelectId(model.getId());
    }

    unreadRecord.setUserUnreadIds(
        Beans.get(UserRepository.class).all().filter("self.id != :id")
            .bind("id", Beans.get(UserService.class).getUser().getId()).fetch().stream()
            .map(user -> user.getId().toString())
            .collect(Collectors.joining("$#", "#", "$")));
    unreadRecordRepo.save(unreadRecord);
  }

  @Transactional
  public void manageReadRecordIds(AuditableModel model) {

    User currentUser = Beans.get(UserService.class).getUser();
    if (model.getCreatedBy() != null && model.getCreatedBy().equals(currentUser)) {
      return;
    }

    UnreadRecord unreadRecord =
        unreadRecordRepo
            .all()
            .filter(
                "self.relatedToSelect = :relatedToSelect AND self.relatedToSelectId = :relatedToSelectId")
            .bind("relatedToSelect", model.getClass().getCanonicalName())
            .bind("relatedToSelectId", model.getId())
            .fetchOne();
    if (unreadRecord == null || StringUtils.isBlank(unreadRecord.getUserUnreadIds())) {
      return;
    }

    String ids = unreadRecord.getUserUnreadIds();
    unreadRecord.setUserUnreadIds(
        ids.replace(String.format("#%s$", currentUser.getId().toString()), ""));
    unreadRecordRepo.save(unreadRecord);
  }

  public boolean isUnreadRecord(Long id, String model) {

    if (id == null) {
      return false;
    }

    User currentUser = Beans.get(UserService.class).getUser();
    UnreadRecord unreadRecord =
        unreadRecordRepo
            .all()
            .filter(
                "self.relatedToSelect = :relatedToSelect AND self.relatedToSelectId = :relatedToSelectId")
            .bind("relatedToSelect", model)
            .bind("relatedToSelectId", id)
            .fetchOne();
    if (unreadRecord == null || StringUtils.isBlank(unreadRecord.getUserUnreadIds())) {
      return false;
    }

    if (unreadRecord.getUserUnreadIds().contains(String.format("#%s$", currentUser.getId()))) {
      return true;
    }

    return false;
  }
}
