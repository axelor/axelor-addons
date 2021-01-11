/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
package com.axelor.apps.dailyts.db.repo;

import com.axelor.common.StringUtils;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.mail.MailConstants;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.axelor.meta.MetaStore;
import com.axelor.meta.schema.views.Selection;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;

public class MailMessageDailytsRepository extends MailMessageRepository {

  @Override
  public MailMessage save(MailMessage message) {

    message = super.save(message);

    if (message.getRelatedModel().equals(TeamTask.class.getName()) && message.getAuthor() != null) {

      /* Check this condition to avoid create html content of unwanted mail message created during redmine import */
      TeamTask teamTask = Beans.get(TeamTaskRepository.class).find(message.getRelatedId());
      if (teamTask != null
          && teamTask.getRedmineId() != 0
          && message.getSubject().equals("Record created")) {
        return message;
      }

      message.setMessageContentHtml(getHtmlContent(message));
    }

    return message;
  }

  @SuppressWarnings("unchecked")
  public String getHtmlContent(MailMessage message) {

    String text = message.getBody().trim();

    if (text == null
        || !MailConstants.MESSAGE_TYPE_NOTIFICATION.equals(message.getType())
        || !(text.startsWith("{") || text.startsWith("}"))) {
      return text;
    }

    Map<String, Object> data = new HashMap<>();

    try {
      data = getBodyData(message);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    String htmlStr = "";
    List<Map<String, String>> tracks = (List<Map<String, String>>) data.get("tracks");

    if (CollectionUtils.isNotEmpty(tracks)) {
      htmlStr = "<ul>";

      if (message.getSubject().contains("Task updated")) {
        htmlStr = generateHtmlForTaskUpdated(htmlStr, tracks);
      } else {
        htmlStr = generateHtmlForTaskCreated(htmlStr, tracks);
      }

      htmlStr = htmlStr + "</ul>" + (data.containsKey("content") ? data.get("content") : "");
    }

    return htmlStr;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Map<String, Object> getBodyData(MailMessage message) throws Exception {

    String body = message.getBody();
    Mapper mapper = Mapper.of(Class.forName(message.getRelatedModel()));
    ObjectMapper json =
        Beans.get(ObjectMapper.class)
            .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
            .configure(
                JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature(), true);
    Map<String, Object> bodyData = json.readValue(body, Map.class);
    List<Map<String, String>> values = new ArrayList<>();

    for (Map<String, String> item : (List<Map>) bodyData.get("tracks")) {
      values.add(item);
      Property property = mapper.getProperty(item.get("name"));

      if (property == null || StringUtils.isBlank(property.getSelection())) {
        continue;
      }

      Selection.Option d1 = MetaStore.getSelectionItem(property.getSelection(), item.get("value"));
      Selection.Option d2 =
          MetaStore.getSelectionItem(property.getSelection(), item.get("oldValue"));
      item.put("displayValue", d1 == null ? null : d1.getLocalizedTitle());
      item.put("oldDisplayValue", d2 == null ? null : d2.getLocalizedTitle());
    }

    bodyData.put("tracks", values);

    return bodyData;
  }

  public String generateHtmlForTaskUpdated(String htmlStr, List<Map<String, String>> tracks) {

    for (Map<String, String> track : tracks) {

      if (track.containsKey("displayValue")) {
        htmlStr =
            htmlStr
                + "<li><strong>"
                + track.get("title")
                + "</strong>: <span>"
                + track.get("oldDisplayValue")
                + "</span> &raquo; <span>"
                + track.get("displayValue")
                + "</span></li>";
      } else {
        htmlStr =
            htmlStr
                + "<li><strong>"
                + track.get("title")
                + "</strong>: <span>"
                + track.get("oldValue")
                + "</span> &raquo; <span>"
                + track.get("value")
                + "</span></li>";
      }
    }

    return htmlStr;
  }

  public String generateHtmlForTaskCreated(String htmlStr, List<Map<String, String>> tracks) {

    for (Map<String, String> track : tracks) {

      if (track.containsKey("displayValue")) {
        htmlStr =
            htmlStr
                + "<li><strong>"
                + track.get("title")
                + "</strong>: <span>"
                + track.get("displayValue")
                + "</span></li>";
      } else {
        htmlStr =
            htmlStr
                + "<li><strong>"
                + track.get("title")
                + "</strong>: <span>"
                + track.get("value")
                + "</span></li>";
      }
    }

    return htmlStr;
  }
}
