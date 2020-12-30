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
package com.axelor.apps.prestashop.imports;

import com.axelor.apps.base.db.AppPrestashop;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.prestashop.imports.service.ImportAddressService;
import com.axelor.apps.prestashop.imports.service.ImportCategoryService;
import com.axelor.apps.prestashop.imports.service.ImportCountryService;
import com.axelor.apps.prestashop.imports.service.ImportCurrencyService;
import com.axelor.apps.prestashop.imports.service.ImportCustomerService;
import com.axelor.apps.prestashop.imports.service.ImportOrderService;
import com.axelor.apps.prestashop.imports.service.ImportProductService;
import com.axelor.apps.prestashop.imports.service.ImportTaxService;
import com.axelor.apps.prestashop.service.library.PrestaShopWebserviceException;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.time.ZonedDateTime;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.tika.io.IOUtils;

@Singleton
public class PrestaShopServiceImportImpl implements PrestaShopServiceImport {
  private MetaFiles metaFiles;
  private ImportCurrencyService currencyService;
  private ImportCountryService countryService;
  private ImportCustomerService customerService;
  private ImportAddressService addressService;
  private ImportTaxService taxService;
  private ImportCategoryService categoryService;
  private ImportProductService productService;
  private ImportOrderService orderService;

  @Inject
  public PrestaShopServiceImportImpl(
      MetaFiles metaFiles,
      ImportCurrencyService currencyService,
      ImportCountryService countryService,
      ImportCustomerService customerService,
      ImportAddressService addressService,
      ImportTaxService taxService,
      ImportCategoryService categoryService,
      ImportProductService productService,
      ImportOrderService orderService) {
    this.metaFiles = metaFiles;
    this.currencyService = currencyService;
    this.countryService = countryService;
    this.customerService = customerService;
    this.addressService = addressService;
    this.taxService = taxService;
    this.categoryService = categoryService;
    this.productService = productService;
    this.orderService = orderService;
  }

  public void importAxelorBase(
      AppPrestashop appConfig, ZonedDateTime endDate, final Writer logWriter)
      throws IOException, PrestaShopWebserviceException, AxelorException {
    currencyService.importCurrency(appConfig, endDate, logWriter);
    countryService.importCountry(appConfig, endDate, logWriter);
    customerService.importCustomer(appConfig, endDate, logWriter);
    addressService.importAddress(appConfig, endDate, logWriter);
    taxService.importTax(appConfig, endDate, logWriter);
    categoryService.importCategory(appConfig, endDate, logWriter);
    productService.importProduct(appConfig, endDate, logWriter);
  }

  /**
   * Import Axelor modules (Base, SaleOrder)
   *
   * @throws AxelorException
   */
  @Override
  public void importFromPrestaShop(AppPrestashop appConfig, ZonedDateTime endDate, Batch batch)
      throws IOException, AxelorException {
    StringBuilderWriter logWriter = new StringBuilderWriter(1024);
    try {
      importAxelorBase(appConfig, endDate, logWriter);
      orderService.importOrder(appConfig, endDate, logWriter);
      logWriter.write(String.format("%n==== END OF LOG ====%n"));
    } catch (PrestaShopWebserviceException e) {
      TraceBackService.trace(e);
    } finally {
      IOUtils.closeQuietly(logWriter);
      MetaFile importMetaFile =
          metaFiles.upload(
              new ByteArrayInputStream(logWriter.toString().getBytes()), "import-log.txt");
      batch.setPrestaShopBatchLog(importMetaFile);
    }
  }
}
