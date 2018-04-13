package com.axelor.apps.redmine.service.app;

import java.util.List;

import com.axelor.exception.AxelorException;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Issue;

public interface AppRedmineService {
	
	public RedmineManager checkRedmineCredentials(String uri, String apiAccessKey) throws AxelorException ;
	
	public List<Issue> getIssuesOfAllRedmineProjects() throws RedmineException;
	
	public void createTicketFromIssue(Issue issue) throws RedmineException;
	
	public boolean isTicketTypeExist(String ticketType);
	
	public boolean isAssignedToUserExist(String userName);
}
