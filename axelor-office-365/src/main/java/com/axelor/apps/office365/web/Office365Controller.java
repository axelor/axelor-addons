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

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.crm.db.Lead;
import com.axelor.apps.crm.db.repo.LeadRepository;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.office365.service.Office365MailService;
import com.axelor.apps.office365.service.Office365Service;
import com.axelor.apps.office365.translation.ITranslation;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Office365Controller {

  @Inject private Office365Service office365Service;

  @SuppressWarnings("unchecked")
  public void syncUserMail(ActionRequest request, ActionResponse response) throws Exception {

    Context context = request.getContext();

    EmailAddress emailAddress = null;
    if (context.containsKey("partner")) {
      Long partnerId = Long.parseLong(context.get("partner").toString());
      Partner partner = Beans.get(PartnerRepository.class).find(partnerId);
      emailAddress = partner != null ? partner.getEmailAddress() : null;
    }
    if (context.containsKey("lead")) {
      Long leadId = Long.parseLong(context.get("lead").toString());
      Lead lead = Beans.get(LeadRepository.class).find(leadId);
      emailAddress = lead != null ? lead.getEmailAddress() : null;
    }
    if (emailAddress == null) {
      TraceBackService.trace(
          new AxelorException(
              TraceBackRepository.CATEGORY_NO_VALUE,
              String.format(
                  I18n.get(ITranslation.OFFICE365_EMAIL_ADDRESS_NOT_EXIST), emailAddress)));
      return;
    }

    List<String> emailIds = new ArrayList<>();
    if (context.containsKey("emails")) {
      emailIds = (ArrayList<String>) context.get("emails");
    }
    office365Service.syncUserMail(emailAddress, emailIds);

    response.setView(
        ActionView.define(I18n.get(ITranslation.OFFICE365_MAIL_TITLE))
            .model(Message.class.getName())
            .add("grid", "message-grid")
            .add("form", "message-form")
            .domain("self.office365Id IS NOT NULL")
            .map());
  }

  public void createUserMessage(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();
    if (!context.containsKey("data")) {
      return;
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> mailMap = (Map<String, Object>) request.getContext().get("data");
      Beans.get(Office365MailService.class).createEmailMessage(mailMap);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }
}
