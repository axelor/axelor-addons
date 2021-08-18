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

import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.message.MessageServiceBaseImpl;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.message.service.SendMailQueueService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.meta.db.repo.MetaAttachmentRepository;
import com.google.inject.Inject;
import java.io.IOException;
import wslite.json.JSONException;

public class MessageServiceOffice365Impl extends MessageServiceBaseImpl {

  @Inject
  public MessageServiceOffice365Impl(
      MetaAttachmentRepository metaAttachmentRepository,
      MessageRepository messageRepository,
      SendMailQueueService sendMailQueueService,
      UserService userService,
      AppBaseService appBaseService) {
    super(
        metaAttachmentRepository,
        messageRepository,
        sendMailQueueService,
        userService,
        appBaseService);
  }

  @Override
  public Message sendMessage(Message message) throws AxelorException, JSONException, IOException {

    EmailAccount emailAccount = message.getMailAccount();
    if (emailAccount != null
        && emailAccount.getServerTypeSelect() == EmailAccountRepository.SERVER_TYPE_OFFICE365) {
      return Beans.get(Office365Service.class).sendOffice365Mail(message);
    }

    return super.sendMessage(message);
  }
}
