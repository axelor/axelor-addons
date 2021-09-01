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
import com.axelor.apps.gsuite.service.ICalUserService;
import com.axelor.apps.gsuite.utils.DateUtils;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.google.api.client.util.DateTime;
import com.google.api.services.tasks.Tasks;
import com.google.api.services.tasks.model.Task;
import com.google.api.services.tasks.model.TaskList;
import com.google.api.services.tasks.model.TaskLists;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteTaskImportServiceImpl implements GSuiteTaskImportService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  @Inject protected GSuiteService gSuiteService;
  @Inject protected EventRepository eventRepo;
  @Inject protected ICalUserService iCalUserService;
  @Inject protected TaskGoogleAccountRepository taskGoogleAccountRepo;

  protected EmailAccount account;
  private Tasks service;

  protected Tasks getService() throws AxelorException {
    if (this.service == null) {
      this.service = gSuiteService.getTask(account.getId());
    }
    return this.service;
  }

  @Override
  public void sync(EmailAccount account) throws AxelorException {
    this.account = account;
    sync(account, null, null);
  }

  @Override
  public void sync(EmailAccount account, LocalDateTime dueDateTMin, LocalDateTime dueDateTMax)
      throws AxelorException {
    this.account = account;
    try {
      String nextPageToken = null;
      List<Event> tasks = new ArrayList<>();
      Tasks.Tasklists.List list = getService().tasklists().list();
      do {
        TaskLists result = list.setPageToken(nextPageToken).execute();
        List<TaskList> taskLists = result.getItems();
        if (ObjectUtils.notEmpty(taskLists)) {
          for (TaskList taskList : taskLists) {
            tasks.addAll(
                createOrUpdateTasks(
                    account,
                    taskList.getId(),
                    taskList.getTitle(),
                    fetchTasks(taskList, dueDateTMin, dueDateTMax)));
          }
        }
        nextPageToken = result.getNextPageToken();
      } while (nextPageToken != null);
      log.debug("{} Event(task) retrived and processed for {}", tasks.size(), account.getUser());
    } catch (IOException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_INCONSISTENCY);
    }
  }

  protected List<Task> fetchTasks(
      TaskList taskList, LocalDateTime dueDateTMin, LocalDateTime dueDateTMax)
      throws IOException, AxelorException {

    com.google.api.services.tasks.Tasks.TasksOperations.List list =
        getService()
            .tasks()
            .list(taskList.getId())
            .setShowCompleted(Boolean.TRUE)
            .setShowHidden(Boolean.TRUE);
    String pageToken = null;
    List<Task> tasks = new ArrayList<>();

    if (dueDateTMax != null) {
      list.setDueMin(DateUtils.toRfc3339(dueDateTMin));
    }
    if (dueDateTMin != null) {
      list.setDueMax(DateUtils.toRfc3339(dueDateTMax));
    }
    do {
      com.google.api.services.tasks.model.Tasks result = list.setPageToken(pageToken).execute();

      if (ObjectUtils.notEmpty(result.getItems())) {
        tasks.addAll(result.getItems());
      }

      pageToken = result.getNextPageToken();

    } while (pageToken != null);

    return tasks;
  }

  protected List<Event> createOrUpdateTasks(
      EmailAccount account, String tasklistId, String taskListName, List<Task> tasks) {
    List<Event> events = new ArrayList<>();
    for (Task task : tasks) {
      Event event = createOrUpdateTask(taskListName, task);
      createOrUpdateTaskAccount(account, tasklistId, task, event);
      events.add(event);
    }
    return events;
  }

  @Transactional
  protected Event createOrUpdateTask(String taskListName, Task task) {
    Event event = eventRepo.findByGoogleTaskId(task.getId());
    if (event == null) {
      event = new Event();
      event.setGoogleTaskId(task.getId());
    }
    event.setTypeSelect(EventRepository.TYPE_TASK);
    event.setSubject(task.getTitle());
    event.setDescription(task.getNotes());
    event.setStatusSelect(getStatusSelect(task));
    event.setTasklistName(taskListName);

    iCalUserService.parseICalUsers(event, task.getNotes()).forEach(event::addAttendee);
    event.setStartDateTime(DateUtils.toLocalDateTime(task.getUpdated()));
    DateTime endDateTime = task.getCompleted();
    if (ObjectUtils.isEmpty(endDateTime)) {
      endDateTime = task.getDue();
    }
    if (ObjectUtils.isEmpty(endDateTime)) {
      endDateTime = task.getUpdated();
    }
    event.setEndDateTime(DateUtils.toLocalDateTime(endDateTime));

    return eventRepo.save(event);
  }

  @Transactional
  protected void createOrUpdateTaskAccount(
      EmailAccount account, String tasklistId, Task task, Event event) {
    List<TaskGoogleAccount> taskAccountList = event.getTaskGoogleAccounts();
    if (CollectionUtils.isEmpty(taskAccountList)) {
      TaskGoogleAccount taskGoogleAccount = new TaskGoogleAccount();
      taskGoogleAccount.setTask(event);
      taskGoogleAccount.setEmailAccount(account);
      taskGoogleAccount.setGoogleTaskId(task.getId());
      taskGoogleAccount.setGoogleTasklistId(tasklistId);
      taskGoogleAccountRepo.save(taskGoogleAccount);
    }
  }

  protected int getStatusSelect(Task task) {
    int statusSelect = 0;
    switch (task.getStatus()) {
      case "needsAction":
        statusSelect = EventRepository.STATUS_PENDING;
        break;
      case "completed":
        statusSelect = EventRepository.STATUS_FINISHED;
        break;
      default:
        break;
    }
    return statusSelect;
  }
}
