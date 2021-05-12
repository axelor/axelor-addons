/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.zapier.hook;

import com.axelor.apps.zapier.db.SubscribeZap;
import com.axelor.apps.zapier.db.repo.SubscribeZapRepository;
import com.axelor.apps.zapier.exception.IExceptionMessage;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
import com.axelor.db.Model;
import com.axelor.event.Observes;
import com.axelor.events.PostRequest;
import com.axelor.events.RequestEvent;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.Context;
import com.axelor.rpc.Request;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wslite.json.JSONObject;

public class ZapierHookImpl {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @SuppressWarnings({"unchecked"})
  public void onSaveRecord(@Observes @Named(RequestEvent.SAVE) PostRequest event) {

    Request request = event.getRequest();
    Context context = request.getContext();
    Class<? extends Model> klass = (Class<? extends Model>) context.getContextClass();
    Map<String, Object> data = request.getData();

    if (ObjectUtils.isEmpty(data) || data.get("id") != null) {
      return;
    }

    final User user = AuthUtils.getUser();
    List<SubscribeZap> zaps =
        Beans.get(SubscribeZapRepository.class)
            .all()
            .filter("self.model = ? AND self.zapierUser = ?", klass.getName(), user)
            .fetch();

    if (zaps.size() == 0) {
      log.debug("No zap subscription with user={} and model={}", user.getName(), klass.getName());
      return;
    }

    CloseableHttpClient client = HttpClients.createDefault();
    try {
      for (SubscribeZap zap : zaps) {
        HttpPost httpPost = new HttpPost(zap.getUrl());
        JSONObject postData = new JSONObject(data);
        String json = postData.toString();
        StringEntity entity = new StringEntity(json);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setEntity(entity);
        CloseableHttpResponse res = client.execute(httpPost);
        log.debug("status: {}", res.getStatusLine());
      }
      client.close();
    } catch (IOException e) {
      TraceBackService.trace(
          new AxelorException(
              TraceBackRepository.CATEGORY_INCONSISTENCY,
              I18n.get(IExceptionMessage.AUTHENTICATION_VALIDATION),
              e.getMessage()));
    }
  }
}
