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
package com.axelor.apps.rossum.web;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Queue;
import com.axelor.apps.rossum.db.repo.QueueRepository;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.apps.rossum.service.queue.QueueService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.io.IOException;
import org.apache.http.ParseException;
import wslite.json.JSONException;

public class QueueController {

  public void updateJsonData(ActionRequest request, ActionResponse response) {

    try {
      Queue queue = request.getContext().asType(Queue.class);

      Beans.get(QueueService.class).updateJsonData(queue);

      response.setValues(queue);
    } catch (JSONException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void updateQueue(ActionRequest request, ActionResponse response) {

    try {
      Queue queue =
          Beans.get(QueueRepository.class).find(request.getContext().asType(Queue.class).getId());

      Beans.get(QueueService.class)
          .updateQueue(Beans.get(AppRossumService.class).getAppRossum(), queue);
      response.setReload(true);
    } catch (IOException | JSONException | AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void createQueue(ActionRequest request, ActionResponse response) {
    try {
      Queue queue =
          Beans.get(QueueRepository.class).find(request.getContext().asType(Queue.class).getId());

      Beans.get(QueueService.class).createQueue(queue);
      response.setReload(true);
    } catch (IOException | JSONException | AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void getQueues(ActionRequest request, ActionResponse response) {

    try {
      AppRossum appRossum = Beans.get(AppRossumService.class).getAppRossum();
      Beans.get(AppRossumService.class).login(appRossum);
      Beans.get(QueueService.class).getQueues(appRossum);
      response.setReload(true);
    } catch (ParseException | IOException | JSONException | AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
