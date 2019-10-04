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
package com.axelor.apps.redmine.imports.service;

import com.axelor.apps.businesssupport.db.repo.ProjectVersionRepository;
import com.axelor.apps.redmine.db.DynamicFieldsSync;
import com.axelor.apps.redmine.db.ValuesMapping;
import com.axelor.apps.redmine.db.repo.DynamicFieldsSyncRepository;
import com.axelor.apps.tool.ObjectTool;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.CustomFieldDefinition;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RedmineDynamicImportServiceImpl implements RedmineDynamicImportService {

  protected MetaFieldRepository metaFieldRepo;
  protected UserRepository userRepo;
  protected ProjectVersionRepository projectVersionRepo;

  @Inject
  public RedmineDynamicImportServiceImpl(
      MetaFieldRepository metaFieldRepo,
      UserRepository userRepo,
      ProjectVersionRepository projectVersionRepo) {

    this.metaFieldRepo = metaFieldRepo;
    this.userRepo = userRepo;
    this.projectVersionRepo = projectVersionRepo;
  }

  public static final String RELATIONSHIP_M2M = "ManyToMany";
  public static final String RELATIONSHIP_M2O = "ManyToOne";
  public static final String MAPPING_DIRECTION = "redminetoos|both";
  public static final String CUSTOM_FIELD_FORMATS_SIMPLE = "string|bool|date|float|int|text|link";
  public static final String CUSTOM_FIELD_FORMAT_USER = "user";
  public static final String CUSTOM_FIELD_FORMAT_VERSION = "version";

  private Map<String, Object> osMap;
  private Map<String, Object> redmineMap;
  private Map<String, Object> redmineCustomFieldsMap;
  private Object redmineObj;
  private RedmineManager redmineManager;

  private String fieldNameInAbs;
  private String fieldNameInRedmine;
  private String typeSelectInAbs;
  private String relatedFieldInAbsToRedmineSelect;
  private String defaultAbsValue;
  private List<ValuesMapping> valuesMappingList;

  @Override
  public Map<String, Object> createOpenSuiteDynamic(
      List<DynamicFieldsSync> dynamicFieldsSyncList,
      Map<String, Object> osMap,
      Map<String, Object> redmineMap,
      Map<String, Object> redmineCustomFieldsMap,
      MetaModel metaModel,
      Object redmineObj,
      RedmineManager redmineManager) {

    this.osMap = osMap;
    this.redmineMap = redmineMap;
    this.redmineCustomFieldsMap = redmineCustomFieldsMap;
    this.redmineObj = redmineObj;
    this.redmineManager = redmineManager;

    for (DynamicFieldsSync dynamicFieldsSync : dynamicFieldsSyncList) {

      this.fieldNameInAbs = dynamicFieldsSync.getFieldNameInAbs();
      this.fieldNameInRedmine = dynamicFieldsSync.getFieldNameInRedmine();
      this.typeSelectInAbs = dynamicFieldsSync.getTypeSelectInAbs();
      this.relatedFieldInAbsToRedmineSelect =
          dynamicFieldsSync.getRelatedFieldInAbsToRedmineSelect();
      this.defaultAbsValue = dynamicFieldsSync.getDefaultAbsValue();
      this.valuesMappingList = dynamicFieldsSync.getValuesMappingList();

      String mappingDirection = dynamicFieldsSync.getFieldMappingDirection();

      if (fieldNameInAbs != null
          && mappingDirection != null
          && mappingDirection.matches(MAPPING_DIRECTION)) {

        // No such field found in ABS

        if (!osMap.containsKey(fieldNameInAbs)) {
          continue;
        }

        MetaField metaField = metaFieldRepo.findByModel(fieldNameInAbs, metaModel);

        // CUSTOM FIELD BINDING

        if (dynamicFieldsSync.getIsCustomRedmineField()) {
          customFieldBinding(dynamicFieldsSync, metaField);
          continue;
        }

        // SIMPLE FIELD BINDING

        if (metaField.getRelationship() == null) {
          simpleFieldBinding(dynamicFieldsSync);
          continue;
        }

        // M2O FIELD BINDING

        if (metaField.getRelationship().equals(RELATIONSHIP_M2O)) {
          m2oFieldBinding(dynamicFieldsSync, metaField);
          continue;
        }

        // M2M FIELD BINDING

        if (metaField.getRelationship().equals(RELATIONSHIP_M2M)) {
          m2mFieldBinding(dynamicFieldsSync, metaField);
          continue;
        }
      }
    }

    return osMap;
  }

  @SuppressWarnings("unchecked")
  public void customFieldBinding(DynamicFieldsSync dynamicFieldsSync, MetaField metaField) {

    if (dynamicFieldsSync.getIsSelectField()) {

      // CUSTOM FIELD BINDING : REDMINE SELECT -> ABS M2O

      if (typeSelectInAbs.equals(DynamicFieldsSyncRepository.TYPE_IN_ABS_M2O)) {
        Class<?> objClass = null;
        try {
          objClass = Class.forName(metaField.getPackageName() + "." + metaField.getTypeName());
        } catch (ClassNotFoundException e) {
          TraceBackService.trace(e);
        }

        Model item =
            Query.of((Class<Model>) objClass)
                .filter("self." + relatedFieldInAbsToRedmineSelect + " = :param")
                .bind("param", redmineCustomFieldsMap.get(fieldNameInRedmine))
                .fetchOne();

        if (item == null && defaultAbsValue != null) {
          item =
              Query.of((Class<Model>) objClass)
                  .filter("self.id = :param")
                  .bind("param", defaultAbsValue)
                  .fetchOne();
        }

        osMap.put(fieldNameInAbs, item != null ? objClass.cast(item) : null);
      }

      // CUSTOM FIELD BINDING : REDMINE SELECT -> ABS SELECT

      else if (typeSelectInAbs.equals(DynamicFieldsSyncRepository.TYPE_IN_ABS_SELECT)
          && valuesMappingList != null
          && !valuesMappingList.isEmpty()) {
        ValuesMapping valueMapping =
            valuesMappingList
                .stream()
                .filter(
                    value ->
                        value.getSelectValueInRedmine() != null
                            && value
                                .getSelectValueInRedmine()
                                .equals(redmineCustomFieldsMap.get(fieldNameInRedmine)))
                .findAny()
                .orElse(null);

        osMap.put(
            fieldNameInAbs,
            valueMapping != null
                ? valueMapping.getSelectValueInAbs()
                : (defaultAbsValue != null ? defaultAbsValue : null));
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

          String customFieldValueInRedmine =
              (String) redmineCustomFieldsMap.get(fieldNameInRedmine);
          String customFieldFormat = customFieldDefinition.getFieldFormat();

          // SPECIAL CASE FOR STRING CUSTOM FIELD -> ABS M2O

          if (customFieldFormat.equals("string")
              && typeSelectInAbs != null
              && typeSelectInAbs.equals(DynamicFieldsSyncRepository.TYPE_IN_ABS_M2O)
              && relatedFieldInAbsToRedmineSelect != null) {
            Class<?> objClass = null;
            try {
              objClass = Class.forName(metaField.getPackageName() + "." + metaField.getTypeName());
            } catch (ClassNotFoundException e) {
              TraceBackService.trace(e);
            }

            Model item =
                Query.of((Class<Model>) objClass)
                    .filter("self." + relatedFieldInAbsToRedmineSelect + " = :param")
                    .bind("param", redmineCustomFieldsMap.get(fieldNameInRedmine))
                    .fetchOne();

            if (item == null && defaultAbsValue != null) {
              item =
                  Query.of((Class<Model>) objClass)
                      .filter("self.id = :param")
                      .bind("param", defaultAbsValue)
                      .fetchOne();
            }

            osMap.put(fieldNameInAbs, item != null ? objClass.cast(item) : null);
          }

          // IF CUSTOM FIELD TYPE IS STRING | BOOL | DATE | FLOAT | INT | TEXT | LINK

          else if (customFieldFormat.matches(CUSTOM_FIELD_FORMATS_SIMPLE)) {

            if (customFieldValueInRedmine != null && customFieldFormat.equals("bool")) {
              customFieldValueInRedmine = customFieldValueInRedmine.equals("1") ? "true" : "false";
            }

            osMap.put(
                fieldNameInAbs,
                customFieldValueInRedmine != null && !customFieldValueInRedmine.equals("")
                    ? customFieldValueInRedmine
                    : (defaultAbsValue != null ? defaultAbsValue : null));
          }

          // IF CUSTOM FIELD TYPE IS USER | VERSION

          else if (customFieldFormat.equals(CUSTOM_FIELD_FORMAT_USER)) {
            Object value = null;

            if (customFieldValueInRedmine != null && !customFieldValueInRedmine.equals("")) {
              value =
                  userRepo
                      .all()
                      .filter(
                          "self.partner.emailAddress.address = ?1",
                          redmineManager
                              .getUserManager()
                              .getUserById(Integer.parseInt(customFieldValueInRedmine))
                              .getMail())
                      .fetchOne();
            }

            if (value == null && defaultAbsValue != null) {
              value = userRepo.find(Long.parseLong(defaultAbsValue));
            }

            osMap.put(fieldNameInAbs, value);
          } else if (customFieldFormat.equals(CUSTOM_FIELD_FORMAT_VERSION)) {
            Object value = null;

            if (customFieldValueInRedmine != null && !customFieldValueInRedmine.equals("")) {
              value =
                  projectVersionRepo.findByRedmineId(Integer.parseInt(customFieldValueInRedmine));
            }

            if (value == null && defaultAbsValue != null) {
              value = projectVersionRepo.find(Long.parseLong(defaultAbsValue));
            }

            osMap.put(fieldNameInAbs, value);
          }
        }
      } catch (RedmineException e) {
        TraceBackService.trace(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  public void m2mFieldBinding(DynamicFieldsSync dynamicFieldsSync, MetaField metaField) {

    Class<?> objClass = null;
    try {
      objClass = Class.forName(metaField.getPackageName() + "." + metaField.getTypeName());
    } catch (ClassNotFoundException e) {
      TraceBackService.trace(e);
    }
    Collection<Model> itemSet = new HashSet<>();

    Collection<?> redmineFieldObjSet =
        (Collection<?>) ObjectTool.getObject(redmineObj, fieldNameInRedmine);

    if (redmineFieldObjSet != null && !redmineFieldObjSet.isEmpty()) {
      redmineMap.put(
          fieldNameInRedmine,
          redmineFieldObjSet
              .stream()
              .map(redmineFieldObj -> (int) ObjectTool.getObject(redmineFieldObj, "id"))
              .collect(Collectors.toList()));
    }

    itemSet =
        (Collection<Model>)
            Query.of((Class<Model>) objClass)
                .filter("self.redmineId in :idList")
                .bind("idList", redmineMap.get(fieldNameInRedmine))
                .fetch();

    osMap.put(fieldNameInAbs, itemSet);
  }

  @SuppressWarnings("unchecked")
  public void m2oFieldBinding(DynamicFieldsSync dynamicFieldsSync, MetaField metaField) {

    Class<?> objClass = null;
    try {
      objClass = Class.forName(metaField.getPackageName() + "." + metaField.getTypeName());
    } catch (ClassNotFoundException e) {
      TraceBackService.trace(e);
    }
    Model item = null;

    if (fieldNameInRedmine != null) {

      if (!fieldNameInRedmine.endsWith("Id") && !fieldNameInRedmine.endsWith("Name")) {
        Object redmineFieldObj = ObjectTool.getObject(redmineObj, fieldNameInRedmine);

        if (redmineFieldObj != null) {
          redmineMap.put(fieldNameInRedmine, (int) ObjectTool.getObject(redmineFieldObj, "id"));

          item =
              Query.of((Class<Model>) objClass)
                  .filter("self.redmineId = :param")
                  .bind("param", redmineMap.get(fieldNameInRedmine))
                  .fetchOne();
        }
      } else {
        item =
            Query.of((Class<Model>) objClass)
                .filter(
                    fieldNameInRedmine.endsWith("Id")
                        ? "self.redmineId = :param"
                        : "self.name = :param")
                .bind("param", redmineMap.get(fieldNameInRedmine))
                .fetchOne();
      }
    }

    if (item == null && defaultAbsValue != null) {

      item =
          Query.of((Class<Model>) objClass)
              .filter("self.id = :param")
              .bind("param", defaultAbsValue)
              .fetchOne();
    }

    osMap.put(fieldNameInAbs, item != null ? objClass.cast(item) : null);
  }

  public void simpleFieldBinding(DynamicFieldsSync dynamicFieldsSync) {

    if (osMap.containsKey(fieldNameInAbs)) {

      if (!dynamicFieldsSync.getIsSelectField()) {
        osMap.put(
            fieldNameInAbs,
            fieldNameInRedmine != null && redmineMap.get(fieldNameInRedmine) != null
                ? redmineMap.get(fieldNameInRedmine)
                : defaultAbsValue);
      } else if (valuesMappingList != null && !valuesMappingList.isEmpty()) {

        // Fixed rule for Tracker binding with ABS select fields for Issues
        if (fieldNameInRedmine.equals("tracker")) {
          Object redmineFieldObj = ObjectTool.getObject(redmineObj, fieldNameInRedmine);

          if (redmineFieldObj != null) {
            String trackerName = (String) ObjectTool.getObject(redmineFieldObj, "name");
            redmineMap.put("tracker", trackerName);
          }
        }

        ValuesMapping valueMapping =
            valuesMappingList
                .stream()
                .filter(
                    value ->
                        value.getSelectValueInRedmine() != null
                            && fieldNameInRedmine != null
                            && value
                                .getSelectValueInRedmine()
                                .equals(redmineMap.get(fieldNameInRedmine)))
                .findAny()
                .orElse(null);

        osMap.put(
            fieldNameInAbs,
            valueMapping != null ? valueMapping.getSelectValueInAbs() : defaultAbsValue);
      }
    }
  }
}
