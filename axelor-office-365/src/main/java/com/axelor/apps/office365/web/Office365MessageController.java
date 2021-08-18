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
package com.axelor.apps.office365.web;

import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.message.exception.IExceptionMessage;
import com.axelor.apps.message.service.MessageService;
import com.axelor.apps.message.web.MessageController;
import com.axelor.apps.office365.translation.ITranslation;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;

public class Office365MessageController extends MessageController {

  @Override
  public void sendMessage(ActionRequest request, ActionResponse response) {
    Message message = request.getContext().asType(Message.class);

    try {
      message = Beans.get(MessageRepository.class).find(message.getId());
      Beans.get(MessageService.class).sendMessage(message);

      response.setReload(true);
      EmailAccount emailAccount = message.getMailAccount();
      if (emailAccount != null
          && emailAccount.getServerTypeSelect() == EmailAccountRepository.SERVER_TYPE_OFFICE365) {
        if (message.getStatusSelect() != MessageRepository.STATUS_SENT) {
          response.setFlash(I18n.get(ITranslation.MAIL_SEND_FAILURE));
        }
        return;
      }
      response.setFlash(I18n.get(IExceptionMessage.MESSAGE_4));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
