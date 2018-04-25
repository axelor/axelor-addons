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
package com.axelor.apps.redmine.service;

import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.db.AppRedmine;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.AppRedmineRepository;
import com.axelor.apps.base.db.repo.UserBaseRepository;
import com.axelor.apps.helpdesk.db.Ticket;
import com.axelor.apps.helpdesk.db.TicketType;
import com.axelor.apps.helpdesk.db.repo.TicketRepository;
import com.axelor.apps.helpdesk.db.repo.TicketTypeRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.mysql.jdbc.StringUtils;
import com.taskadapter.redmineapi.Params;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.CustomFieldDefinition;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Tracker;

public class RedmineServiceImpl implements RedmineService {

	@Inject
	private AppRedmineRepository appRedmineRepo;

	@Inject
	private ProjectRepository projectRepo;

	@Inject
	private TicketRepository ticketRepo;

	@Inject
	private TicketTypeRepository ticketTypeRepo;

	@Inject
	private UserBaseRepository userRepo;

	private final static String CUSTOM_FIELD1_NAME = "isImported";
	private final static String CUSTOM_FIELD1_TYPE = "issue";
	private final static String CUSTOM_FIELD1_FORMAT = "bool";

	private final static String FILTER_BY_PROJECT = "project_id";
	private final static String FILTER_BY_STATUS = "status_id";
	private final static String FILTER_BY_PRIORITY = "priority_id";
	private final static String FILTER_BY_TICKET_TYPE = "tracker_id";
	private final static String FILTER_BY_START_DATE = "start_date";
	private final static String FILTER_BY_CUSTOM_FIELD1 = "cf_";

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Override
	@Transactional
	public void importRedmineIssues(Batch batch, Consumer<Ticket> onSuccess, Consumer<Throwable> onError)
			throws AxelorException, RedmineException {

		final RedmineManager redmineManager = getRedmineManager();
		if (redmineManager == null) {
			return;
		}
		
		final CustomFieldDefinition customFieldDefinition = getCustomFieldDefinition(redmineManager);
		if (customFieldDefinition == null) {
			return;
		}
		
		Set<Project> projects = batch.getRedmineBatch().getProjectSet();
		if (projects.size() > 0) {
			projects = projects.stream().filter(project -> project.getCode() != null).collect(Collectors.toSet());
		} else {
			projects = projectRepo.all().filter("self.code is not null").fetchSteam().collect(Collectors.toSet());
		}

		Map<Integer, Project> commonProjects = obtainCommonProjects(projects, redmineManager);

		if (commonProjects.size() > 0) {
			Params params = applyFilters(batch.getRedmineBatch(), redmineManager, commonProjects,
					customFieldDefinition);

			List<Issue> issueList = redmineManager.getIssueManager().getIssues(params).getResults();
			if (issueList != null && issueList.size() > 0) {
				for (Issue issue : issueList) {
					try {
						Project absProject = commonProjects.get(issue.getProjectId());
						Ticket ticket = createTicketFromIssue(issue, absProject, customFieldDefinition, redmineManager);
						ticketRepo.save(ticket);
						onSuccess.accept(ticket);
					} catch (RedmineException e) {
						onError.accept(e);
					}
				}
			}
		}
	}

	private RedmineManager getRedmineManager() {

		AppRedmine appRedmine = appRedmineRepo.all().fetchOne();

		if (!StringUtils.isNullOrEmpty(appRedmine.getUri())
				&& !StringUtils.isNullOrEmpty(appRedmine.getApiAccessKey())) {
			return RedmineManagerFactory.createWithApiKey(appRedmine.getUri(), appRedmine.getApiAccessKey());
		}
		return null;
	}

