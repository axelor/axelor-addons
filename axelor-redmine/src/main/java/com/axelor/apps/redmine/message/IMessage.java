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
package com.axelor.apps.redmine.message;

public interface IMessage {

  static final String BATCH_SYNC_SUCCESS = /*$$(*/ "Redmine sync completed" /*)*/;

  static final String REDMINE_AUTHENTICATION_1 = /*$$(*/
      "URI and API Access Key should not be empty" /*)*/;

  static final String REDMINE_AUTHENTICATION_2 = /*$$(*/
      "Please check your authentication details" /*)*/;

  static final String REDMINE_TRANSPORT = /*$$(*/
      "Error connecting redmine server. Please check the configuration" /*)*/;

  static final String REDMINE_SYNC_ERROR_ABS_FIELD_NOT_EXIST = /*$$(*/
      "No such field found on ABS" /*)*/;

  static final String REDMINE_SYNC_ERROR_REDMINE_FIELD_NOT_EXIST = /*$$(*/
      "No such field found on Redmine" /*)*/;

  static final String REDMINE_SYNC_ERROR_RECORD_NOT_FOUND = /*$$(*/
      "No record found with linked Redmine Id" /*)*/;

  static final String REDMINE_SYNC_ERROR_ASSIGNEE_IS_NOT_VALID = /*$$(*/
      "Assignee have no membership for associated project" /*)*/;

  static final String REDMINE_SYNC_ERROR_ISSUE_PROJECT_NOT_FOUND = /*$$(*/
      "Associated project is not synced or not found" /*)*/;

  static final String REDMINE_SYNC_ERROR_REQUIRED_FIEDS_BINDINGS_MISSING = /*$$(*/
      "Required field bindings are missing for sync" /*)*/;

  static final String REDMINE_SYNC_ERROR_DYNAMIC_FIELDS_SYNC_LIST_NOT_FOUND = /*$$(*/
      "Dynamic fields sync list not configured" /*)*/;
}
