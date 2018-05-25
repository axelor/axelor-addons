/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.redmine.web;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.redmine.service.RedmineService;
import com.axelor.apps.redmine.service.batch.RedmineBatchService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.mysql.jdbc.StringUtils;

public class RedmineBatchController {

	@Inject
	private AppRedmineRepository appRedmineRepo;

	@Inject
	private RedmineBatchRepository redmineBatchRepo;

	@Inject
	private RedmineBatchService redmineBatchService;

	@Inject
	private RedmineService redmineService;

	public void actionImport(ActionRequest request, ActionResponse response) throws AxelorException {
		AppRedmine appRedmine = appRedmineRepo.all().fetchOne();

		if(!StringUtils.isNullOrEmpty(appRedmine.getUri()) && !StringUtils.isNullOrEmpty(appRedmine.getApiAccessKey())) {
			// checking redmine credentials using Api access key
			redmineService.checkRedmineCredentials(appRedmine.getUri(), appRedmine.getApiAccessKey());

			Batch batch = redmineBatchService.importIssues(redmineBatchRepo.find(request.getContext().asType(RedmineBatch.class).getId()));
			if (batch != null)
				response.setFlash(batch.getComments());
			response.setReload(true);
		} else {
			throw new AxelorException(TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, IMessage.REDMINE_AUTHENTICATION_1);
		}
	}
}
