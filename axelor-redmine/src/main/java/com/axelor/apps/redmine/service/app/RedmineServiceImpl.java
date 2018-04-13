package com.axelor.apps.redmine.service.app;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.taskadapter.redmineapi.IssueManager;
import com.taskadapter.redmineapi.Params;
import com.taskadapter.redmineapi.RedmineAuthenticationException;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.CustomFieldDefinition;
import com.taskadapter.redmineapi.bean.Issue;

@Singleton
public class RedmineServiceImpl implements RedmineService{

	@Inject
	TicketRepository ticketRepo;
	
	@Inject
	TicketTypeRepository ticketTypeRepo;
	
	@Inject
	UserBaseRepository userRepo;

	private RedmineManager mgr;
	
	private IssueManager imgr;
	
	private Map<Integer, Project> commonProjects;
	
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

	@SuppressWarnings("deprecation")
	@Override
	public List<Issue> getIssuesOfAllRedmineProjects() throws RedmineException, AxelorException {
	 
		CustomFieldDefinition cdf = mgr.getCustomFieldManager().getCustomFieldDefinitions().stream()
				.filter(df -> df.getName().contains("isImported"))
				.filter(df -> df.getCustomizedType().equalsIgnoreCase("issue"))
				.filter(df -> df.getFieldFormat().equalsIgnoreCase("bool"))
				.filter(df -> df.isFilter())
				.findFirst().orElseThrow(() -> new AxelorException("Redmine is not properly configured. Please check the configuration on readme file.", IException.CONFIGURATION_ERROR));

		List<Project> absProjects = Beans.get(ProjectRepository.class).all().fetchSteam().filter(pro -> pro.getCode()!=null).collect(Collectors.toList());
		commonProjects = new HashMap<>(); 
		
		absProjects.stream().forEach(project -> {
			com.taskadapter.redmineapi.bean.Project redmineProject;
			try {
				redmineProject = mgr.getProjectManager().getProjectByKey(project.getCode().toLowerCase());
				commonProjects.put(redmineProject.getId(), project);
			} catch (RedmineException e) {	e.printStackTrace();   }
		});
		
		if(commonProjects.size()>0) {
			Params params = new Params()
					.add("f[]", "project_id")
					.add("op[project_id]", "=");
			
			commonProjects.entrySet().forEach(singleCommonProject ->  {
				params.add("v[project_id][]", singleCommonProject.getKey().toString());
			});
			
			params.add("f[]", "cf_"+cdf.getId())
			.add("op[cf_"+cdf.getId()+"]", "!")
			.add("v[cf_"+cdf.getId()+"][]","1");
			
			List<Issue> issues = null;
			imgr = mgr.getIssueManager();
			issues = imgr.getIssues(params).getResults();
			if(issues!=null && issues.size()>0)
				return issues;
		}
		return null;
	}
	
	@Override
	@Transactional
	public void createTicketFromIssue(Issue issue) throws RedmineException {
        Project absProject = commonProjects.get(issue.getProjectId());
        
	    Ticket ticket = new Ticket();
	    ticket.setRedmineId(issue.getId());
	    ticket.setSubject(issue.getSubject());
	    ticket.setProject(absProject);
	    ticket.setDescription(issue.getDescription());
	    ticket.setPrioritySelect(issue.getPriorityId());
	    ticket.setProgressSelect(issue.getDoneRatio());
	    ticket.setStartDateT(LocalDateTime.ofInstant(issue.getStartDate().toInstant(), ZoneId.systemDefault()));
	
	    TicketType ticketType = ticketTypeRepo.findByName(issue.getTracker().getName());
	    if (ticketType == null) {
	        TicketType newTicketType = new TicketType();
	        newTicketType.setName(issue.getTracker().getName());
	        ticketType = newTicketType;
	    }
	    ticket.setTicketType(ticketType);
	
	    ticket.setAssignedToUser(userRepo.findByName(issue.getAssigneeName()));
	    ticketRepo.save(ticket);
	    
	    CustomField customField = issue.getCustomFieldByName("isImported");
	    if (customField != null) {
	    	customField.setValue("1");
	        imgr.update(issue);
	    }
	}
}
