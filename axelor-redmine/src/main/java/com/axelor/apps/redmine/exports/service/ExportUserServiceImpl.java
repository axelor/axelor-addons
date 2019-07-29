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

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.UserBaseRepository;
import com.axelor.apps.redmine.imports.service.ImportService;
import com.axelor.apps.redmine.imports.service.ImportUserService;
import com.axelor.auth.db.Role;
import com.axelor.auth.db.User;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.UserManager;
import com.taskadapter.redmineapi.bean.Group;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportUserServiceImpl extends ExportService implements ExportUserService {

  @Inject UserBaseRepository userRepo;

  @Inject ImportUserService importUserService;
  @Inject ImportService importService;

  Logger LOG = LoggerFactory.getLogger(getClass());

  @Override
  public void exportUser(
      Batch batch,
      RedmineManager redmineManager,
      LocalDateTime lastExportDateTime,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {
    this.redmineManager = redmineManager;
    this.onError = onError;
    this.onSuccess = onSuccess;
    this.batch = batch;

    List<User> userList =
        userRepo
            .all()
            .filter(
                "self.redmineId IS NULL OR self.redmineId = 0 OR self.updatedOn > ?",
                lastExportDateTime)
            .fetch();

    for (User user : userList) {
      exportRedmineUser(user);
    }
    String resultStr =
        String.format("ABS User -> Redmine User : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  @Transactional
  protected void exportRedmineUser(User user) {
    boolean isExist = false;
    UserManager um = redmineManager.getUserManager();

    com.taskadapter.redmineapi.bean.User redmineUser;
    if (Optional.ofNullable(user.getRedmineId()).orElse(0) != 0) {
      try {
        redmineUser = um.getUserById(user.getRedmineId());
        isExist = true;
      } catch (RedmineException e) {
        redmineUser = new com.taskadapter.redmineapi.bean.User(redmineManager.getTransport());
      }
    } else {
      redmineUser = new com.taskadapter.redmineapi.bean.User(redmineManager.getTransport());
    }

    assignUserValues(user, redmineUser);

    try {
      if (isExist) {
        redmineUser.update();
      } else {
        redmineUser = redmineUser.create();
        user.setRedmineId(redmineUser.getId());
        userRepo.save(user);
      }
      onSuccess.accept(user);
      success++;
      addInGroups(user, redmineUser);
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
      onError.accept(e);
      fail++;
    }
  }

  private void assignUserValues(User user, com.taskadapter.redmineapi.bean.User redmineUser) {
    redmineUser.setPassword(user.getPassword());
    if (user.getEmail() == null) {
      redmineUser.setMail("test" + user.getId() + "@test.test");
    } else {
      redmineUser.setMail(user.getEmail().toLowerCase());
    }
    redmineUser.setLogin(user.getCode());
    redmineUser.setStatus(user.getBlocked() ? 0 : 1);
    redmineUser.setFullName(user.getFullName());
    redmineUser.setFirstName(user.getName());
    redmineUser.setLastName(user.getName());
  }

  private void addInGroups(User user, com.taskadapter.redmineapi.bean.User redmineUser) {
    Set<Role> userRoles =
        user.getRoles()
            .stream()
            .filter(role -> role.getRedmineId() != null && role.getRedmineId() != 0)
            .collect(Collectors.toSet());
    for (Role role : userRoles) {
      try {
        Group redmineUserGroup = redmineManager.getUserManager().getGroupById(role.getRedmineId());
        Collection<Group> redmineUserGroups = redmineUser.getGroups();
        if (redmineUserGroup != null && !redmineUserGroups.contains(redmineUserGroup)) {
          redmineManager.getUserManager().addUserToGroup(redmineUser, redmineUserGroup);
        }
      } catch (RedmineException e) {
        TraceBackService.trace(e, "", batch.getId());
      }
    }
  }
}
