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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "tax_rule")
public class PrestashopTaxRule extends PrestashopIdentifiableEntity {
  private Integer taxRuleGroupId;
  private Integer stateId;
  private Integer countryId;
  private Integer zipcodeFrom;
  private Integer zipcodeTo;
  private Integer taxId;
  private Integer behavior;
  private String description;

  @XmlElement(name = "id_tax_rules_group")
  public Integer getTaxRuleGroupId() {
    return taxRuleGroupId;
  }

  public void setTaxRuleGroupId(Integer taxRuleGroupId) {
    this.taxRuleGroupId = taxRuleGroupId;
  }

  @XmlElement(name = "id_state")
  public Integer getStateId() {
    return stateId;
  }

  public void setStateId(Integer stateId) {
    this.stateId = stateId;
  }

  @XmlElement(name = "id_country")
  public Integer getCountryId() {
    return countryId;
  }

  public void setCountryId(Integer countryId) {
    this.countryId = countryId;
  }

  @XmlElement(name = "zipcode_from")
  public Integer getZipcodeFrom() {
    return zipcodeFrom;
  }

  public void setZipcodeFrom(Integer zipcodeFrom) {
    this.zipcodeFrom = zipcodeFrom;
  }

  @XmlElement(name = "zipcode_to")
  public Integer getZipcodeTo() {
    return zipcodeTo;
  }

  public void setZipcodeTo(Integer zipcodeTo) {
    this.zipcodeTo = zipcodeTo;
  }

  @XmlElement(name = "id_tax")
  public Integer getTaxId() {
    return taxId;
  }

  public void setTaxId(Integer taxId) {
    this.taxId = taxId;
  }

  public Integer getBehavior() {
    return behavior;
  }

  public void setBehavior(Integer behavior) {
    this.behavior = behavior;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
