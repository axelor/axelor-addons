package com.axelor.apps.redmine.service.batch;

import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.redmine.service.app.AppRedmineService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.RedmineException;

public class BatchImportIssues extends AbstractBatch{

	@Inject
	AppRedmineService redmineService;
	
	@Override
	protected void process() {
		try {
			int[] count = redmineService.getIssuesOfAllRedmineProjects();
			for (int i = 0; i < count[0]; i++) {  incrementDone(); }
			for (int i = 0; i < count[1]; i++) {  incrementAnomaly(); }
		} catch (RedmineException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected void stop() {
		String comment = I18n.get(IMessage.BATCH_ISSUE_IMPORT_1) + "\n";
		comment += String.format("\t* %s "+I18n.get(IMessage.BATCH_ISSUE_IMPORT_2)+"\n", batch.getDone());
		comment += String.format("\t" + I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.ALARM_ENGINE_BATCH_4), batch.getAnomaly());

		super.stop();
		addComment(comment);
	}
}
