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
package com.axelor.apps.customer.portal.db.repo;

import com.axelor.apps.client.portal.db.DiscussionPost;
import com.axelor.apps.client.portal.db.repo.DiscussionPostRepository;
import com.axelor.apps.customer.portal.service.CommonService;
import com.axelor.apps.customer.portal.service.DiscussionPostService;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.inject.Beans;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.google.inject.Inject;
import java.util.Map;

public class DiscussionPostManagementRepository extends DiscussionPostRepository {

  @Inject UserRepository userRepo;

  @Override
  public Map<String, Object> populate(Map<String, Object> json, Map<String, Object> context) {

    Map<String, Object> map = super.populate(json, context);

    long totalComments =
        Beans.get(MailMessageRepository.class)
            .all()
            .filter("self.relatedModel = :relatedModel AND self.relatedId = :relatedId")
            .bind("relatedModel", DiscussionPost.class.getName())
            .bind("relatedId", json.get("id"))
            .count();
    map.put("$totalComments", totalComments);

    if (json != null && json.get("id") != null) {
      map.put(
          "$unread",
          Beans.get(CommonService.class)
              .isUnreadRecord((Long) json.get("id"), (String) context.get("_model")));
    }

    return map;
  }

  @Override
  public DiscussionPost save(DiscussionPost post) {

    if (post.getVersion().equals(0)) {
      Beans.get(CommonService.class).manageUnreadRecord(post);
    }
    Beans.get(DiscussionPostService.class).addFollowers(post);
    return super.save(post);
  }
}
