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
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.service.timesheet.TimesheetLineService;
import com.axelor.apps.hr.service.timesheet.TimesheetService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.tool.date.DurationTool;
import com.axelor.auth.db.User;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
  public TeamTaskRepository teamTaskRepository;
  public TimesheetLineService timesheetLineService;
  public ICalendarEventRepository iCalendarEventRepository;
  public TimesheetRepository timesheetRepository;
  public TimesheetService timesheetService;

  @Inject
  public DailyTimesheetServiceImpl(
      AppBaseService appBaseService,
      TimesheetLineRepository timesheetLineRepository,
      MailMessageRepository mailMessageRepository,
      TeamTaskRepository teamTaskRepository,
      TimesheetLineService timesheetLineService,
      ICalendarEventRepository iCalendarEventRepository,
      TimesheetRepository timesheetRepository,
      TimesheetService timesheetService) {
    this.appBaseService = appBaseService;
    this.timesheetLineRepository = timesheetLineRepository;
    this.mailMessageRepository = mailMessageRepository;
    this.teamTaskRepository = teamTaskRepository;
    this.timesheetLineService = timesheetLineService;
    this.iCalendarEventRepository = iCalendarEventRepository;
    this.timesheetRepository = timesheetRepository;
    this.timesheetService = timesheetService;
  }

  private final BigDecimal MINUTES_IN_ONE_DAY = new BigDecimal(1440);
  private final BigDecimal HOURS_IN_ONE_DAY = new BigDecimal(24);
  private final BigDecimal MINUTES_IN_ONE_HOUR = new BigDecimal(60);

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

    dailyTimesheet.setDailyTotal(computeDailyTotal(dailyTimesheet));
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

        if (timesheetLine.getProject() != null && timesheetLine.getTeamTask() != null) {
          taskIdList.add(timesheetLine.getTeamTask().getId());
        } else if (timesheetLine.getProject() != null && timesheetLine.getTeamTask() == null) {
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
                TeamTask.class.getName(),
                dailyTimesheetUser,
                dailyTimesheetDate)
            .fetch();

    for (MailMessage mailMessage : mailMessageList) {

      if (!taskIdList.contains(mailMessage.getRelatedId())) {
        TeamTask teamTask = teamTaskRepository.find(mailMessage.getRelatedId());

        if (teamTask != null) {
          createTimesheetLine(teamTask, teamTask.getProject(), dailyTimesheet, null, null);
          taskIdList.add(mailMessage.getRelatedId());
        }
      }
    }

    // Update from favourites

    Set<TeamTask> favouriteTaskSet = dailyTimesheetUser.getFavouriteTaskSet();

    if (CollectionUtils.isNotEmpty(favouriteTaskSet)) {

      for (TeamTask teamTask : favouriteTaskSet) {

        if (!taskIdList.contains(teamTask.getId())) {
          createTimesheetLine(teamTask, teamTask.getProject(), dailyTimesheet, null, null);
          taskIdList.add(teamTask.getId());
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
      TeamTask teamTask,
      Project project,
      DailyTimesheet dailyTimesheet,
      String comments,
      ICalendarEvent iCalendarEvent) {

    LocalDate dailyTimesheetDate = dailyTimesheet.getDailyTimesheetDate();
    User dailyTimesheetUser = dailyTimesheet.getDailyTimesheetUser();

    TimesheetLine timesheetLine =
        timesheetLineService.createTimesheetLine(
            project,
            dailyTimesheetUser.getEmployee() != null
                ? dailyTimesheetUser.getEmployee().getProduct()
                : null,
            dailyTimesheetUser,
            dailyTimesheetDate,
            dailyTimesheet.getTimesheet(),
            BigDecimal.ZERO,
            comments);

    timesheetLine.setDailyTimesheet(dailyTimesheet);
    timesheetLine.setTeamTask(teamTask);

    if (iCalendarEvent != null) {
      timesheetLine.setiCalendarEvent(iCalendarEvent);

      BigDecimal minuteDuration =
          BigDecimal.valueOf(
              DurationTool.computeDuration(
                      iCalendarEvent.getStartDateTime(), iCalendarEvent.getEndDateTime())
                  .toMinutes());

      if (minuteDuration.compareTo(MINUTES_IN_ONE_DAY) < 0) {
        timesheetLine.setHoursDuration(
            minuteDuration.divide(MINUTES_IN_ONE_HOUR, 2, RoundingMode.HALF_UP));
        timesheetLine.setDuration(
            computeDurationFromHours(
                dailyTimesheetUser,
                timesheetLine.getHoursDuration(),
                dailyTimesheet.getTimesheet().getTimeLoggingPreferenceSelect()));
      }
    }

    timesheetLine.setDurationForCustomer(timesheetLine.getDuration());

    if (teamTask != null) {
      timesheetLine.setActivityTypeSelect(TimesheetLineRepository.ACTIVITY_TYPE_ON_TICKET);
    }

    timesheetLineRepository.save(timesheetLine);
  }

  public BigDecimal computeDurationFromHours(User user, BigDecimal duration, String timePref) {

    if (timePref == null && user.getEmployee() != null) {
      timePref = user.getEmployee().getTimeLoggingPreferenceSelect();
    }

    switch (timePref) {
      case EmployeeRepository.TIME_PREFERENCE_DAYS:
        return duration.divide(HOURS_IN_ONE_DAY, 2, RoundingMode.HALF_UP);
      case EmployeeRepository.TIME_PREFERENCE_MINUTES:
        return duration.multiply(MINUTES_IN_ONE_HOUR);
      default:
        return duration;
    }
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

  @Override
  public BigDecimal computeDailyTotal(DailyTimesheet dailyTimesheet) {

    List<TimesheetLine> dailyTimesheetLineList = dailyTimesheet.getDailyTimesheetLineList();

    if (CollectionUtils.isNotEmpty(dailyTimesheetLineList)) {

      return dailyTimesheetLineList.stream()
          .map(dt -> dt.getHoursDuration())
          .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    return BigDecimal.ZERO;
  }
}
