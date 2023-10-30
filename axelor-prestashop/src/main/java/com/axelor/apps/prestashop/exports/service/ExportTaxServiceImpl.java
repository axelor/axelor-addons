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

import com.axelor.apps.account.db.Tax;
import com.axelor.apps.account.db.repo.TaxRepository;
import com.axelor.apps.base.db.AppPrestashop;
import com.axelor.apps.base.db.Country;
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
    final Country defaultCountry = appConfig.getPrestaShopCountry();

    // First, fetch all remote taxes and put them into maps suitable for quick fetching
    // this will avoid round-trips with remote end and considerably speed up performances
    final List<PrestashopTax> remoteTaxes = ws.fetchAll(PrestashopResourceType.TAXES);
    final Map<Integer, PrestashopTax> taxesById = new HashMap<>();
    for (PrestashopTax c : remoteTaxes) {
      taxesById.put(c.getId(), c);
    }

    final List<PrestashopTaxRule> remoteTaxRules = ws.fetchAll(PrestashopResourceType.TAX_RULES);

    final List<PrestashopTaxRuleGroup> remoteTaxRuleGroups =
        ws.fetchAll(PrestashopResourceType.TAX_RULE_GROUPS);
    final Map<Integer, PrestashopTaxRuleGroup> taxRuleGroupById = new HashMap<>();
    final Map<String, PrestashopTaxRuleGroup> taxRuleGroupByName = new HashMap<>();
    for (PrestashopTaxRuleGroup taxRuleGroup : remoteTaxRuleGroups) {
      taxRuleGroupById.put(taxRuleGroup.getId(), taxRuleGroup);
      taxRuleGroupByName.put(taxRuleGroup.getName(), taxRuleGroup);
    }

    for (Tax localTax : taxes) {
      logBuffer.write("Exporting tax " + localTax.getName() + " – ");
      try {
        PrestashopTaxRuleGroup remoteTaxRuleGroup;
        Integer localTaxPrestashopId = localTax.getPrestaShopId();

        if (localTaxPrestashopId != null) {
          logBuffer.write("prestashop id=" + localTaxPrestashopId);

          remoteTaxRuleGroup = taxRuleGroupById.get(localTaxPrestashopId);
          if (remoteTaxRuleGroup == null) {
            logBuffer.write(String.format(" [ERROR] Not found remotely%n"));
            log.error(
                "Unable to fetch remote tax rule goup #{} ({}), something's probably very wrong, skipping",
                localTax.getPrestaShopId(),
                localTax.getName());
            ++errors;
            continue;
          } else {
            if (!localTax.getName().equals(remoteTaxRuleGroup.getName())) {
              log.error(
                  "Remote tax rule group #{} has not the same name as the local one ({} vs {}), skipping",
                  localTax.getPrestaShopId(),
                  remoteTaxRuleGroup.getName(),
                  localTax.getName());
              logBuffer.write(
                  String.format(
                      " [ERROR] Name mismatch: %s vs %s%n",
                      remoteTaxRuleGroup.getName(), localTax.getName()));
              ++errors;
              continue;
            }
          }
        } else {
          remoteTaxRuleGroup = taxRuleGroupByName.get(localTax.getName());
          if (remoteTaxRuleGroup == null) {
            logBuffer.write("no ID and name not found, creating");
            remoteTaxRuleGroup = new PrestashopTaxRuleGroup();
          } else {
            logBuffer.write(String.format("found remotely using its name %s", localTax.getName()));
          }
        }

        if (remoteTaxRuleGroup.getId() == null || !appConfig.getPrestaShopMasterForTaxes()) {
          remoteTaxRuleGroup.setName(localTax.getName());
          remoteTaxRuleGroup.setAddDate(localTax.getCreatedOn());
          if (localTax.getActiveTaxLine() != null) {
            remoteTaxRuleGroup.setActive(true);
          }
          remoteTaxRuleGroup = ws.save(PrestashopResourceType.TAX_RULE_GROUPS, remoteTaxRuleGroup);

          PrestashopTax tax;
          PrestashopTaxRule taxRule = null;

          Integer remoteTaxRuleGroupId = remoteTaxRuleGroup.getId();
          if (remoteTaxRuleGroupId != null) {
            if (defaultCountry.getPrestaShopId() != null) {
              taxRule =
                  remoteTaxRules.stream()
                      .filter(
                          remoteTaxRule ->
                              (remoteTaxRule.getCountryId().equals(defaultCountry.getPrestaShopId())
                                  && remoteTaxRule
                                      .getTaxRuleGroupId()
                                      .equals(remoteTaxRuleGroupId)))
                      .findFirst()
                      .orElse(null);
            }
            if (taxRule == null) {
              tax = new PrestashopTax();
              taxRule = new PrestashopTaxRule();
            } else {
              tax = taxesById.get(taxRule.getTaxId());
            }
          } else {
            log.error("Error while synchronizing tax {}, skipping", localTax.getName());
            logBuffer.write(
                String.format("Error while synchronizing tax %s, skipping", localTax.getName()));
            ++errors;
            continue;
          }

          tax.setName(defaultTax.getName().clone());
          for (PrestashopTranslationEntry e : tax.getName().getTranslations()) {
            e.setTranslation(localTax.getCode());
          }
          tax.setRate(
              localTax.getActiveTaxLine() != null
                  ? localTax.getActiveTaxLine().getValue()
                  : !CollectionUtils.isEmpty(localTax.getTaxLineList())
                      ? localTax.getTaxLineList().get(0).getValue()
                      : BigDecimal.ZERO);
          tax.setActive(remoteTaxRuleGroup.isActive());
          tax = ws.save(PrestashopResourceType.TAXES, tax);

          if (defaultCountry.getPrestaShopId() != null) {
            taxRule.setCountryId(defaultCountry.getPrestaShopId());
          }
          taxRule.setTaxId(tax.getId());
          taxRule.setTaxRuleGroupId(remoteTaxRuleGroupId);
          taxRule = ws.save(PrestashopResourceType.TAX_RULES, taxRule);

          localTax.setPrestaShopId(remoteTaxRuleGroupId);
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
