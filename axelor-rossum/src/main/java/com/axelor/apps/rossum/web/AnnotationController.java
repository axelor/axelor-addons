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
import com.axelor.apps.rossum.db.Annotation;
import com.axelor.apps.rossum.db.repo.AnnotationRepository;
import com.axelor.apps.rossum.service.annotation.AnnotationService;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import java.io.IOException;
import java.util.Map;
import org.apache.http.ParseException;
import wslite.json.JSONException;

public class AnnotationController {

  @SuppressWarnings("unchecked")
  public void exportAnnotation(ActionRequest request, ActionResponse response) {

    try {
      Context context = request.getContext();

      Annotation annotation =
          Beans.get(AnnotationRepository.class)
              .find(
                  Long.valueOf(
                      ((Map<String, Object>) context.get("annotation")).get("id").toString()));

      String invOcrTemplateName = context.get("invoiceOcrTemplateName").toString();

      Beans.get(AnnotationService.class).exportAnnotation(annotation, invOcrTemplateName);
    } catch (IOException | JSONException | AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void getAnnotations(ActionRequest request, ActionResponse response) {

    try {
      AppRossum appRossum = Beans.get(AppRossumService.class).getAppRossum();
      Beans.get(AppRossumService.class).login(appRossum);
      Beans.get(AnnotationService.class).getAnnotations(appRossum);

      response.setReload(true);
    } catch (ParseException | IOException | JSONException | AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