	private CustomFieldDefinition getCustomFieldDefinition(RedmineManager redmineManager) throws AxelorException {

		CustomFieldDefinition customFieldDefinition = null;
		try {
			List<CustomFieldDefinition> customFieldDefList = redmineManager.getCustomFieldManager()
					.getCustomFieldDefinitions();

			customFieldDefinition = customFieldDefList.stream()
					.filter(df -> df.getName().contains(CUSTOM_FIELD1_NAME))
					.filter(df -> df.getCustomizedType().equalsIgnoreCase(CUSTOM_FIELD1_TYPE))
					.filter(df -> df.getFieldFormat().equalsIgnoreCase(CUSTOM_FIELD1_FORMAT))
					.filter(df -> df.isFilter()).findFirst().orElseThrow(
							() -> new AxelorException(IException.CONFIGURATION_ERROR, IMessage.REDMINE_CONFIGURATION));

		} catch (RedmineException e) {
			throw new AxelorException(IException.CONFIGURATION_ERROR, IMessage.REDMINE_AUTHENTICATION_2);
		}
		return customFieldDefinition;
	}

	private Map<Integer, Project> obtainCommonProjects(Set<Project> projects, RedmineManager redmineManager) {

		Map<Integer, Project> commonProjects = new HashMap<>();

		projects.stream().forEach(project -> {
			com.taskadapter.redmineapi.bean.Project redmineProject;

			try {
				redmineProject = redmineManager.getProjectManager().getProjectByKey(project.getCode().toLowerCase());
				commonProjects.put(redmineProject.getId(), project);

			} catch (RedmineException e) {
				LOG.trace("Project with code : " + project.getCode() + " not found");
			}
		});
		return commonProjects;
	}

	private Params applyFilters(RedmineBatch redmineBatch, RedmineManager redmineManager,
			Map<Integer, Project> commonProjects, CustomFieldDefinition cdf) throws RedmineException {

		Params params = new Params();

		for (Integer projectId : commonProjects.keySet()) {
			params
				.add("f[]", FILTER_BY_PROJECT)
				.add("op[" + FILTER_BY_PROJECT + "]", "=")
				.add("v[" + FILTER_BY_PROJECT + "][]", projectId.toString());
		}
		if (!redmineBatch.getIsResetImported()) {
			params
				.add("f[]", FILTER_BY_CUSTOM_FIELD1 + cdf.getId())
				.add("op[" + FILTER_BY_CUSTOM_FIELD1 + cdf.getId() + "]", "!")
				.add("v[" + FILTER_BY_CUSTOM_FIELD1 + cdf.getId() + "][]", "1");
		}
		if (redmineBatch.getStatusSelect() != 0) {
			params
				.add("f[]", FILTER_BY_STATUS)
				.add("op[" + FILTER_BY_STATUS + "]", "=")
				.add("v[" + FILTER_BY_STATUS + "][]", redmineBatch.getStatusSelect().toString());
		}
		if (redmineBatch.getPrioritySelect() != 0) {
			params
				.add("f[]", FILTER_BY_PRIORITY)
				.add("op[" + FILTER_BY_PRIORITY + "]", "=")
				.add("v[" + FILTER_BY_PRIORITY + "][]", redmineBatch.getPrioritySelect().toString());
		}
		if (redmineBatch.getTicketType() != null) {
			Optional<Tracker> tracker = redmineManager.getIssueManager().getTrackers().stream()
					.filter(t -> t.getName().equals(redmineBatch.getTicketType().getName())).findFirst();
			if (tracker.isPresent()) {
				params
					.add("v[" + FILTER_BY_TICKET_TYPE + "][]", tracker.get().getId().toString())
					.add("f[]", FILTER_BY_TICKET_TYPE)
					.add("op[" + FILTER_BY_TICKET_TYPE + "]", "=");
			}
		}
		if (redmineBatch.getStartDate() != null) {
			params
				.add("f[]", FILTER_BY_START_DATE)
				.add("op[" + FILTER_BY_START_DATE + "]", "=")
				.add("v[" + FILTER_BY_START_DATE + "][]", redmineBatch.getStartDate().toString());
		}

		return params;
	}

	private Ticket createTicketFromIssue(Issue issue, Project absProject, CustomFieldDefinition cdf, RedmineManager mgr)
			throws RedmineException {

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

		CustomField customField = issue.getCustomFieldByName(cdf.getName());
		if (customField != null) {
			customField.setValue("1");
			mgr.getIssueManager().update(issue);
		}
		return ticket;
	}
}