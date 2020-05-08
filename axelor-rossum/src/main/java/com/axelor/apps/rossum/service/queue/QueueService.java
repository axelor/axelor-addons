package com.axelor.apps.rossum.service.queue;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Queue;
import com.axelor.exception.AxelorException;
import java.io.IOException;
import wslite.json.JSONException;

public interface QueueService {

  public void updateJsonData(Queue queue) throws JSONException;

  public void updateQueue(AppRossum appRossum, Queue queue)
      throws IOException, JSONException, AxelorException;

  public void getQueues(AppRossum appRossum) throws IOException, JSONException, AxelorException;

  public void createQueue(Queue queue) throws IOException, JSONException, AxelorException;
}
