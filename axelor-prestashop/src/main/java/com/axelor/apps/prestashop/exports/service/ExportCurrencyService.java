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
package com.axelor.apps.prestashop.exports.service;

import com.axelor.apps.prestashop.service.library.PrestaShopWebserviceException;
import com.axelor.studio.db.AppPrestashop;
import java.io.IOException;
import java.io.Writer;

public interface ExportCurrencyService {

  /**
   * Export axelor Currency object
   *
   * @param logBuffer object of log file
   * @return log file object
   * @throws IOException
   * @throws PrestaShopWebserviceException
   */
  public void exportCurrency(AppPrestashop appConfig, Writer logBuffer)
      throws IOException, PrestaShopWebserviceException;
}
