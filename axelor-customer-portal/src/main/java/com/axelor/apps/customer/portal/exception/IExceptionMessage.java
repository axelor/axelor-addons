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
package com.axelor.apps.customer.portal.exception;

public interface IExceptionMessage {

  public static final String PORTAL_APP_INSTALL = /*$$(*/
      "Please install axelor customer portal app" /*)*/;

  public static final String PORTAL_QUOTATION_PRINT_ERROR = /*$$(*/
      "Please select the portal quotation(s) to print."; /*)*/

  public static final String STRIPE_CONFIGIRATION_ERROR = /*$$(*/
      "Stripe configuration is missing" /*)*/;

  public static final String STRIPE_CARD_NOT_FOUND = /*$$(*/
      "No Stripe card found with given id %s" /*)*/;

  public static final String CUSTOMER_MISSING = /*$$(*/
      "User doesn't have associated partner" /*)*/;

  public static final String ADDRESS_MISSING = /*$$(*/ "Address is missing" /*)*/;

  public static final String EMAIL_ADDRESS_MISSING = /*$$(*/
      "Partner Email Address is missing" /*)*/;

  public static final String STRIPE_CUSTOMER_NAME_MISSING = /*$$(*/
      "Customer name is missing on stripe" /*)*/;

  public static final String STRIPE_CUSTOMER_ADDR_MISSING = /*$$(*/
      "Customer does not have any address or it is not properly configured on stripe" /*)*/;

  public static final String STRIPE_CUSTOMER_DEFAULT_ADDR_MISSING = /*$$(*/
      "Add default address" /*)*/;

  public static final String STRIPE_CUSTOMER_DELETED = /*$$(*/
      "Related stripe customer is deleted" /*)*/;

  public static final String PORTAL_CARD_EXIST = /*$$(*/ "A card already exists." /*)*/;

  public static final String STRIPE_CARD_ERROR = /*$$(*/
      "Payment with selected card is declined please review card" /*)*/;

  public static final String STRIPE_CARD_AUTH_REQUIRED_ERROR = /*$$(*/
      "Your card was declined. This transaction requires authentication" /*)*/;

  public static final String STRIPE_DEFAULT_CARD = /*$$(*/ "There is already a default card" /*)*/;

  public static final String STRIPE_NO_DEFAULT_CARD = /*$$(*/ "No Default card specified" /*)*/;

  public static final String STRIPE_NO_CARD_SPECIFIED = /*$$(*/ "Select the card for payment" /*)*/;
}
