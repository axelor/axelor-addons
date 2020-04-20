package com.axelor.apps.gsuite.service;

import com.axelor.apps.base.db.ICalendarEvent;
import com.axelor.apps.base.db.ICalendarUser;
import com.axelor.apps.crm.db.Event;
import java.util.List;

public interface ICalUserService {

  public ICalendarUser findOrCreateICalUser(Object source, ICalendarEvent event);

  public List<ICalendarUser> parseICalUsers(Event event, String text);
}
