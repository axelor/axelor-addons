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
package com.axelor.apps.rossum.exception;

public interface IExceptionMessage {

  public static final String INVALID_USERNAME_OR_PASSWORD = /*$$(*/
      "Invalid Rossum Username or password. Please enter correct Rossum username and password." /*)*/;

  public static final String ROSSUM_FILE_ERROR = /*$$(*/
      "Please Upload PDF or PNG or JPEG file only!" /*)*/;

  public static final String INVOICE_SUBMISSION_ERROR = /*$$(*/
      "Could not submit invoice: %s" /*)*/;

  public static final String DOCUMENT_PROCESS_ERROR = /*$$(*/
      "Could not process document: %s" /*)*/;

  public static final String TIMEOUT_ERROR = /*$$(*/
      "Time out after %d seconds. Please check 'Traceback' for details" /*)*/;

  public static final String INVOICE_GENERATION_ERROR = /*$$(*/
      "Error generating invoice. Please check 'Traceback' for details." /*)*/;

  public static final String INVOICE_ID_EXIST_ERROR = /*$$(*/ "Invoice Id %s already exists!" /*)*/;

  public static final String REQUIRED_FIELD_MISSING = /*$$(*/
      "Required Fields are missing for %s Model. Please check 'Traceback' for details" /*)*/;

  public static final String ANNOTATION_NOT_FOUND = /*$$(*/ "Annotation %s not found!" /*)*/;

  public static final String QUEUE_NOT_FOUND = /*$$(*/ "Queue not found!" /*)*/;
}
