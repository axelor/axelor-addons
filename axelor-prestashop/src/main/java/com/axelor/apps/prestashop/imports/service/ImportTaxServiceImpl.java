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
package com.axelor.apps.prestashop.imports.service;

import com.axelor.apps.account.db.AccountManagement;
import com.axelor.apps.account.db.Tax;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.account.db.repo.TaxRepository;
import com.axelor.apps.base.db.AppPrestashop;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.prestashop.entities.PrestashopResourceType;
import com.axelor.apps.prestashop.entities.PrestashopTax;
import com.axelor.apps.prestashop.entities.PrestashopTaxRule;
import com.axelor.apps.prestashop.service.library.PSWebServiceClient;
import com.axelor.apps.prestashop.service.library.PrestaShopWebserviceException;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ImportTaxServiceImpl implements ImportTaxService {
  private Logger log = LoggerFactory.getLogger(getClass());

  private AppBaseService appBaseService;
  private TaxRepository taxRepo;

  @Inject
  public ImportTaxServiceImpl(AppBaseService appBaseService, TaxRepository taxRepo) {
    this.appBaseService = appBaseService;
    this.taxRepo = taxRepo;
  }

  @Override
  @Transactional
  public void importTax(AppPrestashop appConfig, ZonedDateTime endDate, Writer logWriter)
      throws IOException, PrestaShopWebserviceException {
    int done = 0;
    int errors = 0;

    log.debug("Starting PrestaShop taxes import");
    logWriter.write(String.format("%n====== TAXES ======%n"));

    final PSWebServiceClient ws =
        new PSWebServiceClient(appConfig.getPrestaShopUrl(), appConfig.getPrestaShopKey());

    final List<PrestashopTax> remoteTaxes = ws.fetchAll(PrestashopResourceType.TAXES);
    final List<PrestashopTaxRule> remoteTaxRules = ws.fetchAll(PrestashopResourceType.TAX_RULES);
    Map<Integer, PrestashopTaxRule> taxRulesByTaxId = new HashMap<>();
    for (PrestashopTaxRule taxRule : remoteTaxRules) {
      taxRulesByTaxId.put(taxRule.getTaxId(), taxRule);
    }

    final int language =
        (appConfig.getTextsLanguage().getPrestaShopId() == null
            ? 1
            : appConfig.getTextsLanguage().getPrestaShopId());

    for (PrestashopTax remoteTax : remoteTaxes) {
      logWriter.write(
          String.format(
              "Importing tax %s (%s) – ",
              remoteTax.getId(), remoteTax.getName().getTranslation(language)));

      try {
        Tax localTax =
            taxRepo.findByPrestaShopId(taxRulesByTaxId.get(remoteTax.getId()).getTaxRuleGroupId());
        if (localTax == null) {
          localTax = taxRepo.findByName(remoteTax.getName().getTranslation(language));
          if (localTax == null) {
            logWriter.write("no ID and name not found, creating");
            localTax = new Tax();
          } else {
            logWriter.write(String.format("found locally using its name %s", localTax.getName()));
          }
          // update PrestaShop ID of new and existing ABS tax
          localTax.setPrestaShopId(taxRulesByTaxId.get(remoteTax.getId()).getTaxRuleGroupId());

        } else {
          if (!localTax.getName().equals(remoteTax.getName().getTranslation(language))) {
            log.error(
                "Remote tax #{} has not the same name as the local one ({} vs {}), skipping",
                localTax.getPrestaShopId(),
                remoteTax.getName().getTranslation(language),
                localTax.getName());
            logWriter.write(
                String.format(
                    " [ERROR] Name mismatch: %s vs %s%n",
                    remoteTax.getName().getTranslation(language), localTax.getName()));
            ++errors;
            continue;
          }
        }

        if (localTax.getId() == null || appConfig.getPrestaShopMasterForTaxes()) {
          localTax.setCode(String.format("PRESTA-%04d", remoteTax.getId()));
          localTax.setName(remoteTax.getName().getTranslation(language));
          TaxLine localTaxLine =
              localTax.getId() == null
                  ? new TaxLine()
                  : localTax.getActiveTaxLine() != null
                      ? localTax.getActiveTaxLine()
                      : !CollectionUtils.isEmpty(localTax.getTaxLineList())
                          ? localTax.getTaxLineList().get(0)
                          : new TaxLine();
          localTaxLine.setStartDate(appBaseService.getTodayDate());
          localTaxLine.setValue(
              localTax.getId() == null
                  ? remoteTax.getRate().divide(new BigDecimal(100))
                  : remoteTax.getRate());
          localTaxLine.setTax(localTax);

          if (remoteTax.isActive()) {
            localTax.setActiveTaxLine(localTaxLine);
          }

          AccountManagement accountManagement;
          if (localTax.getId() != null
              && !CollectionUtils.isEmpty(localTax.getAccountManagementList())) {
            accountManagement = localTax.getAccountManagementList().get(0);
          } else {
            accountManagement = new AccountManagement();
          }
          accountManagement.setCompany(AuthUtils.getUser().getActiveCompany());
          accountManagement.setTypeSelect(2);
          accountManagement.setSaleAccount(appConfig.getDefaultSaleAccountForTax());
          localTax.addAccountManagementListItem(accountManagement);

          taxRepo.save(localTax);
        } else {
          logWriter.write(
              "local tax exists and PrestaShop is not master for taxes, leaving untouched");
        }
        logWriter.write(String.format(" [SUCCESS]%n"));
        ++done;

      } catch (Exception e) {
        TraceBackService.trace(
            e, I18n.get("Prestashop taxes import"), AbstractBatch.getCurrentBatchId());
        logWriter.write(
            String.format(
                " [ERROR] %s (full trace is in application logs)%n", e.getLocalizedMessage()));
        log.error(
            String.format(
                "Exception while synchronizing tax %s (%s)",
                remoteTax.getId(), remoteTax.getName().getTranslation(language)),
            e);
        ++errors;
      }
    }

    logWriter.write(
        String.format("%n=== END OF TAXES Import, done: %d, errors: %d ===%n", done, errors));
  }
}
