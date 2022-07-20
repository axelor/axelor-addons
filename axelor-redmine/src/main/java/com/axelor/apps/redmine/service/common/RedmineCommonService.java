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

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.ProjectTaskCategoryRepository;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.auth.db.AuditableModel;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.IssueManager;
import com.taskadapter.redmineapi.ProjectManager;
import com.taskadapter.redmineapi.bean.CustomField;
import java.io.StringWriter;
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

public class RedmineCommonService {

  protected UserRepository userRepo;
  protected EmployeeRepository empRepo;
  protected ProjectRepository projectRepo;
  protected ProductRepository productRepo;
  protected ProjectTaskRepository projectTaskRepo;
  protected ProjectTaskCategoryRepository projectCategoryRepo;
  protected PartnerRepository partnerRepo;
  protected AppRedmineRepository appRedmineRepo;
  protected CompanyRepository companyRepo;

  @Inject
  public RedmineCommonService(
      UserRepository userRepo,
      EmployeeRepository empRepo,
      ProjectRepository projectRepo,
      ProductRepository productRepo,
      ProjectTaskRepository projectTaskRepo,
      ProjectTaskCategoryRepository projectCategoryRepo,
      PartnerRepository partnerRepo,
      AppRedmineRepository appRedmineRepo,
      CompanyRepository companyRepo) {

    this.userRepo = userRepo;
    this.empRepo = empRepo;
    this.projectRepo = projectRepo;
    this.productRepo = productRepo;
    this.projectTaskRepo = projectTaskRepo;
    this.projectCategoryRepo = projectCategoryRepo;
    this.partnerRepo = partnerRepo;
    this.appRedmineRepo = appRedmineRepo;
    this.companyRepo = companyRepo;
  }

  protected static String result = "";
  protected int success = 0;
  protected int fail = 0;
  protected Consumer<Object> onSuccess;
  protected Consumer<Throwable> onError;

  protected Batch batch;
  protected ProjectManager redmineProjectManager;
  protected IssueManager redmineIssueManager;
  protected List<Object[]> errorObjList;
  protected Map<String, String> redmineCustomFieldsMap;
  protected LocalDateTime lastBatchUpdatedOn;
  protected HashMap<String, Object> selectionMap;
  protected HashMap<String, String> fieldMap;

  protected HashMap<Integer, String> redmineUserMap;
  protected HashMap<Long, Integer> parentMap = new HashMap<>();
  protected HashMap<Long, LocalDateTime> updatedOnMap = new HashMap<>();
  protected Object[] errors;

  protected SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
  protected String serverTimeZone;

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
      invokeMethod(createdOnMethod, obj, getRedmineDate(objDate));
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

    redmineCustomFieldsMap = new HashMap<>();

    if (CollectionUtils.isNotEmpty(customFieldSet)) {

      for (CustomField customField : customFieldSet) {
        redmineCustomFieldsMap.put(customField.getName(), customField.getValue());
      }
    }
  }

  public Employee getOsEmployee(Integer redmineId) {
    return empRepo
        .all()
        .filter(
            "self.contactPartner.emailAddress.address = ?1 "
                + "OR self.user.email = ?1 "
                + "OR self.user.partner.emailAddress.address = ?1",
            redmineUserMap.get(redmineId))
        .fetchOne();
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

  protected String getHtmlFromTextile(String textile) {

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

  protected void updateTransaction() {

    JPA.em().getTransaction().commit();

    if (!JPA.em().getTransaction().isActive()) {
      JPA.em().getTransaction().begin();
    }

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
      dateFormat.setTimeZone(TimeZone.getTimeZone(serverTimeZone));
      date = dateFormat.parse(dateGmtStr);
    } catch (ParseException e) {
      TraceBackService.trace(e, "", batch.getId());
    }

    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
  }
}
