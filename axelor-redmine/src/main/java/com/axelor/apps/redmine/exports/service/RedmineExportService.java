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
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.db.MetaModel;
import com.taskadapter.redmineapi.IssueManager;
import com.taskadapter.redmineapi.ProjectManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.TimeEntryManager;
import com.taskadapter.redmineapi.UserManager;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.Tracker;
import com.taskadapter.redmineapi.bean.User;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import javax.swing.text.html.HTMLEditorKit;

public class RedmineExportService {

  public static String result = "";
  protected static int success = 0, fail = 0;

  protected RedmineManager redmineManager;
  protected Consumer<Object> onSuccess;
  protected Consumer<Throwable> onError;
  protected Batch batch;

  protected UserManager redmineUserManager;
  protected IssueManager redmineIssueManager;
  protected ProjectManager redmineProjectManager;
  protected TimeEntryManager redmineTimeEntryManager;

  protected MetaModel metaModel;
  protected List<Object[]> errorObjList;

  public static final String METAMODEL_PROJECT = "Project";
  public static final String METAMODEL_PROJECT_VERSION = "ProjectVersion";
  public static final String METAMODEL_TEAM_TASK = "TeamTask";
  public static final String METAMODEL_TIMESHEET_LINE = "TimesheetLine";

  public static final String DYNAMIC_EXPORT = "Export";
  public static final String REDMINE_SERVER_404_NOT_FOUND =
      "Server returned '404 not found'. response body:";
  public static final String REDMINE_ISSUE_ASSIGNEE_INVALID = "Assignee is invalid\n";

  protected String getTextileFromHTML(String text) {
    EditorKit kit = new HTMLEditorKit();
    Document doc = kit.createDefaultDocument();
    doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
    try {
      Reader reader = new StringReader(text);
      kit.read(reader, doc, 0);
      return doc.getText(0, doc.getLength());
    } catch (Exception e) {
      return "";
    }
  }

  public User findRedmineUserByEmail(String mail) {

    try {
      Map<String, String> params = new HashMap<String, String>();
      params.put("name", mail);
      List<User> redmineUserList = redmineManager.getUserManager().getUsers(params).getResults();
      User redmineUser =
          redmineUserList != null && !redmineUserList.isEmpty() ? redmineUserList.get(0) : null;

      return redmineUser;
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }

    return null;
  }

  public void setRedmineCustomFieldValues(
      Collection<CustomField> customFields, Map<String, Object> redmineCustomFieldsMap, Long osId) {

    if (customFields != null && !customFields.isEmpty()) {

      for (CustomField customField : customFields) {

        if (redmineCustomFieldsMap != null
            && !redmineCustomFieldsMap.isEmpty()
            && redmineCustomFieldsMap.get(customField.getName()) != null) {
          customField.setValue(redmineCustomFieldsMap.get(customField.getName()).toString());
        }
      }
    }
  }

  public Project getRedmineProjectByKey(String key) {

    Project redmineProject;

    try {
      redmineProject =
          redmineProjectManager.getProjectByKey(key.toLowerCase().trim().replace(" ", ""));
    } catch (RedmineException e) {
      return null;
    }

    return redmineProject;
  }

  public Tracker getTrackerById(int redmineId) {

    List<Tracker> trackerList = null;

    try {
      trackerList = redmineIssueManager.getTrackers();
    } catch (RedmineException e) {
      TraceBackService.trace(e, "", batch.getId());
    }

    return trackerList
        .stream()
        .filter(t -> t.getId() == redmineId)
        .findAny()
        .orElse(trackerList.get(0));
  }

  public void setErrorLog(String object, String objectRef, String message) {

    errorObjList.add(new Object[] {object, DYNAMIC_EXPORT, objectRef, "", "", message});
  }
}
