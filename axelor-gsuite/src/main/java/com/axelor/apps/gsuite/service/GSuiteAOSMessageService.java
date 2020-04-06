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

import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.message.db.Message;
import com.axelor.exception.AxelorException;
import com.google.api.services.gmail.Gmail;
import java.io.IOException;
import java.util.List;
import javax.mail.MessagingException;

public interface GSuiteAOSMessageService {

  public GoogleAccount sync(GoogleAccount account) throws AxelorException;

  public List<Message> syncMessages(
      Gmail service, String userId, String query, GoogleAccount googleAccount)
      throws IOException, MessagingException;

  public com.google.api.services.gmail.model.Message getMessage(
      Gmail service, String userId, String messageId, String format) throws IOException;
}
