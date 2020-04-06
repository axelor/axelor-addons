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
package com.axelor.apps.gsuite.service;

import com.axelor.apps.base.db.ICalendarEvent;
import com.axelor.apps.base.db.ICalendarUser;
import com.axelor.apps.base.db.repo.ICalendarEventRepository;
import com.axelor.apps.base.db.repo.ICalendarUserRepository;
import com.axelor.apps.base.ical.ICalendarException;
import com.axelor.apps.crm.db.Event;
import com.axelor.apps.crm.db.repo.EventRepository;
import com.axelor.apps.gsuite.db.EventGoogleAccount;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.repo.EventGoogleAccountRepository;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.service.app.AppGSuiteService;
import com.axelor.apps.gsuite.utils.StringUtils;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event.Organizer;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import javax.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteAOSEventServiceImpl implements GSuiteAOSEventService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Calendar calendar;
  private Set<String> relatedEmailSet;

  @Inject protected EventRepository crmEventRepo;

  @Inject private GSuiteService gSuiteService;

  @Inject private GoogleAccountRepository googleAccountRepo;

  @Inject private EventGoogleAccountRepository eventGoogleAccountRepo;

  @Inject private EmailAddressRepository emailRepo;

  @Inject private ICalendarUserRepository iCalUserRepo;

  @Inject private AppGSuiteService appGSuiteService;

  @Override
  @Transactional
  public GoogleAccount sync(GoogleAccount googleAccount) throws AxelorException {

    if (googleAccount == null) {
      return null;
    }
    LocalDateTime syncDate = googleAccount.getEventSyncFromGoogleDate();
    log.debug("Last sync date: {}", syncDate);
    googleAccount = googleAccountRepo.find(googleAccount.getId());
    try {
      Credential credential = gSuiteService.getCredential(googleAccount.getId());
      calendar = gSuiteService.getCalendar(credential);
      relatedEmailSet = appGSuiteService.getRelatedEmailAddressSet();
      syncEvents(googleAccount, calendar);
      googleAccount.setEventSyncFromGoogleDate(LocalDateTime.now());

    } catch (IOException e) {
      throw new AxelorException(e.getCause(), TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }

    return googleAccountRepo.save(googleAccount);
  }

  @Override
  @Transactional
  public void syncEvents(GoogleAccount googleAccount, Calendar calendar) throws AxelorException {
    String pageToken = null;
    do {
      com.google.api.services.calendar.model.Events events = null;
      try {
        events = calendar.events().list("primary").setPageToken(pageToken).execute();
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
          crmEvent.setOrganizer(findOrCreateICalUser(event.getOrganizer(), crmEvent));
          crmEvent.setVisibilitySelect(getEventVisibilitySelect(event.getVisibility()));
          setEventDates(event, crmEvent, timeZone);
          setAttendees(crmEvent, event);
          crmEventRepo.save(crmEvent);
          eventGoogleAccount =
              eventGoogleAccount == null ? new EventGoogleAccount() : eventGoogleAccount;
          eventGoogleAccount.setEvent(crmEvent);
          eventGoogleAccount.setGoogleAccount(googleAccount);
          eventGoogleAccount.setGoogleEventId(event.getId());
          eventGoogleAccountRepo.save(eventGoogleAccount);
        }
        pageToken = events.getNextPageToken();
      } catch (IOException
          | ClassNotFoundException
          | InstantiationException
          | IllegalAccessException
          | AxelorException
          | MessagingException
          | ICalendarException
          | ParseException e) {
        throw new AxelorException(e.getCause(), TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
      }
    } while (pageToken != null);
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
      Event aosEvent, com.google.api.services.calendar.model.Event gsuiteEvent)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException,
          AxelorException, MessagingException, IOException, ICalendarException, ParseException {

    for (EventAttendee eventAttendee : gsuiteEvent.getAttendees()) {
      ICalendarUser user = findOrCreateICalUser(eventAttendee, aosEvent);
      if (user != null) {
        aosEvent.addAttendee(user);
      }
    }

    for (String email : StringUtils.parseEmails(gsuiteEvent.getDescription())) {
      EmailAddress address = emailRepo.findByAddress(email);
      if (address == null) {
        address = new EmailAddress();
        address.setAddress(email);
        address.setName(email);
      }
      ICalendarUser user = findOrCreateICalUser(address, aosEvent, false);
      if (user != null) {
        aosEvent.addAttendee(user);
      }
    }
  }

  protected void setEventDates(
      com.google.api.services.calendar.model.Event googleEvent, Event aosEvent, String timeZone) {

    EventDateTime eventStart = googleEvent.getStart();
    EventDateTime eventEnd = googleEvent.getEnd();
    long startMils = 0;
    long endMils = 0;

    if (eventStart.getDateTime() != null && eventEnd.getDateTime() != null) {
      startMils = eventStart.getDateTime().getValue();
      endMils = eventEnd.getDateTime().getValue();
    } else if (eventStart.getDate() != null && eventEnd.getDate() != null) {
      startMils = eventStart.getDate().getValue();
      endMils = eventEnd.getDate().getValue();
      aosEvent.setAllDay(true);
    }

    aosEvent.setStartDateTime(
        Instant.ofEpochMilli(startMils).atZone(ZoneId.of(timeZone)).toLocalDateTime());
    aosEvent.setEndDateTime(
        Instant.ofEpochMilli(endMils).atZone(ZoneId.of(timeZone)).toLocalDateTime());
  }

  @Transactional
  protected ICalendarUser findOrCreateICalUser(Object source, ICalendarEvent event) {
    String email = null;
    boolean isOrganizer;

    if (source instanceof Organizer) {
      isOrganizer = true;
    } else if (source instanceof EventAttendee) {
      isOrganizer = false;
    } else {
      return null;
    }

    if (isOrganizer) {
      email = ((Organizer) source).getEmail();
    } else {
      email = ((EventAttendee) source).getEmail();
    }

    if (ObjectUtils.isEmpty(email) || !relatedEmailSet.contains(email)) {
      return null;
    }

    String displayName = email;
    if (isOrganizer && !ObjectUtils.isEmpty(((Organizer) source).getDisplayName())) {
      displayName = ((Organizer) source).getDisplayName();
    } else if (!isOrganizer && !ObjectUtils.isEmpty(((EventAttendee) source).getDisplayName())) {
      displayName = ((EventAttendee) source).getDisplayName();
    }

    EmailAddress emailAddress = emailRepo.findByAddress(email);
    if (emailAddress == null) {
      emailAddress = new EmailAddress();
      emailAddress.setAddress(email);
    }
    emailAddress.setName(displayName);
    emailRepo.save(emailAddress);

    ICalendarUser user = findOrCreateICalUser(emailAddress, event, isOrganizer);

    if (!isOrganizer && !ObjectUtils.isEmpty(((EventAttendee) source).getResponseStatus())) {
      switch (((EventAttendee) source).getResponseStatus()) {
        case "accepted":
          user.setStatusSelect(ICalendarUserRepository.STATUS_YES);
          break;
        case "tentative":
          user.setStatusSelect(ICalendarUserRepository.STATUS_MAYBE);
          break;
        case "declined":
          user.setStatusSelect(ICalendarUserRepository.STATUS_NO);
          break;
      }
    }

    return iCalUserRepo.save(user);
  }

  protected ICalendarUser findOrCreateICalUser(
      EmailAddress email, ICalendarEvent event, boolean isOrganizer) {

    ICalendarUser user = null;

    if (!relatedEmailSet.contains(email.getAddress())) {
      return null;
    }

    if (isOrganizer) {
      user = iCalUserRepo.all().filter("self.email = ?1", email.getAddress()).fetchOne();
    } else {
      user =
          iCalUserRepo
              .all()
              .filter("self.email = ?1 AND self.event.id = ?2", email.getAddress(), event.getId())
              .fetchOne();
    }

    if (user == null) {
      user = new ICalendarUser();
      user.setEmail(email.getAddress());
      user.setName(email.getName());
      if (email.getPartner() != null && email.getPartner().getUser() != null) {
        user.setUser(email.getPartner().getUser());
      }
    }
    return user;
  }
}
