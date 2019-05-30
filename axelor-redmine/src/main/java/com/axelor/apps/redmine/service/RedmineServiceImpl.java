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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.exports.RedmineExportService;
import com.axelor.apps.redmine.imports.RedmineImportService;
import com.axelor.apps.redmine.imports.service.ImportIssueService;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.NotFoundException;
import com.taskadapter.redmineapi.RedmineAuthenticationException;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.RedmineTransportException;

public class RedmineServiceImpl implements RedmineService {

  @Inject private AppRedmineRepository appRedmineRepo;
  @Inject protected RedmineImportService importService;
  @Inject protected RedmineExportService exportService;
  @Inject protected ImportIssueService importIssueService;

  @Override
  @Transactional
  public void importRedmine(Batch batch, Consumer<Object> onSuccess, Consumer<Throwable> onError)
      throws AxelorException, RedmineException {
    final RedmineManager redmineManager = getRedmineManager();
    if (redmineManager == null) {
      return;
    }

    LocalDateTime lastImportDateTime = getLastOperationDate(batch.getRedmineBatch());
    Date lastImportDate = null;
    if (lastImportDateTime != null) {
      lastImportDate = Date.from(lastImportDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
    importService.importRedmine(batch, lastImportDate, redmineManager, onSuccess, onError);
  }

  @Override
  @Transactional
  public void exportRedmine(Batch batch, Consumer<Object> onSuccess, Consumer<Throwable> onError)
      throws AxelorException, RedmineException {

    final RedmineManager redmineManager = getRedmineManager();
    if (redmineManager == null) {
      return;
    }
    LocalDateTime lastExportDate = getLastOperationDate(batch.getRedmineBatch());
    exportService.exportRedmine(batch, lastExportDate, redmineManager, onSuccess, onError);
  }

  protected LocalDateTime getLastOperationDate(RedmineBatch redmineBatch) {
    Stream<Batch> stream =
        Beans.get(BatchRepository.class)
            .all()
            .filter(
                "self.redmineBatch IS NOT NULL AND self.endDate IS NOT NULL AND self.redmineBatch.actionSelect = ? AND self.done > 0",
                redmineBatch.getActionSelect())
            .fetchStream();
    if (stream != null) {
      Optional<Batch> lastRedmineBatch = stream.max(Comparator.comparing(Batch::getEndDate));
      if (lastRedmineBatch.isPresent()) {
        return lastRedmineBatch.get().getUpdatedOn();
      }
    }
    return null;
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
