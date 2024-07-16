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
package com.axelor.apps.prestashop.imports;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.prestashop.service.library.PrestaShopWebserviceException;
import com.axelor.studio.db.AppPrestashop;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.time.ZonedDateTime;
import wslite.json.JSONException;

public interface PrestaShopServiceImport {

  /**
   * Import prestashop details or object to ABS
   *
   * @return import log file object
   * @throws IOException
   * @throws PrestaShopWebserviceException
   * @throws JAXBException
   * @throws TransformerException
   * @throws JSONException
   * @throws AxelorException
   */
  void importFromPrestaShop(AppPrestashop appConfig, ZonedDateTime endDate, Batch batch)
      throws IOException, PrestaShopWebserviceException, JAXBException, JSONException,
          AxelorException;
}
