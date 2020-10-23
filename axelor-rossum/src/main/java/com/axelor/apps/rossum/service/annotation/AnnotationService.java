package com.axelor.apps.rossum.service.annotation;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.exception.AxelorException;
import java.io.IOException;
import wslite.json.JSONException;

public interface AnnotationService {

  public void getAnnotations(AppRossum appRossum)
      throws IOException, JSONException, AxelorException;
}
