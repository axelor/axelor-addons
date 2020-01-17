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
package com.axelor.apps.invoice.extractor.translation;

public interface ITranslation {
  public static final String FILE_DIRECTORY = "file.upload.dir";
  public static final String JSON_PRIFIX = "json";
  public static final String JSON_SUFIX = ".json";
  public static final String JSON_INVOICE_LINES = "lines";
  public static final String JSON_INVOICE_TABLES = "tables";
  public static final String PDF = "pdf";
  public static final String PDF_SUFIX = ".pdf";
  public static final String YML_FILE = "yml";
  public static final String TEMPLATE_FOLDER = "template";
  public static final String MANY_TO_MANY = "ManyToMany";
  public static final String MANY_TO_ONE = "ManyToOne";
  public static final String ONE_TO_MANY = "OneToMany";
  public static final String ONE_TO_ONE = "OneToOne";
  public static final String LOCAL_DATE_TYPE = "LocalDate";
  public static final String STRING_TYPE = "String";
  public static final String BIG_DECIMAL_TYPE = "BigDecimal";
  public static final String NULL_TYPE = "null";
  public static final String DEFAULT = "default";
}
