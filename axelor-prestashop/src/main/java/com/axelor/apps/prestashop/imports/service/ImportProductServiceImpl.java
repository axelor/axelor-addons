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
import com.axelor.apps.account.db.repo.TaxRepository;
import com.axelor.apps.base.db.AppPrestashop;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.ProductCategory;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.ProductCategoryRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.prestashop.entities.Associations.AvailableStocksAssociationsEntry;
import com.axelor.apps.prestashop.entities.PrestashopProduct;
import com.axelor.apps.prestashop.entities.PrestashopProductCategory;
import com.axelor.apps.prestashop.entities.PrestashopResourceType;
import com.axelor.apps.prestashop.exports.service.ExportProductServiceImpl;
import com.axelor.apps.prestashop.service.library.PSWebServiceClient;
import com.axelor.apps.prestashop.service.library.PrestaShopWebserviceException;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.google.common.base.Objects;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ImportProductServiceImpl implements ImportProductService {
  private Logger log = LoggerFactory.getLogger(getClass());

  private MetaFiles metaFiles;
  private TaxRepository taxRepo;
  private ProductCategoryRepository productCategoryRepo;
  private ProductRepository productRepo;
  private CurrencyService currencyService;
  private UnitConversionService unitConversionService;

  @Inject
  public ImportProductServiceImpl(
      MetaFiles metaFiles,
      TaxRepository taxRepo,
      ProductCategoryRepository productCategoryRepo,
      ProductRepository productRepo,
      CurrencyService currencyService,
      UnitConversionService unitConversionService) {
    this.metaFiles = metaFiles;
    this.taxRepo = taxRepo;
    this.productCategoryRepo = productCategoryRepo;
    this.productRepo = productRepo;
    this.currencyService = currencyService;
    this.unitConversionService = unitConversionService;
  }

  @Override
  @Transactional
  public void importProduct(AppPrestashop appConfig, ZonedDateTime endDate, Writer logWriter)
      throws IOException, PrestaShopWebserviceException {
    int done = 0;
    int errors = 0;

    log.debug("Starting PrestaShop products import");
    logWriter.write(String.format("%n====== PRODUCTS ======%n"));

    final PSWebServiceClient ws =
        new PSWebServiceClient(appConfig.getPrestaShopUrl(), appConfig.getPrestaShopKey());

    final PrestashopProductCategory remoteRootCategory =
        ws.fetchOne(
            PrestashopResourceType.PRODUCT_CATEGORIES,
            Collections.singletonMap("is_root_category", "1"));
    final List<PrestashopProduct> remoteProducts = ws.fetchAll(PrestashopResourceType.PRODUCTS);

    final Currency defaultCurrency =
        AbstractBatch.getCurrentBatch().getPrestaShopBatch().getCompany().getCurrency();
    final int language =
        (appConfig.getTextsLanguage().getPrestaShopId() == null
            ? 1
            : appConfig.getTextsLanguage().getPrestaShopId());

    for (PrestashopProduct remoteProduct : remoteProducts) {
      logWriter.write(
          String.format(
              "Importing product %s (%s) – ",
              remoteProduct.getReference(), remoteProduct.getName().getTranslation(language)));

      try {
        if (PrestashopProduct.PRODUCT_TYPE_PACK.equals(remoteProduct.getType())) {
          // Warning, if ever handled, you'll have to handle them in order management too
          logWriter.write(
              String.format(
                  "[ERROR] Product is a pack, this is not handled right now, skipping%n"));
          continue;
        }

        final AvailableStocksAssociationsEntry availableStocks =
            remoteProduct.getAssociations().getAvailableStocks();
        if (availableStocks != null
            && (availableStocks.getStock().size() > 1
                || Objects.equal(availableStocks.getStock().get(0).getProductAttributeId(), 0)
                    == false)) {
          logWriter.write(
              String.format(
                  "[ERROR] Product seems to have variants, these are not handled right now, skipping%n"));
          ++errors;
          continue;
        }

        ProductCategory category = null;

        if (remoteProduct.getDefaultCategoryId() != null
            && remoteProduct.getDefaultCategoryId() != remoteRootCategory.getId()) {
          category = productCategoryRepo.findByPrestaShopId(remoteProduct.getDefaultCategoryId());
          if (category == null) {
            logWriter.write(
                String.format(
                    "[WARNING] Product belongs to a not yet synced category, skipping%n"));
            ++errors;
            continue;
          }
        }

        Product localProduct = productRepo.findByPrestaShopId(remoteProduct.getId());
        if (localProduct == null) {
          localProduct = productRepo.findByCode(remoteProduct.getReference());

          if (localProduct != null && localProduct.getPrestaShopId() != null) {
            logWriter.write(
                String.format(
                    "[ERROR] Found product by code (%s) but it already has a PrestaShop ID, skipping%n",
                    remoteProduct.getReference()));
            ++errors;
            continue;
          }

          if (localProduct == null) {
            localProduct = new Product();
            localProduct.setPrestaShopId(remoteProduct.getId());
            localProduct.setSellable(Boolean.TRUE);
            localProduct.setProductSynchronizedInPrestashop(Boolean.TRUE);
          }
        }

        if (localProduct.getPrestaShopUpdateDateTime() != null
            && remoteProduct.getUpdateDate() != null
            && localProduct.getPrestaShopUpdateDateTime().compareTo(remoteProduct.getUpdateDate())
                >= 0) {
          logWriter.write(String.format("already up-to-date, skipping [WARNING]%n"));
          continue;
        }

        if (localProduct.getId() == null
            || appConfig.getPrestaShopMasterForProducts() == Boolean.TRUE) {
          localProduct.setProductTypeSelect(
              PrestashopProduct.PRODUCT_TYPE_VIRTUAL.equals(remoteProduct.getType())
                  ? ProductRepository.PRODUCT_TYPE_SERVICE
                  : ProductRepository.PRODUCT_TYPE_STORABLE);
          localProduct.setProductCategory(category);
          localProduct.setName(remoteProduct.getName().getTranslation(language));
          localProduct.setDescription(remoteProduct.getDescription().getTranslation(language));
          localProduct.setCode(
              StringUtils.defaultIfBlank(
                  remoteProduct.getReference(),
                  String.format("PRESTA-%04d", remoteProduct.getId())));
          if (remoteProduct.getPrice() != null
              && BigDecimal.ZERO.compareTo(remoteProduct.getPrice()) != 0) {
            final Currency targetCurrency =
                localProduct.getSaleCurrency() == null
                    ? defaultCurrency
                    : localProduct.getSaleCurrency();
            try {
              localProduct.setSalePrice(
                  currencyService.getAmountCurrencyConvertedAtDate(
                      appConfig.getPrestaShopCurrency(),
                      targetCurrency,
                      remoteProduct.getPrice(),
                      LocalDate.now()));
            } catch (AxelorException e) {
              logWriter.write(
                  " [WARNING] Unable to convert sale price, check your currency conversion rates");
            }
          }
          if (remoteProduct.getWholesalePrice() != null
              && BigDecimal.ZERO.compareTo(remoteProduct.getWholesalePrice()) != 0) {
            final Currency targetCurrency =
                localProduct.getPurchaseCurrency() == null
                    ? defaultCurrency
                    : localProduct.getPurchaseCurrency();
            try {
              localProduct.setPurchasePrice(
                  currencyService.getAmountCurrencyConvertedAtDate(
                      appConfig.getPrestaShopCurrency(),
                      targetCurrency,
                      remoteProduct.getWholesalePrice(),
                      LocalDate.now()));
            } catch (AxelorException e) {
              logWriter.write(
                  " [WARNING] Unable to convert sale price, check your currency conversion rates");
            }
          }
          if (localProduct.getMassUnit() == null)
            localProduct.setMassUnit(appConfig.getPrestaShopWeightUnit());
          localProduct.setGrossMass(
              convert(
                  appConfig.getPrestaShopWeightUnit(),
                  localProduct.getMassUnit(),
                  remoteProduct.getWeight(),
                  localProduct));

          if (localProduct.getLengthUnit() == null)
            localProduct.setLengthUnit(appConfig.getPrestaShopLengthUnit());
          localProduct.setWidth(
              convert(
                  appConfig.getPrestaShopLengthUnit(),
                  localProduct.getLengthUnit(),
                  remoteProduct.getWidth(),
                  localProduct));
          localProduct.setHeight(
              convert(
                  appConfig.getPrestaShopLengthUnit(),
                  localProduct.getLengthUnit(),
                  remoteProduct.getHeight(),
                  localProduct));
          localProduct.setLength(
              convert(
                  appConfig.getPrestaShopLengthUnit(),
                  localProduct.getLengthUnit(),
                  remoteProduct.getDepth(),
                  localProduct));

          if (remoteProduct.getDefaultImageId() != null && remoteProduct.getDefaultImageId() != 0) {
            try {
              byte[] remoteImage =
                  ws.fetchImage(
                      PrestashopResourceType.PRODUCTS,
                      remoteProduct,
                      remoteProduct.getDefaultImageId());
              if (localProduct.getPicture() == null) {
                localProduct.setPicture(
                    metaFiles.upload(
                        new ByteArrayInputStream(remoteImage),
                        "prestashop-product-" + remoteProduct.getId() + ".png"));
              } else {
                // OK so now we've two choices: reading, comparing, writing if different… or just
                // write
                File target = MetaFiles.getPath(localProduct.getPicture()).toFile();
                try (FileOutputStream fos = new FileOutputStream(target)) {
                  IOUtils.write(remoteImage, fos);
                }
              }
            } catch (IOException ioe) {
              logWriter.write(
                  String.format(
                      " [WARNING] Error while processing picture: %s", ioe.getLocalizedMessage()));
              log.error("IOException while processing product picture", ioe);
            }
          } else {
            if (localProduct.getPicture() != null) {
              metaFiles.delete(localProduct.getPicture());
              localProduct.setPicture(null);
            }
          }

          AccountManagement accountManagement;
          if (localProduct.getId() != null
              && !CollectionUtils.isEmpty(localProduct.getAccountManagementList())) {
            accountManagement = localProduct.getAccountManagementList().get(0);
          } else {
            accountManagement = new AccountManagement();
          }
          accountManagement.setCompany(AuthUtils.getUser().getActiveCompany());
          accountManagement.setTypeSelect(1);
          accountManagement.setSaleAccount(appConfig.getDefaultSaleAccountForProduct());
          accountManagement.setSaleTax(
              taxRepo.findByPrestaShopId(remoteProduct.getTaxRulesGroupId()));
          localProduct.addAccountManagementListItem(accountManagement);

          localProduct.setPrestaShopUpdateDateTime(remoteProduct.getUpdateDate());
          productRepo.save(localProduct);
        } else {
          logWriter.write(
              "local product exists and PrestaShop is not master for products, leaving untouched");
        }
        logWriter.write(String.format(" [SUCCESS]%n"));
        ++done;
      } catch (PrestaShopWebserviceException | AxelorException e) {
        TraceBackService.trace(
            e, I18n.get("Prestashop products import"), AbstractBatch.getCurrentBatchId());
        logWriter.write(
            String.format(
                " [ERROR] %s (full trace is in application logs)%n", e.getLocalizedMessage()));
        log.error(
            String.format(
                "Exception while synchronizing product %s (%s)",
                remoteProduct.getReference(), remoteProduct.getName().getTranslation(language)),
            e);
        ++errors;
      }
    }

    logWriter.write(
        String.format("%n=== END OF PRODUCTS Import, done: %d, errors: %d ===%n", done, errors));
  }

  /** @see ExportProductServiceImpl#convert */
  private BigDecimal convert(Unit from, Unit to, BigDecimal value, Product product)
      throws AxelorException {
    if (value == null) return null;
    return unitConversionService
        .convert(from, to, value, AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, product)
        .setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_EVEN);
  }
}
