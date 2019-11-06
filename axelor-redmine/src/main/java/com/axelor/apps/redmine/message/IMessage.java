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

  static final String BATCH_REDMINE_IMPORT_SUCCESS = /*$$(*/ "Redmine import completed" /*)*/;

  static final String REDMINE_AUTHENTICATION_1 = /*$$(*/
      "URI and API Access Key should not be empty" /*)*/;

  static final String REDMINE_AUTHENTICATION_2 = /*$$(*/
      "Please check your authentication details" /*)*/;

  static final String REDMINE_TRANSPORT = /*$$(*/
      "Error connecting redmine server. Please check the configuration" /*)*/;

  static final String REDMINE_IMPORT_TEAMTASK_ERROR = /*$$(*/ "TeamTask" /*)*/;

  static final String REDMINE_IMPORT_PROJECT_ERROR = /*$$(*/ "Project" /*)*/;

  static final String REDMINE_IMPORT_TIMESHEET_LINE_ERROR = /*$$(*/ "TimesheetLine" /*)*/;

  static final String REDMINE_IMPORT_PRODUCT_NOT_FOUND = /*$$(*/
      "Entity not imported, no product found in OS" /*)*/;

  static final String REDMINE_IMPORT_PARENT_TASK_NOT_FOUND = /*$$(*/
      "Entity not imported, no parent task found in OS" /*)*/;

  static final String REDMINE_IMPORT_PROJECT_NOT_FOUND = /*$$(*/
      "Entity not imported, no project found in OS" /*)*/;

  static final String REDMINE_IMPORT_PROJECT_CATEGORY_NOT_FOUND = /*$$(*/
      "Entity not imported, no project category found in OS" /*)*/;

  static final String REDMINE_IMPORT_TEAM_TASK_NOT_FOUND = /*$$(*/
      "Entity not imported, no teamtask found in OS" /*)*/;

  static final String REDMINE_IMPORT_WITH_DEFAULT_STATUS = /*$$(*/
      "Status not found in OS, imported with default status" /*)*/;

  static final String REDMINE_IMPORT_WITH_DEFAULT_PRIORITY = /*$$(*/
      "Priority not found in OS, imported with default priority" /*)*/;

  static final String REDMINE_IMPORT_CLIENT_PARTNER_NOT_FOUND = /*$$(*/
      "Client partner not found in OS" /*)*/;

  static final String REDMINE_IMPORT_INVOICING_TYPE_NOT_FOUND = /*$$(*/
      "Invoicing Type not found in OS" /*)*/;

  static final String REDMINE_IMPORT_PARENT_PROJECT_NOT_FOUND = /*$$(*/
      "Parent project not found on OS while it's set in redmine" /*)*/;

  static final String REDMINE_IMPORT_USER_NOT_FOUND = /*$$(*/
      "Entity not imported, no user found in OS" /*)*/;

  static final String REDMINE_IMPORT_CUSTOM_FIELD_CONFIG_VALIDATION_ERROR = /*$$(*/
      "Custom field %s not found for %s, please check the app configurations" /*)*/;
}
