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
package com.axelor.apps.office365.db.repo;

import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.MessageManagementRepository;
import com.axelor.apps.office365.service.Office365Service;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;

public class Office365MessageRepository extends MessageManagementRepository {

  @Inject Office365Service office365Service;

  @Override
  public void remove(Message message) {

    if (message.getOffice365Id() != null && message.getOfficeAccount() != null) {
      try {
        String accessToken = office365Service.getAccessTocken(message.getOfficeAccount());
        office365Service.deleteOffice365Object(
            Office365Service.MAIL_URL, message.getOffice365Id(), accessToken, "mail");
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }

    super.remove(message);
  }

  @Override
  public Message copy(Message message, boolean deep) {

    message = super.copy(message, deep);
    message.setOffice365Id(null);
    return message;
  }
}
