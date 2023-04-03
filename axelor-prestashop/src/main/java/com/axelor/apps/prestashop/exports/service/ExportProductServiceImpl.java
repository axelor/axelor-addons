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

import com.axelor.apps.account.db.AccountManagement;
import com.axelor.apps.account.db.Tax;
import com.axelor.apps.base.db.AppPrestashop;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.prestashop.entities.Associations;
import com.axelor.apps.prestashop.entities.Associations.AvailableStocksAssociationElement;
import com.axelor.apps.prestashop.entities.Associations.AvailableStocksAssociationsEntry;
import com.axelor.apps.prestashop.entities.PrestashopAvailableStock;
import com.axelor.apps.prestashop.entities.PrestashopImage;
import com.axelor.apps.prestashop.entities.PrestashopProduct;
import com.axelor.apps.prestashop.entities.PrestashopProductCategory;
import com.axelor.apps.prestashop.entities.PrestashopResourceType;
import com.axelor.apps.prestashop.entities.PrestashopTranslatableString;
import com.axelor.apps.prestashop.service.library.PSWebServiceClient;
import com.axelor.apps.prestashop.service.library.PrestaShopWebserviceException;
import com.axelor.apps.stock.service.StockLocationService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.google.common.base.Objects;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExportProductServiceImpl implements ExportProductService {
  private Logger log = LoggerFactory.getLogger(getClass());

  /**
   * Version from which BOOM-5826 is fixed. FIXME Not accurate right now as bug is not fixed in last
   * version so we can just put lastVersion+1 until a fix is released
   */
  private static final String FIX_POSITION_IN_CATEGORY_VERSION = "1.7.3.5";

  private ProductRepository productRepo;
  private UnitConversionService unitConversionService;
  private CurrencyService currencyService;

  @Inject
  public ExportProductServiceImpl(
      ProductRepository productRepo,
      UnitConversionService unitConversionService,
      CurrencyService currencyService) {
    this.productRepo = productRepo;
    this.unitConversionService = unitConversionService;
    this.currencyService = currencyService;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void exportProduct(AppPrestashop appConfig, Writer logBuffer)
      throws IOException, PrestaShopWebserviceException {

    if (appConfig.getPrestaShopLengthUnit() == null
        || appConfig.getPrestaShopWeightUnit() == null) {
      logBuffer.write(String.format("[ERROR] Prestashop module isn't fully configured%n"));
      return;
    }

    final PSWebServiceClient ws =
        new PSWebServiceClient(appConfig.getPrestaShopUrl(), appConfig.getPrestaShopKey());

    final List<PrestashopProduct> remoteProducts = ws.fetchAll(PrestashopResourceType.PRODUCTS);
    final Map<Integer, PrestashopProduct> productsById = new HashMap<>();
    for (PrestashopProduct p : remoteProducts) {
      p.setPositionInCategory(null); // Workaround for position in category issue in PS-8.1-beta
      productsById.put(p.getId(), p);
    }

    exportProducts(appConfig, ws, productsById, logBuffer);
    exportStocks(ws, productsById, logBuffer);
    exportPictures(ws, productsById, logBuffer);
  }

  @Transactional(rollbackOn = {Exception.class})
  private void exportProducts(
      final AppPrestashop appConfig,
      final PSWebServiceClient ws,
      final Map<Integer, PrestashopProduct> productsById,
      final Writer logBuffer)
      throws IOException, PrestaShopWebserviceException {
    logBuffer.write(String.format("%n====== PRODUCTS ======%n"));

    int done = 0;
    int errors = 0;
    final StringBuilder filter =
        new StringBuilder(
            "(self.prestaShopVersion is null OR self.prestaShopVersion < self.version) AND self.dtype = 'Product'");
    if (appConfig.getExportNonSoldProducts() == Boolean.FALSE) {
      filter.append(" AND (self.sellable = true and self.productSynchronizedInPrestashop = true)");
    }

    final PrestashopProduct defaultProduct = ws.fetchDefault(PrestashopResourceType.PRODUCTS);
    final PrestashopProductCategory remoteRootCategory =
        ws.fetchOne(
            PrestashopResourceType.PRODUCT_CATEGORIES,
            Collections.singletonMap("is_root_category", "1"));
    final int language =
        (appConfig.getTextsLanguage().getPrestaShopId() == null
            ? 1
            : appConfig.getTextsLanguage().getPrestaShopId());

    final LocalDate today = LocalDate.now();

    final Map<String, PrestashopProduct> productsByReference = new HashMap<>();
    for (PrestashopProduct p : productsById.values()) {
      productsByReference.put(p.getReference(), p);
    }

    for (Product localProduct : productRepo.all().filter(filter.toString()).fetch()) {
      try {
        final String cleanedReference =
            localProduct.getCode() == null
                ? ""
                : localProduct
                    .getCode()
                    .replaceAll("[<>;={}]", ""); // took from Prestashop's ValidateCore::isReference
        logBuffer.write(
            String.format(
                "Exporting product %s (%s/%s) – ",
                localProduct.getName(), localProduct.getCode(), cleanedReference));

        if (localProduct.getParentProduct() != null) {
          logBuffer.write(
              String.format(
                  "[ERROR] Product is a variant, these are not handled right now, skipping%n"));
          continue;
        } else if (localProduct.getProductVariantConfig() != null) {
          logBuffer.write(
              String.format(
                  "[ERROR] Product has variants, which are not handled right now, skipping%n"));
          continue;
        }

        PrestashopProduct remoteProduct;
        if (localProduct.getPrestaShopId() != null) {
          logBuffer.write("prestashop id=" + localProduct.getPrestaShopId());
          remoteProduct = productsById.get(localProduct.getPrestaShopId());
          if (remoteProduct == null) {
            logBuffer.write(String.format(" [ERROR] Not found remotely%n"));
            log.error(
                "Unable to fetch remote product #{} ({}), something's probably very wrong, skipping",
                localProduct.getPrestaShopId(),
                localProduct.getCode());
            ++errors;
            continue;
          } else if (cleanedReference.equals(remoteProduct.getReference()) == false) {
            log.error(
                "Remote product #{} has not the same reference as the local one ({} vs {}), skipping",
                localProduct.getPrestaShopId(),
                remoteProduct.getReference(),
                cleanedReference);
            logBuffer.write(
                String.format(
                    " [ERROR] reference mismatch: %s vs %s%n",
                    remoteProduct.getReference(), cleanedReference));
            ++errors;
            continue;
          }
        } else {
          remoteProduct = productsByReference.get(cleanedReference);
          if (remoteProduct == null) {
            logBuffer.write("no ID and reference not found, creating");
            remoteProduct = new PrestashopProduct();
            remoteProduct.setReference(cleanedReference);
            PrestashopTranslatableString str = defaultProduct.getName().clone();
            str.clearTranslations(localProduct.getName());
            remoteProduct.setName(str);

            str = defaultProduct.getDescription().clone();
            str.clearTranslations(localProduct.getDescription());
            remoteProduct.setDescription(str);

            str = defaultProduct.getLinkRewrite().clone();
            // Normalization taken from PrestaShop's JavaScript str2url function
            str.clearTranslations(
                Normalizer.normalize(
                        String.format("%s-%s", localProduct.getCode(), localProduct.getName()),
                        Normalizer.Form.NFKD)
                    .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                    .toLowerCase()
                    .replaceAll("[^a-z0-9\\s\\'\\:/\\[\\]\\-]", "")
                    .replaceAll("[\\s\\'\\:/\\[\\]-]+", " ")
                    .replaceAll(" ", "-"));
            // TODO Should we update when product name changes?
            remoteProduct.setLinkRewrite(str);
            //  remoteProduct.setPositionInCategory(0); //Causing issue in PS-8.1-beta
          } else {
            logBuffer.write(
                String.format("found remotely using its reference %s", cleanedReference));
          }
        }

        if (remoteProduct.getId() == null
            || appConfig.getPrestaShopMasterForProducts() == Boolean.FALSE) {
          // Here comes the real fun…
          if (localProduct.getProductCategory() != null
              && localProduct.getProductCategory().getPrestaShopId() != null) {
            remoteProduct.setDefaultCategoryId(localProduct.getProductCategory().getPrestaShopId());
          } else {
            remoteProduct.setDefaultCategoryId(remoteRootCategory.getId());
          }

          final int defaultCategoryId = remoteProduct.getDefaultCategoryId();
          if (remoteProduct.getAssociations().getCategories().getAssociations().stream()
                  .anyMatch(c -> c.getId() == defaultCategoryId)
              == false) {
            Associations.CategoriesAssociationElement e =
                new Associations.CategoriesAssociationElement();
            e.setId(defaultCategoryId);
            remoteProduct.getAssociations().getCategories().getAssociations().add(e);
          }

          if (localProduct.getSalePrice() != null) {
            if (localProduct.getSaleCurrency() != null) {
              try {
                remoteProduct.setPrice(
                    currencyService
                        .getAmountCurrencyConvertedAtDate(
                            localProduct.getSaleCurrency(),
                            appConfig.getPrestaShopCurrency(),
                            localProduct.getSalePrice(),
                            today)
                        .setScale(appConfig.getExportPriceScale(), BigDecimal.ROUND_HALF_UP));
              } catch (AxelorException e) {
                logBuffer.write(
                    " [WARNING] Unable to convert sale price, check your currency convsersion rates");
              }
            } else {
              remoteProduct.setPrice(
                  localProduct
                      .getSalePrice()
                      .setScale(appConfig.getExportPriceScale(), BigDecimal.ROUND_HALF_UP));
            }
          }
          if (localProduct.getPurchasePrice() != null) {
            if (localProduct.getPurchaseCurrency() != null) {
              try {
                remoteProduct.setWholesalePrice(
                    currencyService
                        .getAmountCurrencyConvertedAtDate(
                            localProduct.getPurchaseCurrency(),
                            appConfig.getPrestaShopCurrency(),
                            localProduct.getPurchasePrice(),
                            today)
                        .setScale(appConfig.getExportPriceScale(), BigDecimal.ROUND_HALF_UP));
              } catch (AxelorException e) {
                logBuffer.write(
                    " [WARNING] Unable to convert purchase price, check your currency conversion rates");
              }
            } else {
              remoteProduct.setWholesalePrice(
                  localProduct
                      .getPurchasePrice()
                      .setScale(appConfig.getExportPriceScale(), BigDecimal.ROUND_HALF_UP));
            }
          }
          if (localProduct.getLengthUnit() != null) {
            remoteProduct.setWidth(
                convert(
                    appConfig.getPrestaShopLengthUnit(),
                    localProduct.getLengthUnit(),
                    localProduct.getWidth(),
                    localProduct));
            remoteProduct.setHeight(
                convert(
                    appConfig.getPrestaShopLengthUnit(),
                    localProduct.getLengthUnit(),
                    localProduct.getHeight(),
                    localProduct));
            remoteProduct.setDepth(
                convert(
                    appConfig.getPrestaShopLengthUnit(),
                    localProduct.getLengthUnit(),
                    localProduct.getLength(),
                    localProduct));
          } else {
            // assume homogeneous units
            remoteProduct.setWidth(localProduct.getWidth());
            remoteProduct.setHeight(localProduct.getHeight());
            remoteProduct.setDepth(localProduct.getLength());
          }
          BigDecimal weight =
              localProduct.getGrossMass() == null
                  ? localProduct.getNetMass()
                  : localProduct.getGrossMass();
          if (localProduct.getMassUnit() != null && weight != null) {
            remoteProduct.setWeight(
                unitConversionService.convert(
                    appConfig.getPrestaShopWeightUnit(),
                    localProduct.getMassUnit(),
                    weight,
                    weight.scale(),
                    localProduct));
          } else {
            remoteProduct.setWeight(weight);
          }

          remoteProduct.getName().setTranslation(language, localProduct.getName());
          remoteProduct.getDescription().setTranslation(language, localProduct.getDescription());

          Tax tax = null;
          if (CollectionUtils.isEmpty(localProduct.getAccountManagementList())) {
            if (localProduct.getProductFamily() != null
                && !CollectionUtils.isEmpty(
                    localProduct.getProductFamily().getAccountManagementList())) {
              AccountManagement accountManagement =
                  localProduct.getProductFamily().getAccountManagementList().get(0);
              tax = accountManagement.getSaleTax();
            }
          } else {
            AccountManagement accountManagement = localProduct.getAccountManagementList().get(0);
            tax = accountManagement.getSaleTax();
          }
          remoteProduct.setTaxRulesGroupId(
              tax != null ? tax.getPrestaShopId() : appConfig.getDefaultTax().getPrestaShopId());

          if (localProduct.getSalesUnit() != null) {
            remoteProduct.setUnity(localProduct.getSalesUnit().getLabelToPrinting());
          } else if (localProduct.getUnit() != null) {
            remoteProduct.setUnity(localProduct.getUnit().getLabelToPrinting());
          }
          remoteProduct.setVirtual(
              ProductRepository.PRODUCT_TYPE_SERVICE.equals(localProduct.getProductTypeSelect()));
          // TODO Should we handle supplier?

          remoteProduct.setUpdateDate(LocalDateTime.now());
          // Commenting below code to avoid issue in PS-8.1-beta
          //          if (ws.compareVersion(FIX_POSITION_IN_CATEGORY_VERSION) < 0) {
          //            // Workaround Prestashop bug BOOM-5826 (position in category handling in
          // prestashop's
          //            // webservices is a joke). Trade-off is that we shuffle categories on each
          // update…
          //            remoteProduct.setPositionInCategory(0);
          //          }
          remoteProduct.setLowStockAlert(true);
          remoteProduct = ws.save(PrestashopResourceType.PRODUCTS, remoteProduct);
          productsById.put(remoteProduct.getId(), remoteProduct);

          localProduct.setPrestaShopId(remoteProduct.getId());
          localProduct.setPrestaShopVersion(localProduct.getVersion() + 1);
        } else {
          logBuffer.write(
              "remote product exists and PrestaShop is master for products, leaving untouched");
        }
        logBuffer.write(String.format(" [SUCCESS]%n"));
        ++done;
      } catch (AxelorException | PrestaShopWebserviceException e) {
        TraceBackService.trace(
            e, I18n.get("Prestashop products export"), AbstractBatch.getCurrentBatchId());
        logBuffer.write(
            String.format(
                " [ERROR] %s (full trace is in application logs)%n", e.getLocalizedMessage()));
        log.error(
            String.format(
                "Exception while synchronizing product #%d (%s)",
                localProduct.getId(), localProduct.getName()),
            e);
        ++errors;
      }
    }

    logBuffer.write(
        String.format("%n=== END OF PRODUCTS EXPORT, done: %d, errors: %d ===%n", done, errors));
  }

  @Transactional(rollbackOn = {Exception.class})
  private void exportStocks(
      final PSWebServiceClient ws,
      final Map<Integer, PrestashopProduct> productsById,
      final Writer logBuffer)
      throws IOException {
    int errors = 0;
    int done = 0;
    logBuffer.write(String.format("%n===== STOCKS =====%n"));

    List<Product> localProductList =
        productRepo
            .all()
            .filter("self.prestaShopId IS NOT NULL AND self.dtype = 'Product'")
            .fetch();

    StockLocationService stockLocationService = Beans.get(StockLocationService.class);

    for (Product localProduct : localProductList) {
      try {
        final int currentStock =
            stockLocationService.getRealQty(localProduct.getId(), null, null).intValue();
        logBuffer.write(String.format("Updating stock for %s", localProduct.getCode()));
        PrestashopProduct remoteProduct = productsById.get(localProduct.getPrestaShopId());
        if (remoteProduct == null) {
          logBuffer.write(
              String.format(
                  " [ERROR] id %d not found on PrestaShop, skipping%n",
                  localProduct.getPrestaShopId()));
          ++errors;
          continue;
        }

        AvailableStocksAssociationsEntry availableStocks =
            remoteProduct.getAssociations().getAvailableStocks();
        if (availableStocks == null || availableStocks.getStock().size() == 0) {
          logBuffer.write(" [WARNING] No stock for this product, skipping stock update%n");
        } else if (availableStocks.getStock().size() > 1
            || Objects.equal(availableStocks.getStock().get(0).getProductAttributeId(), 0)
                == false) {
          logBuffer.write(" [WARNING] Remote product appears to have variants, skipping%n");
        } else {
          AvailableStocksAssociationElement availableStockRef = availableStocks.getStock().get(0);
          PrestashopAvailableStock availableStock =
              ws.fetch(PrestashopResourceType.STOCK_AVAILABLES, availableStockRef.getId());
          if (availableStock.isDependsOnStock()) {
            logBuffer.write(
                " [WARNING] Remote product uses advanced stock management features, not updating stock%n");
          } else if (currentStock != availableStock.getQuantity()) {
            availableStock.setQuantity(currentStock);
            ws.save(PrestashopResourceType.STOCK_AVAILABLES, availableStock);
            logBuffer.write(String.format(", setting stock to %d [SUCCESS]%n", currentStock));
          } else {
            logBuffer.write(String.format(", nothing to do [SUCCESS]%n"));
          }
        }
        ++done;
      } catch (PrestaShopWebserviceException | AxelorException e) {
        logBuffer.write(String.format(" [ERROR] exception occured: %s%n", e.getMessage()));
        TraceBackService.trace(
            e, I18n.get("Prestashop stocks export"), AbstractBatch.getCurrentBatchId());
        ++errors;
      }
    }

    logBuffer.write(
        String.format("%n=== END OF STOCKS EXPORT, done: %d, errors: %d ===%n", done, errors));
  }

  /** Export all pictures that have been modified */
  @Transactional(rollbackOn = {Exception.class})
  private void exportPictures(
      final PSWebServiceClient ws,
      final Map<Integer, PrestashopProduct> productsById,
      final Writer logBuffer)
      throws IOException {
    int errors = 0;
    int done = 0;
    logBuffer.write(String.format("%n===== PICTURES EXPORT =====%n"));

    final List<Product> products =
        productRepo
            .all()
            .filter(
                "self.prestaShopId is not null and self.picture is not null AND self.dtype = 'Product' AND "
                    + "(self.prestaShopImageVersion is null "
                    + "OR self.prestaShopImageId is null "
                    + "OR self.picture.version != self.prestaShopImageVersion "
                    + "OR self.picture.id != self.prestaShopImageId)")
            .order("code")
            .fetch();

    for (Product localProduct : products) {
      try {
        logBuffer.write(String.format("Updating picture for %s", localProduct.getCode()));
        final PrestashopProduct remoteProduct = productsById.get(localProduct.getPrestaShopId());
        if (remoteProduct == null) {
          logBuffer.write(
              String.format(
                  " [ERROR] id %d not found on PrestaShop, skipping%n",
                  localProduct.getPrestaShopId()));
          ++errors;
          continue;
        }

        try (InputStream is =
            new FileInputStream(MetaFiles.getPath(localProduct.getPicture()).toFile())) {
          PrestashopImage image = ws.addImage(PrestashopResourceType.PRODUCTS, remoteProduct, is);
          remoteProduct.setDefaultImageId(image.getId());
          ws.save(PrestashopResourceType.PRODUCTS, remoteProduct);
          localProduct.setPrestaShopImageId(localProduct.getPicture().getId());
          localProduct.setPrestaShopImageVersion(localProduct.getPicture().getVersion());
          localProduct.setPrestaShopVersion(localProduct.getVersion() + 1);
          logBuffer.write(String.format(" [SUCCESS]%n"));
        }
        ++done;
      } catch (PrestaShopWebserviceException e) {
        ++errors;
        logBuffer.write(String.format(" [ERROR] exception occured: %s%n", e.getMessage()));
        TraceBackService.trace(
            e, I18n.get("Prestashop product images export"), AbstractBatch.getCurrentBatchId());
      }
    }
    logBuffer.write(
        String.format("%n=== END OF PICTURES EXPORT, done: %d, errors: %d ===%n", done, errors));
  }

  // Null-safe version of UnitConversionService::Convert (feel free to integrate to base method).
  private BigDecimal convert(Unit from, Unit to, BigDecimal value, Product product)
      throws AxelorException {
    if (value == null) return null;
    return unitConversionService.convert(from, to, value, value.scale(), product);
  }
}
