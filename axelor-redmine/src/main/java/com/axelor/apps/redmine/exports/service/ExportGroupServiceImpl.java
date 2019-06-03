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
package com.axelor.apps.redmine.exports.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.db.Batch;
import com.axelor.auth.db.Role;
import com.axelor.auth.db.repo.RoleRepository;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.UserManager;
import com.taskadapter.redmineapi.bean.Group;

import java.util.Optional;

public class ExportGroupServiceImpl extends ExportService implements ExportGroupService {

  @Inject RoleRepository roleRepo;

  Logger LOG = LoggerFactory.getLogger(getClass());

  @Override
  public void exportGroup(
      Batch batch,
      RedmineManager redmineManager,
      LocalDateTime lastExportDateTime,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {
    this.redmineManager = redmineManager;
    this.onError = onError;
    this.onSuccess = onSuccess;
    this.batch = batch;

    List<Role> roles =
        roleRepo
            .all()
            .filter(
                "self.redmineId IS NULL OR self.redmineId = 0 OR self.updatedOn > ?",
                lastExportDateTime)
            .fetch();
    for (Role role : roles) {
      exportRedmineGroup(role);
    }
    String resultStr = String.format("ABS Role -> Redmine Group : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  @Transactional
  protected void exportRedmineGroup(Role role) {
    boolean isExist = false;
    UserManager um = redmineManager.getUserManager();

    Group redmineGroup = null;
    if (Optional.ofNullable(role.getRedmineId()).orElse(0) != 0) {
      try {
        redmineGroup = um.getGroupById(role.getRedmineId());
        isExist = true;
      } catch (RedmineException e) {
        redmineGroup = new Group(redmineManager.getTransport());
      }
    } else {
      try {
        redmineGroup = um.getGroupByName(role.getName());
      } catch (RedmineException e) {
      }
      if (redmineGroup != null) {
        isExist = true;
      } else {
        redmineGroup = new Group(redmineManager.getTransport());
      }
    }

    redmineGroup.setName(role.getName());
    try {
      if (isExist) {
        redmineGroup.update();
      } else {
        redmineGroup = redmineGroup.create();
        role.setRedmineId(redmineGroup.getId());
        roleRepo.save(role);
      }
      onSuccess.accept(role);
      success++;
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
      fail++;
    }
  }
}
