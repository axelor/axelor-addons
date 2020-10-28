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

import com.axelor.apps.rossum.db.Schema;
import com.axelor.apps.rossum.db.SchemaField;
import com.axelor.apps.rossum.db.repo.SchemaFieldRepository;
import com.axelor.apps.rossum.db.repo.SchemaRepository;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class SchemaFieldServiceImpl implements SchemaFieldService {

  protected SchemaRepository schemaRepo;
  protected SchemaFieldRepository schemaFieldRepo;
  protected static final String SCHEMA_CHILDREN = "children";
  protected static final String CAN_EXPORT = "can_export";

  @Inject
  public SchemaFieldServiceImpl(
      SchemaRepository schemaRepo, SchemaFieldRepository schemaFieldRepo) {
    this.schemaRepo = schemaRepo;
    this.schemaFieldRepo = schemaFieldRepo;
  }

  @Override
  @Transactional(rollbackOn = {JSONException.class})
  public void updateSchemaContent(SchemaField schemaField) throws JSONException {
    String id = schemaField.getSchemaFieldId();
    Boolean canExport = schemaField.getCanExport();
    Schema schema = schemaField.getSchemaUrl();

    JSONObject resultObject = new JSONObject(schema.getSchemaResult());

    resultObject =
        findAndUpdateSchemaContent(
            id, canExport, resultObject, schemaField.getParentSchemaFieldId());
    schema.setSchemaResult(resultObject.toString());
    schemaRepo.save(schema);
  }

  @SuppressWarnings("unchecked")
  @Override
  public JSONObject findAndUpdateSchemaContent(
      String id, Boolean canExport, JSONObject resultObject, String parentSchemaFieldId)
      throws JSONException {

    JSONArray contentArray = resultObject.getJSONArray("content");
    Boolean updated = Boolean.FALSE;

    for (int i = 0; i < contentArray.size(); i++) {
      JSONObject contentObject = contentArray.getJSONObject(i);
      JSONArray childrenArray = contentObject.getJSONArray(SCHEMA_CHILDREN);

      for (int j = 0; j < childrenArray.size(); j++) {
        if (!Strings.isNullOrEmpty(parentSchemaFieldId)
            && childrenArray.getJSONObject(j).containsValue(parentSchemaFieldId)) {
          JSONArray subChildArray =
              childrenArray
                  .getJSONObject(j)
                  .getJSONObject(SCHEMA_CHILDREN)
                  .getJSONArray(SCHEMA_CHILDREN);

          for (int k = 0; k < subChildArray.size(); k++) {
            if (subChildArray.getJSONObject(k).containsValue(id)) {
              Boolean hidden = Boolean.TRUE.equals(canExport) ? Boolean.FALSE : Boolean.TRUE;

              subChildArray.getJSONObject(k).put(CAN_EXPORT, canExport);
              subChildArray.getJSONObject(k).put("hidden", hidden);
              updated = Boolean.TRUE;
              break;
            }
          }
        } else if (childrenArray.getJSONObject(j).containsValue(id)) {
          Boolean hidden = Boolean.TRUE.equals(canExport) ? Boolean.FALSE : Boolean.TRUE;

          childrenArray.getJSONObject(j).put(CAN_EXPORT, canExport);
          childrenArray.getJSONObject(j).put("hidden", hidden);
          updated = Boolean.TRUE;
          break;
        }
        if (Boolean.TRUE.equals(updated)) break;
      }
      if (Boolean.TRUE.equals(updated)) break;
    }

    if (Boolean.TRUE.equals(updated)) resultObject.replace("content", contentArray);
    return resultObject;
  }

  @Override
  @Transactional(rollbackOn = {JSONException.class})
  public SchemaField findAndUpdateSchemaField(
      Schema schema, JSONObject childObject, String parentSchemaFieldId) throws JSONException {
    String schemaFieldId = childObject.getString("id");
    SchemaField schemaField = schemaFieldRepo.findBySchemaAndId(schema, schemaFieldId);

    if (schemaField == null) {
      schemaField = new SchemaField();
      schemaField.setSchemaUrl(schema);
      schemaField.setSchemaFieldId(schemaFieldId);
      schemaField.setParentSchemaFieldId(parentSchemaFieldId);
    }

    schemaField.setLabel(childObject.getString("label"));
    Boolean canExport =
        (childObject.containsKey(CAN_EXPORT) && !childObject.getBoolean(CAN_EXPORT))
            ? Boolean.FALSE
            : Boolean.TRUE;
    schemaField.setCanExport(canExport);

    return schemaFieldRepo.save(schemaField);
  }
}
