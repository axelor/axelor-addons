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
package com.axelor.apps.customer.portal.web;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.client.portal.db.Card;
import com.axelor.apps.client.portal.db.repo.CardRepository;
import com.axelor.apps.customer.portal.exception.IExceptionMessage;
import com.axelor.apps.customer.portal.service.stripe.StripePaymentService;
import com.axelor.apps.customer.portal.translation.ITranslation;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.axelor.studio.db.AppCustomerPortal;
import com.axelor.studio.db.repo.AppCustomerPortalRepository;
import com.google.inject.Inject;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;

public class InvoiceController {

  @Inject StripePaymentService stripePaymentService;

  public void payInvoiceUsingStripe(ActionRequest request, ActionResponse response)
      throws StripeException, AxelorException, JAXBException, IOException,
          DatatypeConfigurationException {

    AppCustomerPortal app = Beans.get(AppCustomerPortalRepository.class).all().fetchOne();
    if (StringUtils.isBlank(app.getStripeSecretKey())) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.STRIPE_CONFIGIRATION_ERROR));
    }

    Context context = request.getContext();
    Invoice invoice = request.getContext().asType(Invoice.class);
    invoice = Beans.get(InvoiceRepository.class).find(invoice.getId());
    @SuppressWarnings("unchecked")
    Map<String, Object> cardObj = (Map<String, Object>) context.get("card");
    if (cardObj == null) {
      response.setError(I18n.get(IExceptionMessage.STRIPE_NO_CARD_SPECIFIED));
      return;
    }

    Card card = Beans.get(CardRepository.class).find(Long.parseLong(cardObj.get("id").toString()));
    BigDecimal payAmount = new BigDecimal(context.get("paymentAmount").toString());

    Customer customer =
        stripePaymentService.getOrCreateCustomer(Beans.get(UserService.class).getUserPartner());
    if (StringUtils.notBlank(card.getStripeCardId())) {
      Charge charge =
          stripePaymentService.checkout(invoice, customer, card.getStripeCardId(), payAmount);
      if (charge != null) {
        response.setCanClose(true);
        response.setNotify(I18n.get(ITranslation.PORTAL_STRIPE_PAYMENT_SUCCESS));
        return;
      }
    }

    response.setNotify(I18n.get(ITranslation.PORTAL_STRIPE_PAYMENT_FAIL));
  }
}
