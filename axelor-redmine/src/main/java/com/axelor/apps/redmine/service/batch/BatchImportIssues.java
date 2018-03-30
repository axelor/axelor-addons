package com.axelor.apps.redmine.service.batch;

import java.util.List;

import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.redmine.service.app.AppRedmineService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.bean.Issue;

public class BatchImportIssues extends AbstractBatch{

	@Inject
	AppRedmineService redmineService;
	
	@SuppressWarnings("deprecation")
	@Override
	protected void process() {
		List<Issue> issues = null;
		try {
			issues = redmineService.getIssuesOfAllRedmineProjects();
			if(issues!=null && issues.size()>0) {
				for (Issue issue : issues) {
					try {
						redmineService.createTicketFromIssue(issue);
						incrementDone();
					} catch (RedmineException e) {	incrementAnomaly();	 }
				}
			}
		} catch (RedmineException e1) {
			new AxelorException("No issues for Projects found!!", IException.CONFIGURATION_ERROR);
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
