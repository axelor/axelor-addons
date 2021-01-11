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
package com.axelor.apps.redmine.service.log;

import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class RedmineErrorLogService {

  public MetaFile generateErrorLog(
      String sheetName, String fileName, Object[] headers, List<Object[]> dataObjList) {

    XSSFWorkbook workbook = new XSSFWorkbook();
    XSSFSheet sheet = workbook.createSheet(sheetName);

    XSSFFont font = workbook.createFont();
    font.setBold(true);
    XSSFCellStyle style = workbook.createCellStyle();
    style.setFont(font);

    Map<String, Object[]> data = new LinkedHashMap<>();
    data.put("1", headers);

    int i = 2;

    for (Object[] dataObject : dataObjList) {
      data.put(String.valueOf(i++), dataObject);
    }

    Set<String> keyset = data.keySet();

    int rownum = 0;

    for (String key : keyset) {
      Row row = sheet.createRow(rownum++);
      Object[] dataObjArr = data.get(key);

      int cellnum = 0;

      for (Object dataObj : dataObjArr) {
        Cell cell = row.createCell(cellnum++);
        cell.setCellValue((String) dataObj);

        if (rownum == 1) {
          cell.setCellStyle(style);
        }
      }
    }

    /* For auto sizing: length of fix header columns + length of max expected columns after fix header columns */
    IntStream.range(0, headers.length + 2)
        .forEach(columnIndex -> sheet.autoSizeColumn(columnIndex));

    MetaFile metaFile = null;

    try {
      File excelFile = File.createTempFile(fileName, ".xlsx");
      FileOutputStream out = new FileOutputStream(excelFile);
      workbook.write(out);
      out.close();

      DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

      try (FileInputStream fileInputStream = new FileInputStream(excelFile)) {
        metaFile =
            Beans.get(MetaFiles.class)
                .upload(
                    new FileInputStream(excelFile),
                    fileName + dateFormat.format(new Date()) + ".xlsx");
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    return metaFile;
  }
}
