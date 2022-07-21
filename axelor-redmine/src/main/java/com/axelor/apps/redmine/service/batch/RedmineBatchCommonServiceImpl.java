/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2022 Axelor (<http://axelor.com>).
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
package com.axelor.apps.redmine.service.batch;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.redmine.service.common.RedmineExcelLogService;
import com.axelor.i18n.I18n;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.CustomFieldManager;
import com.taskadapter.redmineapi.NotFoundException;
import com.taskadapter.redmineapi.RedmineAuthenticationException;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.RedmineTransportException;
import com.taskadapter.redmineapi.UserManager;
import com.taskadapter.redmineapi.bean.CustomFieldDefinition;
import com.taskadapter.redmineapi.bean.User;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.persistence.PersistenceException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedmineBatchCommonServiceImpl implements RedmineBatchCommonService {

  public static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected RedmineExcelLogService redmineExcelLogService;

  @Inject
  public RedmineBatchCommonServiceImpl(RedmineExcelLogService redmineExcelLogService) {
    this.redmineExcelLogService = redmineExcelLogService;
  }

  @Override
  public RedmineManager getRedmineManager(AppRedmine appRedmine) {

    LOG.debug("Getting redmine manager..");

    if (!StringUtils.isBlank(appRedmine.getUri())
        && !StringUtils.isBlank(appRedmine.getApiAccessKey())) {
      RedmineManager redmineManager =
          RedmineManagerFactory.createWithApiKey(appRedmine.getUri(), appRedmine.getApiAccessKey());

      try {
        redmineManager.getUserManager().getCurrentUser();
      } catch (RedmineTransportException | NotFoundException e) {
        throw new PersistenceException(IMessage.REDMINE_TRANSPORT);
      } catch (RedmineAuthenticationException e) {
        throw new PersistenceException(IMessage.REDMINE_AUTHENTICATION_2);
      } catch (RedmineException e) {
        throw new PersistenceException(e.getLocalizedMessage());
      }

      return redmineManager;
    } else {
      throw new PersistenceException(IMessage.REDMINE_AUTHENTICATION_1);
    }
  }

  @Override
  public void validateCustomFieldConfigProject(
      CustomFieldManager customFieldManager, AppRedmine appRedmine) {

    Map<String, Boolean> customFieldsValidationMap = new HashMap<>();

    customFieldsValidationMap.put("project " + appRedmine.getRedmineProjectClientPartner(), false);
    customFieldsValidationMap.put("project " + appRedmine.getRedmineProjectInvoiceable(), false);
    customFieldsValidationMap.put(
        "project " + appRedmine.getRedmineProjectInvoicingSequenceSelect(), false);
    customFieldsValidationMap.put("project " + appRedmine.getRedmineProjectAssignedTo(), false);
    customFieldsValidationMap.put("version " + appRedmine.getRedmineVersionDeliveryDate(), false);

    validateCustomFieldConfig(customFieldManager, customFieldsValidationMap);
  }

  @Override
  public void validateCustomFieldConfigIssue(
      CustomFieldManager customFieldManager, AppRedmine appRedmine) {

    HashMap<String, Boolean> customFieldsValidationMap = new HashMap<>();

    customFieldsValidationMap.put("issue " + appRedmine.getRedmineIssueEstimatedTime(), false);
    customFieldsValidationMap.put("issue " + appRedmine.getRedmineIssueInvoiced(), false);
    customFieldsValidationMap.put("issue " + appRedmine.getRedmineIssueProduct(), false);
    customFieldsValidationMap.put("issue " + appRedmine.getRedmineIssueDueDate(), false);
    customFieldsValidationMap.put(
        "issue " + appRedmine.getRedmineIssueAccountedForMaintenance(), false);
    customFieldsValidationMap.put("issue " + appRedmine.getRedmineIssueIsTaskAccepted(), false);
    customFieldsValidationMap.put("issue " + appRedmine.getRedmineIssueIsOffered(), false);
    customFieldsValidationMap.put("issue " + appRedmine.getRedmineIssueUnitPrice(), false);

    validateCustomFieldConfig(customFieldManager, customFieldsValidationMap);
  }

  @Override
  public void validateCustomFieldConfigTimeEntry(
      CustomFieldManager customFieldManager, AppRedmine appRedmine) {

    HashMap<String, Boolean> customFieldsValidationMap = new HashMap<>();

    customFieldsValidationMap.put(
        "time_entry " + appRedmine.getRedmineTimeSpentDurationForCustomer(), false);
    customFieldsValidationMap.put("time_entry " + appRedmine.getRedmineTimeSpentProduct(), false);
    customFieldsValidationMap.put(
        "time_entry " + appRedmine.getRedmineTimeSpentDurationUnit(), false);

    validateCustomFieldConfig(customFieldManager, customFieldsValidationMap);
  }

  public void validateCustomFieldConfig(
      CustomFieldManager customFieldManager, Map<String, Boolean> customFieldsValidationMap) {

    LOG.debug("Validating custom fields name filled in redmine app config..");

    List<CustomFieldDefinition> customFieldDefinitions;

    try {
      customFieldDefinitions = customFieldManager.getCustomFieldDefinitions();
    } catch (RedmineException e) {
      throw new PersistenceException(IMessage.REDMINE_CFS_NOT_FETCHED);
    }

    for (CustomFieldDefinition customFieldDefinition : customFieldDefinitions) {
      String uniqueKey =
          customFieldDefinition.getCustomizedType() + " " + customFieldDefinition.getName();

      if (customFieldsValidationMap.containsKey(uniqueKey)) {
        customFieldsValidationMap.put(uniqueKey, true);
      }
    }

    for (Entry<String, Boolean> entry : customFieldsValidationMap.entrySet()) {

      if (!entry.getValue()) {
        String key = entry.getKey();

        throw new PersistenceException(
            String.format(
                I18n.get(IMessage.REDMINE_IMPORT_CUSTOM_FIELD_CONFIG_VALIDATION_ERROR),
                key.substring(key.indexOf(" ") + 1),
                key.substring(0, key.indexOf(" "))));
      }
    }
  }

  @Override
  public Map<Integer, String> getRedmineUserMap(UserManager redmineUserManager) {
    try {
      return redmineUserManager.getUsers().stream()
          .collect(Collectors.toMap(User::getId, User::getMail));
    } catch (RedmineException e) {
      throw new PersistenceException(IMessage.REDMINE_USERS_NOT_FETCHED);
    }
  }

  @Override
  public Map<String, String> getRedmineUserLoginMap(UserManager redmineUserManager) {
    try {
      return redmineUserManager.getUsers().stream()
          .collect(Collectors.toMap(User::getMail, User::getLogin));
    } catch (RedmineException e) {
      throw new PersistenceException(IMessage.REDMINE_USERS_NOT_FETCHED);
    }
  }

  @Override
  public MetaFile generateErrorLog(List<Object[]> errorObjList) {

    LOG.debug("Generating error log file..");

    String sheetName = "ErrorLog";
    String fileName = "RedmineImportErrorLog_";
    Object[] headers = new Object[] {"Object", "Redmine reference", "Error"};

    return redmineExcelLogService.generateExcelLog(sheetName, fileName, headers, errorObjList);
  }
}
