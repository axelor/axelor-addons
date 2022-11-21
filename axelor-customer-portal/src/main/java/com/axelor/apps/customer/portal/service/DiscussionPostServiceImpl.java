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
package com.axelor.apps.customer.portal.service;

import com.axelor.apps.client.portal.db.DiscussionPost;
import com.axelor.auth.db.User;
import com.axelor.db.EntityHelper;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.mail.db.MailFollower;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.db.repo.MailFollowerRepository;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class DiscussionPostServiceImpl implements DiscussionPostService {

  @Inject MailFollowerRepository followerRepo;

  @Override
  public void addFollowers(DiscussionPost discussionPost) {
    List<MailFollower> followers = findFollowers(discussionPost.getDiscussionGroup());
    for (MailFollower mailFollower : followers) {
      followerRepo.follow(discussionPost, mailFollower.getUser());
    }
  }

  protected List<MailFollower> findFollowers(Model entity) {
    if (entity == null) {
      return new ArrayList<>();
    }

    final Long relatedId;
    final String relatedModel;

    if (entity instanceof MailMessage) {
      relatedId = ((MailMessage) entity).getRelatedId();
      relatedModel = ((MailMessage) entity).getRelatedModel();
    } else {
      relatedId = entity.getId();
      relatedModel = EntityHelper.getEntityClass(entity).getName();
    }

    if (relatedId == null || relatedModel == null) {
      return new ArrayList<>();
    }

    return followerRepo
        .all()
        .order("user." + Mapper.of(User.class).getNameField().getName())
        .filter(
            "self.relatedModel = ? AND self.relatedId = ? AND COALESCE(self.archived, false) = false",
            relatedModel,
            relatedId)
        .fetch();
  }
}
