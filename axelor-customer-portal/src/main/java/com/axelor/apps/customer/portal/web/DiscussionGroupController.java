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

import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.client.portal.db.DiscussionGroup;
import com.axelor.apps.client.portal.db.DiscussionPost;
import com.axelor.auth.db.User;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import java.util.List;

public class DiscussionGroupController {

  public void openGroup(ActionRequest request, ActionResponse response) {

    ActionViewBuilder actionViewBuilder =
        ActionView.define(I18n.get("Discussion posts"))
            .model(DiscussionPost.class.getCanonicalName())
            .add("grid", "discussion-post-grid")
            .add("form", "discussion-post-form")
            .param("details-view", "true")
            .param("grid-width", "25%");
    Context context = request.getContext();
    if (context.containsKey("_ids")) {
      @SuppressWarnings("unchecked")
      List<Long> ids = (List<Long>) context.get("_ids");
      actionViewBuilder
          .domain("self.discussionGroup.id IN :discussionGroupIds")
          .context("discussionGroupIds", ids);
      response.setView(actionViewBuilder.map());
    } else {
      DiscussionGroup discussionGroup = context.asType(DiscussionGroup.class);
      if (discussionGroup.getId() != null) {
        actionViewBuilder
            .domain("self.discussionGroup.id = :discussionGroupId")
            .context("discussionGroupId", discussionGroup.getId());
        response.setView(actionViewBuilder.map());
      }
    }
  }

  public void openAllPost(ActionRequest request, ActionResponse response) {
    User currentUser = Beans.get(UserService.class).getUser();
    String domain =
        "self.discussionGroup.id IN "
            + "(SELECT relatedId FROM MailFollower mailFollower "
            + "WHERE mailFollower.relatedModel = :relatedModel "
            + "AND mailFollower.user = :user "
            + "AND mailFollower.archived = false)";
    response.setView(
        ActionView.define(I18n.get("Discussion posts"))
            .model(DiscussionPost.class.getCanonicalName())
            .add("grid", "dashboard-discussion-post-grid")
            .add("form", "discussion-post-form")
            .domain(domain)
            .context("relatedModel", DiscussionGroup.class.getName())
            .context("user", currentUser)
            .context("isAdd", false)
            .param("details-view", "true")
            .param("grid-width", "25%")
            .map());
  }
}
