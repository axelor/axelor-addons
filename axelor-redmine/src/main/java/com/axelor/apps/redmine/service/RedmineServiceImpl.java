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
package com.axelor.apps.redmine.service;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.redmine.imports.service.RedmineIssueService;
import com.axelor.apps.redmine.imports.service.RedmineProjectService;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.NotFoundException;
import com.taskadapter.redmineapi.RedmineAuthenticationException;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.RedmineTransportException;
import java.util.function.Consumer;

public class RedmineServiceImpl implements RedmineService {

  @Inject private AppRedmineRepository appRedmineRepo;
  @Inject protected RedmineProjectService redmineImportProjectService;
  @Inject protected RedmineIssueService redmineImportIssueService;

  @Override
  @Transactional
  public void redmineImportProjects(
      Batch batch, Consumer<Object> onSuccess, Consumer<Throwable> onError) {

    try {
      RedmineManager redmineManager = getRedmineManager();

      if (redmineManager == null) {
        return;
      }

      redmineImportProjectService.redmineImportProject(batch, redmineManager, onSuccess, onError);
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  @Override
  @Transactional
  public void redmineImportIssues(
      Batch batch, Consumer<Object> onSuccess, Consumer<Throwable> onError) {

    try {
      RedmineManager redmineManager = getRedmineManager();

      if (redmineManager == null) {
        return;
      }

      redmineImportIssueService.redmineImportIssue(batch, redmineManager, onSuccess, onError);
    } catch (Exception e) {
      TraceBackService.trace(e, "", batch.getId());
    }
  }

  public RedmineManager getRedmineManager() throws AxelorException {
    AppRedmine appRedmine = appRedmineRepo.all().fetchOne();

    if (!StringUtils.isBlank(appRedmine.getUri())
        && !StringUtils.isBlank(appRedmine.getApiAccessKey())) {
      RedmineManager redmineManager =
          RedmineManagerFactory.createWithApiKey(appRedmine.getUri(), appRedmine.getApiAccessKey());

      try {
        redmineManager.getUserManager().getCurrentUser();
      } catch (RedmineTransportException | NotFoundException e) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_NO_VALUE, IMessage.REDMINE_TRANSPORT);
      } catch (RedmineAuthenticationException e) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_NO_VALUE, IMessage.REDMINE_AUTHENTICATION_2);
      } catch (RedmineException e) {
        throw new AxelorException(TraceBackRepository.CATEGORY_NO_VALUE, e.getLocalizedMessage());
      }

      return redmineManager;
    } else {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE, IMessage.REDMINE_AUTHENTICATION_1);
    }
  }
}
