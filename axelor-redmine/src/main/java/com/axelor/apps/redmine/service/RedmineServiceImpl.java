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

import com.axelor.apps.base.db.Batch;
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
import com.axelor.exception.db.repo.TraceBackRepository;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.IssueManager;
import com.taskadapter.redmineapi.Params;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.CustomFieldDefinition;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Tracker;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RedmineServiceImpl implements RedmineService {

  @Inject private ProjectRepository projectRepo;

  @Inject private TicketRepository ticketRepo;

  @Inject private TicketTypeRepository ticketTypeRepo;

  @Inject private UserBaseRepository userRepo;

  private RedmineManager mgr;

  private CustomFieldDefinition cdf;

  private IssueManager imgr;

  private Map<Integer, Project> commonProjects;

  private Params params;

  private static final String CUSTOM_FIELD1_NAME = "isImported";
  private static final String CUSTOM_FIELD1_TYPE = "issue";
  private static final String CUSTOM_FIELD1_FORMAT = "bool";

  private static final String FILTER_BY_PROJECT = "project_id";
  private static final String FILTER_BY_STATUS = "status_id";
  private static final String FILTER_BY_PRIORITY = "priority_id";
  private static final String FILTER_BY_TICKET_TYPE = "tracker_id";
  private static final String FILTER_BY_START_DATE = "start_date";
  private static final String FILTER_BY_CUSTOM_FIELD1 = "cf_";

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public void checkRedmineCredentials(String uri, String apiAccessKey) throws AxelorException {

    this.mgr = RedmineManagerFactory.createWithApiKey(uri, apiAccessKey);

    try {
      List<CustomFieldDefinition> customFieldDef =
          mgr.getCustomFieldManager().getCustomFieldDefinitions();
      cdf =
          customFieldDef
              .stream()
              .filter(df -> df.getName().contains(CUSTOM_FIELD1_NAME))
              .filter(df -> df.getCustomizedType().equalsIgnoreCase(CUSTOM_FIELD1_TYPE))
              .filter(df -> df.getFieldFormat().equalsIgnoreCase(CUSTOM_FIELD1_FORMAT))
              .filter(df -> df.isFilter())
              .findFirst()
              .orElseThrow(
                  () ->
                      new AxelorException(
                          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
                          IMessage.REDMINE_CONFIGURATION));

    } catch (RedmineException e) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, IMessage.REDMINE_AUTHENTICATION_2);
    }
  }

  private void obtainCommonProjects(Set<Project> projects) {

    projects
        .stream()
        .forEach(
            project -> {
              com.taskadapter.redmineapi.bean.Project redmineProject;

              try {
                redmineProject =
                    mgr.getProjectManager().getProjectByKey(project.getCode().toLowerCase());
                params
                    .add("f[]", FILTER_BY_PROJECT)
                    .add("op[" + FILTER_BY_PROJECT + "]", "=")
                    .add("v[" + FILTER_BY_PROJECT + "][]", redmineProject.getId().toString());
                commonProjects.put(redmineProject.getId(), project);

              } catch (RedmineException e) {
                LOG.trace("Project with code : " + project.getCode() + " not found");
              }
            });
  }

  private void applyFilters(RedmineBatch redmineBatch) throws RedmineException {

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
      Optional<Tracker> tracker =
          mgr.getIssueManager()
              .getTrackers()
              .stream()
              .filter(t -> t.getName().equals(redmineBatch.getTicketType().getName()))
              .findFirst();
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
  }

  @Override
  public List<Issue> getIssues(Batch batch) throws RedmineException {

    commonProjects = new HashMap<>();
    params = new Params();

    Set<Project> projects = batch.getRedmineBatch().getProjectSet();
    if (projects.isEmpty())
      projects =
          projectRepo
              .all()
              .filter("self.code is not null")
              .fetchSteam()
              .collect(Collectors.toSet());

    obtainCommonProjects(projects);

    if (commonProjects.size() > 0) {
      applyFilters(batch.getRedmineBatch());

      List<Issue> issues = null;
      imgr = mgr.getIssueManager();
      issues = imgr.getIssues(params).getResults();
      if (issues != null && issues.size() > 0) return issues;
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
    ticket.setStartDateT(
        LocalDateTime.ofInstant(issue.getStartDate().toInstant(), ZoneId.systemDefault()));

    TicketType ticketType = ticketTypeRepo.findByName(issue.getTracker().getName());
    if (ticketType == null) {
      TicketType newTicketType = new TicketType();
      newTicketType.setName(issue.getTracker().getName());
      ticketType = newTicketType;
    }
    ticket.setTicketType(ticketType);

    ticket.setAssignedToUser(userRepo.findByName(issue.getAssigneeName()));
    ticketRepo.save(ticket);

    CustomField customField = issue.getCustomFieldByName(cdf.getName());
    if (customField != null) {
      customField.setValue("1");
      imgr.update(issue);
    }
  }
}
