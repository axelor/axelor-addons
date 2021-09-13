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
import com.axelor.apps.gsuite.service.GSuiteService;
import com.axelor.apps.gsuite.utils.DateUtils;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
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

  @Inject private EmailAccountRepository emailAccountRepo;

  @Inject private EventRepository eventRepo;

  @Inject private GSuiteEventImportService gSuiteEventImportService;

  @Override
  @Transactional
  public EmailAccount sync(EmailAccount emailAccount) throws AxelorException {

    if (emailAccount == null) {
      return null;
    }
    LocalDateTime syncDate = emailAccount.getEventSyncToGoogleDate();
    log.debug("Last sync date: {}", syncDate);
    emailAccount = emailAccountRepo.find(emailAccount.getId());
    String accountName = emailAccount.getName();
    try {
      Credential credential = gSuiteService.getCredential(emailAccount.getId());
      calendar = gSuiteService.getCalendar(credential);
      List<Event> events;
      if (syncDate == null) {
        events = eventRepo.all().filter("self.typeSelect <> ?1", EventRepository.TYPE_TASK).fetch();
      } else {
        emailAccount = removeEventsFromRemote(emailAccount);
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
        String googleEventId = event.getGoogleEventId();
        googleEventId = updateGoogleEvent(event, new String[] {googleEventId, accountName}, false);
        event.setGoogleEventId(googleEventId);
        event.setEmailAccount(emailAccount);
        eventRepo.save(event);
      }

      emailAccount = updateCrmEvents(emailAccount);
      emailAccount.setEventSyncToGoogleDate(LocalDateTime.now());

    } catch (IOException e) {
      emailAccount.setEventSyncToGoogleLog("\n" + ExceptionUtils.getStackTrace(e));
    }

    return emailAccountRepo.save(emailAccount);
  }

  protected EmailAccount removeEventsFromRemote(EmailAccount emailAccount) {
    Events events = calendar.events();
    String removedEventIds = emailAccount.getRemovedEventIds();
    if (StringUtils.notEmpty(removedEventIds)) {
      try {
        String[] removeEventIdsArr = removedEventIds.split(",");
        for (String eventId : removeEventIdsArr) {
          events.delete("primary", eventId).execute();
        }
      } catch (IOException e) {
        emailAccount.setEventSyncToGoogleLog("\n" + ExceptionUtils.getStackTrace(e));
      }
      emailAccount.setRemovedEventIds(null);
    }
    return emailAccount;
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
    ICalendarUser organizer = event.getOrganizer();
    if (organizer != null) {
      eventAttendees.add(getAttendee(organizer));
    }
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
  public EmailAccount updateCrmEvents(EmailAccount account) throws IOException {

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
      EmailAccount account, Iterator<com.google.api.services.calendar.model.Event> iterator)
      throws IOException {

    if (!iterator.hasNext()) {
      return;
    }
    com.google.api.services.calendar.model.Event googleEvent = iterator.next();
    String eventId = googleEvent.getId();
    Event event = eventRepo.findByGoogleEventIdAndAccount(eventId, account);

    if (event != null && googleEvent.getStatus().equals("cancelled")) {
      eventRepo.remove(event);
      checkEvent(account, iterator);
      return;
    }

    if (event == null) {
      event = new Event();
      event.setEmailAccount(account);
      event.setGoogleEventId(eventId);
    }

    if (!googleEvent.getStatus().equals("cancelled")) {
      createUpdateCrmEvent(event, googleEvent);
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
  public void removeEventFromRemote(Event event) {
    if (ObjectUtils.isEmpty(event.getGoogleEventId())) {
      return;
    }

    EmailAccount account = event.getEmailAccount();
    String removedEventIds = account.getRemovedEventIds();
    if (ObjectUtils.isEmpty(removedEventIds)) {
      account.setRemovedEventIds(event.getGoogleEventId());
    } else {
      account.setRemovedEventIds(removedEventIds + "," + event.getGoogleEventId());
    }
    emailAccountRepo.save(account);
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
