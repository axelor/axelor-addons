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

import java.io.Reader;
import java.io.StringReader;
import java.util.function.Consumer;

import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import javax.swing.text.html.HTMLEditorKit;

import com.axelor.apps.base.db.Batch;
import com.taskadapter.redmineapi.RedmineManager;

public class ExportService {

  public static String result = "";
  protected static int success = 0, fail = 0;

  protected RedmineManager redmineManager;
  protected Consumer<Object> onSuccess;
  protected Consumer<Throwable> onError;
  protected Batch batch;

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
}
