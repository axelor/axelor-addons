package com.axelor.apps.redmine.web;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.apps.redmine.service.app.RedmineService;
import com.axelor.apps.redmine.service.batch.RedmineBatchService;
import com.axelor.exception.AxelorException;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.RedmineManager;

public class RedmineBatchController {

	@Inject
	RedmineBatchRepository redmineBatchRepo;
	
	@Inject
	RedmineBatchService redmineBatchService;
	
	@Inject
	RedmineService redmineService;
	
	public void actionImport(ActionRequest request, ActionResponse response) throws AxelorException{

		RedmineBatch redmineBatch = request.getContext().asType(RedmineBatch.class);
		
		//checking redmine credentials using Api access key
		RedmineManager mgr = redmineService.checkRedmineCredentials(redmineBatch.getUri(), redmineBatch.getApiAccessKey());
		if (mgr != null) {
			
			Batch batch = redmineBatchService.eventImportIssues(redmineBatchRepo.find(redmineBatch.getId()));
			if(batch != null)
				response.setFlash(batch.getComments());
			response.setReload(true);
		}
	}
}
