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
package com.axelor.apps.prestashop.exports.service;

import com.axelor.apps.base.db.AppPrestashop;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.repo.CurrencyRepository;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.prestashop.entities.PrestashopCurrency;
import com.axelor.apps.prestashop.entities.PrestashopResourceType;
import com.axelor.apps.prestashop.service.library.PSWebServiceClient;
import com.axelor.apps.prestashop.service.library.PrestaShopWebserviceException;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExportCurrencyServiceImpl implements ExportCurrencyService {
  private Logger log = LoggerFactory.getLogger(getClass());

  CurrencyRepository currencyRepo;
  CurrencyService currencyService;

  @Inject
  public ExportCurrencyServiceImpl(
      final CurrencyRepository currencyRepo, final CurrencyService currencyService) {
    this.currencyRepo = currencyRepo;
    this.currencyService = currencyService;
  }

  @Override
  @Transactional
  public void exportCurrency(AppPrestashop appConfig, Writer logBuffer)
      throws IOException, PrestaShopWebserviceException {
    int done = 0;
    int errors = 0;

    logBuffer.write(String.format("%n====== CURRENCIES ======%n"));

    final List<Currency> currencies =
        currencyRepo
            .all()
            .filter("(self.prestaShopVersion is null OR self.prestaShopVersion < self.version)")
            .fetch();

    final PSWebServiceClient ws =
        new PSWebServiceClient(appConfig.getPrestaShopUrl(), appConfig.getPrestaShopKey());

    // First, fetch all remote currencies and put them into maps suitable for quick fetching
    // this will avoid round-trips with remote end and considerably speed up performances
    final List<PrestashopCurrency> remoteCurrencies =
        ws.fetchAll(PrestashopResourceType.CURRENCIES);
    final Map<Integer, PrestashopCurrency> currenciesById = new HashMap<>();
    final Map<String, PrestashopCurrency> currenciesByCode = new HashMap<>();
    for (PrestashopCurrency c : remoteCurrencies) {
      currenciesById.put(c.getId(), c);
      currenciesByCode.put(c.getCode(), c);
    }
    final LocalDate today = LocalDate.now();

    for (Currency localCurrency : currencies) {
      logBuffer.write("Exporting currency " + localCurrency.getCode() + " – ");
      try {
        PrestashopCurrency remoteCurrency;
        if (localCurrency.getPrestaShopId() != null) {
          logBuffer.write("prestashop id=" + localCurrency.getPrestaShopId());
          remoteCurrency = currenciesById.get(localCurrency.getPrestaShopId());
          if (remoteCurrency == null) {
            logBuffer.write(String.format(" [ERROR] Not found remotely%n"));
            log.error(
                "Unable to fetch remote currency #{} ({}), something's probably very wrong, skipping",
                localCurrency.getPrestaShopId(),
                localCurrency.getCode());
            ++errors;
            continue;
          } else if (localCurrency.getCode().equals(remoteCurrency.getCode()) == false) {
            log.error(
                "Remote currency #{} has not the same ISO code as the local one ({} vs {}), skipping",
                localCurrency.getPrestaShopId(),
                remoteCurrency.getCode(),
                localCurrency.getCode());
            logBuffer.write(
                String.format(
                    " [ERROR] ISO code mismatch: %s vs %s%n",
                    remoteCurrency.getCode(), localCurrency.getCode()));
            ++errors;
            continue;
          }
        } else {
          remoteCurrency = currenciesByCode.get(localCurrency.getCode());
          if (remoteCurrency == null) {
            logBuffer.write("no ID and code not found, creating");
            remoteCurrency = new PrestashopCurrency();
            remoteCurrency.setCode(localCurrency.getCode());
          } else {
            logBuffer.write(
                String.format("found remotely using its code %s", localCurrency.getCode()));
          }
        }

        if (remoteCurrency.getId() == null
            || appConfig.getPrestaShopMasterForCurrencies() == Boolean.FALSE) {
          remoteCurrency.setName(localCurrency.getName());
          // TODO Add an option
          try {
            remoteCurrency.setConversionRate(
                currencyService.getCurrencyConversionRate(
                    localCurrency, appConfig.getPrestaShopCurrency(), today));
          } catch (AxelorException e) {
            log.debug(
                "Unable to fetch conversion rate for currency {}, leave it unchanged",
                localCurrency.getCode());
          }
          logBuffer.write(" – setting conversion rate to " + remoteCurrency.getConversionRate());
          // Do not change any of the other attributes, defaults are suitable for
          // newly created one, active & deleted should be left untouched.
          remoteCurrency = ws.save(PrestashopResourceType.CURRENCIES, remoteCurrency);
          localCurrency.setPrestaShopId(remoteCurrency.getId());
          localCurrency.setPrestaShopVersion(localCurrency.getVersion() + 1);
        } else {
          logBuffer.write(
              " — remote currency exists and currencies are managed on prestashop, skipping");
        }
        logBuffer.write(String.format(" [SUCCESS]%n"));
        ++done;
      } catch (PrestaShopWebserviceException e) {
        TraceBackService.trace(
            e, I18n.get("Prestashop currencies export"), AbstractBatch.getCurrentBatchId());
        logBuffer.write(
            String.format(
                " [ERROR] %s (full trace is in application logs)%n", e.getLocalizedMessage()));
        log.error(
            String.format("Exception while synchronizing currency #%d", localCurrency.getId()), e);
        ++errors;
      }
    }

    logBuffer.write(
        String.format("%n=== END OF CURRENCIES EXPORT, done: %d, errors: %d ===%n", done, errors));
  }
}
