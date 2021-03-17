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
package com.axelor.apps.office365.service;

import com.axelor.apps.base.db.ICalendar;
import com.axelor.apps.base.db.ICalendarUser;
import com.axelor.apps.base.db.repo.ICalendarEventRepository;
import com.axelor.apps.base.db.repo.ICalendarRepository;
import com.axelor.apps.base.db.repo.ICalendarUserRepository;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.crm.db.Event;
import com.axelor.apps.crm.db.EventReminder;
import com.axelor.apps.crm.db.RecurrenceConfiguration;
import com.axelor.apps.crm.db.repo.EventReminderRepository;
import com.axelor.apps.crm.db.repo.EventRepository;
import com.axelor.apps.crm.db.repo.RecurrenceConfigurationRepository;
import com.axelor.apps.crm.service.EventService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class Office365CalendarService {

  @Inject private EventRepository eventRepo;
  @Inject private ICalendarRepository iCalendarRepo;
  @Inject private ICalendarUserRepository iCalendarUserRepo;
  @Inject private EventReminderRepository eventReminderRepo;
  @Inject private RecurrenceConfigurationRepository recurrenceConfigurationRepo;
  @Inject private UserRepository userRepo;

  @SuppressWarnings("unchecked")
  @Transactional
  public ICalendar createCalendar(JSONObject jsonObject, String login) {

    ICalendar iCalendar = null;
    if (jsonObject != null) {
      try {
        iCalendar = iCalendarRepo.findByOffice365Id(jsonObject.getOrDefault("id", "").toString());
        if (iCalendar == null) {
          iCalendar = new ICalendar();
          iCalendar.setOffice365Id(jsonObject.getOrDefault("id", "").toString());
        }
        iCalendar.setName(jsonObject.getOrDefault("name", "").toString());

        JSONObject ownerObject = jsonObject.getJSONObject("owner");
        if (ownerObject != null) {
          String ownerName = ownerObject.getOrDefault("name", "").toString();
          String emailAddressStr = ownerObject.getOrDefault("address", "").toString();
          String code = ownerName.replaceAll("[^a-zA-Z0-9]", "");
          User user = userRepo.findByCode(code);
          if (user == null) {
            user = new User();
            user.setName(ownerName);
            user.setCode(code);
            user.setEmail(emailAddressStr);
            user.setPassword(code);
          }
          iCalendar.setUrl(Office365Service.CALENDAR_URL);
          iCalendar.setUser(user);
          iCalendar.setLogin(login);
        }
        iCalendarRepo.save(iCalendar);
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }

    return iCalendar;
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public void createEvent(JSONObject jsonObject, ICalendar iCalendar) {

    if (jsonObject != null) {
      try {
        Event event = eventRepo.findByOffice365Id(jsonObject.getOrDefault("id", "").toString());

        if (event == null) {
          event = new Event();
          event.setOffice365Id(jsonObject.getOrDefault("id", "").toString());
          event.setTypeSelect(EventRepository.TYPE_EVENT);
        }

        event.setSubject(jsonObject.getOrDefault("subject", "").toString());
        event.setAllDay((Boolean) jsonObject.getOrDefault("isAllDay", false));

        if ((boolean) jsonObject.getOrDefault("isCancelled", false)) {
          event.setStatusSelect(EventRepository.STATUS_CANCELED);
        } else {
          event.setStatusSelect(EventRepository.STATUS_PLANNED);
        }

        JSONObject startObject = jsonObject.getJSONObject("start");
        event.setStartDateTime(getLocalDateTime(startObject));
        JSONObject endObject = jsonObject.getJSONObject("start");
        event.setEndDateTime(getLocalDateTime(endObject));

        JSONObject bodyObject = jsonObject.getJSONObject("body");
        if (bodyObject != null) {
          event.setDescription(bodyObject.getOrDefault("content", "").toString());
        }

        String sensitivity = jsonObject.getOrDefault("sensitivity", "").toString();
        Integer visibilitySelect = 0;
        if ("normal".equalsIgnoreCase(sensitivity)) {
          visibilitySelect = ICalendarEventRepository.VISIBILITY_PUBLIC;
        } else if ("private".equalsIgnoreCase(sensitivity)) {
          visibilitySelect = ICalendarEventRepository.VISIBILITY_PRIVATE;
        }
        event.setVisibilitySelect(visibilitySelect);

        String showAs = jsonObject.getOrDefault("showAs", "").toString();
        Integer disponibilitySelect = 0;
        if ("busy".equalsIgnoreCase(showAs)) {
          disponibilitySelect = ICalendarEventRepository.DISPONIBILITY_BUSY;
        } else if ("free".equalsIgnoreCase(showAs)) {
          disponibilitySelect = ICalendarEventRepository.DISPONIBILITY_AVAILABLE;
        } else if ("oof".equalsIgnoreCase(showAs)) {
          disponibilitySelect = ICalendarEventRepository.DISPONIBILITY_AWAY;
        } else if ("tentative".equalsIgnoreCase(showAs)) {
          disponibilitySelect = ICalendarEventRepository.DISPONIBILITY_TENTATIVE;
        } else if ("workingElsewhere".equalsIgnoreCase(showAs)) {
          disponibilitySelect = ICalendarEventRepository.DISPONIBILITY_WORKING_ELSEWHERE;
        }
        event.setDisponibilitySelect(disponibilitySelect);

        setEventLocation(event, jsonObject);
        setICalendarUser(event, jsonObject);
        manageReminder(event, jsonObject);
        manageRecurrenceConfigration(event, jsonObject);

        event.setCalendar(iCalendar);
        event.setUser(Beans.get(UserService.class).getUser());
        eventRepo.save(event);
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void setEventLocation(Event event, JSONObject jsonObject) throws JSONException {

    String location = "";
    JSONObject locationObject = jsonObject.getJSONObject("location");
    if (locationObject != null && locationObject.containsKey("address")) {
      JSONObject addressObject = locationObject.getJSONObject("address");
      if (addressObject != null) {
        location += addressObject.getOrDefault("street", "").toString();
        location += " " + addressObject.getOrDefault("city", "").toString();
        location += " " + addressObject.getOrDefault("postalCode", "").toString();
        location += " " + addressObject.getOrDefault("state", "").toString();
        location += " " + addressObject.getOrDefault("countryOrRegion", "").toString();
        event.setLocation(location.trim());
      }

      JSONObject coordinateObject = locationObject.getJSONObject("coordinates");
      if (coordinateObject != null && coordinateObject.containsKey("latitude")) {
        String latitude = coordinateObject.getOrDefault("latitude", "").toString();
        String longitude = coordinateObject.getOrDefault("longitude", "").toString();
        event.setGeo(latitude + "," + longitude);
      }
    }
  }

  private void setICalendarUser(Event event, JSONObject jsonObject) throws JSONException {

    JSONArray attendeesArr = jsonObject.getJSONArray("attendees");
    if (attendeesArr != null) {
      for (Object object : attendeesArr) {
        JSONObject attendeeObj = (JSONObject) object;
        @SuppressWarnings("unchecked")
        String type = attendeeObj.getOrDefault("type", "").toString();
        JSONObject emailAddressObj = attendeeObj.getJSONObject("emailAddress");
        ICalendarUser iCalendarUser = getICalendarUser(emailAddressObj, type);
        event.addAttendee(iCalendarUser);
      }
    }

    JSONObject organizerObj = jsonObject.getJSONObject("organizer");
    if (organizerObj != null) {
      JSONObject emailAddressObj = organizerObj.getJSONObject("emailAddress");
      ICalendarUser iCalendarUser = getICalendarUser(emailAddressObj, null);
      event.setOrganizer(iCalendarUser);
    }
  }

  private void manageReminder(Event event, JSONObject jsonObject) {

    @SuppressWarnings("unchecked")
    Integer reminderMinutes = (Integer) jsonObject.getOrDefault("reminderMinutesBeforeStart", null);
    if (reminderMinutes != null) {
      EventReminder eventReminder =
          eventReminderRepo
              .all()
              .filter(
                  "self.modeSelect = ?1 AND self.typeSelect = 1 AND self.durationTypeSelect = ?2 AND self.duration = ?3",
                  EventReminderRepository.MODE_BEFORE_DATE,
                  EventReminderRepository.DURATION_TYPE_MINUTES,
                  reminderMinutes)
              .fetchOne();
      if (eventReminder == null) {
        eventReminder = new EventReminder();
        eventReminder.setModeSelect(EventReminderRepository.MODE_BEFORE_DATE);
        eventReminder.setTypeSelect(1);
        eventReminder.setDuration(reminderMinutes);
        eventReminder.setDurationTypeSelect(EventReminderRepository.DURATION_TYPE_MINUTES);
        eventReminder.setAssignToSelect(EventReminderRepository.ASSIGN_TO_ME);
        eventReminder.setUser(AuthUtils.getUser());
        eventReminderRepo.save(eventReminder);
      }
      if (event.getEventReminderList() == null) {
        event.setEventReminderList(new ArrayList<>());
      }
      if (!event.getEventReminderList().contains(eventReminder)) {
        event.addEventReminderListItem(eventReminder);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void manageRecurrenceConfigration(Event event, JSONObject jsonObject)
      throws JSONException {

    if (jsonObject.containsKey("recurrence")
        && !jsonObject.get("recurrence").equals(JSONObject.NULL)) {
      JSONObject reminderRecurrenceObj = jsonObject.getJSONObject("recurrence");
      if (reminderRecurrenceObj != null) {

        JSONObject patternObj = reminderRecurrenceObj.getJSONObject("pattern");
        JSONObject rangeObj = reminderRecurrenceObj.getJSONObject("range");

        RecurrenceConfiguration recurrenceConfiguration = new RecurrenceConfiguration();
        recurrenceConfiguration.setEndType(RecurrenceConfigurationRepository.END_TYPE_DATE);

        Integer recurrenceType = 0;
        if (patternObj != null) {
          switch (patternObj.getOrDefault("type", "").toString()) {
            case "daily":
              recurrenceType = RecurrenceConfigurationRepository.TYPE_DAY;
              break;
            case "weekly":
              recurrenceType = RecurrenceConfigurationRepository.TYPE_WEEK;
              break;
            case "absoluteMonthly":
            case "relativeMonthly":
              recurrenceType = RecurrenceConfigurationRepository.TYPE_MONTH;
              break;
            case "absoluteYearly":
            case "relativeYearly":
              recurrenceType = RecurrenceConfigurationRepository.TYPE_YEAR;
              break;
          }
          recurrenceConfiguration.setRecurrenceType(recurrenceType);

          Integer periodicity = (Integer) patternObj.getOrDefault("interval", null);
          recurrenceConfiguration.setPeriodicity(periodicity);

          if (patternObj.containsKey("dayOfWeek")) {
            JSONArray dayOfWeekArr = patternObj.getJSONArray("dayOfWeek");
            if (dayOfWeekArr != null) {

              recurrenceConfiguration.setMonthRepeatType(
                  RecurrenceConfigurationRepository.REPEAT_TYPE_WEEK);
            } else {
              recurrenceConfiguration.setMonthRepeatType(
                  RecurrenceConfigurationRepository.REPEAT_TYPE_MONTH);
            }
          }
        }

        if (rangeObj != null) {
          LocalDate startDate = LocalDate.parse(rangeObj.getOrDefault("startDate", "").toString());
          LocalDate endDate = LocalDate.parse(rangeObj.getOrDefault("endDate", "").toString());
          recurrenceConfiguration.setStartDate(startDate);
          recurrenceConfiguration.setEndDate(endDate);
        }
        event.setRecurrenceConfiguration(recurrenceConfiguration);
        recurrenceConfiguration.setRecurrenceName(
            Beans.get(EventService.class).computeRecurrenceName(recurrenceConfiguration));
        recurrenceConfigurationRepo.save(recurrenceConfiguration);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private ICalendarUser getICalendarUser(JSONObject emailAddressObj, String type) {

    ICalendarUser iCalendarUser = null;
    if (emailAddressObj != null) {
      String address = emailAddressObj.getOrDefault("address", "").toString();
      iCalendarUser = iCalendarUserRepo.findByEmail(address);

      if (iCalendarUser == null) {
        iCalendarUser = new ICalendarUser();
        iCalendarUser.setEmail(address);
        User user = userRepo.findByEmail(address);
        iCalendarUser.setUser(user);
      }
      iCalendarUser.setName(emailAddressObj.getOrDefault("name", "").toString());

      if (!StringUtils.isBlank(type)) {
        switch (type) {
          case "required":
            iCalendarUser.setStatusSelect(ICalendarUserRepository.STATUS_REQUIRED);
            break;
          case "optional":
            iCalendarUser.setStatusSelect(ICalendarUserRepository.STATUS_OPTIONAL);
            break;
        }
      }
      iCalendarUserRepo.save(iCalendarUser);
    }
    return iCalendarUser;
  }

  @SuppressWarnings("unchecked")
  private LocalDateTime getLocalDateTime(JSONObject jsonObject) {

    LocalDateTime eventTime = null;
    try {
      if (jsonObject != null) {
        String dateStr = jsonObject.getOrDefault("dateTime", "").toString();
        String timeZone = jsonObject.getOrDefault("timeZone", "").toString();
        if (!StringUtils.isBlank(dateStr) && !StringUtils.isBlank(dateStr)) {
          DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS");
          eventTime =
              LocalDateTime.parse(dateStr, format).atZone(ZoneId.of(timeZone)).toLocalDateTime();
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
    return eventTime;
  }
}
