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
package com.axelor.apps.redmine.service.common;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.auth.db.AuditableModel;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.CustomField;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Consumer;
import net.java.textilej.parser.MarkupParser;
import net.java.textilej.parser.builder.HtmlDocumentBuilder;
import net.java.textilej.parser.markup.textile.TextileDialect;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineCommonService {

  public static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected UserRepository userRepo;
  protected EmployeeRepository empRepo;
  protected ProjectRepository projectRepo;
  protected RedmineImportMappingRepository redmineImportMappingRepo;
  protected AppBaseService appBaseService;

  protected Map<String, String> fieldMap = new HashMap<>();
  protected Map<String, Object> selectionMap = new HashMap<>();
  protected Map<Long, LocalDateTime> updatedOnMap = new HashMap<>();
  protected Map<Long, Integer> parentMap = new HashMap<>();
  protected Map<String, String> redmineCustomFieldsMap = new HashMap<>();

  protected RedmineManager redmineManager;
  protected LocalDateTime lastBatchEndDate;
  protected Consumer<Object> onSuccess;
  protected Consumer<Throwable> onError;
  protected Batch batch;
  protected AppRedmine appRedmine;
  protected Map<Integer, String> redmineUserMap;

  protected List<Object[]> errorObjList;
  protected Object[] errors = new Object[] {};

  protected Integer redmineFetchLimit;
  protected Integer totalFetchCount;
  protected Integer redmineMaxFetchLimit = 100;

  protected SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
  protected String serverTimeZone;

  protected static String result = "";
  protected int success = 0;
  protected int fail = 0;

  @Inject
  public RedmineCommonService(
      UserRepository userRepo,
      EmployeeRepository empRepo,
      ProjectRepository projectRepo,
      RedmineImportMappingRepository redmineImportMappingRepo,
      AppBaseService appBaseService) {

    this.userRepo = userRepo;
    this.empRepo = empRepo;
    this.projectRepo = projectRepo;
    this.redmineImportMappingRepo = redmineImportMappingRepo;
    this.appBaseService = appBaseService;
  }

  public void setCreatedByUser(AuditableModel obj, User objUser, String methodName) {

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
      invokeMethod(createdOnMethod, obj, getRedmineDate(objDate));
    } catch (NoSuchMethodException | SecurityException | IllegalArgumentException e) {
      TraceBackService.trace(e);
    }
  }

  public void invokeMethod(Method method, AuditableModel obj, Object value) {

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

    redmineCustomFieldsMap = new HashMap<>();

    if (CollectionUtils.isNotEmpty(customFieldSet)) {

      for (CustomField customField : customFieldSet) {
        redmineCustomFieldsMap.put(customField.getName(), customField.getValue());
      }
    }
  }

  public Employee getAosEmployee(Integer redmineId) {
    return empRepo
        .all()
        .filter(
            "self.contactPartner.emailAddress.address = ?1 "
                + "OR self.user.email = ?1 "
                + "OR self.user.partner.emailAddress.address = ?1",
            redmineUserMap.get(redmineId))
        .fetchOne();
  }

  public User getAosUser(Integer redmineId) {
    return userRepo
        .all()
        .filter(
            "self.employee.contactPartner.emailAddress.address = ?1 OR self.partner.emailAddress.address = ?1 OR self.email = ?1",
            redmineUserMap.get(redmineId))
        .fetchOne();
  }

  public String getHtmlFromTextile(String textile) {
    if (!StringUtils.isBlank(textile)) {
      MarkupParser parser = new MarkupParser(new TextileDialect());
      StringWriter sw = new StringWriter();
      HtmlDocumentBuilder builder = new HtmlDocumentBuilder(sw);
      boolean isDocument = false;
      builder.setEmitAsDocument(isDocument);
      parser.setBuilder(builder);
      parser.parse(textile);

      return sw.toString();
    }
    return "";
  }

  public void updateTransaction() {
    if (!JPA.em().getTransaction().isActive()) {
      JPA.em().getTransaction().begin();
    }

    JPA.em().getTransaction().commit();
    JPA.clear();

    if (!JPA.em().contains(batch)) {
      batch = JPA.find(Batch.class, batch.getId());
    }
  }

  public static String getResult() {
    return result;
  }

  public static void setResult(String result) {
    RedmineCommonService.result = result;
  }

  public LocalDateTime getRedmineDate(Date date) {
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    String dateGmtStr = dateFormat.format(date);

    try {
      if (serverTimeZone != null) {
        dateFormat.setTimeZone(TimeZone.getTimeZone(serverTimeZone));
      }
      date = dateFormat.parse(dateGmtStr);
    } catch (ParseException e) {
      TraceBackService.trace(e, "", batch.getId());
    }

    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
  }
}
