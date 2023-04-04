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

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.client.portal.db.PortalQuotation;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.message.db.EmailAccount;
import com.axelor.studio.db.AppCustomerPortal;
import javax.mail.MessagingException;

public interface PortalQuotationService {

  public PortalQuotation getRequestedPortalQuotation(SaleOrder saleOrder);

  public PortalQuotation createPortalQuotation(SaleOrder saleOrder)
      throws MessagingException, AxelorException;

  public Integer sendConfirmCode(PortalQuotation portalQuotation)
      throws MessagingException, AxelorException;

  public void confirmPortalQuotation(PortalQuotation portalQuotation, String name)
      throws MessagingException, AxelorException;

  public EmailAccount getEmailAccount(AppCustomerPortal app);
}
