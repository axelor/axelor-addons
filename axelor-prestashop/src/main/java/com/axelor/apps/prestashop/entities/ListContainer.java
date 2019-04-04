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
package com.axelor.apps.prestashop.entities;

import java.util.LinkedList;
import java.util.List;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.commons.lang3.builder.ToStringBuilder;

public abstract class ListContainer<T extends PrestashopContainerEntity>
    extends PrestashopContainerEntity {
  List<T> entities = new LinkedList<>();

  @XmlElementRef
  public List<T> getEntities() {
    return entities;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this).append("entities", entities).toString();
  }

  @XmlRootElement(name = "addresses")
  public static class AddressesContainer extends ListContainer<PrestashopAddress> {}

  @XmlRootElement(name = "carts")
  public static class CartsContainer extends ListContainer<PrestashopCart> {}

  @XmlRootElement(name = "categories")
  public static class ProductCategoriesContainer extends ListContainer<PrestashopCountry> {}

  @XmlRootElement(name = "countries")
  public static class CountriesContainer extends ListContainer<PrestashopCountry> {}

  @XmlRootElement(name = "currencies")
  public static class CurrenciesContainer extends ListContainer<PrestashopCurrency> {}

  @XmlRootElement(name = "customers")
  public static class CustomersContainer extends ListContainer<PrestashopCustomer> {}

  @XmlRootElement(name = "deliveries")
  public static class DeliveriesContainer extends ListContainer<PrestashopDelivery> {}

  @XmlRootElement(name = "languages")
  public static class LanguagesContainer extends ListContainer<PrestashopLanguage> {}

  @XmlRootElement(name = "order_details")
  public static class OrderRowsDetailsContainer extends ListContainer<PrestashopOrder> {}

  @XmlRootElement(name = "order_histories")
  public static class OrderHistoriesContainer extends ListContainer<PrestashopOrder> {}

  @XmlRootElement(name = "order_invoices")
  public static class OrderInvoicesContainer extends ListContainer<PrestashopOrderInvoice> {}

  @XmlRootElement(name = "order_payments")
  public static class OrderPaymentsContainer extends ListContainer<PrestashopOrderPayment> {}

  @XmlRootElement(name = "order_states")
  public static class OrderStatusesContainer extends ListContainer<PrestashopOrderStatus> {}

  @XmlRootElement(name = "orders")
  public static class OrdersContainer extends ListContainer<PrestashopOrder> {}

  @XmlRootElement(name = "products")
  public static class ProductsContainer extends ListContainer<PrestashopProduct> {}

  @XmlRootElement(name = "tax_rule_groups")
  public static class TaxRuleGroupsContainer extends ListContainer<PrestashopTaxRuleGroup> {}

  @XmlRootElement(name = "tax_rules")
  public static class TaxRulesContainer extends ListContainer<PrestashopTaxRule> {}

  @XmlRootElement(name = "taxes")
  public static class TaxesContainer extends ListContainer<PrestashopTax> {}
}
