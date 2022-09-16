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
package com.axelor.apps.prestashop.service.imports.batch;

import com.axelor.apps.base.db.repo.AppPrestashopRepository;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.prestashop.batch.PrestaShopBatchService;
import com.axelor.apps.prestashop.db.PrestaShopBatch;
import com.axelor.apps.prestashop.exception.IExceptionMessage;
import com.axelor.apps.prestashop.imports.PrestaShopServiceImport;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportPrestaShop extends AbstractBatch {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private PrestaShopServiceImport prestaShopServiceImport;
  private AppPrestashopRepository appRepository;
  private PrestaShopBatchService batchService;

  @Inject
  public ImportPrestaShop(
      PrestaShopServiceImport prestaShopServiceImport,
      AppPrestashopRepository appRepository,
      PrestaShopBatchService batchService) {
    this.prestaShopServiceImport = prestaShopServiceImport;
    this.appRepository = appRepository;
    this.batchService = batchService;
  }

  @Override
  protected void process() {
    try {
      PrestaShopBatch prestaShopBatch = (PrestaShopBatch) model;

      ZonedDateTime referenceDate = batchService.getLastSuccessfullRunStartDate(prestaShopBatch);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Starting import from PrestaShop to ABS with reference date {}", referenceDate);
      }
      prestaShopServiceImport.importFromPrestaShop(
          appRepository.all().fetchOne(), referenceDate, batch);

      checkPoint(); // cannot call save directly as we've no transaction
      incrementDone();
    } catch (Exception e) {
      TraceBackService.trace(e, "Prestashop import batch", batch.getId());
      LOG.error(
          String.format(
              "An error occured while running prestashop export batch #%d", batch.getId()),
          e);
      incrementAnomaly();
    }
  }

  @Override
  protected void stop() {
    super.stop();
    addComment(I18n.get(IExceptionMessage.BATCH_IMPORT));
  }

  @Override
  protected void setBatchTypeSelect() {
    this.batch.setBatchTypeSelect(BatchRepository.BATCH_TYPE_PRESTASHOP_BATCH);
  }
}
