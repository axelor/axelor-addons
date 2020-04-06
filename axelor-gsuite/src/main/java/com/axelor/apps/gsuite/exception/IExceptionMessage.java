/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
package com.axelor.apps.gsuite.exception;

public interface IExceptionMessage {

  public static final String AUTH_EXCEPTION_1 = /*$$(*/
      "Authorization expire for account: %s. Please authorize again" /*)*/;

  public static final String AUTH_EXCEPTION_2 = /*$$(*/
      "Error in authentication. Please authenticate again." /*)*/;

  public static final String CONTACT_UPDATE_EXCEPTION = /*$$(*/
      "Error in google contact update: " /*)*/;

  public static final String DRIVE_UPDATE_EXCEPTION = /*$$(*/
      "Error in updating google drive: " /*)*/;

  public static final String EVENT_UPDATE_EXCEPTION = /*$$(*/
      "Error in updating google event: " /*)*/;

  public static final String NO_CONFIGURATION_EXCEPTION = /*$$(*/
      "No google configuration found" /*)*/;

  public static final String CONFIGURATION_FILE_EXCEPTION = /*$$(*/
      "Configuration file not found" /*)*/;

  public static final String GMAIL_SYNC_FAILURE = /*$$(*/
      "Gmail sync failed.Please check tacebaack for detailed log." /*)*/;

  public static final String GMAIL_SYNC_SUCCESS = /*$$(*/
      "Mails from google synchronized successfully." /*)*/;

  public static final String GSUITE_BATCH_1 = /*$$(*/
      "Unknown action %s for the %s treatment" /*)*/;
}
