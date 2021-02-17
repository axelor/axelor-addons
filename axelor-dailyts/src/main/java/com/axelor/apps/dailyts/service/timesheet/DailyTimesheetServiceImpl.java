/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
package com.axelor.apps.dailyts.service.timesheet;

import com.axelor.apps.base.db.ICalendarEvent;
import com.axelor.apps.base.db.repo.ICalendarEventRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.hr.db.DailyTimesheet;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.service.timesheet.TimesheetLineService;
import com.axelor.apps.hr.service.timesheet.TimesheetService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectTask;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.auth.db.User;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;

public class DailyTimesheetServiceImpl implements DailyTimesheetService {

  public AppBaseService appBaseService;
  public TimesheetLineRepository timesheetLineRepository;
  public MailMessageRepository mailMessageRepository;
  public ProjectTaskRepository projectTaskaRepo;
  public TimesheetLineService timesheetLineService;
  public ICalendarEventRepository iCalendarEventRepository;
  public TimesheetRepository timesheetRepository;
  public TimesheetService timesheetService;

  @Inject
  public DailyTimesheetServiceImpl(
      AppBaseService appBaseService,
      TimesheetLineRepository timesheetLineRepository,
      MailMessageRepository mailMessageRepository,
      ProjectTaskRepository projectTaskaRepo,
      TimesheetLineService timesheetLineService,
      ICalendarEventRepository iCalendarEventRepository,
      TimesheetRepository timesheetRepository,
      TimesheetService timesheetService) {
    this.appBaseService = appBaseService;
    this.timesheetLineRepository = timesheetLineRepository;
    this.mailMessageRepository = mailMessageRepository;
    this.projectTaskaRepo = projectTaskaRepo;
    this.timesheetLineService = timesheetLineService;
    this.iCalendarEventRepository = iCalendarEventRepository;
    this.timesheetRepository = timesheetRepository;
    this.timesheetService = timesheetService;
  }

  @Override
  public void updateFromTimesheets(DailyTimesheet dailyTimesheet) {

    LocalDate dailyTimesheetDate = dailyTimesheet.getDailyTimesheetDate();
    User dailyTimesheetUser = dailyTimesheet.getDailyTimesheetUser();

    Timesheet timesheet = fetchRelatedTimesheet(dailyTimesheetUser, dailyTimesheetDate);

    if (timesheet != null) {
      List<TimesheetLine> timesheetLineList = timesheet.getTimesheetLineList();

      if (CollectionUtils.isNotEmpty(timesheetLineList)) {

        for (TimesheetLine timesheetLine : timesheetLineList) {

          if (timesheetLine.getUser().equals(dailyTimesheetUser)
              && timesheetLine.getDate().compareTo(dailyTimesheetDate) == 0
              && (timesheetLine.getDailyTimesheet() == null
                  || !timesheetLine.getDailyTimesheet().equals(dailyTimesheet))) {
            dailyTimesheet.addDailyTimesheetLineListItem(timesheetLine);
          }
        }
      }

      computeTimesheetPeriodTotal(timesheet);
    }
  }

  @Override
  public void updateFromActivities(DailyTimesheet dailyTimesheet) {

    LocalDate dailyTimesheetDate = dailyTimesheet.getDailyTimesheetDate();
    User dailyTimesheetUser = dailyTimesheet.getDailyTimesheetUser();
    List<Long> taskIdList = new ArrayList<>();
    List<Long> projectIdList = new ArrayList<>();

    List<TimesheetLine> dailyTimesheetLineList = dailyTimesheet.getDailyTimesheetLineList();

    if (CollectionUtils.isNotEmpty(dailyTimesheetLineList)) {

      for (TimesheetLine timesheetLine : dailyTimesheetLineList) {

        if (timesheetLine.getProject() != null && timesheetLine.getProjectTask() != null) {
          taskIdList.add(timesheetLine.getProjectTask().getId());
        } else if (timesheetLine.getProject() != null && timesheetLine.getProjectTask() == null) {
          projectIdList.add(timesheetLine.getProject().getId());
        }
      }
    }

    // Update from activities

    List<MailMessage> mailMessageList =
        mailMessageRepository
            .all()
            .filter(
                "self.relatedModel = ?1 and self.author = ?2 and date(self.createdOn) = ?3",
                ProjectTask.class.getName(),
                dailyTimesheetUser,
                dailyTimesheetDate)
            .fetch();

    for (MailMessage mailMessage : mailMessageList) {

      if (!taskIdList.contains(mailMessage.getRelatedId())) {
        ProjectTask projectTask = projectTaskaRepo.find(mailMessage.getRelatedId());

        if (projectTask != null) {
          createTimesheetLine(projectTask, projectTask.getProject(), dailyTimesheet, null, null);
          taskIdList.add(mailMessage.getRelatedId());
        }
      }
    }

    // Update from favourites

    Set<ProjectTask> favouriteTaskSet = dailyTimesheetUser.getFavouriteTaskSet();

    if (CollectionUtils.isNotEmpty(favouriteTaskSet)) {

      for (ProjectTask projectTask : favouriteTaskSet) {

        if (!taskIdList.contains(projectTask.getId())) {
          createTimesheetLine(projectTask, projectTask.getProject(), dailyTimesheet, null, null);
          taskIdList.add(projectTask.getId());
        }
      }
    }

    Set<Project> favouriteProjectSet = dailyTimesheetUser.getFavouriteProjectSet();

    if (CollectionUtils.isNotEmpty(favouriteProjectSet)) {

      for (Project project : favouriteProjectSet) {

        if (!projectIdList.contains(project.getId())) {
          createTimesheetLine(null, project, dailyTimesheet, null, null);
          projectIdList.add(project.getId());
        }
      }
    }
  }

