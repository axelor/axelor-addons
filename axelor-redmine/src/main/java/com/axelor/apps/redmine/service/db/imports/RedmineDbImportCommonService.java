/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
package com.axelor.apps.redmine.service.db.imports;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.redmine.db.repo.RedmineImportMappingRepository;
import com.axelor.auth.db.AuditableModel;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Inject;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.java.textilej.parser.MarkupParser;
import net.java.textilej.parser.builder.HtmlDocumentBuilder;
import net.java.textilej.parser.markup.textile.TextileDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineDbImportCommonService {

  protected UserRepository userRepo;
  protected ProjectRepository projectRepo;
  protected RedmineImportMappingRepository redmineImportMappingRepo;
  protected AppBaseService appBaseService;

  @Inject
  public RedmineDbImportCommonService(
      UserRepository userRepo,
      ProjectRepository projectRepo,
      RedmineImportMappingRepository redmineImportMappingRepo,
      AppBaseService appBaseService) {

    this.userRepo = userRepo;
    this.projectRepo = projectRepo;
    this.redmineImportMappingRepo = redmineImportMappingRepo;
    this.appBaseService = appBaseService;
  }

  protected static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected Map<String, String> fieldMap = new HashMap<>();
  protected Map<String, Object> selectionMap = new HashMap<>();
  protected Map<Long, LocalDateTime> updatedOnMap = new HashMap<>();
  protected Map<Long, Integer> parentMap = new HashMap<>();

  protected Consumer<Throwable> onError;
  protected Consumer<Object> onSuccess;
  protected AppRedmine appRedmine;
  protected Batch batch;

  protected String serverTimeZone;

  protected List<Object[]> errorObjList;
  protected Object[] errors = new Object[] {};

  protected String failedIds = null;
  protected int success = 0;
  protected int fail = 0;

  public String getHtmlFromTextile(String textile) {

    if (!StringUtils.isBlank(textile)) {
      MarkupParser parser = new MarkupParser(new TextileDialect());
      StringWriter sw = new StringWriter();
      HtmlDocumentBuilder builder = new HtmlDocumentBuilder(sw);
      builder.setEmitAsDocument(false);
      parser.setBuilder(builder);
      parser.parse(textile);

      return sw.toString();
    }

    return "";
  }

  public User getUserFromEmail(String email) {

    if (StringUtils.isEmpty(email)) {
      return null;
    }

    return userRepo
        .all()
        .filter(
            "self.employee.contactPartner.emailAddress.address = ?1 OR self.partner.emailAddress.address = ?1 OR self.email = ?1",
            email)
        .fetchOne();
  }

  public void setLocalDateTime(AuditableModel obj, LocalDateTime objDate, String methodName) {

    try {
      Method createdOnMethod =
          AuditableModel.class.getDeclaredMethod(methodName, LocalDateTime.class);
      invokeMethod(createdOnMethod, obj, objDate);
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public void invokeMethod(Method method, AuditableModel obj, Object value) {

    try {
      method.setAccessible(true);
      method.invoke(obj, value);
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    } finally {
      method.setAccessible(false);
    }
  }

  public void setCreatedByUser(AuditableModel obj, User objUser, String methodName) {

    if (objUser == null) {
      return;
    }

    try {
      Method createdByMethod = AuditableModel.class.getDeclaredMethod(methodName, User.class);
      invokeMethod(createdByMethod, obj, objUser);
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public String getDateAtServerTimezone(ZonedDateTime zDate) {

    if (zDate != null) {
      String dateStr = zDate.withZoneSameInstant(ZoneId.of(serverTimeZone)).withNano(0).toString();
      return dateStr.substring(0, dateStr.indexOf("+")) + "Z";
    }

    return null;
  }

  public LocalDateTime getDateAtLocalTimezone(LocalDateTime lDate) {

    if (lDate != null) {
      ZonedDateTime zDateAtServerTimeZone = lDate.atZone(ZoneId.of(serverTimeZone));
      return zDateAtServerTimeZone.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    return null;
  }

  public void setErrorLog(String object, String redmineReference) {

    errorObjList.add(
        ObjectArrays.concat(
            new Object[] {I18n.get(object), redmineReference}, errors, Object.class));

    errors = new Object[] {};
  }

  public void updateTransaction() {

    JPA.clear();

    if (!JPA.em().contains(batch)) {
      batch = JPA.find(Batch.class, batch.getId());
    }

    if (!JPA.em().contains(appRedmine)) {
      appRedmine = JPA.find(AppRedmine.class, appRedmine.getId());
    }
  }
}
