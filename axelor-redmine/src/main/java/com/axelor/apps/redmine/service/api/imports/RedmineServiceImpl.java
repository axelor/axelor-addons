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
package com.axelor.apps.redmine.service.api.imports;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.common.StringUtils;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.CustomFieldManager;
import com.taskadapter.redmineapi.NotFoundException;
import com.taskadapter.redmineapi.RedmineAuthenticationException;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.RedmineTransportException;
import com.taskadapter.redmineapi.bean.CustomFieldDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;
import javax.persistence.PersistenceException;

public class RedmineServiceImpl implements RedmineService {

  @Inject protected AppRedmineRepository appRedmineRepo;
  @Inject protected RedmineProjectService redmineImportProjectService;
  @Inject protected RedmineIssueService redmineImportIssueService;

  @Override
  @Transactional
  public void redmineImportProjects(
      Batch batch, Consumer<Object> onSuccess, Consumer<Throwable> onError) {

    RedmineManager redmineManager = null;

    try {
      AppRedmine appRedmine = appRedmineRepo.all().fetchOne();
      redmineManager = getRedmineManager(appRedmine);

      if (redmineManager == null) {
        return;
      }

      validateCustomFieldConfig(redmineManager, appRedmine, true);
    } catch (RedmineException e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }

    redmineImportProjectService.redmineImportProject(batch, redmineManager, onSuccess, onError);
  }

  @Override
  @Transactional
  public void redmineImportIssues(
      Batch batch, Consumer<Object> onSuccess, Consumer<Throwable> onError) {

    RedmineManager redmineManager = null;

    try {
      AppRedmine appRedmine = appRedmineRepo.all().fetchOne();
      redmineManager = getRedmineManager(appRedmine);

      if (redmineManager == null) {
        return;
      }

      validateCustomFieldConfig(redmineManager, appRedmine, false);
    } catch (RedmineException e) {
      onError.accept(e);
      TraceBackService.trace(e, "", batch.getId());
    }

    redmineImportIssueService.redmineImportIssue(batch, redmineManager, onSuccess, onError);
  }

  @Override
  public RedmineManager getRedmineManager(AppRedmine appRedmine) {

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

  public void validateCustomFieldConfig(
      RedmineManager redmineManager, AppRedmine appRedmine, Boolean isProject)
      throws RedmineException {

    HashMap<String, Boolean> customFieldsValidationMap = new HashMap<String, Boolean>();

    if (!isProject) {
      customFieldsValidationMap.put("issue " + appRedmine.getRedmineIssueEstimatedTime(), false);
      customFieldsValidationMap.put("issue " + appRedmine.getRedmineIssueInvoiced(), false);
      customFieldsValidationMap.put("issue " + appRedmine.getRedmineIssueProduct(), false);
      customFieldsValidationMap.put("issue " + appRedmine.getRedmineIssueDueDate(), false);
      customFieldsValidationMap.put(
          "issue " + appRedmine.getRedmineIssueAccountedForMaintenance(), false);
      customFieldsValidationMap.put("issue " + appRedmine.getRedmineIssueIsTaskAccepted(), false);
      customFieldsValidationMap.put("issue " + appRedmine.getRedmineIssueIsOffered(), false);
      customFieldsValidationMap.put("issue " + appRedmine.getRedmineIssueUnitPrice(), false);

      customFieldsValidationMap.put(
          "time_entry " + appRedmine.getRedmineTimeSpentDurationForCustomer(), false);
      customFieldsValidationMap.put("time_entry " + appRedmine.getRedmineTimeSpentProduct(), false);
      customFieldsValidationMap.put(
          "time_entry " + appRedmine.getRedmineTimeSpentDurationUnit(), false);
    } else {
      customFieldsValidationMap.put(
          "project " + appRedmine.getRedmineProjectClientPartner(), false);
      customFieldsValidationMap.put("project " + appRedmine.getRedmineProjectInvoiceable(), false);
      customFieldsValidationMap.put(
          "project " + appRedmine.getRedmineProjectInvoicingSequenceSelect(), false);
      customFieldsValidationMap.put("project " + appRedmine.getRedmineProjectAssignedTo(), false);
      customFieldsValidationMap.put("version " + appRedmine.getRedmineVersionDeliveryDate(), false);
    }

    CustomFieldManager customFieldManager = redmineManager.getCustomFieldManager();
    List<CustomFieldDefinition> customFieldDefinitions =
        customFieldManager.getCustomFieldDefinitions();

    for (CustomFieldDefinition customFieldDefinition : customFieldDefinitions) {
      String customFieldName = customFieldDefinition.getName();
      String customFieldType = customFieldDefinition.getCustomizedType();
      String uniqueKey = customFieldType + " " + customFieldName;

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
}
