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
import com.axelor.apps.base.db.ICalendarEvent;
import com.axelor.apps.base.db.ICalendarUser;
import com.axelor.apps.base.db.repo.ICalendarEventRepository;
import com.axelor.apps.base.db.repo.ICalendarRepository;
import com.axelor.apps.base.db.repo.ICalendarUserRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.crm.db.Event;
import com.axelor.apps.crm.db.EventCategory;
import com.axelor.apps.crm.db.EventReminder;
import com.axelor.apps.crm.db.RecurrenceConfiguration;
import com.axelor.apps.crm.db.repo.EventCategoryRepository;
import com.axelor.apps.crm.db.repo.EventReminderRepository;
import com.axelor.apps.crm.db.repo.EventRepository;
import com.axelor.apps.crm.db.repo.RecurrenceConfigurationRepository;
import com.axelor.apps.crm.service.EventService;
import com.axelor.apps.office.db.OfficeAccount;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class Office365CalendarService {

  @Inject private Office365Service office365Service;

  @Inject private ICalendarEventRepository eventRepo;
  @Inject private EventCategoryRepository eventCategoryRepo;
  @Inject private ICalendarRepository iCalendarRepo;
  @Inject private ICalendarUserRepository iCalendarUserRepo;
  @Inject private EventReminderRepository eventReminderRepo;
  @Inject private RecurrenceConfigurationRepository recurrenceConfigurationRepo;
  @Inject private UserRepository userRepo;

  private static final String DEFAULT_CAL_NAME = "AOS_Office365 Calendar";

  @Transactional
  public ICalendar createCalendar(
      JSONObject jsonObject, OfficeAccount officeAccount, LocalDateTime lastSyncOn) {

    if (jsonObject == null) {
      return null;
    }

    ICalendar iCalendar = null;
    try {
      String office365Id = office365Service.processJsonValue("id", jsonObject);
      iCalendar = iCalendarRepo.findByOffice365Id(office365Id);
      if (iCalendar == null) {
        iCalendar = new ICalendar();
        iCalendar.setOffice365Id(office365Id);
        iCalendar.setOfficeAccount(officeAccount);
        iCalendar.setTypeSelect(ICalendarRepository.OFFICE_365);
        iCalendar.setSynchronizationSelect(ICalendarRepository.CRM_SYNCHRO);
        iCalendar.setSynchronizationDuration(5);
      }

      iCalendar.setName(office365Service.processJsonValue("name", jsonObject));
      setCalendarOwer(jsonObject, iCalendar);
      iCalendarRepo.save(iCalendar);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    return iCalendar;
  }

  @Transactional
  public void createOffice365Calendar(
      ICalendar calendar, OfficeAccount officeAccount, String accessToken) {

    try {
      JSONObject calendarJsonObject = new JSONObject();
      office365Service.putObjValue(calendarJsonObject, "name", calendar.getName());
      office365Service.putUserEmailAddress(calendar.getUser(), calendarJsonObject, "owner");

      String office365Id =
          office365Service.createOffice365Object(
              Office365Service.CALENDAR_URL,
              calendarJsonObject,
              accessToken,
              calendar.getOffice365Id(),
              "calendars");

      calendar.setOffice365Id(office365Id);
      calendar.setOfficeAccount(officeAccount);
      iCalendarRepo.save(calendar);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  @Transactional
  public void createEvent(
      JSONObject jsonObject,
      OfficeAccount officeAccount,
      ICalendar iCalendar,
      ICalendar defaultCalendar,
      LocalDateTime lastSyncOn,
      LocalDateTime now) {

    if (jsonObject == null) {
      return;
    }

    try {
      LocalDateTime eventStart = getLocalDateTime(jsonObject.getJSONObject("start"));
      LocalDateTime eventEnd = getLocalDateTime(jsonObject.getJSONObject("end"));
      if (iCalendar.getSynchronizationDuration() > 0) {
        LocalDateTime start = now.minusWeeks(iCalendar.getSynchronizationDuration());
        LocalDateTime end = now.plusWeeks(iCalendar.getSynchronizationDuration());
        if (!isDateWithinRange(eventStart, start, end)
            && !isDateWithinRange(eventEnd, start, end)) {
          return;
        }
      }

      String office365Id = office365Service.processJsonValue("id", jsonObject);
      ICalendarEvent event = eventRepo.findByOffice365Id(office365Id);
      if (event == null) {
        event = new Event();
        event.setOffice365Id(office365Id);
        event.setTypeSelect(EventRepository.TYPE_EVENT);

      } else if (!iCalendar.getKeepRemote()
          && !office365Service.needUpdation(
              jsonObject, lastSyncOn, event.getCreatedOn(), event.getUpdatedOn())) {
        return;
      }

      setEventValues(
          jsonObject,
          event,
          iCalendar,
          defaultCalendar,
          officeAccount.getOwnerUser(),
          eventStart,
          eventEnd);
      eventRepo.save(event);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  @Transactional
  public void createOffice365Event(
      ICalendarEvent event,
      OfficeAccount officeAccount,
      String accessToken,
      User currentUser,
      ICalendar defaultCalendar,
      LocalDateTime now) {

    try {
      ICalendar calendar = event.getCalendar();
      String calendarId = getCalendarOffice365Id(calendar, defaultCalendar);
      if (calendarId == null) {
        return;
      }

      if (calendar.getSynchronizationDuration() > 0) {
        LocalDateTime start = now.minusWeeks(calendar.getSynchronizationDuration());
        LocalDateTime end = now.plusWeeks(calendar.getSynchronizationDuration());
        if (!isDateWithinRange(event.getStartDateTime(), start, end)
            && !isDateWithinRange(event.getEndDateTime(), start, end)) {
          return;
        }
      }

      JSONObject eventJsonObject = setOffice365EventValues(event, currentUser);
      String urlStr = String.format(Office365Service.EVENT_URL, calendarId);
      String office365Id =
          office365Service.createOffice365Object(
              urlStr, eventJsonObject, accessToken, event.getOffice365Id(), "events");
      event.setOffice365Id(office365Id);
      eventRepo.save(event);

      calendar.setLastSynchronizationDateT(
          Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime());
      iCalendarRepo.save(calendar);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  public String getCalendarOffice365Id(ICalendar calendar, ICalendar defaultCalendar) {

    String calendarId = null;

    if (calendar != null && StringUtils.isNotBlank(calendar.getOffice365Id())) {
      calendarId = calendar.getOffice365Id();
    }

    if (calendarId == null
        && defaultCalendar != null
        && StringUtils.isNotBlank(defaultCalendar.getOffice365Id())) {
      calendar = defaultCalendar;
      calendarId = defaultCalendar.getOffice365Id();
    }

    return calendarId;
  }

  public ICalendar manageDefaultCalendar(
      List<ICalendarEvent> eventList, OfficeAccount officeAccount, String accessToken) {

    ICalendar defaultCalendar = null;
    if (ObjectUtils.notEmpty(eventList)
        && eventList.stream().anyMatch(event -> event.getCalendar() == null)) {
      defaultCalendar = Beans.get(ICalendarRepository.class).findByName(DEFAULT_CAL_NAME);
      if (defaultCalendar == null) {
        defaultCalendar = new ICalendar();
        defaultCalendar.setName(DEFAULT_CAL_NAME);
        defaultCalendar.setUser(officeAccount.getOwnerUser());
        defaultCalendar.setTypeSelect(ICalendarRepository.OFFICE_365);
        defaultCalendar.setSynchronizationSelect(ICalendarRepository.CRM_SYNCHRO);
        defaultCalendar.setArchived(true);
        Beans.get(ICalendarRepository.class).save(defaultCalendar);
        createOffice365Calendar(defaultCalendar, officeAccount, accessToken);
      }
    }

    return defaultCalendar;
  }

  public List<ICalendarEvent> getICalendarEvents(
      ICalendar calendar, LocalDateTime lastSync, LocalDateTime start) {

    String queryStr =
        "(self.office365Id IS NULL OR COALESCE(self.calendar.keepRemote, false) = false) AND self.calendar = :calendar";
    if (lastSync == null) {
      queryStr = queryStr + " AND self.createdOn < :start";
      if (calendar.getSynchronizationSelect() != null
          && ICalendarRepository.CRM_SYNCHRO.equals(calendar.getSynchronizationSelect())) {
        return new ArrayList<ICalendarEvent>(
            Beans.get(EventRepository.class)
                .all()
                .filter(queryStr)
                .bind("calendar", calendar)
                .bind("start", start)
                .fetch());
      } else {
        return Beans.get(ICalendarEventRepository.class)
            .all()
            .filter(queryStr)
            .bind("calendar", calendar)
            .bind("start", start)
            .fetch();
      }
    } else {
      queryStr =
          queryStr + " AND COALESCE(self.updatedOn, self.createdOn) BETWEEN :lastSync AND :start";
      if (calendar.getSynchronizationSelect() != null
          && ICalendarRepository.CRM_SYNCHRO.equals(calendar.getSynchronizationSelect())) {
        return new ArrayList<ICalendarEvent>(
            Beans.get(EventRepository.class)
                .all()
                .filter(queryStr)
                .bind("calendar", calendar)
                .bind("start", start)
                .bind("lastSync", lastSync)
                .fetch());
      } else {
        return Beans.get(ICalendarEventRepository.class)
            .all()
            .filter(queryStr)
            .bind("calendar", calendar)
            .bind("start", start)
            .bind("lastSync", lastSync)
            .fetch();
      }
    }
  }

  private boolean isDateWithinRange(
      LocalDateTime dateTime, LocalDateTime start, LocalDateTime end) {

    if (dateTime == null) {
      return false;
    }

    return !(dateTime.isBefore(start) || dateTime.isAfter(end));
  }

  @SuppressWarnings("unchecked")
  private void setCalendarOwer(JSONObject jsonObject, ICalendar iCalendar) throws JSONException {

    JSONObject ownerObject = jsonObject.getJSONObject("owner");
    if (ownerObject == null) {
      return;
    }

    String ownerName = ownerObject.getOrDefault("name", "").toString();
    String emailAddressStr = ownerObject.getOrDefault("address", "").toString();
    iCalendar.setUser(office365Service.getUser(ownerName, emailAddressStr));
  }

  @SuppressWarnings("unchecked")
  private void setEventValues(
      JSONObject jsonObject,
      ICalendarEvent event,
      ICalendar iCalendar,
      ICalendar defaultCalendar,
      User ownerUser,
      LocalDateTime eventStart,
      LocalDateTime eventEnd)
      throws JSONException {

    event.setSubject(jsonObject.getOrDefault("subject", "").toString());
    event.setAllDay((Boolean) jsonObject.getOrDefault("isAllDay", false));
    event.setUser(ownerUser);
    event.setStartDateTime(eventStart);
    event.setEndDateTime(eventEnd);

    JSONObject bodyObject = jsonObject.getJSONObject("body");
    if (bodyObject != null) {
      event.setDescription(bodyObject.getOrDefault("content", "").toString());
    }

    if (!iCalendar.equals(defaultCalendar)) {
      event.setCalendar(iCalendar);
    }

    setVisibilitySelect(jsonObject, event);
    setDisponibilitySelect(jsonObject, event);

    if (Event.class.isAssignableFrom(event.getClass())) {
      Event crmEvent = (Event) event;
      if ((boolean) jsonObject.getOrDefault("isCancelled", false)) {
        crmEvent.setStatusSelect(EventRepository.STATUS_CANCELED);
      } else {
        crmEvent.setStatusSelect(EventRepository.STATUS_PLANNED);
      }
      setEventLocation(crmEvent, jsonObject);
      setICalendarUser(crmEvent, jsonObject);
      manageReminder(crmEvent, jsonObject);
      manageRecurrenceConfigration(crmEvent, jsonObject);
      manageEventCategory(crmEvent, jsonObject);
    }
  }

  private void setVisibilitySelect(JSONObject jsonObject, ICalendarEvent event) {

    String sensitivity = office365Service.processJsonValue("sensitivity", jsonObject);
    Integer visibilitySelect = 0;
    if ("normal".equalsIgnoreCase(sensitivity)) {
      visibilitySelect = ICalendarEventRepository.VISIBILITY_PUBLIC;
    } else if ("private".equalsIgnoreCase(sensitivity)) {
      visibilitySelect = ICalendarEventRepository.VISIBILITY_PRIVATE;
    }
    event.setVisibilitySelect(visibilitySelect);
  }

  private void setDisponibilitySelect(JSONObject jsonObject, ICalendarEvent event) {

    String showAs = office365Service.processJsonValue("showAs", jsonObject);
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
  }

  private void setEventLocation(Event event, JSONObject jsonObject) throws JSONException {

    JSONObject locationObject = jsonObject.getJSONObject("location");
    if (locationObject == null) {
      return;
    }

    String location = "";
    if (locationObject.containsKey("address")) {
      JSONObject addressObject = locationObject.getJSONObject("address");
      if (addressObject != null) {
        location += office365Service.processJsonValue("street", addressObject);
        location += " " + office365Service.processJsonValue("city", addressObject);
        location += " " + office365Service.processJsonValue("postalCode", addressObject);
        location += " " + office365Service.processJsonValue("state", addressObject);
        location += " " + office365Service.processJsonValue("countryOrRegion", addressObject);
      }
    } else if (locationObject.containsKey("displayName")) {
      location = office365Service.processJsonValue("displayName", locationObject);
    }
    event.setLocation(location.trim());

    if (locationObject.containsKey("coordinates")) {
      JSONObject coordinateObject = locationObject.getJSONObject("coordinates");
      if (coordinateObject != null && coordinateObject.containsKey("latitude")) {
        String latitude = office365Service.processJsonValue("latitude", coordinateObject);
        String longitude = office365Service.processJsonValue("longitude", coordinateObject);
        event.setGeo(latitude + "," + longitude);
      }
    }
  }

  private void setICalendarUser(Event event, JSONObject jsonObject) throws JSONException {

    JSONArray attendeesArr = jsonObject.getJSONArray("attendees");
    if (attendeesArr != null) {
      event.clearAttendees();
      for (Object object : attendeesArr) {
        JSONObject attendeeObj = (JSONObject) object;
        String type = office365Service.processJsonValue("type", attendeeObj);
        JSONObject emailAddressObj = attendeeObj.getJSONObject("emailAddress");
        ICalendarUser iCalendarUser = getICalendarUser(emailAddressObj, type);
        event.addAttendee(iCalendarUser);
      }
    } else {
      event.clearAttendees();
    }

    JSONObject organizerObj = jsonObject.getJSONObject("organizer");
    if (organizerObj != null) {
      JSONObject emailAddressObj = organizerObj.getJSONObject("emailAddress");
      ICalendarUser iCalendarUser = getICalendarUser(emailAddressObj, null);
      event.setOrganizer(iCalendarUser);
    } else {
      event.setOrganizer(null);
    }
  }

  @Transactional
  public void manageReminder(Event event, JSONObject jsonObject) {

    @SuppressWarnings("unchecked")
    Integer reminderMinutes = (Integer) jsonObject.getOrDefault("reminderMinutesBeforeStart", null);
    if (reminderMinutes == null) {
      return;
    }

    EventReminder eventReminder = null;
    if (event != null) {
      eventReminder =
          eventReminderRepo
              .all()
              .filter(
                  "self.modeSelect = :modeSelect "
                      + "AND self.typeSelect = 1 "
                      + "AND self.durationTypeSelect = :durationTypeSelect "
                      + "AND self.duration = :duration "
                      + "AND self.event = :event")
              .bind("modeSelect", EventReminderRepository.MODE_BEFORE_DATE)
              .bind("durationTypeSelect", EventReminderRepository.DURATION_TYPE_MINUTES)
              .bind("duration", reminderMinutes)
              .bind("event", event)
              .fetchOne();
      if (eventReminder != null) {
        return;
      }
    }

    eventReminder = new EventReminder();
    eventReminder.setModeSelect(EventReminderRepository.MODE_BEFORE_DATE);
    eventReminder.setTypeSelect(1);
    eventReminder.setDuration(reminderMinutes);
    eventReminder.setDurationTypeSelect(EventReminderRepository.DURATION_TYPE_MINUTES);
    eventReminder.setAssignToSelect(EventReminderRepository.ASSIGN_TO_ME);
    eventReminder.setUser(AuthUtils.getUser());
    eventReminderRepo.save(eventReminder);

    if (event.getEventReminderList() == null) {
      event.setEventReminderList(new ArrayList<>());
    }
    if (!event.getEventReminderList().contains(eventReminder)) {
      event.addEventReminderListItem(eventReminder);
    }
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public void manageRecurrenceConfigration(Event event, JSONObject jsonObject)
      throws JSONException {

    if (!jsonObject.containsKey("recurrence")
        || jsonObject.get("recurrence").equals(JSONObject.NULL)
        || jsonObject.getJSONObject("recurrence") == null) {
      return;
    }

    JSONObject reminderRecurrenceObj = jsonObject.getJSONObject("recurrence");
    JSONObject patternObj = reminderRecurrenceObj.getJSONObject("pattern");

    RecurrenceConfiguration recurrenceConfiguration = null;
    if (event.getRecurrenceConfiguration() == null) {
      recurrenceConfiguration = new RecurrenceConfiguration();
    } else {
      recurrenceConfiguration = event.getRecurrenceConfiguration();
    }
    recurrenceConfiguration.setEndType(RecurrenceConfigurationRepository.END_TYPE_DATE);

    Integer recurrenceType = 0;
    if (patternObj != null) {
      switch (office365Service.processJsonValue("type", patternObj)) {
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

    JSONObject rangeObj = reminderRecurrenceObj.getJSONObject("range");
    if (rangeObj != null) {
      LocalDate startDate = LocalDate.parse(rangeObj.getOrDefault("startDate", "").toString());
      LocalDate endDate = LocalDate.parse(rangeObj.getOrDefault("endDate", "").toString());
      recurrenceConfiguration.setStartDate(startDate);
      recurrenceConfiguration.setEndDate(endDate);
    }

    recurrenceConfiguration.setRecurrenceName(
        Beans.get(EventService.class).computeRecurrenceName(recurrenceConfiguration));
    event.setRecurrenceConfiguration(recurrenceConfiguration);
    recurrenceConfigurationRepo.save(recurrenceConfiguration);
  }

  @Transactional
  public void manageEventCategory(Event event, JSONObject jsonObject) throws JSONException {

    JSONArray categoryJsonArr = jsonObject.getJSONArray("categories");
    if (categoryJsonArr == null) {
      return;
    }

    EventCategory eventCategory = null;
    for (Object category : categoryJsonArr) {
      String categoryStr = category.toString();
      eventCategory = eventCategoryRepo.findByName(categoryStr);
      if (eventCategory == null) {
        eventCategory = new EventCategory();
        eventCategory.setName(categoryStr);
        eventCategory.setCode(categoryStr);
        eventCategoryRepo.save(eventCategory);
      }
    }
    event.setEventCategory(eventCategory);
  }

  @Transactional
  public ICalendarUser getICalendarUser(JSONObject emailAddressObj, String type) {

    ICalendarUser iCalendarUser = null;
    if (emailAddressObj != null) {
      String address = office365Service.processJsonValue("address", emailAddressObj);
      iCalendarUser = iCalendarUserRepo.findByEmail(address);

      if (iCalendarUser == null) {
        iCalendarUser = new ICalendarUser();
        iCalendarUser.setEmail(address);
        User user = userRepo.findByEmail(address);
        iCalendarUser.setUser(user);
      }
      iCalendarUser.setName(office365Service.processJsonValue("name", emailAddressObj));

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

  private LocalDateTime getLocalDateTime(JSONObject jsonObject) {

    LocalDateTime eventTime = null;
    try {
      if (jsonObject != null) {
        String dateStr = office365Service.processJsonValue("dateTime", jsonObject);
        String timeZone = office365Service.processJsonValue("timeZone", jsonObject);
        if (!StringUtils.isBlank(dateStr) && !StringUtils.isBlank(timeZone)) {
          dateStr = StringUtils.endsWithIgnoreCase(dateStr, "Z") ? dateStr : dateStr + "Z";
          if (!ZoneId.systemDefault().toString().equalsIgnoreCase(timeZone)) {
            eventTime = LocalDateTime.ofInstant(Instant.parse(dateStr), ZoneId.systemDefault());
          } else {
            eventTime = LocalDateTime.parse(dateStr);
          }
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
    return eventTime;
  }

  private JSONObject setOffice365EventValues(ICalendarEvent event, User currentUser)
      throws JSONException {

    JSONObject eventJsonObject = new JSONObject();
    office365Service.putObjValue(eventJsonObject, "subject", event.getSubject());
    eventJsonObject.put("isAllDay", event.getAllDay());

    JSONObject bodyJsonObject = new JSONObject();
    bodyJsonObject.put("content", event.getDescription());
    bodyJsonObject.put("contentType", "HTML");
    eventJsonObject.put("body", (Object) bodyJsonObject);

    String timezone = Calendar.getInstance().getTimeZone().getDisplayName();
    putDateTime(event, eventJsonObject, "start", event.getStartDateTime(), timezone);
    putDateTime(event, eventJsonObject, "end", event.getEndDateTime(), timezone);
    putSensitivity(event, eventJsonObject);
    putShowAs(event, eventJsonObject);
    putLocation(event, eventJsonObject);
    putOrganizer(event, eventJsonObject, currentUser);
    putAttendees(event, eventJsonObject);

    if (Event.class.isAssignableFrom(event.getClass())) {
      Event crmEvent = (Event) event;
      if (EventRepository.STATUS_CANCELED == crmEvent.getStatusSelect()) {
        eventJsonObject.put("isCancelled", true);
      }
      if (crmEvent.getEventCategory() != null) {
        eventJsonObject.put("categories", new String[] {crmEvent.getEventCategory().getName()});
      }
      putReminder(crmEvent, eventJsonObject);
      putRepeat(crmEvent, eventJsonObject, timezone);
    }

    return eventJsonObject;
  }

  private void putSensitivity(ICalendarEvent event, JSONObject eventJsonObject)
      throws JSONException {

    if (event.getVisibilitySelect() == null) {
      return;
    }

    if (ICalendarEventRepository.VISIBILITY_PUBLIC == event.getVisibilitySelect()) {
      eventJsonObject.put("sensitivity", "normal");
    } else if (ICalendarEventRepository.VISIBILITY_PRIVATE == event.getVisibilitySelect()) {
      eventJsonObject.put("sensitivity", "private");
    }
  }

  private void putShowAs(ICalendarEvent event, JSONObject eventJsonObject) throws JSONException {

    if (event.getDisponibilitySelect() == null) {
      return;
    }

    if (ICalendarEventRepository.DISPONIBILITY_BUSY == event.getDisponibilitySelect()) {
      eventJsonObject.put("showAs", "busy");
    } else if (ICalendarEventRepository.DISPONIBILITY_AVAILABLE == event.getDisponibilitySelect()) {
      eventJsonObject.put("showAs", "free");
    } else if (ICalendarEventRepository.DISPONIBILITY_AWAY == event.getDisponibilitySelect()) {
      eventJsonObject.put("showAs", "oof");
    } else if (ICalendarEventRepository.DISPONIBILITY_TENTATIVE == event.getDisponibilitySelect()) {
      eventJsonObject.put("showAs", "tentative");
    } else if (ICalendarEventRepository.DISPONIBILITY_WORKING_ELSEWHERE
        == event.getDisponibilitySelect()) {
      eventJsonObject.put("showAs", "workingElsewhere");
    } else {
      eventJsonObject.put("showAs", "unknown");
    }
  }

  private void putDateTime(
      ICalendarEvent event,
      JSONObject eventJsonObject,
      String key,
      LocalDateTime value,
      String timezone)
      throws JSONException {

    if (value == null) {
      return;
    }

    JSONObject startJsonObject = new JSONObject();
    startJsonObject.put("dateTime", value.toString());
    startJsonObject.put("timeZone", timezone);
    eventJsonObject.put(key, (Object) startJsonObject);
  }

  private void putLocation(ICalendarEvent event, JSONObject eventJsonObject) throws JSONException {

    JSONObject locationJsonObject = new JSONObject();
    locationJsonObject.put("displayName", event.getLocation());

    String geo = event.getGeo();
    if (StringUtils.isNotBlank(geo)) {
      JSONObject coordinatesJsonObject = new JSONObject();
      coordinatesJsonObject.put("latitude", StringUtils.substringBefore(geo, ";"));
      coordinatesJsonObject.put("longitude", StringUtils.substringAfter(geo, ";"));
      locationJsonObject.put("coordinates", (Object) coordinatesJsonObject);
    }

    eventJsonObject.put("location", (Object) locationJsonObject);
  }

  private void putOrganizer(ICalendarEvent event, JSONObject eventJsonObject, User currentUser)
      throws JSONException {

    ICalendarUser calendarUser = event.getOrganizer();
    if (calendarUser == null) {
      return;
    }

    if (calendarUser.getUser() != null && calendarUser.getUser().equals(currentUser)) {
      eventJsonObject.put("isOrganizer", true);
    }

    JSONObject organizerJsonObject = new JSONObject();
    JSONObject emailJsonObject = new JSONObject();
    office365Service.putObjValue(emailJsonObject, "address", calendarUser.getEmail());
    office365Service.putObjValue(emailJsonObject, "name", calendarUser.getName());
    organizerJsonObject.put("emailAddress", (Object) emailJsonObject);
    eventJsonObject.put("organizer", (Object) organizerJsonObject);
  }

  private void putAttendees(ICalendarEvent event, JSONObject eventJsonObject) throws JSONException {

    if (ObjectUtils.isEmpty(event.getAttendees())) {
      return;
    }

    JSONArray attendeesJsonArr = new JSONArray();
    for (ICalendarUser iCalendarUser : event.getAttendees()) {
      JSONObject attendeeJsonObject = new JSONObject();
      JSONObject emailJsonObject = new JSONObject();
      office365Service.putObjValue(emailJsonObject, "address", iCalendarUser.getEmail());
      office365Service.putObjValue(emailJsonObject, "name", iCalendarUser.getName());
      attendeeJsonObject.put("emailAddress", (Object) emailJsonObject);

      if (iCalendarUser.getStatusSelect() == ICalendarUserRepository.STATUS_REQUIRED) {
        attendeeJsonObject.put("type", "required");
      } else {
        attendeeJsonObject.put("type", "optional");
      }
      attendeesJsonArr.add(attendeeJsonObject);
    }
    eventJsonObject.put("attendees", (Object) attendeesJsonArr);
  }

  private void putReminder(Event event, JSONObject eventJsonObject) throws JSONException {

    if (ObjectUtils.isEmpty(event.getEventReminderList())) {
      return;
    }

    EventReminder eventReminder = event.getEventReminderList().get(0);
    Integer duration = 0;
    if (eventReminder.getModeSelect() == EventReminderRepository.MODE_BEFORE_DATE) {
      duration = eventReminder.getDuration();
      if (eventReminder.getDurationTypeSelect() == EventReminderRepository.DURATION_TYPE_HOURS) {
        duration = duration * 60;
      } else if (eventReminder.getDurationTypeSelect()
          == EventReminderRepository.DURATION_TYPE_DAYS) {
        duration = duration * 60 * 24;
      } else if (eventReminder.getDurationTypeSelect()
          == EventReminderRepository.DURATION_TYPE_WEEKS) {
        duration = duration * 60 * 24 * 7;
      }
    } else if (eventReminder.getModeSelect() == EventReminderRepository.MODE_AT_DATE) {
      duration =
          (int)
              eventReminder
                  .getSendingDateT()
                  .until(eventReminder.getEvent().getStartDateTime(), ChronoUnit.MINUTES);
    }
    eventJsonObject.put("reminderMinutesBeforeStart", duration);
    eventJsonObject.put("isReminderOn", true);
  }

  private void putRepeat(Event event, JSONObject eventJsonObject, String timezone)
      throws JSONException {

    if (event.getRecurrenceConfiguration() == null) {
      return;
    }

    RecurrenceConfiguration recurrenceConfg = event.getRecurrenceConfiguration();

    JSONObject rangeJsonObject = new JSONObject();
    LocalDate startOn = recurrenceConfg.getStartDate();
    rangeJsonObject.put("startDate", startOn != null ? startOn.toString() : null);
    rangeJsonObject.put("recurrenceTimeZone", timezone);
    if (recurrenceConfg.getEndType() == RecurrenceConfigurationRepository.END_TYPE_DATE) {
      if (recurrenceConfg.getEndDate() != null) {
        LocalDate endOn = recurrenceConfg.getEndDate();
        rangeJsonObject.put("endDate", endOn != null ? endOn.toString() : null);
        rangeJsonObject.put("type", "endDate");
      } else {
        rangeJsonObject.put("type", "noEnd");
      }
    } else {
      rangeJsonObject.put("numberOfOccurrences", recurrenceConfg.getRepetitionsNumber());
      rangeJsonObject.put("type", "numbered");
    }

    List<String> weeks = new ArrayList<>();
    addWeek(weeks, recurrenceConfg.getSunday(), "sunday");
    addWeek(weeks, recurrenceConfg.getMonday(), "monday");
    addWeek(weeks, recurrenceConfg.getTuesday(), "tuesday");
    addWeek(weeks, recurrenceConfg.getWednesday(), "wednesday");
    addWeek(weeks, recurrenceConfg.getThursday(), "thursday");
    addWeek(weeks, recurrenceConfg.getFriday(), "friday");
    addWeek(weeks, recurrenceConfg.getSaturday(), "saturday");

    JSONObject patternJsonObject = new JSONObject();

    if (recurrenceConfg.getRecurrenceType() == RecurrenceConfigurationRepository.TYPE_DAY) {
      patternJsonObject.put("type", "daily");

    } else if (recurrenceConfg.getRecurrenceType() == RecurrenceConfigurationRepository.TYPE_WEEK) {
      patternJsonObject.put("type", "weekly");
      patternJsonObject.put("daysOfWeek", weeks.toArray());
      patternJsonObject.put("firstDayOfWeek", "sunday");

    } else if (recurrenceConfg.getRecurrenceType()
        == RecurrenceConfigurationRepository.TYPE_MONTH) {
      if (recurrenceConfg.getMonthRepeatType()
          == RecurrenceConfigurationRepository.REPEAT_TYPE_MONTH) {
        patternJsonObject.put("type", "absoluteMonthly");
      } else if (recurrenceConfg.getMonthRepeatType()
          == RecurrenceConfigurationRepository.REPEAT_TYPE_WEEK) {
        patternJsonObject.put("type", "relativeMonthly");
        patternJsonObject.put("daysOfWeek", weeks.toArray());
      }
    } else if (recurrenceConfg.getRecurrenceType() == RecurrenceConfigurationRepository.TYPE_YEAR) {
      patternJsonObject.put("type", "absoluteYearly");
    }
    patternJsonObject.put("interval", recurrenceConfg.getPeriodicity());

    JSONObject recurrenceConfgJsonObject = new JSONObject();
    recurrenceConfgJsonObject.put("pattern", (Object) patternJsonObject);
    recurrenceConfgJsonObject.put("range", (Object) rangeJsonObject);
    eventJsonObject.put("recurrence", (Object) recurrenceConfgJsonObject);
  }

  private void addWeek(List<String> weeks, boolean isDay, String weekDay) {

    if (isDay) {
      weeks.add(weekDay);
    }
  }
}
