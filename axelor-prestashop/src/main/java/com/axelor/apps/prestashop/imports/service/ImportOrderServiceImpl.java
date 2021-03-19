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

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.PaymentCondition;
import com.axelor.apps.account.db.repo.PaymentConditionRepository;
import com.axelor.apps.account.service.AccountingSituationService;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCreateService;
import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.AppPrestashop;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PartnerAddress;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.AddressRepository;
import com.axelor.apps.base.db.repo.CurrencyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.AddressService;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.db.IPrestaShopBatch;
import com.axelor.apps.prestashop.db.PrestashopOrderStatusCacheEntry;
import com.axelor.apps.prestashop.db.repo.PrestashopOrderStatusCacheEntryRepository;
import com.axelor.apps.prestashop.entities.PrestashopOrder;
import com.axelor.apps.prestashop.entities.PrestashopOrderRowDetails;
import com.axelor.apps.prestashop.entities.PrestashopResourceType;
import com.axelor.apps.prestashop.service.library.PSWebServiceClient;
import com.axelor.apps.prestashop.service.library.PrestaShopWebserviceException;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.service.saleorder.SaleOrderComputeService;
import com.axelor.apps.sale.service.saleorder.SaleOrderCreateService;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;
import com.axelor.apps.sale.service.saleorder.SaleOrderWorkflowService;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockMoveService;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceService;
import com.axelor.apps.supplychain.service.SaleOrderStockService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ImportOrderServiceImpl implements ImportOrderService {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private AddressRepository addressRepo;
  private CurrencyRepository currencyRepo;
  private PartnerRepository partnerRepo;
  private PaymentConditionRepository paymentConditionRepo;
  private PrestashopOrderStatusCacheEntryRepository statusRepo;
  private ProductRepository productRepository;
  private SaleOrderRepository saleOrderRepo;

  private AppBaseService appBaseService;
  private AccountingSituationService accountingSituationService;
  private AddressService addressService;
  private InvoicePaymentCreateService invoicePaymentCreateService;
  private InvoiceService invoiceService;
  private SaleOrderCreateService saleOrderCreateService;
  private SaleOrderComputeService saleOrderComputeService;
  private SaleOrderInvoiceService saleOrderInvoiceService;
  private SaleOrderLineService saleOrderLineService;
  private SaleOrderStockService deliveryService;
  private SaleOrderWorkflowService saleOrderWorkflowService;
  private StockMoveService stockMoveService;

  @Inject
  public ImportOrderServiceImpl(
      AppBaseService appBaseService,
      AddressRepository addressRepo,
      CurrencyRepository currencyRepo,
      PartnerRepository partnerRepo,
      PaymentConditionRepository paymentConditionRepo,
      PrestashopOrderStatusCacheEntryRepository statusRepo,
      ProductRepository productRepository,
      SaleOrderRepository saleOrderRepo,
      AccountingSituationService accountingSituationService,
      AddressService addressService,
      InvoicePaymentCreateService invoicePaymentCreateService,
      InvoiceService invoiceService,
      SaleOrderCreateService saleOrderCreateService,
      SaleOrderComputeService saleOrderComputeService,
      SaleOrderInvoiceService saleOrderInvoiceService,
      SaleOrderLineService saleOrderLineService,
      SaleOrderStockService deliveryService,
      SaleOrderWorkflowService saleOrderWorkflowService,
      StockMoveService stockMoveService) {
    this.appBaseService = appBaseService;
    this.addressRepo = addressRepo;
    this.currencyRepo = currencyRepo;
    this.partnerRepo = partnerRepo;
    this.paymentConditionRepo = paymentConditionRepo;
    this.statusRepo = statusRepo;
    this.productRepository = productRepository;
    this.saleOrderRepo = saleOrderRepo;
    this.accountingSituationService = accountingSituationService;
    this.addressService = addressService;
    this.invoicePaymentCreateService = invoicePaymentCreateService;
    this.invoiceService = invoiceService;
    this.saleOrderCreateService = saleOrderCreateService;
    this.saleOrderComputeService = saleOrderComputeService;
    this.saleOrderInvoiceService = saleOrderInvoiceService;
    this.saleOrderLineService = saleOrderLineService;
    this.deliveryService = deliveryService;
    this.saleOrderWorkflowService = saleOrderWorkflowService;
    this.stockMoveService = stockMoveService;
  }

  @Override
  @Transactional
  public void importOrder(AppPrestashop appConfig, ZonedDateTime endDate, Writer logWriter)
      throws IOException, PrestaShopWebserviceException {
    int done = 0;
    int errors = 0;

    log.debug("Starting PrestaShop orders import");
    logWriter.write(String.format("%n====== ORDERS ======%n"));

    // So we gonna import orders from PrestaShop. There are two main way the process can work
    // - either the option prestaShopMasterForOrders is activated, in that case that means
    //   all modifications from Axelor are lost and we just copy the orders and manage related
    //   entities
    // - either the option prestaShopMasterForOrders is disabled and we only update the content
    //   of order (only if there is no local payment nor invoice).

    final PSWebServiceClient ws =
        new PSWebServiceClient(appConfig.getPrestaShopUrl(), appConfig.getPrestaShopKey());

    // TODO For huge sites, it could be useful to window this as prestashop web services supports
    // it.
    final List<PrestashopOrder> remoteOrders = ws.fetchAll(PrestashopResourceType.ORDERS);

    for (PrestashopOrder remoteOrder : remoteOrders) {
      try {
        logWriter.write(
            String.format(
                "Importing order #%d (%s) ", remoteOrder.getId(), remoteOrder.getReference()));

        final Partner customer = partnerRepo.findByPrestaShopId(remoteOrder.getCustomerId());
        if (customer == null) {
          logWriter.write(
              String.format(" [WARNING] order belongs to a not yet synced customer, skpping%n"));
          ++errors;
          continue;
        }
        final Currency localCurrency = currencyRepo.findByPrestaShopId(remoteOrder.getCurrencyId());
        if (localCurrency == null) {
          logWriter.write(
              String.format(" [WARNING] order refers to a not yet synced currency, skpping%n"));
          ++errors;
          continue;
        }

        final PrestashopOrderStatusCacheEntry localStatus =
            statusRepo.findByPrestaShopId(remoteOrder.getCurrentState());
        if (localStatus == null) {
          logWriter.write(String.format(" [WARNING] order status is unknown locally, skipping%n"));
          ++errors;
          continue;
        }

        final Address localDeliveryAddress =
            addressRepo.findByPrestaShopId(remoteOrder.getDeliveryAddressId());
        if (localDeliveryAddress == null) {
          logWriter.write(
              String.format(
                  " [WARNING] order refers to a not yet synced delivery address, skpping%n"));
          ++errors;
          continue;
        }

        final Address localInvoicingAddress =
            addressRepo.findByPrestaShopId(remoteOrder.getInvoiceAddressId());
        if (localInvoicingAddress == null) {
          logWriter.write(
              String.format(
                  " [WARNING] order refers to a not yet synced invoicing address, skpping%n"));
          ++errors;
          continue;
        }

        SaleOrder localOrder = saleOrderRepo.findByPrestaShopId(remoteOrder.getId());
        if (localOrder == null) {
          try {
            localOrder =
                saleOrderCreateService.createSaleOrder(
                    null,
                    AbstractBatch.getCurrentBatch().getPrestaShopBatch().getCompany(),
                    null,
                    localCurrency,
                    null,
                    null,
                    remoteOrder.getReference(),
                    null,
                    customer,
                    null);
          } catch (AxelorException ae) {
            TraceBackService.trace(
                ae, I18n.get("Prestashop order import"), AbstractBatch.getCurrentBatchId());
            logWriter.write(
                String.format(
                    " [ERROR] An error occured while creating sale order %s: %s%n",
                    remoteOrder.getReference(), ae.getLocalizedMessage()));
            log.error(
                String.format(
                    "An error occured while creating sale order %s: %s",
                    remoteOrder.getReference(), ae.getLocalizedMessage()));
            continue;
          }
          localOrder.setPrestaShopId(remoteOrder.getId());
          localOrder.setImportOrigin(IPrestaShopBatch.IMPORT_ORIGIN_PRESTASHOP);
          localOrder.setPrintingSettings(localOrder.getCompany().getPrintingSettings());
        }

        if (localOrder.getPrestaShopUpdateDateTime() != null
            && remoteOrder.getUpdateDate() != null
            && localOrder.getPrestaShopUpdateDateTime().compareTo(remoteOrder.getUpdateDate())
                >= 0) {
          logWriter.write(String.format("already up-to-date, skipping [WARNING]%n"));
          continue;
        }

        if (localOrder.getId() == null || appConfig.getPrestaShopMasterForOrders()) {
          // Let' populate base data
          localOrder.setDeliveryAddress(localDeliveryAddress);
          localOrder.setDeliveryAddressStr(addressService.computeAddressStr(localDeliveryAddress));
          localOrder.setMainInvoicingAddress(localInvoicingAddress);
          localOrder.setMainInvoicingAddressStr(
              addressService.computeAddressStr(localInvoicingAddress));

          boolean deliveryAddressFound = false;
          boolean invoicingAddressFound = false;
          for (ListIterator<PartnerAddress> it = customer.getPartnerAddressList().listIterator();
              deliveryAddressFound == false && invoicingAddressFound && it.hasNext(); ) {
            PartnerAddress partnerAddress = it.next();
            if (partnerAddress.getAddress().getId() == localDeliveryAddress.getId()) {
              partnerAddress.setIsDeliveryAddr(Boolean.TRUE);
              deliveryAddressFound = true;
            }
            if (partnerAddress.getAddress().getId() == localInvoicingAddress.getId()) {
              partnerAddress.setIsInvoicingAddr(Boolean.TRUE);
              invoicingAddressFound = true;
            }
          }
          if (deliveryAddressFound == false) {
            PartnerAddress partnerAddress = new PartnerAddress();
            partnerAddress.setPartner(customer);
            partnerAddress.setAddress(localDeliveryAddress);
            partnerAddress.setIsDeliveryAddr(Boolean.TRUE);
            customer.addPartnerAddressListItem(partnerAddress);
            if (localDeliveryAddress.equals(localInvoicingAddress)) {
              partnerAddress.setIsInvoicingAddr(Boolean.TRUE);
            }
          }
          if (invoicingAddressFound == false
              && localInvoicingAddress.equals(localDeliveryAddress) == false) {
            PartnerAddress partnerAddress = new PartnerAddress();
            partnerAddress.setPartner(customer);
            partnerAddress.setAddress(localDeliveryAddress);
            partnerAddress.setIsInvoicingAddr(Boolean.TRUE);
            customer.addPartnerAddressListItem(partnerAddress);
          }

          PaymentCondition localPaymentCondition =
              paymentConditionRepo.findByName(remoteOrder.getPayment());
          if (localPaymentCondition == null) {
            localPaymentCondition = paymentConditionRepo.findByCode(remoteOrder.getPayment());
            if (localPaymentCondition == null)
              localPaymentCondition = appConfig.getDefaultPaymentCondition();
          }
          localOrder.setPaymentCondition(localPaymentCondition);

          // Maybe we should compute this from lines…
          localOrder.setExTaxTotal(
              remoteOrder.getTotalPaidTaxExcluded().setScale(2, RoundingMode.HALF_UP));
          localOrder.setInTaxTotal(
              remoteOrder.getTotalPaidTaxIncluded().setScale(2, RoundingMode.HALF_UP));
          localOrder.setTaxTotal(
              remoteOrder
                  .getTotalDiscountsTaxIncluded()
                  .subtract(remoteOrder.getTotalDiscountsTaxExcluded())
                  .setScale(2, RoundingMode.HALF_UP));
          if (localOrder.getTaxTotal().compareTo(BigDecimal.ZERO) < 0)
            localOrder.setTaxTotal(BigDecimal.ZERO);
          localOrder.setCreationDate(remoteOrder.getAddDate().toLocalDate());
          localOrder.setOrderDate(remoteOrder.getAddDate().toLocalDate());
          localOrder.setExternalReference(remoteOrder.getReference());
          localOrder.setCompanyBankDetails(
              accountingSituationService.getCompanySalesBankDetails(
                  AbstractBatch.getCurrentBatch().getPrestaShopBatch().getCompany(),
                  localOrder.getClientPartner()));

          // FIXME handle mapping between payment modes and modules (remoteOrder.getModule())
          localOrder.setPaymentMode(appConfig.getDefaultPaymentMode());
          localOrder.setCompany(AbstractBatch.getCurrentBatch().getPrestaShopBatch().getCompany());
          localOrder.setCurrency(localCurrency);

          if (importLines(appConfig, ws, remoteOrder, localOrder, logWriter) == false) {
            continue;
          }

          // Now handle status, this is rather complicated since PrestaShop status also handle
          // invoicing, payment and delivery
          if (localOrder.getStatusSelect() == SaleOrderRepository.STATUS_DRAFT_QUOTATION) {
            try {
              // Note that in case of taxes mismatch, this will probably blow everything up
              saleOrderComputeService.computeSaleOrder(localOrder);
            } catch (AxelorException ae) {
              TraceBackService.trace(
                  ae, I18n.get("Prestashop order import"), AbstractBatch.getCurrentBatchId());
              logWriter.write(
                  String.format(
                      " [ERROR] An error occured while computing sale order elements: %s%n",
                      ae.getLocalizedMessage()));
              log.error(
                  String.format(
                      "An error occured while computing sale order #%d elements (PS #%d)",
                      localOrder.getId(), remoteOrder.getId()),
                  ae);
              continue;
            }

            // Consider this as finalized, draft would be a cart without order, unhandled right now
            localOrder.setManualUnblock(Boolean.TRUE);
            try {
              saleOrderWorkflowService.finalizeQuotation(localOrder);
            } catch (AxelorException ae) {
              TraceBackService.trace(
                  ae, I18n.get("Prestashop order import"), AbstractBatch.getCurrentBatchId());
              logWriter.write(
                  String.format(
                      " [ERROR] An error occured while finalizing sale order: %s%n",
                      ae.getLocalizedMessage()));
              log.error(
                  String.format(
                      "An error occured while finalizing sale order #%d (PS #%d)",
                      localOrder.getId(), remoteOrder.getId()),
                  ae);
              continue;
            }
            // Nothing to deleted as we've a new order
          }
          localOrder.setPrestaShopUpdateDateTime(remoteOrder.getUpdateDate());
          saleOrderRepo.save(localOrder);
          localOrder = saleOrderRepo.find(localOrder.getId());
        } else {
          if (IPrestaShopBatch.IMPORT_ORIGIN_PRESTASHOP.equals(localOrder.getImportOrigin())
              == false) {
            // Avoid round trips
            logWriter.write(
                String.format(
                    " - PrestaShop isn't master for orders and local order hasn't been imported from it, skipping [SUCCESS]%n"));
            continue;
          } else {
            // OK so order exists, we've to see if we need to update something
            if (remoteOrder.getTotalPaidTaxIncluded().compareTo(localOrder.getInTaxTotal()) != 0) {
              logWriter.write(
                  String.format(
                      " - Remote and local order total differs (%f vs %f)",
                      remoteOrder.getTotalPaidTaxIncluded(), localOrder.getInTaxTotal()));
              if ((localOrder.getStatusSelect() != SaleOrderRepository.STATUS_DRAFT_QUOTATION
                      && localOrder.getStatusSelect()
                          != SaleOrderRepository.STATUS_FINALIZED_QUOTATION)
                  || localOrder.getDeliveryState() != SaleOrderRepository.DELIVERY_STATE_DELIVERED
                  || (localOrder.getAmountInvoiced() == null
                      || BigDecimal.ZERO.compareTo(localOrder.getAmountInvoiced()) != 0)) {
                final String additionalComment =
                    I18n.get(
                        "<p>WARNING: Order has been modified on PrestaShop but could not be updated locally.</p>");
                if (localOrder.getInternalNote() == null
                    || localOrder.getInternalNote().indexOf(additionalComment) < 0) {
                  localOrder.setInternalNote(
                      (localOrder.getInternalNote() == null ? "" : localOrder.getInternalNote())
                          + additionalComment);
                }
                logWriter.write(
                    String.format(
                        " - Order status does not allow updates anymore, skipping (status: %d, delivery status: %d, amount invoiced: %f [ERROR]%n",
                        localOrder.getStatusSelect(),
                        localOrder.getDeliveryState(),
                        localOrder.getAmountInvoiced()));
                continue;
              } else {
                // TODO Maybe we should do this more smoothly
                localOrder.clearSaleOrderLineList();
                if (importLines(appConfig, ws, remoteOrder, localOrder, logWriter) == false) {
                  continue;
                }
              }
            }
          }
        }

        if ((localStatus.getPaid()
                || localStatus.getDelivered()
                || localStatus.getInvoiced()
                || localStatus.getShipped())
            && localOrder.getStatusSelect() == SaleOrderRepository.STATUS_FINALIZED_QUOTATION) {
          // Order has been paid or invoiced, it means it's confirmed
          localOrder.setManualUnblock(Boolean.TRUE);
          try {
            saleOrderWorkflowService.confirmSaleOrder(localOrder);
          } catch (AxelorException ae) {
            TraceBackService.trace(
                ae, I18n.get("Prestashop order import"), AbstractBatch.getCurrentBatchId());
            logWriter.write(
                String.format(
                    " [ERROR] An error occured while confirming sale order: %s%n",
                    ae.getLocalizedMessage()));
            log.error(
                String.format(
                    "An error occured while finalizing or confirming sale order #%d (PS #%d)",
                    localOrder.getId(), remoteOrder.getId()),
                ae);
            continue;
          }
        }

        // If we end up here, we've a local sale order with lines matching the remote one
        // Let's see if we've more to to
        if (localStatus.getInvoiced()) {
          if (BigDecimal.ZERO.compareTo(localOrder.getAmountInvoiced()) == 0) {
            try {
              Invoice invoice =
                  saleOrderInvoiceService.generateInvoice(
                      localOrder, SaleOrderRepository.INVOICE_ALL, null, false, null, null);
              invoice.setImportOrigin(IPrestaShopBatch.IMPORT_ORIGIN_PRESTASHOP);
              invoice.setPrintingSettings(localOrder.getPrintingSettings());
              invoiceService.ventilate(invoice);
            } catch (AxelorException ae) {
              TraceBackService.trace(
                  ae, I18n.get("Prestashop order import"), AbstractBatch.getCurrentBatchId());
              logWriter.write(
                  String.format(
                      " [ERROR] An error occured while generating invoice for sale order: %s%n",
                      ae.getLocalizedMessage()));
              log.error(
                  String.format(
                      "An error occured while generating invoice for sale order #%d (PS #%d)",
                      localOrder.getId(), remoteOrder.getId()),
                  ae);
              continue;
            }
          }
        }

        // Currently, all statuses with paid mean invoiced too, but to cover all cases
        // we should register an advance payment in case of paid but not invoiced
        if (localStatus.getPaid()) {
          List<Invoice> invoices = saleOrderInvoiceService.getInvoices(localOrder);
          if (invoices.size() != 1) {
            logWriter.write(
                String.format(
                    " [WARNING] Found %d invoice(s) for this order, cannot record payment (exactly one invoice needed), skipping payment creation%n",
                    invoices.size()));
          } else {
            Invoice invoice = invoices.get(0);
            if (BigDecimal.ZERO.compareTo(invoice.getAmountPaid()) == 0) {
              invoicePaymentCreateService.createInvoicePayment(
                  invoice, invoice.getCompanyBankDetails());
            }
          }
        }

        if (localStatus.getShipped()) {
          if (localOrder.getDeliveryState() == SaleOrderRepository.DELIVERY_STATE_NOT_DELIVERED) {
            localOrder.setDeliveryDate(
                remoteOrder.getDeliveryDate() == null
                    ? appBaseService.getTodayDate()
                    : remoteOrder.getDeliveryDate().toLocalDate());
            try {
              List<Long> stockMoveIds = deliveryService.createStocksMovesFromSaleOrder(localOrder);

              // Will be null if only services are present on order
              if (stockMoveIds != null) {
                for (Long stockMoveId : stockMoveIds) {
                  StockMove delivery = Beans.get(StockMoveRepository.class).find(stockMoveId);
                  stockMoveService.realize(delivery, true);
                  for (SaleOrderLine line : localOrder.getSaleOrderLineList()) {
                    if (ProductRepository.PRODUCT_TYPE_SERVICE.equals(
                        line.getProduct().getProductTypeSelect())) {
                      line.setDeliveryState(SaleOrderRepository.DELIVERY_STATE_DELIVERED);
                    }
                  }
                }
              }
              localOrder.setDeliveryState(SaleOrderRepository.DELIVERY_STATE_DELIVERED);
            } catch (AxelorException ae) {
              TraceBackService.trace(
                  ae, I18n.get("Prestashop order import"), AbstractBatch.getCurrentBatchId());
              logWriter.write(
                  String.format(
                      " [ERROR] An error occured while generating delivery for sale order: %s%n",
                      ae.getLocalizedMessage()));
              log.error(
                  String.format(
                      "An error occured while generating delivery for sale order #%d (PS #%d)",
                      localOrder.getId(), remoteOrder.getId()),
                  ae);
              continue;
            }
          }
        }

        logWriter.write(String.format(" [SUCCESS]%n"));
        ++done;
      } catch (PrestaShopWebserviceException e) {
        TraceBackService.trace(
            e, I18n.get("Prestashop orders import"), AbstractBatch.getCurrentBatchId());
        logWriter.write(
            String.format(
                " [ERROR] %s (full trace is in application logs)%n", e.getLocalizedMessage()));
        log.error(
            String.format(
                "Exception while synchronizing order %s (%s)",
                remoteOrder.getId(), remoteOrder.getReference()),
            e);
        ++errors;
      }
    }

    logWriter.write(
        String.format("%n=== END OF ORDERS IMPORT, done: %d, errors: %d ===%n", done, errors));
  }

  private boolean importLines(
      final AppPrestashop appConfig,
      final PSWebServiceClient ws,
      final PrestashopOrder remoteOrder,
      final SaleOrder localOrder,
      final Writer logWriter)
      throws IOException, PrestaShopWebserviceException {
    List<PrestashopOrderRowDetails> remoteLines =
        ws.fetch(
            PrestashopResourceType.ORDER_DETAILS,
            Collections.singletonMap("id_order", remoteOrder.getId().toString()));
    // There is absolutely no documentation on the meaning of fields on prestathop side
    // so we compute total from lines, compare it to the total paid and add a discount product
    // if needed
    BigDecimal computedTotal = BigDecimal.ZERO;

    for (PrestashopOrderRowDetails remoteLine : remoteLines) {
      try {
        final Product product = productRepository.findByPrestaShopId(remoteLine.getProductId());
        if (product == null) {
          logWriter.write(
              String.format(
                  " [ERROR] Product %d was not found locally, skipping%n",
                  remoteLine.getProductId()));
          log.error(
              "Product {} was not found locally, skipping for order {}, skipping",
              remoteLine.getProductId(),
              localOrder.getExternalReference());
          return false;
        }
        final SaleOrderLine localLine = new SaleOrderLine();
        localLine.setSaleOrder(localOrder);
        localLine.setTypeSelect(SaleOrderLineRepository.TYPE_NORMAL);
        localLine.setProduct(product);

        // Set all default information (product name, tax, unit, cost price)
        // Even if we'll override some of them later, it does not hurt to pass
        // through service method
        saleOrderLineService.computeProductInformation(localLine, localOrder);

        localLine.setQty(BigDecimal.valueOf(remoteLine.getProductQuantity()));
        localLine.setProductName(remoteLine.getProductName());
        // poor attempt to preserve discount
        if ((remoteLine.getUnitPriceTaxIncluded().compareTo(remoteLine.getProductPrice())) < 0) {
          // We do not take into account any discount related field to avoid issues with
          // rounding
          localLine.setPrice(remoteLine.getProductPrice());
          localLine.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_FIXED);
          localLine.setDiscountAmount(
              remoteLine.getProductPrice().subtract(remoteLine.getUnitPriceTaxExcluded()));
        } else {
          localLine.setPrice(remoteLine.getUnitPriceTaxExcluded());
          localLine.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
        }
        localLine.setPriceDiscounted(saleOrderLineService.computeDiscount(localLine, false));
        computedTotal =
            computedTotal.add(
                remoteLine
                    .getUnitPriceTaxExcluded()
                    .multiply(BigDecimal.valueOf(remoteLine.getProductQuantity())));

        // Sets exTaxTotal, inTaxTotal, companyInTaxTotal, companyExTaxTotal
        // We just prey for rounding & tax rates to be consistent between PS & ABS since
        // discounted price is computed
        // TODO Have a tax mapping?
        saleOrderLineService.computeValues(localOrder, localLine);
        saleOrderLineService.computeSubMargin(localOrder, localLine);

        localOrder.addSaleOrderLineListItem(localLine);
      } catch (AxelorException ae) {
        TraceBackService.trace(
            ae, I18n.get("Prestashop order import"), AbstractBatch.getCurrentBatchId());
        logWriter.write(
            String.format(
                " [ERROR] An error occured while creating line for sale order order: %s, line %s (%d units): %s%n",
                localOrder.getExternalReference(),
                remoteLine.getProductName(),
                remoteLine.getProductQuantity(),
                ae.getMessage()));
        log.error(
            String.format(
                "An error occured while creating line for sale order : %s, line %s (%d units)",
                localOrder.getExternalReference(),
                remoteLine.getProductName(),
                remoteLine.getProductQuantity()),
            ae);
        return false;
      }
    }

    if (BigDecimal.ZERO.compareTo(remoteOrder.getTotalShippingTaxExcluded()) != 0) {
      if (addCustomLine(
              appConfig.getDefaultShippingCostsProduct(),
              localOrder,
              remoteOrder.getTotalShippingTaxExcluded(),
              logWriter)
          == false) {
        return false;
      }
      computedTotal = computedTotal.add(remoteOrder.getTotalShippingTaxExcluded());
    }

    BigDecimal footerDiscountAmount = computedTotal.subtract(remoteOrder.getTotalPaidTaxExcluded());
    if (footerDiscountAmount.compareTo(BigDecimal.ZERO) < 0) {
      logWriter.write(
          String.format(
              "[ERROR] Computed total (%f) is less than paid total (%f) looks like a bug",
              computedTotal, remoteOrder.getTotalPaidTaxExcluded()));
      return false;
    } else if (footerDiscountAmount.compareTo(BigDecimal.ZERO) > 0) {
      logWriter.write(
          String.format(
              " - total paid (%f) differs from computed total (%f) adding discount",
              remoteOrder.getTotalPaidTaxExcluded(), computedTotal));

      if (addCustomLine(
              appConfig.getDiscountProduct(), localOrder, footerDiscountAmount.negate(), logWriter)
          == false) {
        return false;
      }
    }

    return true;
  }

  private boolean addCustomLine(
      final Product product,
      final SaleOrder localOrder,
      final BigDecimal price,
      final Writer logWriter)
      throws IOException {
    try {
      final SaleOrderLine localLine = new SaleOrderLine();
      localLine.setSaleOrder(localOrder);
      localLine.setTypeSelect(SaleOrderLineRepository.TYPE_NORMAL);
      localLine.setProduct(product);

      // Set all default information (product name, tax, unit, cost price)
      // Even if we'll override some of them later, it does not hurt to pass
      // through service method
      saleOrderLineService.computeProductInformation(localLine, localOrder);

      localLine.setQty(BigDecimal.ONE);
      localLine.setPrice(price);
      localLine.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
      localLine.setPriceDiscounted(price);

      // Sets exTaxTotal, inTaxTotal, companyInTaxTotal, companyExTaxTotal
      // We just prey for rounding & tax rates to be consistent between PS & ABS since
      // discounted price is computed
      saleOrderLineService.computeValues(localOrder, localLine);
      saleOrderLineService.computeSubMargin(localOrder, localLine);

      localOrder.addSaleOrderLineListItem(localLine);
    } catch (AxelorException ae) {
      TraceBackService.trace(
          ae, I18n.get("Prestashop order import"), AbstractBatch.getCurrentBatchId());
      logWriter.write(
          String.format(
              " [ERROR] An error occured while adding custom line (product %s) for sale order: %s: %s%n",
              product.getCode(), localOrder.getExternalReference(), ae.getMessage()));
      log.error(
          String.format(
              "An error occured while adding delivery fees line for sale order: %s",
              localOrder.getExternalReference()),
          ae);
      return false;
    }
    return true;
  }
}
