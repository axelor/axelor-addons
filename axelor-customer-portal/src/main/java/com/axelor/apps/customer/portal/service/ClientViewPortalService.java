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

import com.axelor.meta.CallMethod;

public interface ClientViewPortalService {

  /* Quotation */
  @CallMethod
  public Long getOpenQuotation();

  @CallMethod
  public Long getQuotationSaleOrder();

  @CallMethod
  public Long getQuotationHistory();

  @CallMethod
  public Long getAllQuotation();

  /* Invoice */
  @CallMethod
  public Long getToPayInvoice();

  @CallMethod
  public Long getOldInvoice();

  @CallMethod
  public Long getRefundInvoice();

  /* Ticket */
  @CallMethod
  public Long getMyTicket();

  @CallMethod
  public Long getProviderTicket();

  @CallMethod
  public Long getOpenTicket();

  @CallMethod
  public Long getCloseTicket();

  /* Client Portal*/
  @CallMethod
  public Long getDiscussionGroup();

  @CallMethod
  public Long getAnnouncement();

  @CallMethod
  public Long getResouces();

  @CallMethod
  public Long getIdea();

  @CallMethod
  public Long getIdeaHistory();

  @CallMethod
  public Long getIdeaTag();
}
