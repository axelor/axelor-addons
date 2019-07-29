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
import com.axelor.auth.db.Role;
import com.axelor.auth.db.repo.RoleRepository;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.UserManager;
import java.util.List;
import java.util.function.Consumer;
import javax.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportGroupServiceImpl extends ImportService implements ImportGroupService {

  @Inject RoleRepository roleRepo;

  Logger LOG = LoggerFactory.getLogger(getClass());

  @Transactional
  @Override
  public void importGroup(
      Batch batch,
      RedmineManager redmineManager,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {
    this.redmineManager = redmineManager;
    this.isReset = batch.getRedmineBatch().getUpdateAlreadyImported();
    this.batch = batch;

    try {
      UserManager um = redmineManager.getUserManager();
      List<com.taskadapter.redmineapi.bean.Group> groupList = um.getGroups();

      if (groupList != null && !groupList.isEmpty()) {
        for (com.taskadapter.redmineapi.bean.Group redmineGroup : groupList) {
          Role role = getRole(redmineGroup);
          try {
            if (role != null) {
              roleRepo.save(role);
              JPA.em().getTransaction().commit();
              if (!JPA.em().getTransaction().isActive()) {
                JPA.em().getTransaction().begin();
              }
              onSuccess.accept(role);
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
      onError.accept(e);
      fail++;
      TraceBackService.trace(e, "", batch.getId());
    }
    String resultStr =
        String.format("Redmine Group -> ABS Role : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  protected Role getRole(com.taskadapter.redmineapi.bean.Group redmineGroup) {
    Role existRole =
        roleRepo
            .all()
            .filter(
                "self.name = ? AND (self.redmineId IS NULL OR self.redmineId = 0)",
                redmineGroup.getName())
            .fetchOne();
    if (existRole != null) {
      existRole.setRedmineId(redmineGroup.getId());
      return existRole;
    }

    Role role = roleRepo.findByRedmineId(redmineGroup.getId());
    if (role == null) {
      role = new Role();
      role.setRedmineId(redmineGroup.getId());
    }
    role.setName(redmineGroup.getName());
    return role;
  }
}
