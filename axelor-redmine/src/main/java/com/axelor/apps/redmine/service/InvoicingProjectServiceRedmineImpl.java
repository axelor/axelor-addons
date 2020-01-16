package com.axelor.apps.redmine.service;

import com.axelor.apps.businessproduction.service.InvoicingProjectServiceBusinessProdImpl;
import com.axelor.apps.businessproject.db.InvoicingProject;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.service.ProjectServiceImpl;
import com.axelor.inject.Beans;
import java.util.List;

public class InvoicingProjectServiceRedmineImpl extends InvoicingProjectServiceBusinessProdImpl {

  @Override
  public void setLines(InvoicingProject invoicingProject, Project project, int counter) {
    if (counter > ProjectServiceImpl.MAX_LEVEL_OF_PROJECT) {
      return;
    }
    counter++;

    this.fillLines(invoicingProject, project);

    if (!invoicingProject.getConsolidatePhaseWhenInvoicing()) {
      return;
    }

    List<Project> projectChildrenList =
        Beans.get(ProjectRepository.class).all().filter("self.parentProject = ?1", project).fetch();

    for (Project projectChild : projectChildrenList) {
      this.setLines(invoicingProject, projectChild, counter);
    }
    return;
  }
}
