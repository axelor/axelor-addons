/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2022 Axelor (<http://axelor.com>).
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
package com.axelor.apps.customer.portal.web.interceptor;

import com.axelor.apps.customer.portal.service.response.PortalRestResponse;
import com.axelor.auth.AuthSecurityException;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.google.common.base.Throwables;
import com.stripe.exception.StripeException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortalResponseInterceptor implements MethodInterceptor {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    logger.trace("Web Service: {}", invocation.getMethod());

    PortalRestResponse response = null;

    try {
      response = (PortalRestResponse) invocation.proceed();
    } catch (Exception e) {
      response = new PortalRestResponse();
      response = onException(e, response);
    }
    return response;
  }

  private PortalRestResponse onException(Throwable throwable, PortalRestResponse response) {
    final Throwable cause = throwable.getCause();
    final Throwable root = Throwables.getRootCause(throwable);
    for (Throwable ex : Arrays.asList(throwable, cause, root)) {
      if (ex instanceof AxelorException) {
        return onAxelorException((AxelorException) ex, response);
      }
      if (ex instanceof AuthSecurityException) {
        return onAuthSecurityException((AuthSecurityException) ex, response);
      }

      if (ex instanceof StripeException) {
        return onStripeException((StripeException) ex, response);
      }
    }
    logger.error("Error: {}", throwable.getMessage());
    response.setException(throwable);
    TraceBackService.trace(throwable);
    return response;
  }

  private PortalRestResponse onStripeException(StripeException ex, PortalRestResponse response) {
    logger.error("Stripe Error: {}", ex.getMessage());
    TraceBackService.trace(ex);
    response.setException(ex);
    return response;
  }

  private PortalRestResponse onAuthSecurityException(
      AuthSecurityException e, PortalRestResponse response) {
    logger.error("Access Error: {}", e.getMessage());
    response.setException(e);
    return response;
  }

  private PortalRestResponse onAxelorException(AxelorException ex, PortalRestResponse response) {
    logger.error("Error: {}", ex.getMessage());
    TraceBackService.trace(ex);
    response.setException(ex);
    return response;
  }
}
