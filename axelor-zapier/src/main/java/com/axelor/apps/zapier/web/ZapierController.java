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
package com.axelor.apps.zapier.web;

import com.axelor.apps.zapier.exception.IExceptionMessage;
import com.axelor.apps.zapier.service.ZapierService;
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
import java.util.List;
import java.util.Map;

public class ZapierController {

  @SuppressWarnings({"unchecked", "static-access"})
  @CallMethod
  public void createRecord(ActionRequest request, ActionResponse response)
      throws ClassNotFoundException {

    Map<String, Object> data = request.getData();
    String model = (String) data.get("domain");
    Class<Model> klass = (Class<Model>) Class.forName(model);

    if (Beans.get(JpaSecurity.class).isPermitted(AccessType.WRITE, klass)) {
      try {
        Beans.get(ZapierService.class).mapFields(data);
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

  @SuppressWarnings({"unchecked", "rawtypes"})
  @CallMethod
  public void getMetaFields(ActionRequest request, ActionResponse response) {
    try {
      Map<String, Object> data = request.getData();
      String model = (String) data.get("domain");
      Class<Model> klass = (Class<Model>) Class.forName(model);
      List<Map> metaFields = Beans.get(ZapierService.class).getMetaFieldList(klass);
      response.setValue("fields", metaFields);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  @SuppressWarnings("rawtypes")
  @CallMethod
  public void getMetaModle(ActionRequest request, ActionResponse response) {
    try {
      List<Map> modeldFullNames = Beans.get(ZapierService.class).getMetaModelList();
      response.setValue("fields", modeldFullNames);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  @SuppressWarnings({"unchecked", "static-access"})
  public void updateRecord(ActionRequest request, ActionResponse response)
      throws ClassNotFoundException {

    Map<String, Object> data = request.getData();
    String model = (String) data.get("domain");
    Class<Model> klass = (Class<Model>) Class.forName(model);
    if (Beans.get(JpaSecurity.class).isPermitted(AccessType.WRITE, klass)) {
      try {
        Object obj = Beans.get(ZapierService.class).updateRecord(data);
        response.setValue("name", obj);
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
}
