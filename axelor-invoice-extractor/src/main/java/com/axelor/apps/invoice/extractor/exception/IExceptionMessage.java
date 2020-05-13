/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.invoice.extractor.exception;

public interface IExceptionMessage {

  public static final String PDF_OR_TEMPLATE_ERROR = /*$$(*/
      "Please add pdf or template for extract data."; /*)*/
  public static final String TEMPLATE_ERROR = /*$$(*/ "ERROR:invoice2data.main:No template."; /*)*/
  public static final String ROOT_ERROR_CONTAIN = /*$$(*/
      "Unable to match all required fields"; /*)*/
  public static final String ROOT_ERROR_MESSAGE = /*$$(*/
      "ERROR:invoice2data:Unable to match all required fields.['date', 'amount', 'invoice_number', 'issuer']"; /*)*/
  public static final String PDF_TEMPLATE_ERROR = /*$$(*/ "Please add valid template."; /*)*/
  public static final String JSON_ERROR = /*$$(*/ "Json file not found."; /*)*/
  public static final String VALIDATION_ERROR = /*$$(*/ "Please add valid template or pdf."; /*)*/
  public static final String PDF_FILE_UPLOAD = /*$$(*/ "Please upload pdf file."; /*)*/
  public static final String YML_FILE_UPLOAD = /*$$(*/ "Please upload yml file."; /*)*/
}
