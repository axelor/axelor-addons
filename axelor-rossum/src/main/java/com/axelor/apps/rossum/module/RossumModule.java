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
package com.axelor.apps.rossum.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.rossum.db.repo.InvoiceOcrTemplateManagementRepository;
import com.axelor.apps.rossum.db.repo.InvoiceOcrTemplateRepository;
import com.axelor.apps.rossum.db.repo.RossumAccountManagementRepository;
import com.axelor.apps.rossum.db.repo.RossumAccountRepository;
import com.axelor.apps.rossum.service.AnnotationService;
import com.axelor.apps.rossum.service.AnnotationServiceImpl;
import com.axelor.apps.rossum.service.InvoiceOcrTemplateService;
import com.axelor.apps.rossum.service.InvoiceOcrTemplateServiceImpl;
import com.axelor.apps.rossum.service.OrganisationService;
import com.axelor.apps.rossum.service.OrganisationServiceImpl;
import com.axelor.apps.rossum.service.QueueService;
import com.axelor.apps.rossum.service.QueueServiceImpl;
import com.axelor.apps.rossum.service.RossumAccountService;
import com.axelor.apps.rossum.service.RossumAccountServiceImpl;
import com.axelor.apps.rossum.service.SchemaFieldService;
import com.axelor.apps.rossum.service.SchemaFieldServiceImpl;
import com.axelor.apps.rossum.service.SchemaService;
import com.axelor.apps.rossum.service.SchemaServiceImpl;
import com.axelor.apps.rossum.service.WorkspaceService;
import com.axelor.apps.rossum.service.WorkspaceServiceImpl;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.apps.rossum.service.app.AppRossumServiceImpl;

public class RossumModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(AppRossumService.class).to(AppRossumServiceImpl.class);
    bind(InvoiceOcrTemplateService.class).to(InvoiceOcrTemplateServiceImpl.class);
    bind(OrganisationService.class).to(OrganisationServiceImpl.class);
    bind(WorkspaceService.class).to(WorkspaceServiceImpl.class);
    bind(SchemaService.class).to(SchemaServiceImpl.class);
    bind(QueueService.class).to(QueueServiceImpl.class);
    bind(SchemaFieldService.class).to(SchemaFieldServiceImpl.class);
    bind(AnnotationService.class).to(AnnotationServiceImpl.class);
    bind(InvoiceOcrTemplateRepository.class).to(InvoiceOcrTemplateManagementRepository.class);
    bind(RossumAccountService.class).to(RossumAccountServiceImpl.class);
    bind(RossumAccountRepository.class).to(RossumAccountManagementRepository.class);
  }
}
