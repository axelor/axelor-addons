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
package com.axelor.apps.sendinblue.service;

import com.axelor.apps.base.db.AppSendinblue;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.sendinblue.translation.ITranslation;
import com.axelor.apps.tool.service.TranslationService;
import com.axelor.db.Model;
import com.axelor.db.annotations.NameColumn;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.db.MetaField;
import com.google.inject.Inject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import sendinblue.ApiException;
import sibApi.AttributesApi;
import sibModel.CreateAttribute;
import sibModel.CreateAttribute.TypeEnum;
import sibModel.GetAttributes;
import sibModel.GetAttributesAttributes;

public class SendinBlueFieldService {

  @Inject AppSendinBlueService appSendinBlueService;
  @Inject TranslationService translationService;
  @Inject UserService userService;

  protected static final String ATTRIBUTE_CATEGORY = "normal";

  protected static List<String> attributeList = new ArrayList<>();

  protected String userLanguage;
  protected boolean isRelational = false;

  public void exportFields(AppSendinblue appSendinblue) throws AxelorException {
    appSendinBlueService.getApiKeyAuth();
    userLanguage = userService.getUser().getLanguage();
    fetchAttributes();
    exportMetaModelFields(appSendinblue.getPartnerFieldSet());
    createContactAttribute(
        translationService.getValueTranslation(ITranslation.IS_LEAD_LABEL, userLanguage),
        CreateAttribute.TypeEnum.BOOLEAN);
    exportMetaModelFields(appSendinblue.getLeadFieldSet());
  }

  public void fetchAttributes() {
    AttributesApi attributApiInstance = new AttributesApi();
    try {
      GetAttributes result = attributApiInstance.getAttributes();
      List<GetAttributesAttributes> attributes = result.getAttributes();
      attributeList =
          attributes.stream().map(GetAttributesAttributes::getName).collect(Collectors.toList());
    } catch (ApiException e) {
      TraceBackService.trace(e);
    }
  }

  protected void exportMetaModelFields(Set<MetaField> metaFields) {
    for (MetaField metaField : metaFields) {
      isRelational = false;
      TypeEnum fieldType = getAttributeType(metaField);
      if (fieldType != null) {
        String attributeName;
        if (isRelational) {
          attributeName =
              translationService.getValueTranslation(metaField.getName(), userLanguage)
                  + translationService.getValueTranslation(
                      ITranslation.NAME_PREFIX_LABEL, userLanguage);
        } else {
          attributeName = translationService.getValueTranslation(metaField.getName(), userLanguage);
        }
        createContactAttribute(attributeName, fieldType);
      }
    }
  }

  protected void createContactAttribute(String attributeName, TypeEnum fieldType) {
    AttributesApi attributApiInstance = new AttributesApi();
    CreateAttribute createAttribute = new CreateAttribute();
    createAttribute.setType(fieldType);
    try {
      if (!attributeList.contains(attributeName.toUpperCase())) {
        attributApiInstance.createAttribute(ATTRIBUTE_CATEGORY, attributeName, createAttribute);
        attributeList.add(attributeName.toUpperCase());
      }
    } catch (ApiException e) {
      TraceBackService.trace(e);
    }
  }

  protected TypeEnum getAttributeType(MetaField metaField) {
    String fieldType = metaField.getTypeName();
    switch (fieldType) {
      case "String":
        return CreateAttribute.TypeEnum.TEXT;
      case "Boolean":
        return CreateAttribute.TypeEnum.BOOLEAN;
      case "LocalDateTime":
      case "LocalDate":
      case "LocalTime":
      case "ZonedDateTime":
        return CreateAttribute.TypeEnum.DATE;
      case "Integer":
      case "Long":
      case "BigDecimal":
        return CreateAttribute.TypeEnum.FLOAT;
      default:
        String relationship = metaField.getRelationship();
        if (relationship != null
            && (relationship.equals("ManyToOne") || relationship.equals("OneToOne"))) {
          try {
            String classStr = metaField.getPackageName() + "." + metaField.getTypeName();
            @SuppressWarnings("unchecked")
            Class<Model> klass = (Class<Model>) Class.forName(classStr);
            List<Field> fields = FieldUtils.getFieldsListWithAnnotation(klass, NameColumn.class);
            if (fields != null && !fields.isEmpty()) {
              isRelational = true;
              return CreateAttribute.TypeEnum.TEXT;
            }
          } catch (ClassNotFoundException e) {
            TraceBackService.trace(e);
          }
        }
        return null;
    }
  }
}
