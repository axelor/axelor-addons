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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.auth.db.AuditableModel;
import com.axelor.db.JPA;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.MetaFiles;
import com.google.common.io.ByteSource;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Attachment;

import net.java.textilej.parser.MarkupParser;
import net.java.textilej.parser.builder.HtmlDocumentBuilder;
import net.java.textilej.parser.markup.textile.TextileDialect;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ImportService {

  @Inject DMSFileRepository dmsFileRepo;
  @Inject AppRedmineRepository appRedmineRepo;

  @Inject private MetaFiles metaFiles;

  public static String result = "";
  protected static int success = 0, fail = 0;

  protected RedmineManager redmineManager;
  protected Boolean isReset;
  protected Batch batch;

  protected void setLocalDate(AuditableModel obj, Date objDate, String methodName) {
    try {
      Method createdOnMethod = AuditableModel.class.getDeclaredMethod(methodName, LocalDate.class);
      try {
        createdOnMethod.setAccessible(true);
        createdOnMethod.invoke(
            obj, objDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());

      } finally {
        createdOnMethod.setAccessible(false);
      }
    } catch (NoSuchMethodException
        | SecurityException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException e) {
      TraceBackService.trace(e);
    }
  }

  protected void setLocalDateTime(AuditableModel obj, Date objDate, String methodName) {
    try {
      Method createdOnMethod =
          AuditableModel.class.getDeclaredMethod(methodName, LocalDateTime.class);
      try {
        createdOnMethod.setAccessible(true);
        createdOnMethod.invoke(
            obj, objDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());

      } finally {
        createdOnMethod.setAccessible(false);
      }
    } catch (NoSuchMethodException
        | SecurityException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException e) {
      TraceBackService.trace(e);
    }
  }

  @Transactional
  protected void importAttachments(
      AuditableModel obj, Collection<Attachment> redmineAttachments, Date lastImportDateTime) {

    if (redmineAttachments != null && !redmineAttachments.isEmpty()) {
      for (Attachment redmineAttachment : redmineAttachments) {
        if (lastImportDateTime != null
            && redmineAttachment.getCreatedOn().before(lastImportDateTime)) {
          continue;
        }
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
        is = new URL(redmineAttachment.getContentURL()).openStream();
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
  private void attachExistingFile(DMSFile attachmentFile, AuditableModel obj) {
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

  protected String getHtmlFromTextile(String textile) {
    MarkupParser parser = new MarkupParser(new TextileDialect());
    StringWriter sw = new StringWriter();
    HtmlDocumentBuilder builder = new HtmlDocumentBuilder(sw);
    boolean isDocument = false;
    builder.setEmitAsDocument(isDocument);
    parser.setBuilder(builder);
    parser.parse(textile);
    return sw.toString();
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
}
