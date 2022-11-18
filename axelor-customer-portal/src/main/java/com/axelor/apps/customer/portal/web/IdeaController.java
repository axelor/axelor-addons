/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2022 Axelor (<http://axelor.com>).
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
package com.axelor.apps.customer.portal.web;

import com.axelor.apps.client.portal.db.Idea;
import com.axelor.apps.client.portal.db.repo.IdeaRepository;
import com.axelor.apps.customer.portal.service.CommonService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.persist.Transactional;

public class IdeaController {

  @Transactional
  public void acceptIdea(ActionRequest request, ActionResponse response) {

    Idea idea = request.getContext().asType(Idea.class);
    idea = Beans.get(IdeaRepository.class).find(idea.getId());
    idea.setAccepted(!idea.getAccepted());
    Beans.get(IdeaRepository.class).save(idea);
    response.setReload(true);
  }

  @Transactional
  public void closeIdea(ActionRequest request, ActionResponse response) {

    Idea idea = request.getContext().asType(Idea.class);
    idea = Beans.get(IdeaRepository.class).find(idea.getId());
    idea.setClose(!idea.getClose());
    Beans.get(IdeaRepository.class).save(idea);
    response.setReload(true);
  }

  @Transactional
  public void markRead(ActionRequest request, ActionResponse response) {

    Idea idea = request.getContext().asType(Idea.class);
    idea = Beans.get(IdeaRepository.class).find(idea.getId());
    Beans.get(CommonService.class).manageReadRecordIds(idea);
  }
}
