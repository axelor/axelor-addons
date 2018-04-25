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

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.apps.redmine.service.batch.RedmineBatchService;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class RedmineBatchController {

	@Inject
	private RedmineBatchRepository redmineBatchRepo;

	@Inject
	private RedmineBatchService redmineBatchService;

	public void actionImport(ActionRequest request, ActionResponse response) {
		RedmineBatch redmineBatch = request.getContext().asType(RedmineBatch.class);
		
		// checking redmine credentials using Api access key
		Batch batch = redmineBatchService.importIssues(redmineBatchRepo.find(redmineBatch.getId()));
		if (batch != null) {
			response.setFlash(batch.getComments());
		}
		response.setReload(true);
	}
}