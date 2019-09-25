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
package com.axelor.apps.redmine.exports.service;

import com.axelor.apps.redmine.db.DynamicFieldsSync;
import com.axelor.apps.redmine.db.OpenSuitRedmineSync;
import com.axelor.apps.redmine.db.ValuesMapping;
import com.axelor.apps.redmine.db.repo.DynamicFieldsSyncRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.tool.ObjectTool;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.CustomFieldDefinition;
import com.taskadapter.redmineapi.bean.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RedmineDynamicExportServiceImpl implements RedmineDynamicExportService {

  @Inject private MetaFieldRepository metaFieldRepo;

  private Map<String, Object> osMap;
  private Map<String, Object> redmineMap;
  private Map<String, Object> redmineCustomFieldsMap;
  private Object osObj;

  private String fieldNameInAbs;
  private String fieldNameInRedmine;
  private String typeSelectInAbs;
  private String relatedFieldInAbsToRedmineSelect;
  private List<ValuesMapping> valuesMappingList;

  private RedmineManager redmineManager;

  private List<Object[]> errorObjList;

  public static final String RELATIONSHIP_M2O = "ManyToOne";
  public static final String MAPPING_DIRECTION = "ostoredmine|both";
  public static final String CUSTOM_FIELD_FORMATS_SIMPLE = "string|bool|date|float|int|text|link";
  public static final String CUSTOM_FIELD_FORMAT_USER = "user";
  public static final String CUSTOM_FIELD_FORMAT_VERSION = "version";
  public static final String DYNAMIC_EXPORT = "Export";

  @Override
  public Map<String, Object> createRedmineDynamic(
      OpenSuitRedmineSync openSuiteRedmineSync,
      Map<String, Object> osMap,
      Map<String, Object> redmineMap,
      Map<String, Object> redmineCustomFieldsMap,
      MetaModel metaModel,
      Object osObj,
      RedmineManager redmineManager,
      List<Object[]> errorObjList) {

    this.osMap = osMap;
    this.redmineMap = redmineMap;
    this.redmineCustomFieldsMap = redmineCustomFieldsMap;
    this.osObj = osObj;
    this.redmineManager = redmineManager;
    this.errorObjList = errorObjList;

    List<DynamicFieldsSync> dynamicFieldsSyncList = openSuiteRedmineSync.getDynamicFieldsSyncList();

    if (dynamicFieldsSyncList != null && !dynamicFieldsSyncList.isEmpty()) {

      for (DynamicFieldsSync dynamicFieldsSync : dynamicFieldsSyncList) {

        this.fieldNameInAbs = dynamicFieldsSync.getFieldNameInAbs();
        this.fieldNameInRedmine = dynamicFieldsSync.getFieldNameInRedmine();
        this.typeSelectInAbs = dynamicFieldsSync.getTypeSelectInAbs();
        this.relatedFieldInAbsToRedmineSelect =
            dynamicFieldsSync.getRelatedFieldInAbsToRedmineSelect();
        this.valuesMappingList = dynamicFieldsSync.getValuesMappingList();

        String mappingDirection = dynamicFieldsSync.getFieldMappingDirection();

        if (fieldNameInAbs != null
            && fieldNameInRedmine != null
            && mappingDirection != null
            && mappingDirection.matches(MAPPING_DIRECTION)) {

          // No such field found in ABS

          if (!osMap.containsKey(fieldNameInAbs)) {
            setErrorLog(
                osObj.getClass().getSimpleName(),
                fieldNameInAbs,
                fieldNameInRedmine,
                I18n.get(IMessage.REDMINE_SYNC_ERROR_ABS_FIELD_NOT_EXIST));
            continue;
          }

          // No such field found in Redmine

          if (!dynamicFieldsSync.getIsCustomRedmineField()
              && !redmineMap.containsKey(fieldNameInRedmine)) {
            setErrorLog(
                osObj.getClass().getSimpleName(),
                fieldNameInAbs,
                fieldNameInRedmine,
                I18n.get(IMessage.REDMINE_SYNC_ERROR_REDMINE_FIELD_NOT_EXIST));
            continue;
          }

          MetaField metaField = metaFieldRepo.findByModel(fieldNameInAbs, metaModel);

          // CUSTOM FIELD BINDING

          if (dynamicFieldsSync.getIsCustomRedmineField()) {
            customFieldBinding(dynamicFieldsSync);
            continue;
          }

          // SIMPLE FIELD BINDING

          if (metaField.getRelationship() == null) {
            simpleFieldBinding(dynamicFieldsSync);
            continue;
          }

          // M2O FIELD BINDING

          if (metaField.getRelationship().equals(RELATIONSHIP_M2O)) {
            m2oFieldBinding(dynamicFieldsSync);
            continue;
          }
        }
      }
    }

    return redmineMap;
  }

  public void customFieldBinding(DynamicFieldsSync dynamicFieldsSync) {

    if (dynamicFieldsSync.getIsSelectField()) {

      // CUSTOM FIELD BINDING : ABS M2O -> REDMINE SELECT

      if (typeSelectInAbs.equals(DynamicFieldsSyncRepository.TYPE_IN_ABS_M2O)
          && relatedFieldInAbsToRedmineSelect != null) {
        Object osFieldObj = ObjectTool.getObject(osObj, fieldNameInAbs);

        redmineCustomFieldsMap.put(
            fieldNameInRedmine, ObjectTool.getObject(osFieldObj, relatedFieldInAbsToRedmineSelect));
      }

      // CUSTOM FIELD BINDING : ABS SELECT -> REDMINE SELECT

      else if (typeSelectInAbs.equals(DynamicFieldsSyncRepository.TYPE_IN_ABS_SELECT)
          && valuesMappingList != null
          && !valuesMappingList.isEmpty()) {

        String valueInRedmine = null;

        for (ValuesMapping valuesMapping : valuesMappingList) {

          if (valuesMapping.getSelectValueInAbs() != null
              && osMap.get(fieldNameInAbs) != null
              && valuesMapping.getSelectValueInAbs().equals(osMap.get(fieldNameInAbs).toString())) {
            valueInRedmine = valuesMapping.getSelectValueInRedmine();
          }
        }

        redmineCustomFieldsMap.put(fieldNameInRedmine, valueInRedmine);
      }
    }

    // OTHER TYPES OF CUSTOM FIELDS

    else {

      try {
        CustomFieldDefinition customFieldDefinition =
            redmineManager
                .getCustomFieldManager()
                .getCustomFieldDefinitions()
                .stream()
                .filter(field -> field.getName().equals(fieldNameInRedmine))
                .findAny()
                .orElse(null);

        if (customFieldDefinition != null) {

          // IF CUSTOM FIELD TYPE IS STRING | BOOL | DATE | FLOAT | INT | TEXT | LINK

          String customFieldFormat = customFieldDefinition.getFieldFormat();

          if (customFieldFormat.matches(CUSTOM_FIELD_FORMATS_SIMPLE)) {
            Object value = osMap.get(fieldNameInAbs);

            if (customFieldFormat.equals("bool") && value != null) {
              value = value.equals(Boolean.TRUE) ? "1" : "0";
            }
            redmineCustomFieldsMap.put(fieldNameInRedmine, value);
          }

          // IF CUSTOM FIELD TYPE IS USER | VERSION

          else if (customFieldFormat.equals(CUSTOM_FIELD_FORMAT_VERSION)) {
            Object osFieldObj = ObjectTool.getObject(osObj, fieldNameInAbs);

            redmineCustomFieldsMap.put(
                fieldNameInRedmine,
                osFieldObj != null ? (int) ObjectTool.getObject(osFieldObj, "redmineId") : null);
          } else if (customFieldFormat.equals(CUSTOM_FIELD_FORMAT_USER)) {
            Object osFieldObj = ObjectTool.getObject(osObj, fieldNameInAbs);

            if (osFieldObj != null) {
              Object partner = ObjectTool.getObject(osFieldObj, "partner");

              if (partner != null) {
                Object emailAddress = ObjectTool.getObject(partner, "emailAddress");

                if (emailAddress != null) {
                  Map<String, String> params = new HashMap<String, String>();
                  params.put("name", (String) ObjectTool.getObject(emailAddress, "address"));
                  List<User> redmineUserList =
                      redmineManager.getUserManager().getUsers(params).getResults();

                  redmineCustomFieldsMap.put(
                      fieldNameInRedmine,
                      redmineUserList != null && !redmineUserList.isEmpty()
                          ? redmineUserList.get(0).getId()
                          : null);
                }
              }
            }
          }
        }
      } catch (RedmineException e) {
        TraceBackService.trace(e);
      }
    }
  }

  public void m2oFieldBinding(DynamicFieldsSync dynamicFieldsSync) {

    Object osFieldObj = ObjectTool.getObject(osObj, fieldNameInAbs);

    if (fieldNameInRedmine.endsWith("Id")
        && osFieldObj != null
        && ObjectTool.getObject(osFieldObj, "redmineId") != null) {
      redmineMap.put(fieldNameInRedmine, (int) ObjectTool.getObject(osFieldObj, "redmineId"));
    } else if ((fieldNameInRedmine.endsWith("Name") || fieldNameInRedmine.endsWith("Text"))
        && osFieldObj != null
        && ObjectTool.getObject(osFieldObj, "name") != null) {
      redmineMap.put(fieldNameInRedmine, (String) ObjectTool.getObject(osFieldObj, "name"));
    }
  }

  public void simpleFieldBinding(DynamicFieldsSync dynamicFieldsSync) {

    if (redmineMap.containsKey(fieldNameInRedmine)) {

      if (!dynamicFieldsSync.getIsSelectField()) {
        redmineMap.put(fieldNameInRedmine, osMap.get(fieldNameInAbs));
      } else if (valuesMappingList != null && !valuesMappingList.isEmpty()) {
        ValuesMapping valueMapping =
            valuesMappingList
                .stream()
                .filter(
                    value ->
                        value.getSelectValueInAbs() != null
                            && value.getSelectValueInAbs().equals(osMap.get(fieldNameInAbs)))
                .findAny()
                .orElse(null);

        redmineMap.put(
            fieldNameInRedmine,
            valueMapping != null ? valueMapping.getSelectValueInRedmine() : null);
      }
    }
  }

  public void setErrorLog(
      String object, String fieldNameInAbs, String fieldNameInRedmine, String message) {

    errorObjList.add(
        new Object[] {object, DYNAMIC_EXPORT, null, fieldNameInAbs, fieldNameInRedmine, message});
  }
}
