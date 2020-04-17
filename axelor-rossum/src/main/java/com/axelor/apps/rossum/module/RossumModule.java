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
package com.axelor.apps.rossum.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.rossum.service.InvoiceOcrService;
import com.axelor.apps.rossum.service.InvoiceOcrServiceImpl;
import com.axelor.apps.rossum.service.InvoiceOcrTemplateService;
import com.axelor.apps.rossum.service.InvoiceOcrTemplateServiceImpl;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.apps.rossum.service.app.AppRossumServiceImpl;
import com.axelor.apps.rossum.service.organisation.OrganisationService;
import com.axelor.apps.rossum.service.organisation.OrganisationServiceImpl;
import com.axelor.apps.rossum.service.queue.QueueService;
import com.axelor.apps.rossum.service.queue.QueueServiceImpl;
import com.axelor.apps.rossum.service.schema.SchemaService;
import com.axelor.apps.rossum.service.schema.SchemaServiceImpl;
import com.axelor.apps.rossum.service.workspace.WorkspaceService;
import com.axelor.apps.rossum.service.workspace.WorkspaceServiceImpl;

public class RossumModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(AppRossumService.class).to(AppRossumServiceImpl.class);
    bind(InvoiceOcrService.class).to(InvoiceOcrServiceImpl.class);
    bind(InvoiceOcrTemplateService.class).to(InvoiceOcrTemplateServiceImpl.class);
    bind(OrganisationService.class).to(OrganisationServiceImpl.class);
    bind(WorkspaceService.class).to(WorkspaceServiceImpl.class);
    bind(SchemaService.class).to(SchemaServiceImpl.class);
    bind(QueueService.class).to(QueueServiceImpl.class);
  }
}
