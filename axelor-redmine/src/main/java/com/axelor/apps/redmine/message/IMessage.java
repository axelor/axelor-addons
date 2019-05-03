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

	/**
	 * Importing Issues
	 */
	static final String BATCH_ISSUE_IMPORT_1 = /*$$(*/ "Issues imported report :" /*)*/;
	static final String BATCH_ISSUE_IMPORT_2 = /*$$(*/ "Issue(s) imported" /*)*/;

	static final String BATCH_IMPORT_1 = /*$$(*/ "Import completed" /*)*/;
	static final String BATCH_EXPORT_1 = /*$$(*/ "Export completed" /*)*/;

	static final String REDMINE_AUTHENTICATION_1 = /*$$(*/ "URI and API Access Key should not be empty" /*)*/;
	static final String REDMINE_AUTHENTICATION_2 = /*$$(*/ "Please check your authentication details" /*)*/;
	static final String REDMINE_TRANSPORT = /*$$(*/ "Error connecting redmine server. Please check the configuration" /*)*/;
}
