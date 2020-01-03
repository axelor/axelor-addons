/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
import com.axelor.apps.gsuite.db.EventGoogleAccount;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.repo.EventGoogleAccountRepository;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.exception.AxelorException;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleAbsEventServiceImpl implements GoogleAbsEventService {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private Calendar calendar;

  @Inject protected EventRepository crmEventRepo;

  @Inject private GoogleService googleService;

  @Inject private GoogleAccountRepository googleAccountRepo;

  @Inject private EventGoogleAccountRepository eventGoogleAccountRepo;

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
      Credential credential = googleService.getCredential(googleAccount.getId());
      calendar = googleService.getCalendar(credential);
      syncEvents(googleAccount, calendar);
      googleAccount.setEventSyncFromGoogleDate(LocalDateTime.now());

    } catch (IOException e) {
      googleAccount.setEventSyncToGoogleLog("\n" + ExceptionUtils.getStackTrace(e));
    }

    return googleAccountRepo.save(googleAccount);
  }

  @Override
  @Transactional
  public void syncEvents(GoogleAccount googleAccount, Calendar calendar) {
    String pageToken = null;
    do {
      com.google.api.services.calendar.model.Events events = null;
      try {
        events = calendar.events().list("primary").setPageToken(pageToken).execute();
        List<com.google.api.services.calendar.model.Event> items = events.getItems();
        System.err.println("timezone::" + events.getTimeZone());
        for (com.google.api.services.calendar.model.Event event : items) {
          EventGoogleAccount eventGoogleAccount =
              eventGoogleAccountRepo.findByGoogleEventId(event.getId());
          Event crmEvent =
              eventGoogleAccount != null
                  ? crmEventRepo.find(eventGoogleAccount.getEvent().getId())
                  : new Event();
          crmEvent.setSubject(event.getSummary());
          crmEvent.setDescription(event.getDescription());
          DateTime startDateTime = event.getStart().getDateTime();
          // System.out.println(startDateTime.toString());
          DateTime endDateTime = event.getEnd().getDateTime();
          // System.out.println(endDateTime.toString());
          LocalDateTime startLocalDateTime =
              LocalDateTime.parse(
                  startDateTime
                      .toStringRfc3339()
                      .substring(0, startDateTime.toStringRfc3339().lastIndexOf("+")),
                  DateTimeFormatter.ISO_LOCAL_DATE_TIME);
          // System.out.println(startLocalDateTime.toString());
          LocalDateTime endLocalDateTime =
              LocalDateTime.parse(
                  endDateTime
                      .toStringRfc3339()
                      .substring(0, endDateTime.toStringRfc3339().lastIndexOf("+")),
                  DateTimeFormatter.ISO_LOCAL_DATE_TIME);
          // System.out.println(endLocalDateTime.toString());
          crmEvent.setStartDateTime(startLocalDateTime);
          crmEvent.setEndDateTime(endLocalDateTime);
          System.err.println(event.getId());
          crmEventRepo.save(crmEvent);

          eventGoogleAccount =
              eventGoogleAccount == null ? new EventGoogleAccount() : eventGoogleAccount;
          eventGoogleAccount.setEvent(crmEvent);
          eventGoogleAccount.setGoogleAccount(googleAccount);
          eventGoogleAccount.setGoogleEventId(event.getId());
          eventGoogleAccountRepo.save(eventGoogleAccount);
        }
        pageToken = events.getNextPageToken();
      } catch (IOException e) {
        googleAccount.setEventSyncFromGoogleLog("\n" + ExceptionUtils.getStackTrace(e));
      }
    } while (pageToken != null);
  }
}
