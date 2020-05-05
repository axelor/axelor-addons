package com.axelor.apps.gsuite.service;

import com.axelor.apps.base.db.ICalendarEvent;
import com.axelor.apps.base.db.ICalendarUser;
import com.axelor.apps.base.db.repo.ICalendarUserRepository;
import com.axelor.apps.crm.db.Event;
import com.axelor.apps.gsuite.service.app.AppGSuiteService;
import com.axelor.apps.gsuite.utils.StringUtils;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.inject.Beans;
import com.google.api.services.calendar.model.Event.Organizer;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ICalUserServiceImpl implements ICalUserService {

  @Inject private EmailAddressRepository emailRepo;
  @Inject private ICalendarUserRepository iCalUserRepo;
  @Inject private AppGSuiteService appGSuiteService;

  @Override
  public ICalendarUser findOrCreateICalUser(Object source, ICalendarEvent event) {
    String email = null;
    boolean isOrganizer;
    Set<String> relatedEmailSet = appGSuiteService.getRelatedEmailAddressSet();
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

  @Override
  @Transactional
  public List<ICalendarUser> parseICalUsers(Event event, String text) {
    List<ICalendarUser> users = new ArrayList<>();
    if (ObjectUtils.isEmpty(text)) {
      return users;
    }

    for (String email : StringUtils.parseEmails(text)) {
      EmailAddress address = emailRepo.findByAddress(email);
      if (address == null) {
        address = new EmailAddress();
        address.setAddress(email);
        address.setName(email);
      }
      ICalendarUser user = findOrCreateICalUser(address, event, false);
      if (user != null) {
        users.add(iCalUserRepo.save(user));
      }
    }
    return users;
  }

  protected ICalendarUser findOrCreateICalUser(
      EmailAddress email, ICalendarEvent event, boolean isOrganizer) {

    ICalendarUser user = null;
    Set<String> relatedEmailSet = appGSuiteService.getRelatedEmailAddressSet();

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
      if (email.getPartner() != null) {
        user.setUser(
            Beans.get(UserRepository.class)
                .all()
                .filter(
                    "self.partner = :partner or self.partner.emailAddress.address = :email OR self.email = :email")
                .bind("partner", email.getPartner())
                .bind("email", email.getAddress())
                .fetchOne());
      }
    }
    return user;
  }
}
