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

import com.axelor.apps.account.db.Tax;
import com.axelor.apps.account.db.repo.TaxRepository;
import com.axelor.apps.base.db.AppPrestashop;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.prestashop.entities.PrestashopResourceType;
import com.axelor.apps.prestashop.entities.PrestashopTax;
import com.axelor.apps.prestashop.entities.PrestashopTaxRule;
import com.axelor.apps.prestashop.entities.PrestashopTaxRuleGroup;
import com.axelor.apps.prestashop.entities.PrestashopTranslatableString.PrestashopTranslationEntry;
import com.axelor.apps.prestashop.service.library.PSWebServiceClient;
import com.axelor.apps.prestashop.service.library.PrestaShopWebserviceException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExportTaxServiceImpl implements ExportTaxService {
  private Logger log = LoggerFactory.getLogger(getClass());

  TaxRepository taxRepo;

  @Inject
  public ExportTaxServiceImpl(final TaxRepository taxRepo) {
    this.taxRepo = taxRepo;
  }

  @Override
  @Transactional
  public void exportTax(AppPrestashop appConfig, Writer logBuffer)
      throws IOException, PrestaShopWebserviceException {
    int done = 0;
    int errors = 0;

    logBuffer.write(String.format("%n====== TAXES ======%n"));

    final List<Tax> taxes =
        taxRepo
            .all()
            .filter("(self.prestaShopVersion is null OR self.prestaShopVersion < self.version)")
            .fetch();

    final PSWebServiceClient ws =
        new PSWebServiceClient(appConfig.getPrestaShopUrl(), appConfig.getPrestaShopKey());

    final PrestashopTax defaultTax = ws.fetchDefault(PrestashopResourceType.TAXES);
    final int language =
        (appConfig.getTextsLanguage().getPrestaShopId() == null
            ? 1
            : appConfig.getTextsLanguage().getPrestaShopId());

    // First, fetch all remote taxes and put them into maps suitable for quick fetching
    // this will avoid round-trips with remote end and considerably speed up performances
    final List<PrestashopTax> remoteTaxes = ws.fetchAll(PrestashopResourceType.TAXES);
    final Map<Integer, PrestashopTax> taxesById = new HashMap<>();
    final Map<String, PrestashopTax> taxesByName = new HashMap<>();
    for (PrestashopTax c : remoteTaxes) {
      taxesById.put(c.getId(), c);
      taxesByName.put(c.getName().getTranslation(language), c);
    }

    final List<PrestashopTaxRule> remoteTaxRules = ws.fetchAll(PrestashopResourceType.TAX_RULES);
    final Map<Integer, PrestashopTaxRule> taxRulesByTaxId = new HashMap<>();
    for (PrestashopTaxRule taxRule : remoteTaxRules) {
      taxRulesByTaxId.put(taxRule.getTaxId(), taxRule);
    }

    final List<PrestashopTaxRuleGroup> remoteTaxRuleGroups =
        ws.fetchAll(PrestashopResourceType.TAX_RULE_GROUPS);
    final Map<Integer, PrestashopTaxRuleGroup> taxRuleGroupById = new HashMap<>();
    for (PrestashopTaxRuleGroup taxRuleGroup : remoteTaxRuleGroups) {
      taxRuleGroupById.put(taxRuleGroup.getId(), taxRuleGroup);
    }

    for (Tax localTax : taxes) {
      logBuffer.write("Exporting tax " + localTax.getName() + " – ");
      try {
        PrestashopTax remoteTax;
        if (localTax.getPrestaShopId() != null) {
          logBuffer.write("prestashop id=" + localTax.getPrestaShopId());
          remoteTax = taxesById.get(localTax.getPrestaShopId());
          if (remoteTax == null) {
            logBuffer.write(String.format(" [ERROR] Not found remotely%n"));
            log.error(
                "Unable to fetch remote tax #{} ({}), something's probably very wrong, skipping",
                localTax.getPrestaShopId(),
                localTax.getName());
            ++errors;
            continue;
          } else {
            if (!localTax.getName().equals(remoteTax.getName().getTranslation(language))) {
              log.error(
                  "Remote tax #{} has not the same name as the local one ({} vs {}), skipping",
                  localTax.getPrestaShopId(),
                  remoteTax.getName().getTranslation(language),
                  localTax.getName());
              logBuffer.write(
                  String.format(
                      " [ERROR] Name mismatch: %s vs %s%n",
                      remoteTax.getName().getTranslation(language), localTax.getName()));
              ++errors;
              continue;
            }
          }
        } else {
          remoteTax = taxesByName.get(localTax.getCode());
          if (remoteTax == null) {
            logBuffer.write("no ID and code not found, creating");
            remoteTax = new PrestashopTax();
          } else {
            logBuffer.write(String.format("found remotely using its name %s", localTax.getName()));
          }
        }

        if (remoteTax.getId() == null || !appConfig.getPrestaShopMasterForTaxes()) {
          remoteTax.setName(defaultTax.getName().clone());
          for (PrestashopTranslationEntry e : remoteTax.getName().getTranslations()) {
            e.setTranslation(localTax.getCode());
          }
          remoteTax.setRate(
              localTax.getActiveTaxLine() != null
                  ? localTax.getActiveTaxLine().getValue().multiply(new BigDecimal(100))
                  : !CollectionUtils.isEmpty(localTax.getTaxLineList())
                      ? localTax.getTaxLineList().get(0).getValue().multiply(new BigDecimal(100))
                      : BigDecimal.ZERO);
          if (localTax.getActiveTaxLine() != null) {
            remoteTax.setActive(true);
          }

          remoteTax = ws.save(PrestashopResourceType.TAXES, remoteTax);

          PrestashopTaxRule taxRule;
          PrestashopTaxRuleGroup taxRuleGroup;
          if (remoteTax.getId() != null) {
            taxRule = taxRulesByTaxId.get(remoteTax.getId());
            if (taxRule == null) {
              taxRule = new PrestashopTaxRule();
              taxRuleGroup = new PrestashopTaxRuleGroup();
            } else {
              taxRuleGroup = taxRuleGroupById.get(taxRule.getTaxRuleGroupId());
            }
          } else {
            taxRule = new PrestashopTaxRule();
            taxRuleGroup = new PrestashopTaxRuleGroup();
          }
          if (appConfig.getPrestaShopCountry().getPrestaShopId() != null) {
            taxRule.setCountryId(appConfig.getPrestaShopCountry().getPrestaShopId());
          }
          taxRule.setTaxId(remoteTax.getId());

          taxRuleGroup.setName(localTax.getName());
          taxRuleGroup.setAddDate(localTax.getCreatedOn());
          taxRuleGroup.setActive(remoteTax.isActive());

          taxRuleGroup = ws.save(PrestashopResourceType.TAX_RULE_GROUPS, taxRuleGroup);

          taxRule.setTaxRuleGroupId(taxRuleGroup.getId());
          taxRule = ws.save(PrestashopResourceType.TAX_RULES, taxRule);

          localTax.setPrestaShopId(taxRuleGroup.getId());
          localTax.setPrestaShopVersion(localTax.getVersion() + 1);
        } else {
          logBuffer.write(" — remote tax exists and taxes are managed on prestashop, skipping");
        }
        logBuffer.write(String.format(" [SUCCESS]%n"));
        ++done;

      } catch (PrestaShopWebserviceException e) {
        TraceBackService.trace(
            e, I18n.get("Prestashop taxes export"), AbstractBatch.getCurrentBatchId());
        logBuffer.write(
            String.format(
                " [ERROR] %s (full trace is in application logs)%n", e.getLocalizedMessage()));
        log.error(String.format("Exception while synchronizing tax #%d", localTax.getId()), e);
        ++errors;
      }
    }

    logBuffer.write(
        String.format("%n=== END OF TAXES EXPORT, done: %d, errors: %d ===%n", done, errors));
  }
}
