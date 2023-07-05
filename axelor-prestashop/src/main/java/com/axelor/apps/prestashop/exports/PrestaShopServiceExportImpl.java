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
package com.axelor.apps.prestashop.exports;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.prestashop.db.PrestaShopBatch;
import com.axelor.apps.prestashop.exports.service.ExportAddressService;
import com.axelor.apps.prestashop.exports.service.ExportCategoryService;
import com.axelor.apps.prestashop.exports.service.ExportCountryService;
import com.axelor.apps.prestashop.exports.service.ExportCurrencyService;
import com.axelor.apps.prestashop.exports.service.ExportCustomerService;
import com.axelor.apps.prestashop.exports.service.ExportOrderService;
import com.axelor.apps.prestashop.exports.service.ExportProductService;
import com.axelor.apps.prestashop.exports.service.ExportTaxService;
import com.axelor.apps.prestashop.service.library.PrestaShopWebserviceException;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.axelor.studio.db.AppPrestashop;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.StringBuilderWriter;

@Singleton
public class PrestaShopServiceExportImpl implements PrestaShopServiceExport {

  @Inject private MetaFiles metaFiles;

  @Inject private ExportCurrencyService currencyService;

  @Inject private ExportCountryService countryService;

  @Inject private ExportCustomerService customerService;

  @Inject private ExportAddressService addressService;

  @Inject private ExportTaxService taxService;

  @Inject private ExportCategoryService categoryService;

  @Inject private ExportProductService productService;

  @Inject private ExportOrderService orderService;

  /**
   * Export base elements.
   *
   * @param appConfig Prestashop module's configuration
   * @param logWriter Buffer used to write log messages to be displayed in Axelor.
   * @throws PrestaShopWebserviceException If any remote call fails or anything goes wrong on a
   *     logic point of view
   * @throws IOException If any underlying I/O fails.
   */
  public void exportAxelorBase(
      AppPrestashop appConfig, boolean includeArchiveRecords, final Writer logWriter)
      throws PrestaShopWebserviceException, IOException {
    currencyService.exportCurrency(appConfig, includeArchiveRecords, logWriter);
    countryService.exportCountry(appConfig, includeArchiveRecords, logWriter);
    customerService.exportCustomer(appConfig, includeArchiveRecords, logWriter);
    addressService.exportAddress(appConfig, includeArchiveRecords, logWriter);
    taxService.exportTax(appConfig, includeArchiveRecords, logWriter);
    categoryService.exportCategory(appConfig, includeArchiveRecords, logWriter);
    productService.exportProduct(appConfig, includeArchiveRecords, logWriter);
  }

  /** Export Axelor modules (Base, SaleOrder) */
  @Override
  public void export(AppPrestashop appConfig, Batch batch) throws IOException {
    StringBuilderWriter logWriter = new StringBuilderWriter(1024);
    try {
      boolean includeArchiveRecords = false;
      PrestaShopBatch prestaShopBatch = batch.getPrestaShopBatch();
      if (prestaShopBatch != null) {
        includeArchiveRecords = prestaShopBatch.getIncludeArchiveRecords();
      }
      exportAxelorBase(appConfig, includeArchiveRecords, logWriter);

      orderService.exportOrder(appConfig, includeArchiveRecords, logWriter);
      logWriter.write(String.format("%n==== END OF LOG ====%n"));
      logWriter.write(String.format("%n==== END OF LOG ====%n"));
    } catch (PrestaShopWebserviceException e) {
      TraceBackService.trace(e);
    } finally {
      IOUtils.closeQuietly(logWriter);
      MetaFile exporMetaFile =
          metaFiles.upload(
              new ByteArrayInputStream(logWriter.toString().getBytes()), "export-log.txt");
      batch.setPrestaShopBatchLog(exporMetaFile);
    }
  }
}
