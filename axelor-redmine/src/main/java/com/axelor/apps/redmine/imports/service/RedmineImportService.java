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

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.redmine.db.DynamicFieldsSync;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.auth.db.AuditableModel;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.MetaModel;
import com.google.common.io.ByteSource;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.AttachmentManager;
import com.taskadapter.redmineapi.IssueManager;
import com.taskadapter.redmineapi.ProjectManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.TimeEntryManager;
import com.taskadapter.redmineapi.UserManager;
import com.taskadapter.redmineapi.WikiManager;
import com.taskadapter.redmineapi.bean.Attachment;
import com.taskadapter.redmineapi.bean.CustomField;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.java.textilej.parser.MarkupParser;
import net.java.textilej.parser.builder.HtmlDocumentBuilder;
import net.java.textilej.parser.markup.textile.TextileDialect;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.hibernate.Hibernate;

public class RedmineImportService {

  protected DMSFileRepository dmsFileRepo;
  protected AppRedmineRepository appRedmineRepo;
  protected MetaFiles metaFiles;
  protected BatchRepository batchRepo;
  protected UserRepository userRepo;

  protected static final Pattern PATTERN = Pattern.compile("src=\"(.*?)\"");
  protected static final Pattern TAG_PATTERN = Pattern.compile("(<)([^>]*)(>)");

  protected static final String IMG_URL =
      "ws/rest/com.axelor.meta.db.MetaFile/%s/content/download?v=%s&amp;image=true";

  public static String result = "";
  protected static int success = 0, fail = 0;

  protected RedmineManager redmineManager;
  protected Batch batch;
  protected Consumer<Object> onSuccess;
  protected Consumer<Throwable> onError;
  protected Boolean addInList = false;

  protected IssueManager redmineIssueManager;
  protected UserManager redmineUserManager;
  protected ProjectManager redmineProjectManager;
  protected WikiManager redmineWikiManager;
  protected AttachmentManager redmineAttachmentManager;
  protected TimeEntryManager redmineTimeEntryManager;

  protected MetaModel metaModel;
  protected List<Object[]> errorObjList;
  protected List<DynamicFieldsSync> dynamicFieldsSyncList;

  public static final String METAMODEL_PROJECT_CATEGORY = "ProjectCategory";
  public static final String METAMODEL_PROJECT = "Project";
  public static final String METAMODEL_PROJECT_VERSION = "ProjectVersion";
  public static final String METAMODEL_TEAM_TASK = "TeamTask";
  public static final String METAMODEL_TIMESHEET_LINE = "TimesheetLine";

  public static final String DYNAMIC_IMPORT = "Import";

  // This number can theoretically be different for different Redmine versions
  // see http://www.redmine.org/projects/redmine/repository/entry/trunk/app/models/project.rb
  public static final Integer REDMINE_PROJECT_STATUS_CLOSED = 5;

  private Map<Integer, User> redmineUserMap = new HashMap<>();

  @Inject
  public RedmineImportService(
      DMSFileRepository dmsFileRepo,
      AppRedmineRepository appRedmineRepo,
      MetaFiles metaFiles,
      BatchRepository batchRepo,
      UserRepository userRepo) {

    this.dmsFileRepo = dmsFileRepo;
    this.appRedmineRepo = appRedmineRepo;
    this.metaFiles = metaFiles;
    this.batchRepo = batchRepo;
    this.userRepo = userRepo;
  }

