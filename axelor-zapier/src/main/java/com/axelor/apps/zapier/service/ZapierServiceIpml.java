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
package com.axelor.apps.zapier.service;

import com.axelor.apps.zapier.constant.DateTimeFormat;
import com.axelor.apps.zapier.exception.IExceptionMessage;
import com.axelor.db.JpaRepository;
import com.axelor.db.JpaSecurity;
import com.axelor.db.JpaSecurity.AccessType;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.meta.schema.views.Selection.Option;
import com.google.common.base.Strings;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZapierServiceIpml implements ZapierService {

  public static final String MANY_TO_MANY = "MANY_TO_MANY";
  public static final String MANY_TO_ONE = "MANY_TO_ONE";
  public static final String ONE_TO_MANY = "ONE_TO_MANY";
  public static final String ONE_TO_ONE = "ONE_TO_ONE";
  public static final String LOCAL_DATE_TYPE = "DATE";
  public static final String LOCAL_DATE_TIME = "DATETIME";
  public static final String INTEGER = "INTEGER";
  public static final String LONG = "LONG";
  public static final String DECIMAL = "DECIMAL";
  public static final String STRING_TYPE = "STRING";
  public static final String TEXT = "TEXT";
  public static final String BOOLEAN = "BOOLEAN";
  public static final String NULL_TYPE = "null";
  public static final String DEFAULT = "default";

  @SuppressWarnings("unchecked")
  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void mapFields(Map<String, Object> data) throws AxelorException, ClassNotFoundException {

    String model = (String) data.get("domain");

    Class<Model> klass = (Class<Model>) Class.forName(model);
    Map<String, Object> domainMap = setDataType(data, klass);

    Model bean = Mapper.toBean(klass, domainMap);
    JpaRepository<Model> repo = JpaRepository.of(klass);

    repo.save(bean);
  }

  public Map<String, Object> setDataType(Map<String, Object> domainMap, Class<Model> klass)
      throws AxelorException {

    Mapper mapper = Mapper.of(klass);

    /** non empty field filter (from API) */
    for (Property prot : mapper.getProperties()) {
      if (domainMap.get(prot.getName()) != null) {
        domainMap.put(prot.getName(), getFieldRelation(prot, domainMap.get(prot.getName())));
      }
    }

    return domainMap;
  }

  public Object getFieldRelation(Property property, Object value) throws AxelorException {

    Object bean = null;
    switch (property.getType().toString()) {
      case MANY_TO_ONE:
        bean = addM2OBinding(property, value);
        break;

      case ONE_TO_ONE:
        bean = addO2OBinding(property, value);
        break;

      case MANY_TO_MANY:
        bean = addM2MBinding(property, value);
        break;
      case ONE_TO_MANY:
        break;

      default:
        bean = getFieldType(property, value);
        break;
    }

    return bean;
  }

  protected Object getFieldType(Property property, Object value) throws AxelorException {
    String propertyType = property.getType().toString();
    switch (propertyType) {
      case LOCAL_DATE_TYPE:
        value = (LocalDate) bindLocalDate(value.toString(), property);
        break;
      case LOCAL_DATE_TIME:
        value = (LocalDateTime) bindLocalDateTime(value.toString(), property);
        break;
      case STRING_TYPE:
      case TEXT:
        try {
          value = value.toString();
        } catch (Exception e) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.INVALID_FIELD_TYPE),
              property.getTitle(),
              TEXT);
        }
        break;
      case DECIMAL:
        try {
          if (value.getClass().equals(Double.class) || value.getClass().equals(Integer.class)) {
            value = new BigDecimal(String.valueOf(value));
          } else {
            String digit = (String) value;
            value = new BigDecimal(digit.replace(",", "")); // remove comma from digit
          }
        } catch (Exception e) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.INVALID_FIELD_TYPE),
              property.getTitle(),
              DECIMAL);
        }

        break;
      case LONG:
        try {
          value = Long.valueOf(value.toString());
        } catch (Exception e) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.INVALID_FIELD_TYPE),
              property.getTitle(),
              LONG);
        }
        break;
      case INTEGER:
        try {
          value = Integer.parseInt(value.toString());
        } catch (Exception e) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.INVALID_FIELD_TYPE),
              property.getTitle(),
              INTEGER);
        }
        break;
      case BOOLEAN:
        if (value.toString().equalsIgnoreCase("true")
            || value.toString().equalsIgnoreCase("false")) {
          value = (value.toString().equalsIgnoreCase("true")) ? true : false;
        } else {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.INVALID_FIELD_TYPE),
              property.getTitle(),
              BOOLEAN);
        }

        break;
      default:
        break;
    }
    return value;
  }

  @SuppressWarnings({"unchecked", "unused", "rawtypes"})
  @Transactional(rollbackOn = {Exception.class})
  protected Object addM2OBinding(Property property, Object value) throws AxelorException {

    Class subKlass = property.getTarget(); // e.g. : product -> productCategory(m2o)
    Mapper mapper = Mapper.of(subKlass);
    Object bean = null;
    JpaRepository<Model> query = null;
    query = JpaRepository.of(subKlass);
    Map<String, Object> map = Mapper.toMap(Mapper.toBean(subKlass, new HashMap<>()));
    String filter = "";
    for (Property prot : mapper.getProperties()) {

      if (prot.isNameColumn() || prot.getName().equals("name") || prot.getName().equals("code")) {
        filter += " self." + prot.getName() + " LIKE '%" + value + "%' OR";
      }
    }
    filter = filter.substring(0, filter.length() - 2);
    bean = query.all().filter(filter).fetchOne();

    return bean;
  }

  @SuppressWarnings({"rawtypes", "unused"})
  protected Object addM2MBinding(Property property, Object value) throws AxelorException {

    Class subKlass = property.getTarget();
    String[] items = value.toString().split("\\|");
    Object beans = null;
    List<Object> m2mFields = new ArrayList<>();
    for (String item : items) {
      beans = addM2OBinding(property, item);
      if (beans != null) {
        m2mFields.add(beans);
      }
    }
    return m2mFields;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  protected Object addO2OBinding(Property property, Object value) throws AxelorException {
    Class subKlass = property.getTarget();
    Map<String, Object> map = Mapper.toMap(Mapper.toBean(subKlass, new HashMap<>()));
    Mapper mapper = Mapper.of(subKlass);
    Object bean = null;

    for (Property prot : mapper.getProperties()) {
      if (prot.getType().toString().equals("STRING")
          && !prot.getName().equals("importOrigin")
          && !prot.getName().equals("attrs")
          && !prot.getName().equals("importId")) {

        map.put(prot.getName(), getFieldRelation(prot, value));
      }
    }
    try {
      Model model = (Model) Mapper.toBean(subKlass, map);
      JpaRepository<Model> repo = JpaRepository.of(subKlass);
      bean = repo.save(model);
    } catch (Exception e) {
      return null;
    }

    return bean;
  }

  protected LocalDate bindLocalDate(String value, Property property) throws AxelorException {
    if (value != null) {

      for (String parse : DateTimeFormat.DATE_TIME_FORMATS) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(parse);
        try {
          LocalDate localDate = LocalDate.parse(value, formatter);
          return localDate;
        } catch (Exception e) {

        }
      }
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.DATE_FORMAT_ERROR),
          property.getTitle());
    }
    return null;
  }

  protected LocalDateTime bindLocalDateTime(String value, Property property)
      throws AxelorException {
    if (value != null) {

      for (String parse : DateTimeFormat.DATE_TIME_FORMATS) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(parse);
        try {
          LocalDateTime localDateTime = LocalDateTime.parse(value, formatter);
          return localDateTime;
        } catch (Exception e) {

        }
      }
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.DATE_TIME_FORMAT_ERROR),
          property.getTitle());
    }
    return null;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public List<Map> getMetaFieldList(Class<Model> klass) throws Exception {

    List<Map> metaFieldNames = new ArrayList<>();
    Mapper mapper = Mapper.of(klass);

    for (Property property : mapper.getProperties()) {

      Map<String, Object> map = new HashMap<>();

      if (!Strings.isNullOrEmpty(property.getSelection())) {
        List<Option> selectionList = MetaStore.getSelectionList(property.getSelection());
        Map<String, Object> selectionItem = new HashMap<>();

        for (Option item : selectionList) {
          selectionItem.put(item.getValue(), item.getTitle());
        }
        map.put("selectionItem", selectionItem);
      } else {
        map.put("selectionItem", null);
      }

      map.put("field", property.getName());
      map.put("type", property.getType());
      map.put("require", property.isRequired());
      map.put("title", property.getTitle());
      map.put("relational", property.isReference());

      metaFieldNames.add(map);
    }

    return metaFieldNames;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public List<Map> getMetaModelList() throws Exception {

    List<Map> modeldFullNames = new ArrayList<>();
    List<MetaModel> metaModelList = Beans.get(MetaModelRepository.class).all().fetch();

    for (MetaModel modelName : metaModelList) {
      try {
        Class<Model> klass = (Class<Model>) Class.forName(modelName.getFullName());
        if (Beans.get(JpaSecurity.class).isPermitted(AccessType.WRITE, klass)) {
          Map<String, Object> map = new HashMap<>();
          map.put("name", modelName.getFullName());

          modeldFullNames.add(map);
        }
      } catch (Exception e) {
      }
    }

    return modeldFullNames;
  }

  @SuppressWarnings("unchecked")
  @Transactional(rollbackOn = {Exception.class})
  @Override
  public Object updateRecord(Map<String, Object> data)
      throws ClassNotFoundException, AxelorException {
    String modelName = (String) data.get("domain");

    Class<Model> klass = (Class<Model>) Class.forName(modelName);
    Model model = getRecord(data, klass);
    if (model == null) {
      List<String> searchFields = (List<String>) data.get("searchField");
      List<String> searchValues = (List<String>) data.get("searchValue");

      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessage.NO_EXISTING_RECORD),
          searchFields.toString(),
          searchValues.toString());
    }
    Map<String, Object> map = Mapper.toMap(model);
    Model updatedModel = updateObject(map, data, klass, model);

    JpaRepository<Model> repo = JpaRepository.of(klass);
    repo.save(updatedModel);
    return updatedModel;
  }

  @SuppressWarnings({"unchecked"})
  private Model getRecord(Map<String, Object> data, Class<Model> klass)
      throws ClassNotFoundException, AxelorException {
    Object obj = null;

    List<String> searchFields = (List<String>) data.get("searchField");
    List<String> searchValues = (List<String>) data.get("searchValue");
    String filter = "";

    Mapper mapper = Mapper.of(klass);
    JpaRepository<Model> repo = JpaRepository.of(klass);

    for (int i = 0; i < searchFields.size(); i++) {
      String searchField = searchFields.get(i);
      String searchValue = searchValues.get(i);
      Property prot = mapper.getProperty(searchField);

      if (prot != null && prot.isReference()) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_NO_VALUE,
            I18n.get(IExceptionMessage.SEARCH_FIELD_IS_RELATIONAL));
      }
      try {
        obj = getFieldType(prot, searchValue);
      } catch (Exception e) {
        return null;
      }

      if (prot == null || obj == null) {
        return null;
      }

      filter += " self." + searchField + " = '" + obj + "' AND";
    }
    filter = filter.substring(0, filter.length() - 3);
    try {
      Model model = repo.all().filter(filter).fetchOne();
      return model;
    } catch (Exception e) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessage.INVALID_SEARCH_FIELD),
          searchFields.toString());
    }
  }

  private Model updateObject(
      Map<String, Object> oldValue, Map<String, Object> newValue, Class<Model> klass, Model model)
      throws AxelorException {

    Mapper mapper = Mapper.of(klass);
    for (String key : newValue.keySet()) {
      if (!key.equals("domain")
          && !key.equals("searchField")
          && !key.equals("searchValue")
          && newValue.get(key) != null
          && mapper.getProperty(key) != null) {
        Property prot = mapper.getProperty(key);
        mapper.set(model, prot.getName(), getFieldRelation(prot, newValue.get(key)));
      }
    }
    return model;
  }
}
