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
  public void updateFromTimesheet(DailyTimesheet dailyTimesheet) {

    List<TimesheetLine> timesheetLineList = dailyTimesheet.getTimesheet().getTimesheetLineList();

    if (CollectionUtils.isNotEmpty(timesheetLineList)) {

      for (TimesheetLine timesheetLine : timesheetLineList) {

        if (timesheetLine.getDate().compareTo(dailyTimesheet.getDailyTimesheetDate()) == 0
            && (timesheetLine.getDailyTimesheet() == null
                || !timesheetLine.getDailyTimesheet().equals(dailyTimesheet))) {
          dailyTimesheet.addDailyTimesheetLineListItem(timesheetLine);
        }
      }
    }
  }

  @Override
  public int updateFromActivities(DailyTimesheet dailyTimesheet) {

    List<Long> taskIdList = new ArrayList<>();

    List<TimesheetLine> dailyTimesheetLineList = dailyTimesheet.getDailyTimesheetLineList();

    if (CollectionUtils.isNotEmpty(dailyTimesheetLineList)) {

      for (TimesheetLine timesheetLine : dailyTimesheetLineList) {

        if (timesheetLine.getTeamTask() != null) {
          taskIdList.add(timesheetLine.getTeamTask().getId());
        }
      }
    }

    List<MailMessage> mailMessageList =
        mailMessageRepository
            .all()
            .filter(
                "self.relatedModel = ?1 and self.author = ?2 and date(self.createdOn) = ?3",
                TeamTask.class.getName(),
                dailyTimesheet.getDailyTimesheetUser(),
                dailyTimesheet.getDailyTimesheetDate())
            .fetch();

    int count = 0;

    for (MailMessage mailMessage : mailMessageList) {

      if (!taskIdList.contains(mailMessage.getRelatedId())) {
        TeamTask teamTask = teamTaskRepository.find(mailMessage.getRelatedId());

        if (teamTask != null) {
          dailyTimesheet.addDailyTimesheetLineListItem(
              createTimesheetLine(teamTask, teamTask.getProject(), dailyTimesheet, null, null));
          taskIdList.add(mailMessage.getRelatedId());
          count++;
        }
      }
    }

    return count;
  }

  @Override
  public void updateFromTimesheetAndFavs(DailyTimesheet dailyTimesheet) {

    // Set related timesheet

    dailyTimesheet.setTimesheet(getRelatedTimesheet(dailyTimesheet));

    // Update from related timesheet before favourites

    updateFromTimesheet(dailyTimesheet);

    // Update from favourites

    List<Long> taskIdList = new ArrayList<>();
    List<Long> projectIdList = new ArrayList<>();

    List<TimesheetLine> dailyTimesheetLineList = dailyTimesheet.getDailyTimesheetLineList();

    if (CollectionUtils.isNotEmpty(dailyTimesheetLineList)) {

      for (TimesheetLine timesheetLine : dailyTimesheetLineList) {

        if (timesheetLine.getTeamTask() != null) {
          taskIdList.add(timesheetLine.getTeamTask().getId());
        }

        if (timesheetLine.getProject() != null) {
          projectIdList.add(timesheetLine.getProject().getId());
        }
      }
    }

    Set<TeamTask> favouriteTaskSet = dailyTimesheet.getDailyTimesheetUser().getFavouriteTaskSet();

    if (CollectionUtils.isNotEmpty(favouriteTaskSet)) {

      for (TeamTask teamTask : favouriteTaskSet) {

        if (!taskIdList.contains(teamTask.getId())) {
          dailyTimesheet.addDailyTimesheetLineListItem(
              createTimesheetLine(teamTask, teamTask.getProject(), dailyTimesheet, null, null));
          projectIdList.add(teamTask.getProject().getId());
        }
      }
    }

    Set<Project> favouriteProjectSet =
        dailyTimesheet.getDailyTimesheetUser().getFavouriteProjectSet();

    if (CollectionUtils.isNotEmpty(favouriteProjectSet)) {

      for (Project project : favouriteProjectSet) {

        if (!projectIdList.contains(project.getId())) {
          dailyTimesheet.addDailyTimesheetLineListItem(
              createTimesheetLine(null, project, dailyTimesheet, null, null));
        }
      }
    }
  }

  @Override
  public int updateFromEvents(DailyTimesheet dailyTimesheet) {

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
    int count = 0;

    for (ICalendarEvent iCalendarEvent : iCalendarEventList) {
      dailyTimesheet.addDailyTimesheetLineListItem(
          createTimesheetLine(
              null,
              null,
              dailyTimesheet,
              iCalendarEvent.getSubject()
                  + ": "
                  + formatter.format(iCalendarEvent.getStartDateTime())
                  + " - "
                  + formatter.format(iCalendarEvent.getEndDateTime()),
              iCalendarEvent));
      count++;
    }

    dailyTimesheet.setDailyTotal(computeDailyTotal(dailyTimesheet));

    return count;
  }

  public TimesheetLine createTimesheetLine(
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
    timesheetLine.setToInvoice(false);

    if (teamTask != null) {
      timesheetLine.setTeamTask(teamTask);
      timesheetLine.setActivityTypeSelect(TimesheetLineRepository.ACTIVITY_TYPE_ON_TICKET);

      if (teamTask.getInvoicingType() != null
          && teamTask.getInvoicingType().equals(TeamTaskRepository.INVOICING_TYPE_TIME_SPENT)
          && teamTask.getToInvoice()) {
        timesheetLine.setToInvoice(true);
      }
    } else {
      timesheetLine.setActivityTypeSelect(
          TimesheetLineRepository.ACTIVITY_TYPE_ON_PROJECT_OR_PROJECT_MANAGEMENT);
    }

    return timesheetLine;
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

    Timesheet timesheet =
        timesheetRepository
            .all()
            .filter(
                "self.user = ?1 AND self.statusSelect != ?2 AND self.fromDate <= ?3",
                dailyTimesheetUser,
                TimesheetRepository.STATUS_CANCELED,
                dailyTimesheetDate)
            .order("-fromDate")
            .fetchOne();

    if (timesheet == null) {
      timesheet = timesheetService.createTimesheet(dailyTimesheetUser, dailyTimesheetDate, null);
      timesheetRepository.save(timesheet);
    }

    return timesheet;
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
