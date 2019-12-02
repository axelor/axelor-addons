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
package com.axelor.apps.sendinblue.translation;

public interface ITranslation {

  public static final String CONFIGURATION_ERROR = /*$$(*/
      "No API key specify in configuration"; /*)*/
  public static final String AUTHENTICATE_ERROR = /*$$(*/ "Authentication Failed"; /*)*/
  public static final String AUTHENTICATE_MESSAGE = /*$$(*/ "Authenticate Successfully"; /*)*/

  public static final String IMPORT_CONFIGURATION_ERROR = /*$$(*/
      "No Import configuration found"; /*)*/
  public static final String EXPORT_CONFIGURATION_ERROR = /*$$(*/
      "No Export configuration found"; /*)*/

  public static final String EXPORT_FIELD_ERROR = /*$$(*/
      "Select Fields of Partner or Lead to export"; /*)*/

  public static final String CAMPAIGN_SENDER_ERROR = /*$$(*/
      "Campaign Sender cannot be blank"; /*)*/
  public static final String CAMPAIGN_TEMPLATE_ERROR = /*$$(*/
      "Campaign Template content cannot be blank"; /*)*/
  public static final String CAMPAIGN_RECIPIENT_ERROR = /*$$(*/
      "Campaign Recipients cannot be blank"; /*)*/

  public static final String TEMPLATE_SENDER_ERROR = /*$$(*/
      "Template Sender cannot be blank"; /*)*/

  public static final String IS_LEAD_LABEL = /*$$(*/ "isLead"; /*)*/
  public static final String NAME_PREFIX_LABEL = /*$$(*/ "Name"; /*)*/
  public static final String AGGREGATE_STATISTICS_MESSAGE = /*$$(*/
      "Aggregate Statistics deleted successfully"; /*)*/
  public static final String AGGREGATE_STATISTICS_ERROR = /*$$(*/
      "No Aggregate Statistics exist"; /*)*/
}
