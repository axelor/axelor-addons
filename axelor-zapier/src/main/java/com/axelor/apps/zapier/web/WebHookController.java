/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.zapier.web;

import com.axelor.apps.zapier.db.SubscribeZap;
import com.axelor.apps.zapier.db.repo.SubscribeZapRepository;
import com.axelor.apps.zapier.exception.IExceptionMessage;
import com.axelor.auth.AuthUtils;
import com.axelor.db.JpaSecurity;
import com.axelor.db.JpaSecurity.AccessType;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.CallMethod;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.persist.Transactional;
import java.util.List;
import java.util.Map;

public class WebHookController {

  @SuppressWarnings({"unchecked", "static-access"})
  @CallMethod
  @Transactional(rollbackOn = {Exception.class})
  public void subscribeZap(ActionRequest request, ActionResponse response)
      throws ClassNotFoundException {

    Map<String, Object> data = request.getData();
    String model = (String) data.get("domain");
    Class<Model> klass = (Class<Model>) Class.forName(model);

    if (Beans.get(JpaSecurity.class).isPermitted(AccessType.WRITE, klass)
        || Beans.get(JpaSecurity.class).isPermitted(AccessType.READ, klass)) {
      try {
        SubscribeZap subscriptionZap = new SubscribeZap();
        subscriptionZap.setModel(model);
        subscriptionZap.setUrl((String) data.get("url"));
        subscriptionZap.setZapierUser(AuthUtils.getUser());
        Beans.get(SubscribeZapRepository.class).save(subscriptionZap);
        response.setValue("name", data);
      } catch (Exception e) {
        response.setStatus(response.STATUS_FAILURE);
        TraceBackService.trace(response, e);
      }
    } else {
      response.setStatus(response.STATUS_FAILURE);
      TraceBackService.trace(
          response,
          new AxelorException(
              TraceBackRepository.CATEGORY_INCONSISTENCY,
              I18n.get(IExceptionMessage.AUTHENTICATION_VALIDATION),
              klass.toString()));
    }
  }

  @CallMethod
  @Transactional(rollbackOn = {Exception.class})
  public void unSubscribeZap(ActionRequest request, ActionResponse response) {

    SubscribeZapRepository subscribeZapRepo = Beans.get(SubscribeZapRepository.class);
    Map<String, Object> data = request.getData();
    String url = ((String) data.get("url"));
    List<SubscribeZap> zaps = subscribeZapRepo.all().filter("self.url = ?", url).fetch();

    if (zaps.size() == 0) {
      return;
    }

    for (SubscribeZap zap : zaps) {
      subscribeZapRepo.remove(zap);
    }
  }

  @SuppressWarnings({"unchecked", "unused"})
  @CallMethod
  public void testData(ActionRequest request, ActionResponse response)
      throws ClassNotFoundException {

    Map<String, Object> data = request.getData();
    String model = (String) data.get("domain");
    Class<Model> klass = (Class<Model>) Class.forName(model);

    TraceBackService.trace(
        response,
        new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.AUTHENTICATION_VALIDATION),
            data.toString()));
  }
}
