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

import com.axelor.apps.project.db.ProjectTask;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.axelor.meta.db.repo.MetaJsonModelRepository;
import com.axelor.text.GroovyTemplates;
import com.axelor.text.Template;
import com.axelor.text.Templates;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MailMessageDailytsRepository extends MailMessageRepository {

  @Override
  public MailMessage save(MailMessage message) {

    message = super.save(message);

    if (message.getRelatedModel().equals(ProjectTask.class.getName())
        && message.getAuthor() != null) {
      message.setMessageContentHtml(getHtmlContent(message));
    }

    return message;
  }

  public String getHtmlContent(MailMessage message) {

    final String text = message.getBody().trim();

    if (text == null
        || !"notification".equals(message.getType())
        || !(text.startsWith("{") || text.startsWith("}"))) {
      return text;
    }

    final MailMessageRepository messages = Beans.get(MailMessageRepository.class);
    final Map<String, Object> details = messages.details(message);
    final String jsonBody = details.containsKey("body") ? (String) details.get("body") : text;

    final ObjectMapper mapper = Beans.get(ObjectMapper.class);
    Map<String, Object> data = new HashMap<>();

    try {
      data = mapper.readValue(jsonBody, new TypeReference<Map<String, Object>>() {});
    } catch (IOException e) {
      TraceBackService.trace(e);
    }

    data.put(
        "entity", Beans.get(MetaJsonModelRepository.class).findByName(message.getRelatedModel()));

    Templates templates = Beans.get(GroovyTemplates.class);
    Template tmpl =
        templates.fromText(
            message.getSubject().contains("Task updated")
                ? ""
                    + "<ul>"
                    + "<% for (def item : tracks) { %>"
                    + "<% if (item.containsKey('displayValue')) { %>"
                    + "<li><strong>${item.title}</strong>: <span>${item.oldDisplayValue}</span> &raquo; <span>${item.displayValue}</span></li>"
                    + "<% } else { %>"
                    + "<li><strong>${item.title}</strong>: <span>${item.oldValue}</span> &raquo; <span>${item.value}</span></li>"
                    + "<% } %>"
                    + "<% } %>"
                    + "</ul>"
                    + (data.containsKey("content") ? data.get("content") : "")
                : ""
                    + "<ul>"
                    + "<% for (def item : tracks) { %>"
                    + "<% if (item.containsKey('displayValue')) { %>"
                    + "<li><strong>${item.title}</strong>: <span>${item.displayValue}</span></li>"
                    + "<% } else { %>"
                    + "<li><strong>${item.title}</strong>: <span>${item.value}</span></li>"
                    + "<% } %>"
                    + "<% } %>"
                    + "</ul>");

    return tmpl.make(data).render();
  }
}
