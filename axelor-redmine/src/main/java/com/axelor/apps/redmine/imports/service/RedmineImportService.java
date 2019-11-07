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
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.TeamTaskCategoryRepository;
import com.axelor.auth.db.AuditableModel;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.ProjectManager;
import com.taskadapter.redmineapi.bean.CustomField;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class RedmineImportService {

  protected UserRepository userRepo;
  protected ProjectRepository projectRepo;
  protected ProductRepository productRepo;
  protected TeamTaskRepository teamTaskRepo;
  protected TeamTaskCategoryRepository projectCategoryRepo;
  protected PartnerRepository partnerRepo;
  protected AppRedmineRepository appRedmineRepo;
  protected CompanyRepository companyRepo;

  @Inject
  public RedmineImportService(
      UserRepository userRepo,
      ProjectRepository projectRepo,
      ProductRepository productRepo,
      TeamTaskRepository teamTaskRepo,
      TeamTaskCategoryRepository projectCategoryRepo,
      PartnerRepository partnerRepo,
      AppRedmineRepository appRedmineRepo,
      CompanyRepository companyRepo) {

    this.userRepo = userRepo;
    this.projectRepo = projectRepo;
    this.productRepo = productRepo;
    this.teamTaskRepo = teamTaskRepo;
    this.projectCategoryRepo = projectCategoryRepo;
    this.partnerRepo = partnerRepo;
    this.appRedmineRepo = appRedmineRepo;
    this.companyRepo = companyRepo;
  }

  public static String result = "";
  protected static int success = 0, fail = 0;
  protected Consumer<Object> onSuccess;
  protected Consumer<Throwable> onError;

  protected Batch batch;
  protected ProjectManager redmineProjectManager;
  protected List<Object[]> errorObjList;
  protected Map<String, Object> redmineCustomFieldsMap;
  protected LocalDateTime lastBatchUpdatedOn;
  protected HashMap<String, String> selectionMap;
  protected HashMap<String, String> fieldMap;

  protected HashMap<Integer, String> redmineUserMap;
  protected HashMap<Long, Integer> parentMap = new HashMap<Long, Integer>();
  protected HashMap<Long, LocalDateTime> updatedOnMap = new HashMap<Long, LocalDateTime>();
  protected Object[] errors;

  public static final Integer REDMINE_PROJECT_STATUS_CLOSED = 5;

  protected void setCreatedByUser(AuditableModel obj, User objUser, String methodName) {

    if (objUser == null) {
      return;
    }

    try {
      Method createdByMethod = AuditableModel.class.getDeclaredMethod(methodName, User.class);
      invokeMethod(createdByMethod, obj, objUser);
    } catch (NoSuchMethodException | SecurityException | IllegalArgumentException e) {
      TraceBackService.trace(e);
    }
  }

  public void setLocalDateTime(AuditableModel obj, Date objDate, String methodName) {

    try {
      Method createdOnMethod =
          AuditableModel.class.getDeclaredMethod(methodName, LocalDateTime.class);
      invokeMethod(
          createdOnMethod,
          obj,
          objDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
    } catch (NoSuchMethodException | SecurityException | IllegalArgumentException e) {
      TraceBackService.trace(e);
    }
  }

  protected void invokeMethod(Method method, AuditableModel obj, Object value) {

    try {
      method.setAccessible(true);
      method.invoke(obj, value);
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      TraceBackService.trace(e);
    } finally {
      method.setAccessible(false);
    }
  }

  public void setRedmineCustomFieldsMap(Collection<CustomField> customFieldSet) {

    this.redmineCustomFieldsMap = new HashMap<>();

    if (customFieldSet != null && !customFieldSet.isEmpty()) {

      for (CustomField customField : customFieldSet) {
        redmineCustomFieldsMap.put(customField.getName(), customField);
      }
    }
  }

  public User getOsUser(Integer redmineId) {

    return userRepo
        .all()
        .filter(
            "self.email = ?1 OR self.partner.emailAddress.address = ?1",
            redmineUserMap.get(redmineId))
        .fetchOne();
  }

  public void setErrorLog(String object, String redmineRef) {

    errorObjList.add(ObjectArrays.concat(new Object[] {object, redmineRef}, errors, Object.class));
  }
}