  protected void setCreatedUser(AuditableModel obj, User objUser, String methodName) {

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

  @Transactional
  public void importAttachments(
      AuditableModel obj, Collection<Attachment> redmineAttachments, Date lastImportDateTime) {

    if (redmineAttachments != null && !redmineAttachments.isEmpty()) {

      for (Attachment redmineAttachment : redmineAttachments) {
        DMSFile attachmentFile =
            dmsFileRepo.all().filter("self.redmineId = ?", redmineAttachment.getId()).fetchOne();

        if (attachmentFile == null) {
          downloadNewAttachment(redmineAttachment, obj);
        } else {
          attachExistingFile(attachmentFile, obj);
        }
      }
      JPA.manage(obj);
    }
  }

  private void downloadNewAttachment(Attachment redmineAttachment, AuditableModel obj) {

    try {
      InputStream is;

      try {

        if (redmineAttachment.getContentType().contains("image/")) {
          byte[] imageArr =
              redmineManager.getAttachmentManager().downloadAttachmentContent(redmineAttachment);
          is = ByteSource.wrap(imageArr).openStream();
        } else {
          is = new URL(redmineAttachment.getContentURL()).openStream();
        }
      } catch (Exception e) {
        byte[] imageArr =
            redmineManager.getAttachmentManager().downloadAttachmentContent(redmineAttachment);
        is = ByteSource.wrap(imageArr).openStream();
      }
      DMSFile attachmentFile = metaFiles.attach(is, redmineAttachment.getFileName(), obj);
      attachmentFile.setRedmineId(redmineAttachment.getId());
    } catch (IOException | RedmineException e) {
      TraceBackService.trace(e);
    }
  }

  @Transactional
  public void attachExistingFile(DMSFile attachmentFile, AuditableModel obj) {

    obj = (AuditableModel) Hibernate.unproxy(obj);
    AuditableModel attachObj = JPA.find(obj.getClass(), attachmentFile.getRelatedId());

    if (attachObj == null) {
      List<DMSFile> attachmentFiles =
          dmsFileRepo.all().filter("self.relatedId = ?", attachmentFile.getRelatedId()).fetch();

      for (DMSFile dmsFile : attachmentFiles) {
        dmsFile.setRelatedId(obj.getId());
        dmsFileRepo.save(dmsFile);
        metaFiles.attach(attachmentFile.getMetaFile(), obj);
      }
    }
  }

  public String getHtmlFromTextile(String textile, AuditableModel obj) {

    if (!StringUtils.isBlank(textile)) {
      Matcher matcher = TAG_PATTERN.matcher(textile);

      while (matcher.find()) {

        if (!matcher.group(2).startsWith("pre") && !matcher.group(2).startsWith("/pre")) {
          String input = "<" + matcher.group(2) + ">";
          String output = "&lt;" + matcher.group(2) + "&gt;";
          textile = textile.replace(input, output);
        }
      }

      MarkupParser parser = new MarkupParser(new TextileDialect());
      StringWriter sw = new StringWriter();
      HtmlDocumentBuilder builder = new HtmlDocumentBuilder(sw);
      boolean isDocument = false;
      builder.setEmitAsDocument(isDocument);
      parser.setBuilder(builder);
      parser.parse(textile);
      String content = sw.toString().replaceAll("&amp;", "&");
      content =
          replaceImagePath(content, obj).replaceAll("(\r\n|\n)", "<br />").replaceAll("\\s", " ");

      return content;
    }

    return "";
  }

  protected String replaceImagePath(String content, AuditableModel obj) {

    if (!StringUtils.isBlank(content) && obj != null && obj.getId() != null) {
      Matcher matcher = PATTERN.matcher(content);

      while (matcher.find()) {

        if (matcher.groupCount() > 0) {
          String path = "";
          String fileName = matcher.group(1);

          int index = matcher.group(1).lastIndexOf("/");

          if (index > -1) {
            path = matcher.group(1).substring(0, index);
            fileName = matcher.group(1).substring(index + 1);
          }

          DMSFile attachmentFile =
              dmsFileRepo
                  .all()
                  .filter(
                      "self.fileName = ? AND self.relatedId = ? AND self.relatedModel = ?",
                      fileName,
                      obj.getId(),
                      obj.getClass().getName())
                  .fetchOne();

          if (attachmentFile != null) {
            MetaFile metaFile = attachmentFile.getMetaFile();
            String newPath = String.format(IMG_URL, metaFile.getId(), metaFile.getVersion());

            if (!StringUtils.isBlank(newPath)) {
              content = content.replace(path + fileName, newPath);
            }
          }
        }
      }
    }

    return content;
  }

  public ResponseBody getResponseBody(String url) {

    AppRedmine appRedmine = appRedmineRepo.all().fetchOne();
    url = appRedmine.getUri() + url;
    OkHttpClient client = new OkHttpClient();
    okhttp3.Request request =
        new okhttp3.Request.Builder()
            .url(url)
            .addHeader("X-Redmine-API-Key", appRedmine.getApiAccessKey())
            .addHeader("Content-Type", "application/json")
            .build();
    Response response = null;

    try {
      response = client.newCall(request).execute();
      return response.body();
    } catch (IOException e) {
      TraceBackService.trace(e);
    }

    return null;
  }

  public Map<String, Object> setRedmineCustomFieldsMap(Collection<CustomField> customFieldSet) {

    Map<String, Object> redmineCustomFieldsMap = new HashMap<>();

    if (customFieldSet != null && !customFieldSet.isEmpty()) {

      for (CustomField customField : customFieldSet) {
        redmineCustomFieldsMap.put(
            customField.getName(),
            !customField.getValues().isEmpty() ? customField.getValues() : customField.getValue());
      }
    }

    return redmineCustomFieldsMap;
  }

  public User findOpensuiteUser(
      Integer redmineUserId, com.taskadapter.redmineapi.bean.User redmineUser)
      throws RedmineException {

    if (redmineUser != null) {
      redmineUserId = redmineUser.getId();
    }

    if (redmineUserId == null) {
      return null;
    }

    if (redmineUserMap.containsKey(redmineUserId)) {
      return redmineUserMap.get(redmineUserId);
    }
    if (redmineUser == null) {
      redmineUser = redmineUserManager.getUserById(redmineUserId);
    }
    String mail = redmineUser.getMail();
    User user = userRepo.all().filter("self.partner.emailAddress.address = ?1", mail).fetchOne();

    redmineUserMap.put(redmineUserId, user);

    return user;
  }

  public boolean validateDynamicFieldsSycList(
      List<DynamicFieldsSync> dynamicFieldsSyncList,
      String modelName,
      Map<String, Object> osMap,
      Map<String, Object> redmineMap) {

    Map<String, Boolean> validationMap = new HashMap<String, Boolean>();

    if (dynamicFieldsSyncList != null && !dynamicFieldsSyncList.isEmpty()) {

      switch (modelName) {
        case METAMODEL_TEAM_TASK:
          validationMap.put("name", false);
          break;
        case METAMODEL_PROJECT:
          validationMap.put("name", false);
          validationMap.put("code", false);
          break;
        case METAMODEL_PROJECT_CATEGORY:
          validationMap.put("name", false);
          break;
        case METAMODEL_TIMESHEET_LINE:
          validationMap.put("date", false);
          break;
        default:
          break;
      }

      for (DynamicFieldsSync dynamicFieldsSync : dynamicFieldsSyncList) {
        String fieldNameInAbs = dynamicFieldsSync.getFieldNameInAbs();
        String fieldNameInRedmine = dynamicFieldsSync.getFieldNameInRedmine();

        if (validationMap.containsKey(fieldNameInAbs)) {
          validationMap.put(fieldNameInAbs, true);
        }

        if (!osMap.containsKey(fieldNameInAbs)) {
          setErrorLog(
              modelName,
              fieldNameInAbs,
              fieldNameInRedmine,
              I18n.get(IMessage.REDMINE_SYNC_ERROR_ABS_FIELD_NOT_EXIST));
        }

        if (!dynamicFieldsSync.getIsCustomRedmineField()
            && !redmineMap.containsKey(fieldNameInRedmine)) {
          setErrorLog(
              modelName,
              fieldNameInAbs,
              fieldNameInRedmine,
              I18n.get(IMessage.REDMINE_SYNC_ERROR_REDMINE_FIELD_NOT_EXIST));
        }
      }

      if (!validationMap.containsValue(false)) {
        return true;
      } else {
        setErrorLog(
            modelName,
            null,
            null,
            I18n.get(IMessage.REDMINE_SYNC_ERROR_REQUIRED_FIEDS_BINDINGS_MISSING));
      }
    } else {
      setErrorLog(
          modelName,
          null,
          null,
          I18n.get(IMessage.REDMINE_SYNC_ERROR_DYNAMIC_FIELDS_SYNC_LIST_NOT_FOUND));
    }

    return false;
  }

  public void setErrorLog(
      String object, String fieldNameInAbs, String fieldNameInRedmine, String message) {

    errorObjList.add(
        new Object[] {object, DYNAMIC_IMPORT, "", fieldNameInAbs, fieldNameInRedmine, message});
  }
}
