/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.redmine.log.service;

import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class RedmineErrorLogService {

  @Inject MetaFiles metaFiles;

  public static final String EXCEL_SHEET_NAME = "Error Data";
  public static final String EXCEL_FILE_NAME = "Redmine_Error_Log_";
  public static final String EXCEL_FILE_EXTENSION = ".xlsx";

  public static final String HEADER_COL1 = "Object";
  public static final String HEADER_COL2 = "Import/Export";
  public static final String HEADER_COL3 = "ABS Ref. Id";
  public static final String HEADER_COL4 = "ABS Field";
  public static final String HEADER_COL5 = "Redmine Field";
  public static final String HEADER_COL6 = "Error";

  private File excelFile;

  public MetaFile redmineErrorLogService(List<Object[]> errorObjList) {

    MetaFile errorMetaFile = null;

    XSSFWorkbook workbook = new XSSFWorkbook();
    XSSFSheet sheet = workbook.createSheet(EXCEL_SHEET_NAME);
    Map<String, Object[]> errorData = new TreeMap<String, Object[]>();
    errorData.put(
        "1",
        new Object[] {
          HEADER_COL1, HEADER_COL2, HEADER_COL3, HEADER_COL4, HEADER_COL5, HEADER_COL6
        });

    int i = 2;
    for (Object[] object : errorObjList) {
      errorData.put(String.valueOf(i++), object);
    }

    Set<String> keyset = errorData.keySet();
    int rownum = 0;

    for (String key : keyset) {
      Row row = sheet.createRow(rownum++);
      Object[] objArr = errorData.get(key);
      int cellnum = 0;

      for (Object obj : objArr) {
        Cell cell = row.createCell(cellnum++);

        if (obj instanceof String) cell.setCellValue((String) obj);
        else if (obj instanceof Integer) cell.setCellValue((Integer) obj);
      }
    }

    try {
      excelFile = File.createTempFile(EXCEL_FILE_NAME, EXCEL_FILE_EXTENSION);
      FileOutputStream out = new FileOutputStream(excelFile);
      workbook.write(out);
      out.close();

      DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
      errorMetaFile =
          metaFiles.upload(
              new FileInputStream(excelFile),
              EXCEL_FILE_NAME + dateFormat.format(new Date()) + EXCEL_FILE_EXTENSION);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    return errorMetaFile;
  }
}
