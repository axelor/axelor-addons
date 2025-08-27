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
package com.axelor.apps.prestashop.entities;

import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.w3c.dom.Element;

@XmlRootElement(name = "currency")
public class PrestashopCurrency extends PrestashopIdentifiableEntity {
  private String name;
  private String code;
  private BigDecimal conversionRate = BigDecimal.ONE;
  private boolean deleted = false;
  private boolean active = true;
  private int precision = 2;
  private PrestashopTranslatableString translatableNames;
  private PrestashopTranslatableString symbol;
  private PrestashopTranslatableString pattern;
  private String numericIsoCode;
  private boolean unofficial = false;
  private boolean modified = false;
  private List<Element> additionalProperties = new LinkedList<>();

  public boolean isUnofficial() {
    return unofficial;
  }

  public void setUnofficial(boolean unofficial) {
    this.unofficial = unofficial;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @XmlElement(name = "numeric_iso_code")
  public String getNumericIsoCode() {
    return numericIsoCode;
  }

  public void setNumericIsoCode(String numericIsoCode) {
    this.numericIsoCode = numericIsoCode;
  }

  public boolean isModified() {
    return modified;
  }

  public void setModified(boolean modified) {
    this.modified = modified;
  }

  public PrestashopTranslatableString getSymbol() {
    return symbol;
  }

  public void setSymbol(PrestashopTranslatableString symbol) {
    this.symbol = symbol;
  }

  public PrestashopTranslatableString getPattern() {
    return pattern;
  }

  public void setPattern(PrestashopTranslatableString pattern) {
    this.pattern = pattern;
  }

  @XmlElement(name = "iso_code")
  public String getCode() {
    return code;
  }

  public void setCode(String isoCode) {
    this.code = isoCode;
  }

  @XmlElement(name = "conversion_rate")
  public BigDecimal getConversionRate() {
    return conversionRate;
  }

  public void setConversionRate(BigDecimal conversionRate) {
    this.conversionRate = conversionRate;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public void setDeleted(boolean deleted) {
    this.deleted = deleted;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  @XmlElement(name = "names")
  public PrestashopTranslatableString getTranslatableNames() {
    return translatableNames;
  }

  public void setTranslatableNames(PrestashopTranslatableString translatableNames) {
    this.translatableNames = translatableNames;
  }

  @XmlAnyElement
  public List<Element> getAdditionalProperties() {
    return additionalProperties;
  }

  public void setAdditionalProperties(List<Element> additionalProperties) {
    this.additionalProperties = additionalProperties;
  }

  public int getPrecision() {
    return precision;
  }

  public void setPrecision(int precision) {
    this.precision = precision;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("name", name)
        .append("isoCode", code)
        .append("conversionRate", conversionRate)
        .append("deleted", deleted)
        .append("active", active)
        .toString();
  }
}
