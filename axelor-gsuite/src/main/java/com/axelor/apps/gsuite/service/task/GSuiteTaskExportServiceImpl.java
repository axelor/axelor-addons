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
package com.axelor.apps.gsuite.service.task;

import com.axelor.apps.crm.db.Event;
import com.axelor.apps.crm.db.repo.EventRepository;
import com.axelor.apps.gsuite.db.TaskGoogleAccount;
import com.axelor.apps.gsuite.db.repo.TaskGoogleAccountRepository;
import com.axelor.apps.gsuite.service.GSuiteService;
import com.axelor.apps.gsuite.utils.DateUtils;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.exception.AxelorException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.tasks.Tasks;
import com.google.api.services.tasks.Tasks.Tasklists;
import com.google.api.services.tasks.Tasks.TasksOperations;
import com.google.api.services.tasks.model.Task;
import com.google.api.services.tasks.model.TaskList;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteTaskExportServiceImpl implements GSuiteTaskExportService {

  @Inject private GSuiteService gSuiteService;
  @Inject private EmailAccountRepository emailAccountRepo;
  @Inject private EventRepository eventRepo;
  @Inject private TaskGoogleAccountRepository taskGoogleAccountRepo;

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  @Transactional
  public EmailAccount sync(EmailAccount emailAccount) throws AxelorException {
    if (emailAccount == null) {
      return null;
    }

    emailAccount = emailAccountRepo.find(emailAccount.getId());
    LocalDateTime syncDate = emailAccount.getTaskSyncToGoogleDate();
    LOG.debug("Last sync date: {}", syncDate);

    List<Event> events;
    if (syncDate == null) {
      events = eventRepo.all().filter("self.typeSelect = ?1", EventRepository.TYPE_TASK).fetch();
    } else {
      events =
          eventRepo
              .all()
              .filter(
                  "self.typeSelect = ?1 AND (self.updatedOn > ?2 OR self.createdOn > ?2)",
                  EventRepository.TYPE_TASK,
                  syncDate)
              .fetch();
    }
    LOG.debug("total size: {}", events.size());
    Map<String, String> tasklistMap = new HashMap<>();
    for (Event task : events) {
      if (task.getEndDateTime() != null
          && task.getStartDateTime() != null
          && task.getStartDateTime().isAfter(task.getEndDateTime())) {
        LOG.debug("task id : {} not synchronized", task.getId());
        continue;
      }
      TaskGoogleAccount taskAccount = new TaskGoogleAccount();
      String taskId = null;
      String tasklistId = tasklistMap.getOrDefault(task.getTasklistName(), null);

      for (TaskGoogleAccount account : task.getTaskGoogleAccounts()) {
        if (emailAccount.equals(account.getEmailAccount())) {
          taskAccount = account;
          taskId = account.getGoogleTaskId();
          tasklistId = account.getGoogleTasklistId();
          LOG.debug("Task id: {}", taskId);
          break;
        }
      }

      Tasks tasks = gSuiteService.getTask(emailAccount.getId());
      try {
        tasklistId = insertTaskList(tasklistId, tasks.tasklists(), task);
        taskId = createUpdateGoogleTask(tasks.tasks(), task, tasklistId, taskId);
      } catch (IOException e) {
        emailAccount.setTaskSyncToGoogleLog("\n" + ExceptionUtils.getStackTrace(e));
      }
      taskAccount.setTask(task);
      taskAccount.setEmailAccount(emailAccount);
      taskAccount.setGoogleTaskId(taskId);
      taskAccount.setGoogleTasklistId(tasklistId);
      tasklistMap.put(task.getTasklistName(), tasklistId);
      taskGoogleAccountRepo.save(taskAccount);
    }
    emailAccount.setTaskSyncToGoogleDate(LocalDateTime.now());
    return emailAccountRepo.save(emailAccount);
  }

  protected String insertTaskList(String tasklistId, Tasklists tasklists, Event task)
      throws IOException {
    if (tasklistId != null) {
      try {
        tasklists.get(tasklistId).execute();
        return tasklistId;
      } catch (IOException e) {
      }
    }
    TaskList taskList = new TaskList();
    taskList.setTitle(task.getTasklistName());
    taskList = tasklists.insert(taskList).execute();
    tasklistId = taskList.getId();
    return tasklistId;
  }

  @Override
  public String createUpdateGoogleTask(
      TasksOperations taskOperations, Event task, String tasklistId, String taskId)
      throws IOException {

    Task googleTask = new Task();
    LOG.debug("Exporting task id: {}, subject: {}", task.getId(), task.getSubject());

    if (taskId != null) {
      try {
        googleTask = taskOperations.get(tasklistId, taskId).execute();
      } catch (GoogleJsonResponseException e) {
        googleTask = new Task();
        taskId = null;
      }
    }

    googleTask = extractEvent(task, googleTask);

    if (taskId != null && googleTask.getDeleted() != Boolean.TRUE) {
      googleTask = taskOperations.update(tasklistId, taskId, googleTask).execute();

    } else {
      googleTask.setDeleted(false);
      googleTask = taskOperations.insert(tasklistId, googleTask).execute();
    }

    return googleTask.getId();
  }

  @Override
  public Task extractEvent(Event task, Task googleTask) {
    googleTask.setTitle(task.getSubject());
    googleTask.setNotes(task.getDescription());
    googleTask.setStatus(getStatus(task));
    googleTask.setDue(DateUtils.toGoogleDateTime(task.getEndDateTime()));
    return googleTask;
  }

  protected String getStatus(Event task) {
    switch (task.getStatusSelect()) {
      case EventRepository.STATUS_PENDING:
        return "needsAction";
      case EventRepository.STATUS_FINISHED:
        return "completed";
      default:
        return null;
    }
  }
}
