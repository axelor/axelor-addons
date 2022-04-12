/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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

import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PartnerCategory;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.app.AppService;
import com.axelor.apps.client.portal.db.repo.ClientResourceRepository;
import com.axelor.apps.client.portal.db.repo.DiscussionGroupRepository;
import com.axelor.apps.client.portal.db.repo.GeneralAnnouncementRepository;
import com.axelor.apps.client.portal.db.repo.IdeaRepository;
import com.axelor.apps.client.portal.db.repo.PortalIdeaTagRepository;
import com.axelor.apps.client.portal.db.repo.PortalQuotationRepository;
import com.axelor.apps.helpdesk.db.repo.TicketRepository;
import com.axelor.apps.portal.service.ClientViewServiceImpl;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.auth.db.User;
import com.axelor.db.JpaSecurity;
import com.axelor.inject.Beans;
import com.google.inject.Inject;

public class ClientViewPortalServiceImpl extends ClientViewServiceImpl
    implements ClientViewPortalService {

  protected PortalQuotationRepository portalQuotationRepo;
  protected DiscussionGroupRepository discussionGroupRepo;
  protected GeneralAnnouncementRepository announcementRepo;
  protected ClientResourceRepository clientResourceRepo;
  protected IdeaRepository ideaRepo;
  protected PortalIdeaTagRepository ideaTagRepo;

  @Inject
  public ClientViewPortalServiceImpl(
      SaleOrderRepository saleOrderRepo,
      StockMoveRepository stockMoveRepo,
      ProjectRepository projectRepo,
      TicketRepository ticketRepo,
      InvoiceRepository invoiceRepo,
      ProjectTaskRepository projectTaskRepo,
      JpaSecurity jpaSecurity,
      AppService appService,
      PortalQuotationRepository portalQuotationRepo,
      DiscussionGroupRepository discussionGroupRepo,
      GeneralAnnouncementRepository announcementRepo,
      ClientResourceRepository clientResourceRepo,
      IdeaRepository ideaRepo,
      PortalIdeaTagRepository ideaTagRepo) {
    super(
        saleOrderRepo,
        stockMoveRepo,
        projectRepo,
        ticketRepo,
        invoiceRepo,
        projectTaskRepo,
        jpaSecurity,
        appService);
    this.portalQuotationRepo = portalQuotationRepo;
    this.discussionGroupRepo = discussionGroupRepo;
    this.announcementRepo = announcementRepo;
    this.clientResourceRepo = clientResourceRepo;
    this.ideaRepo = ideaRepo;
    this.ideaTagRepo = ideaTagRepo;
  }

  @Override
  public Long getOpenQuotation() {
    return portalQuotationRepo
        .all()
        .filter(
            "(self.saleOrder.clientPartner = :clientPartner OR self.saleOrder.contactPartner = :clientPartner) AND "
                + "self IN (SELECT MAX(id) FROM PortalQuotation portalQuotation WHERE self.statusSelect < :status GROUP BY portalQuotation.saleOrder)")
        .bind("status", PortalQuotationRepository.STATUS_ORDER_CONFIRMED)
        .bind("clientPartner", getClientUser().getPartner())
        .count();
  }

  @Override
  public Long getQuotationSaleOrder() {
    return portalQuotationRepo
        .all()
        .filter(
            "(self.saleOrder.clientPartner = :clientPartner OR self.saleOrder.contactPartner = :clientPartner) AND "
                + "self IN (SELECT MAX(id) FROM PortalQuotation portalQuotation WHERE self.statusSelect = :status GROUP BY portalQuotation.saleOrder)")
        .bind("status", PortalQuotationRepository.STATUS_ORDER_CONFIRMED)
        .bind("clientPartner", getClientUser().getPartner())
        .count();
  }

  @Override
  public Long getQuotationHistory() {
    return portalQuotationRepo
        .all()
        .filter(
            "(self.saleOrder.clientPartner = :clientPartner OR self.saleOrder.contactPartner = :clientPartner) AND "
                + "((self.endOfValidity < :today AND self.signature IS NULL) OR (self.statusSelect >= :status))")
        .bind("today", Beans.get(AppBaseService.class).getTodayDateTime().toLocalDate())
        .bind("status", SaleOrderRepository.STATUS_ORDER_COMPLETED)
        .bind("clientPartner", getClientUser().getPartner())
        .count();
  }

  @Override
  public Long getAllQuotation() {
    return portalQuotationRepo
        .all()
        .filter(
            "(self.saleOrder.clientPartner = :clientPartner OR self.saleOrder.contactPartner = :clientPartner)")
        .bind("clientPartner", getClientUser().getPartner())
        .count();
  }

  @Override
  public Long getToPayInvoice() {
    return invoiceRepo
        .all()
        .filter(
            "(self.partner = :clientPartner OR self.contactPartner = :clientPartner) AND "
                + "self.statusSelect = :statusSelect AND "
                + "(self.operationTypeSelect = :operationTypeSelect AND self.amountRemaining > 0)")
        .bind("operationTypeSelect", InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)
        .bind("statusSelect", InvoiceRepository.STATUS_VENTILATED)
        .bind("clientPartner", getClientUser().getPartner())
        .count();
  }

  @Override
  public Long getOldInvoice() {
    return invoiceRepo
        .all()
        .filter(
            "(self.partner = :clientPartner OR self.contactPartner = :clientPartner) AND "
                + "self.statusSelect = :statusSelect AND "
                + "(self.operationTypeSelect = :operationTypeSelect AND self.amountRemaining = 0)")
        .bind("operationTypeSelect", InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)
        .bind("statusSelect", InvoiceRepository.STATUS_VENTILATED)
        .bind("clientPartner", getClientUser().getPartner())
        .count();
  }

  @Override
  public Long getRefundInvoice() {
    return invoiceRepo
        .all()
        .filter(
            "(self.partner = :clientPartner OR self.contactPartner = :clientPartner) AND "
                + "self.statusSelect = :statusSelect AND "
                + "(self.operationTypeSelect = :operationTypeSelect)")
        .bind("operationTypeSelect", InvoiceRepository.OPERATION_TYPE_CLIENT_REFUND)
        .bind("statusSelect", InvoiceRepository.STATUS_VENTILATED)
        .bind("clientPartner", getClientUser().getPartner())
        .count();
  }

  @Override
  public Long getMyTicket() {

    User user = getClientUser();
    Partner partner = user.getPartner();
    return projectTaskRepo
        .all()
        .filter(
            "self.assignment = 1 AND self.project.clientPartner = :partner AND self.status.isCompleted != true AND self.typeSelect = :typeSelect")
        .bind(
            "partner",
            partner != null ? partner.getIsContact() ? partner.getMainPartner() : partner : null)
        .bind("typeSelect", ProjectTaskRepository.TYPE_TICKET)
        .count();
  }

  @Override
  public Long getProviderTicket() {

    User user = getClientUser();
    Partner partner = user.getPartner();
    return projectTaskRepo
        .all()
        .filter(
            "self.assignment = 2 AND self.project.clientPartner = :partner AND self.status.isCompleted != true AND self.typeSelect = :typeSelect")
        .bind(
            "partner",
            partner != null ? partner.getIsContact() ? partner.getMainPartner() : partner : null)
        .bind("typeSelect", ProjectTaskRepository.TYPE_TICKET)
        .count();
  }

  @Override
  public Long getOpenTicket() {

    User user = getClientUser();
    Partner partner = user.getPartner();
    return projectTaskRepo
        .all()
        .filter(
            "self.project.clientPartner = :partner AND self.status.isCompleted != true AND self.typeSelect = :typeSelect")
        .bind(
            "partner",
            partner != null ? partner.getIsContact() ? partner.getMainPartner() : partner : null)
        .bind("typeSelect", ProjectTaskRepository.TYPE_TICKET)
        .count();
  }

  @Override
  public Long getCloseTicket() {

    User user = getClientUser();
    Partner partner = user.getPartner();
    return projectTaskRepo
        .all()
        .filter(
            "self.project.clientPartner = :partner AND self.status.isCompleted = true AND self.typeSelect = :typeSelect")
        .bind(
            "partner",
            partner != null ? partner.getIsContact() ? partner.getMainPartner() : partner : null)
        .bind("typeSelect", ProjectTaskRepository.TYPE_TICKET)
        .count();
  }

  @Override
  public Long getDiscussionGroup() {

    return discussionGroupRepo
        .all()
        .filter(":partnerCategory MEMBER OF self.partnerCategorySet")
        .bind("partnerCategory", getUserPartnerCategory())
        .count();
  }

  @Override
  public Long getAnnouncement() {

    return announcementRepo
        .all()
        .filter(":partnerCategory MEMBER OF self.partnerCategorySet")
        .bind("partnerCategory", getUserPartnerCategory())
        .count();
  }

  @Override
  public Long getResouces() {

    return clientResourceRepo
        .all()
        .filter(
            ":partnerCategory MEMBER OF self.partnerCategorySet OR size(self.partnerCategorySet) = 0")
        .bind("partnerCategory", getUserPartnerCategory())
        .count();
  }

  @Override
  public Long getIdea() {
    return ideaRepo.all().filter("self.close = false").count();
  }

  @Override
  public Long getIdeaHistory() {
    return ideaRepo.all().filter("self.close = true").count();
  }

  @Override
  public Long getIdeaTag() {
    return ideaTagRepo.all().count();
  }

  protected PartnerCategory getUserPartnerCategory() {

    Partner partner = getClientUser().getPartner();
    PartnerCategory partnerCategory = null;
    if (partner != null) {
      partnerCategory = partner.getPartnerCategory();
      if (partner.getIsContact()) {
        partnerCategory =
            partner.getMainPartner() != null ? partner.getMainPartner().getPartnerCategory() : null;
      }
    }

    return partnerCategory;
  }
}
