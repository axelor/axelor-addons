package com.axelor.apps.rossum.web;

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
}
