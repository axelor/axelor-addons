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
package com.axelor.apps.customer.portal.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.base.db.repo.AddressBaseRepository;
import com.axelor.apps.businessproduction.service.SaleOrderWorkflowServiceBusinessProductionImpl;
import com.axelor.apps.businessproject.db.repo.SaleOrderProjectRepository;
import com.axelor.apps.client.portal.db.repo.CardRepository;
import com.axelor.apps.client.portal.db.repo.ClientResourceRepository;
import com.axelor.apps.client.portal.db.repo.DiscussionGroupRepository;
import com.axelor.apps.client.portal.db.repo.DiscussionPostRepository;
import com.axelor.apps.client.portal.db.repo.GeneralAnnouncementRepository;
import com.axelor.apps.client.portal.db.repo.IdeaRepository;
import com.axelor.apps.client.portal.db.repo.PortalQuotationRepository;
import com.axelor.apps.customer.portal.db.repo.AddressPortalRepository;
import com.axelor.apps.customer.portal.db.repo.AppCustomerPortalPortalRepository;
import com.axelor.apps.customer.portal.db.repo.CardManagementRepository;
import com.axelor.apps.customer.portal.db.repo.ClientResourceManagementRepository;
import com.axelor.apps.customer.portal.db.repo.DiscussionGroupManagementRepository;
import com.axelor.apps.customer.portal.db.repo.DiscussionPostManagementRepository;
import com.axelor.apps.customer.portal.db.repo.GeneralAnnounceManagementRepository;
import com.axelor.apps.customer.portal.db.repo.IdeaManagementRepository;
import com.axelor.apps.customer.portal.db.repo.PartnerPortalRepository;
import com.axelor.apps.customer.portal.db.repo.PortalQuotationManagementRepository;
import com.axelor.apps.customer.portal.db.repo.ProductPortalRepository;
import com.axelor.apps.customer.portal.db.repo.SaleOrderPortalRepository;
import com.axelor.apps.customer.portal.service.AddressPortalService;
import com.axelor.apps.customer.portal.service.AddressPortalServiceImpl;
import com.axelor.apps.customer.portal.service.CardService;
import com.axelor.apps.customer.portal.service.CardServiceImpl;
import com.axelor.apps.customer.portal.service.ClientViewPortalService;
import com.axelor.apps.customer.portal.service.ClientViewPortalServiceImpl;
import com.axelor.apps.customer.portal.service.DiscussionGroupService;
import com.axelor.apps.customer.portal.service.DiscussionGroupServiceImpl;
import com.axelor.apps.customer.portal.service.DiscussionPostService;
import com.axelor.apps.customer.portal.service.DiscussionPostServiceImpl;
import com.axelor.apps.customer.portal.service.MetaFilesPortal;
import com.axelor.apps.customer.portal.service.PartnerPortalService;
import com.axelor.apps.customer.portal.service.PartnerPortalServiceImpl;
import com.axelor.apps.customer.portal.service.PortalQuotationService;
import com.axelor.apps.customer.portal.service.PortalQuotationServiceImpl;
import com.axelor.apps.customer.portal.service.ProductPortalService;
import com.axelor.apps.customer.portal.service.ProductPortalServiceImpl;
import com.axelor.apps.customer.portal.service.SaleOrderPortalService;
import com.axelor.apps.customer.portal.service.SaleOrderPortalServiceImpl;
import com.axelor.apps.customer.portal.service.SaleOrderWorkflowServicePortalImpl;
import com.axelor.apps.customer.portal.service.paybox.PayboxService;
import com.axelor.apps.customer.portal.service.paybox.PayboxServiceImpl;
import com.axelor.apps.customer.portal.service.paypal.PaypalService;
import com.axelor.apps.customer.portal.service.paypal.PaypalServiceImpl;
import com.axelor.apps.customer.portal.service.response.PortalRestResponse;
import com.axelor.apps.customer.portal.service.stripe.StripePaymentService;
import com.axelor.apps.customer.portal.service.stripe.StripePaymentServiceImpl;
import com.axelor.apps.customer.portal.web.MailPortalController;
import com.axelor.apps.customer.portal.web.interceptor.PortalResponseInterceptor;
import com.axelor.apps.hr.db.repo.PartnerHRRepository;
import com.axelor.apps.portal.service.ClientViewServiceImpl;
import com.axelor.apps.production.db.repo.ProductProductionRepository;
import com.axelor.mail.web.MailController;
import com.axelor.meta.MetaFiles;
import com.axelor.studio.db.repo.AppCustomerPortalRepository;
import com.google.inject.matcher.Matchers;

public class PortalModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(AddressPortalService.class).to(AddressPortalServiceImpl.class);
    bind(CardService.class).to(CardServiceImpl.class);
    bind(DiscussionGroupService.class).to(DiscussionGroupServiceImpl.class);
    bind(MetaFiles.class).to(MetaFilesPortal.class);
    bind(PartnerPortalService.class).to(PartnerPortalServiceImpl.class);
    bind(ClientViewPortalService.class).to(ClientViewPortalServiceImpl.class);
    bind(ClientViewServiceImpl.class).to(ClientViewPortalServiceImpl.class);
    bind(PortalQuotationService.class).to(PortalQuotationServiceImpl.class);
    bind(ProductPortalService.class).to(ProductPortalServiceImpl.class);
    bind(SaleOrderPortalService.class).to(SaleOrderPortalServiceImpl.class);
    bind(SaleOrderWorkflowServiceBusinessProductionImpl.class)
        .to(SaleOrderWorkflowServicePortalImpl.class);
    bind(PayboxService.class).to(PayboxServiceImpl.class);
    bind(PaypalService.class).to(PaypalServiceImpl.class);
    bind(StripePaymentService.class).to(StripePaymentServiceImpl.class);
    bind(DiscussionPostService.class).to(DiscussionPostServiceImpl.class);

    // intercept all response methods
    bindInterceptor(
        Matchers.any(),
        Matchers.returns(Matchers.subclassesOf(PortalRestResponse.class)),
        new PortalResponseInterceptor());

    bind(CardRepository.class).to(CardManagementRepository.class);
    bind(ClientResourceRepository.class).to(ClientResourceManagementRepository.class);
    bind(DiscussionGroupRepository.class).to(DiscussionGroupManagementRepository.class);
    bind(DiscussionPostRepository.class).to(DiscussionPostManagementRepository.class);
    bind(GeneralAnnouncementRepository.class).to(GeneralAnnounceManagementRepository.class);
    bind(IdeaRepository.class).to(IdeaManagementRepository.class);
    bind(PortalQuotationRepository.class).to(PortalQuotationManagementRepository.class);
    bind(ProductProductionRepository.class).to(ProductPortalRepository.class);
    bind(SaleOrderProjectRepository.class).to(SaleOrderPortalRepository.class);
    bind(AddressBaseRepository.class).to(AddressPortalRepository.class);
    bind(PartnerHRRepository.class).to(PartnerPortalRepository.class);
    bind(AppCustomerPortalRepository.class).to(AppCustomerPortalPortalRepository.class);

    bind(MailController.class).to(MailPortalController.class);
  }
}
