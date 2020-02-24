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
package com.axelor.apps.zapier.exception;

public interface IExceptionMessage {

  /** Field Type */
  static final String INVALID_FIELD_TYPE = /*$$(*/
      "Invalid mapping type of '%s' field,Can not convert into '%s'" /*)*/;
  /** check user permission */
  static final String AUTHENTICATION_VALIDATION = /*$$(*/
      "You are not authorized for '%s' resource" /*)*/;

  static final String DATE_FORMAT_ERROR = /*$$(*/
      "Invalid Date format of '%s' field,Please change date format with(yyyy-MM-dd(2020-02-21)) OR add 'Formater' zap after trigger step" /*)*/;
  static final String DATE_TIME_FORMAT_ERROR = /*$$(*/
      "Invalid DateTime format of '%s' field,Please change DateTime format with (YYYY-MM-DDTHH:mm:ssZ (2006-01-22T10:00:05-0000)) OR add 'Formater' zap after trigger step" /*)*/;
  static final String NO_EXISTING_RECORD = /*$$(*/
      "Record not found for update with fields '%s' values '%s'" /*)*/;
  static final String SEARCH_FIELD_IS_RELATIONAL = /*$$(*/
      "Search field should not be relational field" /*)*/;
  public static final String INVALID_SEARCH_FIELD = /*$$(*/ "Invalid search field list '%s'" /*)*/;
}
