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
package com.axelor.apps.gsuite.service.event;

import com.axelor.apps.base.db.ICalendarUser;
import com.axelor.apps.base.db.repo.ICalendarEventRepository;
import com.axelor.apps.base.db.repo.ICalendarUserRepository;
import com.axelor.apps.crm.db.Event;
import com.axelor.apps.crm.db.repo.EventRepository;
import com.axelor.apps.gsuite.db.EventGoogleAccount;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.repo.EventGoogleAccountRepository;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.exception.IExceptionMessage;
import com.axelor.apps.gsuite.service.GSuiteService;
import com.axelor.apps.gsuite.utils.DateUtils;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.Calendar.Events;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteEventExportServiceImpl implements GSuiteEventExportService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Calendar calendar;

  @Inject private GSuiteService gSuiteService;

  @Inject private GoogleAccountRepository googleAccountRepo;

  @Inject private EventRepository eventRepo;

  @Inject private EventGoogleAccountRepository eventGoogleAccountRepo;

  @Inject private GSuiteEventImportService gSuiteEventImportService;

  @Override
  @Transactional
  public GoogleAccount sync(GoogleAccount googleAccount) throws AxelorException {

    if (googleAccount == null) {
      return null;
    }
    LocalDateTime syncDate = googleAccount.getEventSyncToGoogleDate();
    log.debug("Last sync date: {}", syncDate);
    googleAccount = googleAccountRepo.find(googleAccount.getId());
    String accountName = googleAccount.getName();
    try {
      Credential credential = gSuiteService.getCredential(googleAccount.getId());
      calendar = gSuiteService.getCalendar(credential);
      List<Event> events;
      if (syncDate == null) {
        events = eventRepo.all().filter("self.typeSelect <> ?1", EventRepository.TYPE_TASK).fetch();
      } else {
        googleAccount = removeEventsFromRemote(googleAccount);
        events =
            eventRepo
                .all()
                .filter(
                    "self.typeSelect <> ?1 AND (self.updatedOn > ?2 OR self.createdOn > ?2)",
                    EventRepository.TYPE_TASK,
                    syncDate)
                .fetch();
      }
      log.debug("total size: {}", events.size());
      for (Event event : events) {
        if (event.getEndDateTime() != null
            && event.getStartDateTime() != null
            && event.getStartDateTime().isAfter(event.getEndDateTime())) {
          log.debug("event id : {} not synchronized", event.getId());
          continue;
        }
        EventGoogleAccount eventAccount = new EventGoogleAccount();
        String eventId = null;
        for (EventGoogleAccount account : event.getEventGoogleAccounts()) {
          if (googleAccount.equals(account.getGoogleAccount())) {
            eventAccount = account;
            eventId = account.getGoogleEventId();
            log.debug("Event id: {}", eventId);
            break;
          }
        }

        eventId = updateGoogleEvent(event, new String[] {eventId, accountName}, false);
        eventAccount.setEvent(event);
        eventAccount.setGoogleAccount(googleAccount);
        eventAccount.setGoogleEventId(eventId);
        eventGoogleAccountRepo.save(eventAccount);
      }

      googleAccount = updateCrmEvents(googleAccount);
      googleAccount.setEventSyncToGoogleDate(LocalDateTime.now());

    } catch (IOException e) {
      googleAccount.setEventSyncToGoogleLog("\n" + ExceptionUtils.getStackTrace(e));
    }

    return googleAccountRepo.save(googleAccount);
  }

  protected GoogleAccount removeEventsFromRemote(GoogleAccount googleAccount) {
    Events events = calendar.events();
    String removedEventIds = googleAccount.getRemovedEventIds();
    if (StringUtils.notEmpty(removedEventIds)) {
      try {
        String[] removeEventIdsArr = removedEventIds.split(",");
        for (String eventId : removeEventIdsArr) {
          events.delete("primary", eventId).execute();
        }
      } catch (IOException e) {
        googleAccount.setEventSyncToGoogleLog("\n" + ExceptionUtils.getStackTrace(e));
      }
      googleAccount.setRemovedEventIds(null);
    }
    return googleAccount;
  }

  @Override
  public Event sync(Event event, boolean remove) throws AxelorException {

    List<GoogleAccount> accounts = googleAccountRepo.all().filter("self.authorized = true").fetch();

    try {
      for (GoogleAccount googleAccount : accounts) {
        Credential credential = gSuiteService.getCredential(googleAccount.getId());
        String accountName = googleAccount.getName();
        EventGoogleAccount eventAccount = new EventGoogleAccount();
        String eventId = null;
        LocalDateTime syncDate = googleAccount.getEventSyncToGoogleDate();
        if (syncDate != null
            && event.getCreatedOn() != null
            && event.getCreatedOn().isAfter(syncDate)) {
          continue;
        }

        if (event.getEventGoogleAccounts() != null) {
          for (EventGoogleAccount account : event.getEventGoogleAccounts()) {
            if (googleAccount.equals(account.getGoogleAccount())) {
              eventAccount = account;
              eventId = account.getGoogleEventId();
              break;
            }
          }
        }
        calendar = gSuiteService.getCalendar(credential);
        eventId = updateGoogleEvent(event, new String[] {eventId, accountName}, remove);
        if (!remove) {
          eventAccount.setGoogleAccount(googleAccount);
          eventAccount.setGoogleEventId(eventId);
          if (eventAccount.getEvent() == null) {
            event.addEventGoogleAccount(eventAccount);
          }
        }
      }
    } catch (IOException e) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.EVENT_UPDATE_EXCEPTION),
          e.getLocalizedMessage());
    }

    return event;
  }

  @Override
  public String updateGoogleEvent(Event event, String account[], boolean remove)
      throws IOException {

    log.debug("Exporting event id: {}, subject: {}", event.getId(), event.getSubject());
    Events events = calendar.events();
    com.google.api.services.calendar.model.Event googleEvent =
        new com.google.api.services.calendar.model.Event();
    if (remove) {
      if (account[0] != null) {
        events.delete("primary", account[0]).execute();
      }
      return googleEvent.getId();
    }
    if (account[0] != null) {
      try {
        googleEvent = events.get("primary", account[0]).execute();
      } catch (GoogleJsonResponseException e) {
        googleEvent = new com.google.api.services.calendar.model.Event();
        account[0] = null;
      }
    }

    googleEvent = extractEvent(event, googleEvent);

    if (account[0] != null) {
      log.debug("Updating google event Id: {}", googleEvent.getId());
      googleEvent = events.update("primary", account[0], googleEvent).execute();

    } else {
      googleEvent = events.insert("primary", googleEvent).execute();
      log.debug("Google calendar event created: {}", googleEvent.getId());
    }

    return googleEvent.getId();
  }

  @Override
  public com.google.api.services.calendar.model.Event extractEvent(
      Event event, com.google.api.services.calendar.model.Event googleEvent) {
    boolean allDay = event.getAllDay();

    googleEvent.setStart(DateUtils.toEventDateTime(event.getStartDateTime(), allDay));
    googleEvent.setEnd(DateUtils.toEventDateTime(event.getEndDateTime(), allDay));
    googleEvent.setDescription(event.getDescription());
    googleEvent.setLocation(event.getLocation());
    googleEvent.setSummary(event.getSubject());
    googleEvent.setVisibility(getVisibility(event));

    List<EventAttendee> eventAttendees = new ArrayList<>();
    for (ICalendarUser icalUser : event.getAttendees()) {
      EventAttendee eventAttendee = getAttendee(icalUser);
      eventAttendees.add(eventAttendee);
    }
    eventAttendees.add(getAttendee(event.getOrganizer()));
    googleEvent.setAttendees(eventAttendees);

    return googleEvent;
  }

  protected EventAttendee getAttendee(ICalendarUser iCalUser) {
    EventAttendee eventAttendee = new EventAttendee();
    eventAttendee.setDisplayName(iCalUser.getName());
    eventAttendee.setEmail(iCalUser.getEmail());
    eventAttendee.setResponseStatus(getReponseStatus(iCalUser));
    return eventAttendee;
  }

  protected String getReponseStatus(ICalendarUser iCalUser) {
    int statusSelect = iCalUser.getStatusSelect();
    if (statusSelect == ICalendarUserRepository.STATUS_YES) {
      return "accepted";
    } else if (statusSelect == ICalendarUserRepository.STATUS_MAYBE) {
      return "tentative";
    } else if (statusSelect == ICalendarUserRepository.STATUS_NO) {
      return "declined";
    }
    return null;
  }

  @Override
  public GoogleAccount updateCrmEvents(GoogleAccount account) throws IOException {

    String pageToken = null;
    com.google.api.services.calendar.model.Events events;
    String syncToken = account.getEventSyncToken();
    do {

      com.google.api.services.calendar.Calendar.Events.List request =
          calendar
              .events()
              .list("primary")
              .setPageToken(pageToken)
              .setShowDeleted(true)
              .setFields(
                  "items(id,updated,status,"
                      + "start,end,summary,description,location),"
                      + "nextSyncToken");

      if (syncToken != null) {
        request.setSyncToken(syncToken);
      }
      events = request.execute();
      checkEvent(account, events.getItems().iterator());
      pageToken = events.getNextPageToken();
    } while (pageToken != null);

    log.debug("Next sync token : {}", events.getNextSyncToken());
    account.setEventSyncToken(events.getNextSyncToken());
    return account;
  }

  @Override
  public void checkEvent(
      GoogleAccount account, Iterator<com.google.api.services.calendar.model.Event> iterator)
      throws IOException {

    if (!iterator.hasNext()) {
      return;
    }
    com.google.api.services.calendar.model.Event googleEvent = iterator.next();
    String eventId = googleEvent.getId();
    EventGoogleAccount eventAccount =
        eventGoogleAccountRepo
            .all()
            .filter("self.googleAccount = ?1 and self.googleEventId = ?2", account, eventId)
            .fetchOne();
    Event event = new Event();
    if (eventAccount != null) {
      event = eventAccount.getEvent();
      if (googleEvent.getStatus().equals("cancelled")) {
        eventRepo.remove(event);
        checkEvent(account, iterator);
        return;
      }
    }
    if (!googleEvent.getStatus().equals("cancelled")) {
      event = createUpdateCrmEvent(event, googleEvent);
      if (eventAccount == null && event != null) {
        createEventAccount(account, event, googleEvent.getId());
      }
    }
    checkEvent(account, iterator);
  }

  @Override
  @Transactional
  public Event createUpdateCrmEvent(
      Event event, com.google.api.services.calendar.model.Event googleEvent) throws IOException {

    log.debug("Updating event: {}", event.getId());

    if (googleEvent.getSummary() == null) {
      return null;
    }
    event.setSubject(googleEvent.getSummary());
    event.setDescription(googleEvent.getDescription());
    event.setLocation(googleEvent.getLocation());
    event.setTypeSelect(EventRepository.TYPE_MEETING);

    gSuiteEventImportService.setEventDates(googleEvent, event);
    return eventRepo.save(event);
  }

  @Override
  @Transactional
  public void createEventAccount(GoogleAccount account, Event event, String eventId) {

    EventGoogleAccount eventAccount = new EventGoogleAccount();
    eventAccount.setEvent(event);
    eventAccount.setGoogleAccount(account);
    eventAccount.setGoogleEventId(eventId);
    eventGoogleAccountRepo.save(eventAccount);
  }

  @Override
  @Transactional
  public void removeEventFromRemote(Event event) {
    EventGoogleAccount eventGoogleAccount =
        eventGoogleAccountRepo.all().filter("self.event = ?1", event).fetchOne();
    if (eventGoogleAccount == null) {
      return;
    }

    GoogleAccount googleAccount = eventGoogleAccount.getGoogleAccount();
    String removedEventIds = googleAccount.getRemovedEventIds();
    if (removedEventIds == null) {
      googleAccount.setRemovedEventIds(eventGoogleAccount.getGoogleEventId());
    } else {
      googleAccount.setRemovedEventIds(
          removedEventIds + "," + eventGoogleAccount.getGoogleEventId());
    }
    googleAccountRepo.save(googleAccount);
  }

  protected String getVisibility(Event event) {
    int visibility = event.getVisibilitySelect();
    if (visibility == ICalendarEventRepository.VISIBILITY_PUBLIC) {
      return "public";
    } else if (visibility == ICalendarEventRepository.VISIBILITY_PRIVATE) {
      return "private";
    }
    return null;
  }
}
