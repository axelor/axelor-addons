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

import com.axelor.apps.base.db.ICalendarUser;
import com.axelor.apps.base.db.repo.ICalendarEventRepository;
import com.axelor.apps.crm.db.Event;
import com.axelor.apps.crm.db.repo.EventRepository;
import com.axelor.apps.gsuite.db.EventGoogleAccount;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.repo.EventGoogleAccountRepository;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.utils.DateUtils;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.Calendar.Events.List;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteAOSEventServiceImpl implements GSuiteAOSEventService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject protected EventRepository crmEventRepo;

  @Inject protected GSuiteService gSuiteService;

  @Inject protected GoogleAccountRepository googleAccountRepo;

  @Inject protected EventGoogleAccountRepository eventGoogleAccountRepo;

  @Inject protected EmailAddressRepository emailRepo;

  @Inject protected ICalUserService iCalUserService;

  @Override
  @Transactional
  public GoogleAccount sync(GoogleAccount googleAccount) throws AxelorException {
    if (googleAccount == null) {
      return null;
    }
    return sync(googleAccount, null, null);
  }

  @Override
  @Transactional
  public GoogleAccount sync(
      GoogleAccount googleAccount, LocalDateTime startDateT, LocalDateTime endDateT)
      throws AxelorException {

    googleAccount = googleAccountRepo.find(googleAccount.getId());
    Credential credential = gSuiteService.getCredential(googleAccount.getId());
    Calendar calendar = gSuiteService.getCalendar(credential);
    syncEvents(googleAccount, calendar, startDateT, endDateT);

    return googleAccountRepo.save(googleAccount);
  }

  @Override
  @Transactional
  public void syncEvents(
      GoogleAccount googleAccount,
      Calendar calendar,
      LocalDateTime startDateT,
      LocalDateTime endDateT)
      throws AxelorException {
    String pageToken = null;
    int total = 0;
    do {
      com.google.api.services.calendar.model.Events events = null;
      try {
        List list = calendar.events().list("primary");
        if (startDateT != null) {
          list = list.setTimeMin(DateUtils.toGoogleDateTime(startDateT));
        }
        if (endDateT != null) {
          list = list.setTimeMax(DateUtils.toGoogleDateTime(endDateT));
        }

        events = list.setPageToken(pageToken).execute();
        String timeZone = events.getTimeZone();

        for (com.google.api.services.calendar.model.Event event : events.getItems()) {

          EventGoogleAccount eventGoogleAccount =
              eventGoogleAccountRepo.findByGoogleEventId(event.getId());

          Event crmEvent =
              eventGoogleAccount != null
                  ? crmEventRepo.find(eventGoogleAccount.getEvent().getId())
                  : new Event();
          crmEvent.setSubject(event.getSummary());
          crmEvent.setDescription(event.getDescription());
          crmEvent.setLocation(event.getLocation());
          crmEvent.setOrganizer(
              iCalUserService.findOrCreateICalUser(event.getOrganizer(), crmEvent));
          crmEvent.setVisibilitySelect(getEventVisibilitySelect(event.getVisibility()));
          crmEvent.setGoogleAccount(googleAccount);
          setEventDates(event, crmEvent, timeZone);
          setAttendees(crmEvent, event);

          crmEventRepo.save(crmEvent);

          eventGoogleAccount =
              eventGoogleAccount == null ? new EventGoogleAccount() : eventGoogleAccount;
          eventGoogleAccount.setEvent(crmEvent);
          eventGoogleAccount.setGoogleAccount(googleAccount);
          eventGoogleAccount.setGoogleEventId(event.getId());

          eventGoogleAccountRepo.save(eventGoogleAccount);
          total++;
        }

        pageToken = events.getNextPageToken();
      } catch (IOException e) {
        throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
      }
    } while (pageToken != null);

    log.debug("{} Event retrived and processed", total);
  }

  protected Integer getEventVisibilitySelect(String value) {
    int selectValue = 0;
    if (value == null) {
      return selectValue;
    }
    switch (value) {
      case "public":
        selectValue = ICalendarEventRepository.VISIBILITY_PUBLIC;
        break;
      case "private":
        selectValue = ICalendarEventRepository.VISIBILITY_PRIVATE;
        break;
    }
    return selectValue;
  }

  @Transactional
  protected void setAttendees(
      Event aosEvent, com.google.api.services.calendar.model.Event gsuiteEvent) {

    if (ObjectUtils.notEmpty(gsuiteEvent.getAttendees())) {
      for (EventAttendee eventAttendee : gsuiteEvent.getAttendees()) {
        ICalendarUser user = iCalUserService.findOrCreateICalUser(eventAttendee, aosEvent);
        if (user != null) {
          aosEvent.addAttendee(user);
        }
      }
    }
    iCalUserService
        .parseICalUsers(aosEvent, gsuiteEvent.getDescription())
        .forEach(aosEvent::addAttendee);
  }

  protected void setEventDates(
      com.google.api.services.calendar.model.Event googleEvent, Event aosEvent, String timeZone) {

    EventDateTime eventStart = googleEvent.getStart();
    EventDateTime eventEnd = googleEvent.getEnd();
    LocalDateTime startDateTime = null;
    LocalDateTime endDateTime = null;

    if (eventStart.getDateTime() != null && eventEnd.getDateTime() != null) {
      startDateTime = DateUtils.toLocalDateTime(eventStart.getDateTime());
      endDateTime = DateUtils.toLocalDateTime(eventEnd.getDateTime());

    } else if (eventStart.getDate() != null && eventEnd.getDate() != null) {
      startDateTime = DateUtils.toLocalDateTime(eventStart.getDate());
      endDateTime = DateUtils.toLocalDateTime(eventEnd.getDate());
      aosEvent.setAllDay(true);
    }

    aosEvent.setStartDateTime(startDateTime);
    aosEvent.setEndDateTime(endDateTime);
  }
}
