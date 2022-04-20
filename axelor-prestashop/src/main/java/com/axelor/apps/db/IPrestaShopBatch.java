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
package com.axelor.apps.db;

import com.axelor.db.Model;

/**
 * Interface of Event object. Enum all static variable of object.
 *
 * @author dubaux
 */
public interface IPrestaShopBatch {

  /** Static select in PrestaShopBatch */
  // ACTION TYPE
  static final int BATCH_IMPORT = 1;

  static final int BATCH_EXPORT = 2;

  static final String TRACE_ORIGIN_IMPORT = "prestashopImport";
  static final String TRACE_ORIGIN_EXPORT = "prestashopExport";

  /** @see Model#getImportOrigin() */
  static final String IMPORT_ORIGIN_PRESTASHOP = "prestashop";
}
