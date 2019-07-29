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
package com.axelor.apps.redmine.imports.service;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.UserBaseRepository;
import com.axelor.auth.db.Role;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.RoleRepository;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.UserManager;
import com.taskadapter.redmineapi.bean.Group;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportUserServiceImpl extends ImportService implements ImportUserService {

  @Inject UserBaseRepository userRepo;
  @Inject RoleRepository roleRepo;

  Logger LOG = LoggerFactory.getLogger(getClass());

  @Transactional
  @Override
  public void importUser(
      Batch batch,
      Date lastImportDateTime,
      RedmineManager redmineManager,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {
    this.redmineManager = redmineManager;
    this.isReset = batch.getRedmineBatch().getUpdateAlreadyImported();
    this.batch = batch;

    try {
      UserManager um = redmineManager.getUserManager();
      List<com.taskadapter.redmineapi.bean.User> userList = um.getUsers();
      if (userList != null && !userList.isEmpty()) {
        for (com.taskadapter.redmineapi.bean.User redmineUser : userList) {
          if (!isReset
              && lastImportDateTime != null
              && redmineUser.getCreatedOn().before(lastImportDateTime)) {
            continue;
          }
          User user = userRepo.findByRedmineId(redmineUser.getId());
          try {
            user = getUser(user, redmineUser);
            if (user != null) {
              userRepo.save(user);
              onSuccess.accept(user);
              success++;
            }
          } catch (PersistenceException e) {
            onError.accept(e);
            fail++;
            JPA.em().getTransaction().rollback();
            JPA.em().getTransaction().begin();
          } catch (Exception e) {
            onError.accept(e);
            fail++;
            TraceBackService.trace(e, "", batch.getId());
          }
        }
      }
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
    String resultStr =
        String.format("Redmine User -> ABS User : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  protected User getUser(User user, com.taskadapter.redmineapi.bean.User redmineUser) {
    if (user == null) {
      User existUser =
          userRepo
              .all()
              .filter(
                  "self.code = ? AND (self.redmineId IS NULL OR self.redmineId = 0)",
                  redmineUser.getLogin())
              .fetchOne();
      if (existUser != null) {
        existUser.setRedmineId(redmineUser.getId());
        return existUser;
      }

      user = new User();
      user.setRedmineId(redmineUser.getId());
    }
    setUserValues(user, redmineUser);
    return user;
  }

  private void setUserValues(User user, com.taskadapter.redmineapi.bean.User redmineUser) {
    user.setCode(redmineUser.getLogin());
    user.setPassword(redmineUser.getPassword());
    if (redmineUser.getPassword() == null) {
      user.setPassword(redmineUser.getLogin());
    }

    user.setBlocked(redmineUser.getStatus() != null && redmineUser.getStatus() == 0 ? true : false);

    user.setEmail(redmineUser.getMail());

    String fullName = "";
    if (!StringUtils.isBlank(redmineUser.getFirstName())
        && !StringUtils.isBlank(redmineUser.getLastName())) {
      fullName = redmineUser.getFirstName() + " " + redmineUser.getLastName();
    } else if (!StringUtils.isBlank(redmineUser.getFirstName())) {
      fullName = redmineUser.getFirstName();
    } else if (!StringUtils.isBlank(redmineUser.getLastName())) {
      fullName = redmineUser.getLastName();
    }
    user.setName(fullName);
    user.setFullName(fullName);
    user.setRoles(getUserRole(redmineUser));
  }

  private Set<Role> getUserRole(com.taskadapter.redmineapi.bean.User redmineUser) {
    Collection<Group> redmineUserGroups = null;
    try {
      redmineUserGroups =
          redmineManager.getUserManager().getUserById(redmineUser.getId()).getGroups();
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }
    if (redmineUserGroups != null && !redmineUserGroups.isEmpty()) {
      Set<Role> roles = new HashSet<>();
      for (Group redmineGroup : redmineUserGroups) {
        Role role = roleRepo.findByRedmineId(redmineGroup.getId());
        if (role != null) {
          roles.add(role);
        }
      }
      if (!roles.isEmpty()) {
        return roles;
      }
    }
    return null;
  }
}
