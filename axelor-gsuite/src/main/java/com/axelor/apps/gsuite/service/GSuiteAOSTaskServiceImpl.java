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
package com.axelor.apps.gsuite.service;

import com.axelor.apps.crm.db.Event;
import com.axelor.apps.crm.db.repo.EventRepository;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.utils.DateUtils;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteAOSTaskServiceImpl implements GSuiteAOSTaskService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  @Inject protected GSuiteService gSuiteService;
  @Inject protected EventRepository eventRepo;
  @Inject protected ICalUserService iCalUserService;

  protected GoogleAccount account;
  private Tasks service;

  protected Tasks getService() throws AxelorException {
    if (this.service == null) {
      this.service = gSuiteService.getTask(account.getId());
    }
    return this.service;
  }

  @Override
  public void sync(GoogleAccount account) throws AxelorException {
    this.account = account;
    sync(account, null, null);
  }

  @Override
  public void sync(GoogleAccount account, LocalDateTime dueDateTMin, LocalDateTime dueDateTMax)
      throws AxelorException {
    this.account = account;
    try {
      List<Task> tasks = fetchTasks(dueDateTMin, dueDateTMax);
      List<Event> events = createOrUpdateTasks(tasks);
      log.debug(
          "{} Event(task) retrived and processed for {}", events.size(), account.getOwnerUser());
    } catch (IOException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_INCONSISTENCY);
    }
  }

  protected List<Task> fetchTasks(LocalDateTime dueDateTMin, LocalDateTime dueDateTMax)
      throws IOException, AxelorException {
    String nextPageToken = null;
    List<Task> tasks = new ArrayList<>();
    Tasks.Tasklists.List list = getService().tasklists().list();
    do {
      TaskLists result = list.setPageToken(nextPageToken).execute();
      List<TaskList> taskLists = result.getItems();
      if (ObjectUtils.notEmpty(taskLists)) {
        for (TaskList taskList : taskLists) {
          tasks.addAll(fetchTasks(taskList, dueDateTMin, dueDateTMax));
        }
      }
      nextPageToken = result.getNextPageToken();
    } while (nextPageToken != null);
    return tasks;
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

  protected List<Event> createOrUpdateTasks(List<Task> tasks) {
    List<Event> events = new ArrayList<>();
    for (Task task : tasks) {
      events.add(createOrUpdateTask(task));
    }
    return events;
  }

  @Transactional
  protected Event createOrUpdateTask(Task task) {
    Event event = eventRepo.findByGoogleTaskId(task.getId());
    if (event == null) {
      event = new Event();
      event.setGoogleTaskId(task.getId());
    }

    event.setTypeSelect(EventRepository.TYPE_TASK);
    event.setSubject(task.getTitle());
    event.setDescription(task.getNotes());
    event.setStatusSelect(getStatusSelect(task));

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
