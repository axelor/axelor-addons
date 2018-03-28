package com.axelor.apps.redmine.service.app;

import com.axelor.apps.helpdesk.db.Ticket;
import com.axelor.apps.helpdesk.db.TicketType;
import com.axelor.exception.AxelorException;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Issue;

public interface AppRedmineService {
	
	public RedmineManager checkRedmineCredentials(String uri, String apiAccessKey) throws AxelorException ;
	
	public int[] getIssuesOfAllRedmineProjects() throws RedmineException;
	
	public int createTicket(Ticket ticket);
	
	public void createTicketType(TicketType ticketType);
	
	public void createTicketFromIssue(Issue issue, String projectCode);
	
	public boolean isTicketTypeExist(String ticketType);
	
	public boolean isAssignedToUserExist(String userName);
}
