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
package com.axelor.apps.customer.portal.service;

import com.axelor.apps.account.db.repo.AnalyticMoveLineRepository;
import com.axelor.apps.base.db.AppCustomerPortal;
import com.axelor.apps.base.db.CancelReason;
import com.axelor.apps.base.db.repo.AppCustomerPortalRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.businessproduction.service.SaleOrderWorkflowServiceBusinessProductionImpl;
import com.axelor.apps.client.portal.db.PortalQuotation;
import com.axelor.apps.client.portal.db.repo.PortalQuotationRepository;
import com.axelor.apps.crm.service.app.AppCrmService;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.Template;
import com.axelor.apps.message.service.MessageService;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.apps.production.service.app.AppProductionService;
import com.axelor.apps.production.service.productionorder.ProductionOrderSaleOrderService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.exception.BlockedSaleOrderException;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.config.SaleConfigService;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;
import com.axelor.apps.supplychain.service.AccountingSituationSupplychainService;
import com.axelor.apps.supplychain.service.PartnerSupplychainService;
import com.axelor.apps.supplychain.service.SaleOrderCheckAnalyticService;
import com.axelor.apps.supplychain.service.SaleOrderPurchaseService;
import com.axelor.apps.supplychain.service.SaleOrderStockService;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import javax.mail.MessagingException;
import wslite.json.JSONException;

public class SaleOrderWorkflowServicePortalImpl
    extends SaleOrderWorkflowServiceBusinessProductionImpl {

  protected AppCustomerPortalRepository appRepo;
  protected TemplateMessageService templateService;
  protected MessageService messageService;
  protected PortalQuotationService portalQuotationService;

  @Inject
  public SaleOrderWorkflowServicePortalImpl(
      SequenceService sequenceService,
      PartnerRepository partnerRepo,
      SaleOrderRepository saleOrderRepo,
      AppSaleService appSaleService,
      AppCrmService appCrmService,
      UserService userService,
      SaleOrderLineService saleOrderLineService,
      SaleOrderStockService saleOrderStockService,
      SaleOrderPurchaseService saleOrderPurchaseService,
      AppSupplychainService appSupplychainService,
      AccountingSituationSupplychainService accountingSituationSupplychainService,
      PartnerSupplychainService partnerSupplychainService,
      SaleConfigService saleConfigService,
      SaleOrderCheckAnalyticService saleOrderCheckAnalyticService,
      ProductionOrderSaleOrderService productionOrderSaleOrderService,
      AppProductionService appProductionService,
      AnalyticMoveLineRepository analyticMoveLineRepository,
      AppCustomerPortalRepository appRepo,
      TemplateMessageService templateService,
      MessageService messageService,
      PortalQuotationService portalQuotationService) {
    super(
        sequenceService,
        partnerRepo,
        saleOrderRepo,
        appSaleService,
        appCrmService,
        userService,
        saleOrderLineService,
        saleOrderStockService,
        saleOrderPurchaseService,
        appSupplychainService,
        accountingSituationSupplychainService,
        partnerSupplychainService,
        saleConfigService,
        saleOrderCheckAnalyticService,
        productionOrderSaleOrderService,
        appProductionService,
        analyticMoveLineRepository);
    this.appRepo = appRepo;
    this.templateService = templateService;
    this.messageService = messageService;
    this.portalQuotationService = portalQuotationService;
  }

  @Override
  @Transactional(
      rollbackOn = {AxelorException.class, RuntimeException.class},
      ignore = {BlockedSaleOrderException.class})
  public void finalizeQuotation(SaleOrder saleOrder) throws AxelorException {
    super.finalizeQuotation(saleOrder);
    try {
      AppCustomerPortal app = appRepo.all().fetchOne();
      if (app.getManageQuotations() && app.getIsNotifyCustomer()) {
        Template template = app.getCustomerNotificationTemplate();
        Message message = templateService.generateMessage(saleOrder, template);
        EmailAccount emailAccount = portalQuotationService.getEmailAccount(app);
        if (emailAccount != null) {
          message.setMailAccount(emailAccount);
        }
        messageService.addMessageRelatedTo(
            message, SaleOrder.class.getCanonicalName(), saleOrder.getId());
        message = messageService.sendMessage(message);
      }
    } catch (ClassNotFoundException
        | InstantiationException
        | IllegalAccessException
        | IOException
        | JSONException e) {
      throw new AxelorException(e.getCause(), TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void confirmSaleOrder(SaleOrder saleOrder) throws AxelorException {

    AppCustomerPortal app = appRepo.all().fetchOne();
    if (app.getManageQuotations()
        && Beans.get(PortalQuotationRepository.class)
                .all()
                .filter("self.saleOrder = :saleOrder")
                .bind("saleOrder", saleOrder)
                .count()
            == 0) {
      try {
        PortalQuotation portalQuotation = portalQuotationService.createPortalQuotation(saleOrder);
        portalQuotation.setStatusSelect(PortalQuotationRepository.STATUS_ORDER_CONFIRMED);
        portalQuotation.setTypeSelect(null);
        Beans.get(PortalQuotationRepository.class).save(portalQuotation);
      } catch (MessagingException | AxelorException e) {
        TraceBackService.trace(e);
      }
    }
    super.confirmSaleOrder(saleOrder);
  }

  @Override
  @Transactional
  public void cancelSaleOrder(
      SaleOrder saleOrder, CancelReason cancelReason, String cancelReasonStr)
      throws AxelorException {

    super.cancelSaleOrder(saleOrder, cancelReason, cancelReasonStr);
    AppCustomerPortal app = appRepo.all().fetchOne();
    if (app.getManageQuotations()) {

      PortalQuotation portalQuotation =
          Beans.get(PortalQuotationRepository.class)
              .all()
              .filter(
                  "self.saleOrder = :saleOrder AND (self.statusSelect = :proposed_status OR self.statusSelect = :request_status)")
              .bind("saleOrder", saleOrder)
              .bind("proposed_status", PortalQuotationRepository.STATUS_PROPOSED_QUOTATION)
              .bind("request_status", PortalQuotationRepository.STATUS_REQUESTED_QUOTATION)
              .fetchOne();
      if (portalQuotation == null) {
        return;
      }

      portalQuotation.setStatusSelect(PortalQuotationRepository.STATUS_DECLINED_QUOTATION);
      Beans.get(PortalQuotationRepository.class).save(portalQuotation);
    }
  }
}