  @Override
  public void updateFromEvents(DailyTimesheet dailyTimesheet) {

    LocalDate dailyTimesheetDate = dailyTimesheet.getDailyTimesheetDate();
    User dailyTimesheetUser = dailyTimesheet.getDailyTimesheetUser();
    List<TimesheetLine> dailyTimesheetLineList = dailyTimesheet.getDailyTimesheetLineList();
    String idString = "0";

    if (CollectionUtils.isNotEmpty(dailyTimesheetLineList)) {
      List<Long> idList = new ArrayList<>();

      for (TimesheetLine dailyTimesheetLine : dailyTimesheetLineList) {

        if (dailyTimesheetLine.getiCalendarEvent() != null) {
          idList.add(dailyTimesheetLine.getiCalendarEvent().getId());
        }
      }

      if (CollectionUtils.isNotEmpty(idList)) {
        idString = idList.stream().map(l -> l.toString()).collect(Collectors.joining(","));
      }
    }

    List<ICalendarEvent> iCalendarEventList =
        iCalendarEventRepository
            .all()
            .filter(
                "self.id not in ("
                    + idString
                    + ") and (self.user = ?1 or self.organizer.user = ?1 or self.attendees.user in (?1)) and date(self.startDateTime) <= ?2 and date(self.endDateTime) >= ?2",
                dailyTimesheetUser,
                dailyTimesheetDate)
            .fetch();

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyy HH:mm");

    for (ICalendarEvent iCalendarEvent : iCalendarEventList) {
      createTimesheetLine(
          null,
          null,
          dailyTimesheet,
          iCalendarEvent.getSubject()
              + ": "
              + formatter.format(iCalendarEvent.getStartDateTime())
              + " - "
              + formatter.format(iCalendarEvent.getEndDateTime()),
          iCalendarEvent);
    }
  }

  @Transactional
  public void createTimesheetLine(
      ProjectTask projectTask,
      Project project,
      DailyTimesheet dailyTimesheet,
      String comments,
      ICalendarEvent iCalendarEvent) {

    LocalDate dailyTimesheetDate = dailyTimesheet.getDailyTimesheetDate();
    User dailyTimesheetUser = dailyTimesheet.getDailyTimesheetUser();

    TimesheetLine timesheetLine =
        timesheetLineService.createTimesheetLine(
            project, null, dailyTimesheetUser, dailyTimesheetDate, null, BigDecimal.ZERO, comments);

    timesheetLine.setDailyTimesheet(dailyTimesheet);
    timesheetLine.setTimesheet(dailyTimesheet.getTimesheet());
    timesheetLine.setProjectTask(projectTask);
    timesheetLine.setDurationForCustomer(timesheetLine.getDuration());

    if (iCalendarEvent != null) {
      timesheetLine.setiCalendarEvent(iCalendarEvent);
    }

    if (projectTask != null) {
      timesheetLine.setActivityTypeSelect(TimesheetLineRepository.ACTIVITY_TYPE_ON_TICKET);
    }

    if (dailyTimesheetUser.getEmployee() != null) {
      timesheetLine.setProduct(dailyTimesheetUser.getEmployee().getProduct());
    }

    timesheetLineRepository.save(timesheetLine);
  }

  @Override
  @Transactional
  public Timesheet getRelatedTimesheet(DailyTimesheet dailyTimesheet) {

    LocalDate dailyTimesheetDate = dailyTimesheet.getDailyTimesheetDate();
    User dailyTimesheetUser = dailyTimesheet.getDailyTimesheetUser();

    Timesheet timesheet = fetchRelatedTimesheet(dailyTimesheetUser, dailyTimesheetDate);

    if (timesheet == null) {
      timesheet = timesheetService.createTimesheet(dailyTimesheetUser, dailyTimesheetDate, null);
      timesheetRepository.save(timesheet);
    } else if (timesheet.getToDate() != null
        && timesheet.getToDate().isBefore(dailyTimesheetDate)) {
      timesheet.setToDate(dailyTimesheetDate);
      timesheetRepository.save(timesheet);
    }

    return timesheet;
  }

  public Timesheet fetchRelatedTimesheet(User dailyTimesheetUser, LocalDate dailyTimesheetDate) {

    return timesheetRepository
        .all()
        .filter(
            "self.user = ?1 AND self.statusSelect != ?2 AND self.fromDate <= ?3",
            dailyTimesheetUser,
            TimesheetRepository.STATUS_CANCELED,
            dailyTimesheetDate)
        .order("-fromDate")
        .fetchOne();
  }

  @Transactional
  public void computeTimesheetPeriodTotal(Timesheet timesheet) {

    timesheet.setPeriodTotal(timesheetService.computePeriodTotal(timesheet));
    timesheetRepository.save(timesheet);
  }
}
