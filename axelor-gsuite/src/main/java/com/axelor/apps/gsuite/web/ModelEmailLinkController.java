/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.gsuite.web;

import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Singleton;
import java.util.Map;

@Singleton
public class ModelEmailLinkController {

  @SuppressWarnings("unchecked")
  public void fillTargetField(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();
    Map<String, Object> tagetMetaFieldMap = (Map<String, Object>) context.get("targetMetaField");
    MetaField targetMetaField =
        Beans.get(MetaFieldRepository.class)
            .find(new Long((int) tagetMetaFieldMap.getOrDefault("id", 0)));

    if (targetMetaField != null) {
      String metaField = "";
      if (context.get("metaField") == null) {
        metaField = targetMetaField.getName();
      } else {
        metaField = context.get("metaField").toString();
        metaField += "." + targetMetaField.getName();
      }
      response.setValue("metaField", metaField);

      if (targetMetaField.getRelationship() != null) {
        response.setValue("currentDomain", targetMetaField.getTypeName());
        response.setValue("$targetMetaField", null);
      } else {
        response.setAttr("$targetMetaField", "readonly", true);
        response.setAttr("validateFieldSelectionBtn", "readonly", true);
        response.setAttr("$viewerMessage", "hidden", false);
        response.setAttr("$isValidate", "value", true);
        response.setAttr("metaModel", "readonly", true);
      }
    }
  }
}
