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
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.redmine.db.RedmineBatch;
import com.axelor.apps.redmine.db.repo.RedmineBatchRepository;
import com.axelor.apps.redmine.message.IMessage;
import com.axelor.apps.redmine.service.batch.RedmineBatchService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.team.db.TeamTask;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class RedmineBatchController {

  @Inject private RedmineBatchRepository redmineBatchRepo;
  @Inject private BatchRepository batchRepo;

  public void redmineImportProjects(ActionRequest request, ActionResponse response) {

    RedmineBatch redmineBatch = request.getContext().asType(RedmineBatch.class);
    redmineBatch = redmineBatchRepo.find(redmineBatch.getId());

    Batch batch = Beans.get(RedmineBatchService.class).redmineImportProjects(redmineBatch);

    if (batch != null) {
      response.setFlash(IMessage.BATCH_REDMINE_IMPORT_SUCCESS);
    }

    response.setReload(true);
  }

  public void redmineImportIssues(ActionRequest request, ActionResponse response) {

    RedmineBatch redmineBatch = request.getContext().asType(RedmineBatch.class);
    redmineBatch = redmineBatchRepo.find(redmineBatch.getId());

    Batch batch = Beans.get(RedmineBatchService.class).redmineImportIssues(redmineBatch);

    if (batch != null) {
      response.setFlash(IMessage.BATCH_REDMINE_IMPORT_SUCCESS);
    }

    response.setReload(true);
  }

  public void createdProjectsInOs(ActionRequest request, ActionResponse response) {

    Batch batch = request.getContext().asType(Batch.class);
    batch = batchRepo.find(batch.getId());

    List<Long> idList = new ArrayList<Long>();
    batch.getCreatedProjectsInOs().forEach(p -> idList.add(p.getId()));

    if (!idList.isEmpty()) {
      response.setView(
          ActionView.define(I18n.get("Projects"))
              .model(Project.class.getName())
              .add("grid", "project-grid")
              .add("form", "project-form")
              .domain("self.id in (" + Joiner.on(",").join(idList) + ")")
              .map());

      response.setCanClose(true);
    }
  }

  public void updatedProjectsInOs(ActionRequest request, ActionResponse response) {

    Batch batch = request.getContext().asType(Batch.class);
    batch = batchRepo.find(batch.getId());

    List<Long> idList = new ArrayList<Long>();
    batch.getUpdatedProjectsInOs().forEach(p -> idList.add(p.getId()));

    if (!idList.isEmpty()) {
      response.setView(
          ActionView.define(I18n.get("Projects"))
              .model(Project.class.getName())
              .add("grid", "project-grid")
              .add("form", "project-form")
              .domain("self.id in (" + Joiner.on(",").join(idList) + ")")
              .map());

      response.setCanClose(true);
    }
  }

  public void createdTimesheetLinesInOs(ActionRequest request, ActionResponse response) {

    Batch batch = request.getContext().asType(Batch.class);
    batch = batchRepo.find(batch.getId());

    List<Long> idList = new ArrayList<Long>();
    batch.getCreatedTimesheetLinesInOs().forEach(t -> idList.add(t.getId()));

    if (!idList.isEmpty()) {
      response.setView(
          ActionView.define(I18n.get("Timesheetlines"))
              .model(TimesheetLine.class.getName())
              .add("grid", "timesheet-line-grid")
              .add("form", "timesheet-line-form")
              .domain("self.id in (" + Joiner.on(",").join(idList) + ")")
              .map());

      response.setCanClose(true);
    }
  }

  public void updatedTimesheetLinesInOs(ActionRequest request, ActionResponse response) {

    Batch batch = request.getContext().asType(Batch.class);
    batch = batchRepo.find(batch.getId());

    List<Long> idList = new ArrayList<Long>();
    batch.getUpdatedTimesheetLinesInOs().forEach(t -> idList.add(t.getId()));

    if (!idList.isEmpty()) {
      response.setView(
          ActionView.define(I18n.get("Timesheetlines"))
              .model(TimesheetLine.class.getName())
              .add("grid", "timesheet-line-grid")
              .add("form", "timesheet-line-form")
              .domain("self.id in (" + Joiner.on(",").join(idList) + ")")
              .map());

      response.setCanClose(true);
    }
  }

  public void createdTasksInOs(ActionRequest request, ActionResponse response) {

    Batch batch = request.getContext().asType(Batch.class);
    batch = batchRepo.find(batch.getId());

    List<Long> idList = new ArrayList<Long>();
    batch.getCreatedTasksInOs().forEach(t -> idList.add(t.getId()));

    if (!idList.isEmpty()) {
      response.setView(
          ActionView.define(I18n.get("Teamtasks"))
              .model(TeamTask.class.getName())
              .add("grid", "team-task-grid")
              .add("form", "team-task-form")
              .domain("self.id in (" + Joiner.on(",").join(idList) + ")")
              .map());

      response.setCanClose(true);
    }
  }

  public void updatedTasksInOs(ActionRequest request, ActionResponse response) {

    Batch batch = request.getContext().asType(Batch.class);
    batch = batchRepo.find(batch.getId());

    List<Long> idList = new ArrayList<Long>();
    batch.getUpdatedTasksInOs().forEach(t -> idList.add(t.getId()));

    if (!idList.isEmpty()) {
      response.setView(
          ActionView.define(I18n.get("Teamtasks"))
              .model(TeamTask.class.getName())
              .add("grid", "team-task-grid")
              .add("form", "team-task-form")
              .domain("self.id in (" + Joiner.on(",").join(idList) + ")")
              .map());

      response.setCanClose(true);
    }
  }
}
