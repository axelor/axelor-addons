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

import com.axelor.apps.client.portal.db.DiscussionPost;
import com.axelor.apps.client.portal.db.repo.DiscussionPostRepository;
import com.axelor.apps.customer.portal.service.CommonService;
import com.axelor.inject.Beans;
import com.axelor.mail.db.MailFollower;
import com.axelor.mail.db.repo.MailFollowerRepository;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.persist.Transactional;
import java.util.List;

public class DiscussionPostController {

  public void addFollower(ActionRequest request, ActionResponse response) {

    DiscussionPost discussionPost = request.getContext().asType(DiscussionPost.class);
    discussionPost = Beans.get(DiscussionPostRepository.class).find(discussionPost.getId());
    final MailFollowerRepository followerRepo = Beans.get(MailFollowerRepository.class);

    List<MailFollower> followers = followerRepo.findAll(discussionPost.getDiscussionGroup());
    for (MailFollower mailFollower : followers) {
      followerRepo.follow(discussionPost, mailFollower.getUser());
    }
  }

  @Transactional
  public void markRead(ActionRequest request, ActionResponse response) {

    DiscussionPost post = request.getContext().asType(DiscussionPost.class);
    post = Beans.get(DiscussionPostRepository.class).find(post.getId());
    Beans.get(CommonService.class).manageReadRecordIds(post);
  }
}
