package com.axelor.apps.redmine.service.app;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.axelor.apps.base.db.repo.UserBaseRepository;
import com.axelor.apps.helpdesk.db.Ticket;
import com.axelor.apps.helpdesk.db.TicketType;
import com.axelor.apps.helpdesk.db.repo.TicketRepository;
import com.axelor.apps.helpdesk.db.repo.TicketTypeRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineAuthenticationException;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;

@Singleton
public class AppRedmineServiceImpl implements AppRedmineService{

	@Inject
	TicketRepository ticketRepo;
	
	@Inject
	TicketTypeRepository ticketTypeRepo;
	
	@Inject
	UserBaseRepository userRepo;

	private RedmineManager mgr;

	private int SUCCEEDED_TICKET_COUNT;
	
	private int ANOMALY_TICKET_COUNT;
	
	@SuppressWarnings("deprecation")
	@Override
	public RedmineManager checkRedmineCredentials(String uri, String apiAccessKey) throws AxelorException {
		try {
			RedmineManager mgr = RedmineManagerFactory.createWithApiKey(uri, apiAccessKey);
			
			if (mgr.getProjectManager().getProjects() instanceof RedmineAuthenticationException) 
				mgr = null;
			else if(mgr.getProjectManager().getProjects().size()>0)
				this.mgr = mgr;
		} catch(Exception e) {
			throw new AxelorException("Please check your authentication details", IException.CONFIGURATION_ERROR);
		}
		return mgr;
	}

	@Override
	public int[] getIssuesOfAllRedmineProjects() throws RedmineException {
		Integer queryId = null;
		SUCCEEDED_TICKET_COUNT = 0;
		ANOMALY_TICKET_COUNT = 0;
		
		//ABS projects code
		List<String> projectCodes = Beans.get(ProjectRepository.class).all().fetchSteam().map(p -> p.getCode()).collect(Collectors.toList());
				
		List<com.taskadapter.redmineapi.bean.Project> commomProjects = new ArrayList<>();//Common projects between ABS projects and Redmine projects
		
		mgr.getProjectManager().getProjects().stream().forEach(project -> {
			if(projectCodes.stream().anyMatch(project.getIdentifier()::equalsIgnoreCase))
				commomProjects.add(project);
		});
		
		for (com.taskadapter.redmineapi.bean.Project singleCommonProject : commomProjects) {
			List<Issue> issues = mgr.getIssueManager().getIssues(singleCommonProject.getIdentifier(), queryId);
			if(issues!=null && issues.size()>0) {
				String code = projectCodes.stream().filter(codes -> codes.equalsIgnoreCase(singleCommonProject.getIdentifier())).findFirst().orElse(null);
				if(code!=null)
					createTicketsFromIssues(issues,code);
			}
		}
		return new int[] { SUCCEEDED_TICKET_COUNT , ANOMALY_TICKET_COUNT };
	}
	
	@Override
	@Transactional
	public int createTicket(Ticket ticket) {
		if(ticketRepo.save(ticket)!=null)
			return 1;
		else 
			return 0;
	}

	@Override
	@Transactional
	public void createTicketType(TicketType ticketType) {
		ticketTypeRepo.save(ticketType);
	}
	
	@Override
	public void createTicketsFromIssues(List<Issue> issues, String projectCode) {
		
		Project absProject = Beans.get(ProjectRepository.class).findByCode(projectCode);
		for (Issue issue : issues) {	
			Ticket ticket = ticketRepo.all().filter("self.redmineId = ?1",issue.getId()).fetchOne();
			if(ticket==null) {
				ticket = new Ticket();
				ticket.setRedmineId(issue.getId());
				ticket.setSubject(issue.getSubject());
				ticket.setProject(absProject);
				ticket.setDescription(issue.getDescription());
				ticket.setPrioritySelect(issue.getPriorityId());
				ticket.setProgressSelect(issue.getDoneRatio());
				ticket.setStartDateT(LocalDateTime.ofInstant(issue.getStartDate().toInstant(), ZoneId.systemDefault()));
				
				if(isTicketTypeExist(issue.getTracker().getName()))
					ticket.setTicketType(ticketTypeRepo.findByName(issue.getTracker().getName()));
				else {
					TicketType ticketType = new TicketType();
					ticketType.setName(issue.getTracker().getName());
					createTicketType(ticketType);
					ticket.setTicketType(ticketType);
				}
				
				if(isAssignedToUserExist(issue.getAssigneeName()))
						ticket.setAssignedToUser(userRepo.findByName(issue.getAssigneeName()));	
				if(createTicket(ticket)==1)
					SUCCEEDED_TICKET_COUNT  += 1;
				else 
					ANOMALY_TICKET_COUNT += 1;
			}
		}
	}

	@Override
	public boolean isTicketTypeExist(String ticketType) {
		TicketType type = ticketTypeRepo.all().filter("self.name = ?1",ticketType).fetchOne();
		if(type!=null)
			return true;
		else 
			return false;
	}

	@Override
	public boolean isAssignedToUserExist(String userName) {
		User user = Beans.get(UserBaseRepository.class).findByName(userName);
		if(user!=null)
			return true;
		else 
			return false;
	}
}
