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
package com.axelor.apps.rossum.service.schema;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Schema;
import com.axelor.exception.AxelorException;
import java.io.IOException;
import wslite.json.JSONException;

public interface SchemaService {

  public void updateJsonData(Schema schema) throws JSONException;

  public void getSchemas(AppRossum appRossum) throws IOException, JSONException, AxelorException;

  public void updateSchema(AppRossum appRossum, Schema schema)
      throws IOException, JSONException, AxelorException;

  public void createSchema(Schema schema) throws IOException, JSONException, AxelorException;

  public void updateSchemaContent(Schema schema) throws JSONException;
}
