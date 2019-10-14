/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.sendinblue.service;

import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.message.db.Template;
import com.axelor.apps.message.db.repo.TemplateRepository;
import com.axelor.apps.sendinblue.db.ExportSendinBlue;
import com.axelor.apps.sendinblue.db.ImportSendinBlue;
import com.axelor.apps.sendinblue.translation.ITranslation;
import com.axelor.apps.tool.service.TranslationService;
import com.axelor.common.StringUtils;
import com.axelor.db.Query;
import com.axelor.db.annotations.NameColumn;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.reflect.FieldUtils;
import sendinblue.ApiException;
import sibApi.SmtpApi;
import sibModel.CreateModel;
import sibModel.CreateSmtpTemplate;
import sibModel.CreateSmtpTemplateSender;
import sibModel.GetSmtpTemplateOverview;
import sibModel.GetSmtpTemplateOverviewSender;
import sibModel.GetSmtpTemplates;
import sibModel.UpdateSmtpTemplate;
import sibModel.UpdateSmtpTemplateSender;

public class SendinBlueTemplateService {

  @Inject TemplateRepository templateRepo;

  @Inject TranslationService translationService;
  @Inject UserService userService;
  @Inject SendinBlueCampaignService sendinBlueCampaignService;

  protected static final Integer DATA_FETCH_LIMIT = 100;

  protected String userLanguage;

  private long totalExportRecord, totalImportRecord;

  public void exportTemplate(
      ExportSendinBlue exportSendinBlue, LocalDateTime lastExportDateTime, StringBuilder logWriter)
      throws AxelorException {
    totalExportRecord = 0;
    userLanguage = userService.getUser().getLanguage();
    List<String> senderEmails = sendinBlueCampaignService.getSenders();
    Query<Template> templateQuery = Beans.get(TemplateRepository.class).all();
    if (exportSendinBlue.getIsExportLatest() && lastExportDateTime != null) {
      templateQuery =
          templateQuery.filter(
              "self.mediaTypeSelect IN (4) AND self.metaModel.id IN (SELECT id from MetaModel WHERE name IN ('Partner','Lead')) AND (self.createdOn > ?1 OR self.updatedOn > ?1)",
              lastExportDateTime);
    } else if (exportSendinBlue.getIsNoUpdate() && lastExportDateTime != null) {
      templateQuery =
          templateQuery.filter(
              "self.mediaTypeSelect IN (4) AND self.metaModel.id IN (SELECT id from MetaModel WHERE name IN ('Partner','Lead')) AND self.createdOn > ?1",
              lastExportDateTime);
    }
    if (templateQuery != null) {
      int totalTemplate = (int) templateQuery.count();
      List<Template> templates;
      int offset = 0;
      while (totalTemplate > 0) {
        templates = templateQuery.fetch(DATA_FETCH_LIMIT, offset);
        if (templates != null) {
          totalTemplate = templates.size();
          if (!templates.isEmpty()) {
            offset += totalTemplate;
            for (Template dataObject : templates) {
              if (StringUtils.isBlank(dataObject.getFromAdress())) {
                TraceBackService.trace(
                    new AxelorException(
                        TraceBackRepository.CATEGORY_NO_VALUE,
                        I18n.get(ITranslation.TEMPLATE_SENDER_ERROR)));
                continue;
              } else if (!senderEmails
                  .stream()
                  .anyMatch(email -> email.equals(dataObject.getFromAdress()))) {
                sendinBlueCampaignService.createSender(dataObject.getFromAdress());
              }
              exportTemplateDataObject(dataObject);
            }
          }
        }
      }
      logWriter.append(String.format("%nTotal Template Exported : %s", totalExportRecord));
    }
  }

  private void exportTemplateDataObject(Template dataObject) {
    SmtpApi smtpApiInstance = new SmtpApi();
    if (dataObject != null) {
      if (Optional.ofNullable(dataObject.getSendinBlueId()).orElse(0L) != 0) {
        GetSmtpTemplateOverview result = null;
        try {
          result = smtpApiInstance.getSmtpTemplate(dataObject.getSendinBlueId());
        } catch (ApiException e) {
        }
        if (result != null) {
          updateTemplate(dataObject);
        } else {
          createTemplate(dataObject);
        }
      } else {
        createTemplate(dataObject);
      }
    }
  }

  @Transactional
  public void createTemplate(Template dataObject) {
    SmtpApi smtpApiInstance = new SmtpApi();
    CreateSmtpTemplateSender sender = new CreateSmtpTemplateSender();
    sender = sender.name(dataObject.getFromAdress());
    sender = sender.email(dataObject.getFromAdress());

    CreateSmtpTemplate smtpTemplate = new CreateSmtpTemplate();

    smtpTemplate.setTemplateName(
        translationService.getValueTranslation(dataObject.getName(), userLanguage));
    smtpTemplate.setSubject(
        translationService.getValueTranslation(dataObject.getSubject(), userLanguage));
    smtpTemplate.setToField(getSendinBlueContent(dataObject.getToRecipients()));
    smtpTemplate.setReplyTo(dataObject.getReplyToRecipients());
    smtpTemplate = smtpTemplate.sender(sender);
    smtpTemplate.setIsActive(true);

    String content = getSendinBlueContent(dataObject.getContent());
    smtpTemplate.setHtmlContent(content);

    try {
      CreateModel result = smtpApiInstance.createSmtpTemplate(smtpTemplate);
      totalExportRecord++;
      dataObject.setSendinBlueId(result.getId());
      Beans.get(TemplateRepository.class).save(dataObject);
    } catch (ApiException e) {
      TraceBackService.trace(e);
    } catch (Exception e) {
    }
  }

  private void updateTemplate(Template dataObject) {
    SmtpApi smtpApiInstance = new SmtpApi();
    UpdateSmtpTemplateSender sender = new UpdateSmtpTemplateSender();
    sender.setEmail(dataObject.getFromAdress());
    sender.setName(dataObject.getFromAdress());

    UpdateSmtpTemplate smtpTemplate = new UpdateSmtpTemplate();
    smtpTemplate.setTemplateName(
        translationService.getValueTranslation(dataObject.getName(), userLanguage));
    smtpTemplate.setSubject(
        translationService.getValueTranslation(dataObject.getSubject(), userLanguage));
    smtpTemplate.setToField(getSendinBlueContent(dataObject.getToRecipients()));
    smtpTemplate.setReplyTo(dataObject.getReplyToRecipients());
    smtpTemplate.setSender(sender);
    smtpTemplate.setIsActive(true);

    String content = getSendinBlueContent(dataObject.getContent());
    smtpTemplate.setHtmlContent(content);

    try {
      smtpApiInstance.updateSmtpTemplate(dataObject.getSendinBlueId(), smtpTemplate);
      totalExportRecord++;
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  public String getSendinBlueContent(String content) {
    if (!StringUtils.isBlank(content)) {
      content = translationService.getValueTranslation(content, userLanguage);
      String patternStr = "(\\$)(Partner|Lead)(.+?)(\\$)";
      Pattern pat = Pattern.compile(patternStr);
      Matcher matcher = pat.matcher(content);
      while (matcher.find()) {
        String field = matcher.group(3);
        String[] fields = field.substring(1).split("\\.");
        if (fields != null && fields.length > 1) {
          String mainField = fields[0];
          MetaField metaField =
              Beans.get(MetaFieldRepository.class)
                  .all()
                  .filter("self.metaModel.name = ? AND self.name = ?", matcher.group(2), mainField)
                  .fetchOne();
          if (metaField != null) {
            try {
              Class<?> klass =
                  Class.forName(metaField.getPackageName() + "." + metaField.getTypeName());
              List<Field> nameColumnFields =
                  FieldUtils.getFieldsListWithAnnotation(klass, NameColumn.class);
              if (nameColumnFields != null && !nameColumnFields.isEmpty()) {
                if (nameColumnFields.get(0).getName().equals(fields[1])) {
                  field = "." + mainField + "Name";
                }
              }
            } catch (ClassNotFoundException e) {
            }
          }
        }

        String value = matcher.group(1) + matcher.group(2) + matcher.group(3) + matcher.group(4);
        String variable = "{{ Contact" + field.toUpperCase() + " }}";
        content = content.replace(value, variable);
      }
    }
    return content;
  }

  @Transactional
  public void importTemplate(
      ImportSendinBlue importSendinBlue, LocalDateTime lastImportDateTime, StringBuilder logWriter)
      throws AxelorException {
    totalImportRecord = 0;
    SmtpApi apiInstance = new SmtpApi();
    try {
      long offset = 0L;
      int total = 0;
      do {
        GetSmtpTemplates result =
            apiInstance.getSmtpTemplates(null, (long) DATA_FETCH_LIMIT, offset);
        if (result != null && result.getTemplates() != null) {
          total = result.getTemplates().size();
          offset += total;
          for (GetSmtpTemplateOverview template : result.getTemplates()) {
            String dateTimeStr = template.getCreatedAt().toString();
            LocalDateTime createdAt =
                LocalDateTime.parse(dateTimeStr.substring(0, dateTimeStr.indexOf("+")));
            LocalDateTime modifiedAt = null;
            if (template.getModifiedAt() != null) {
              dateTimeStr = template.getModifiedAt().toString();
              modifiedAt = LocalDateTime.parse(dateTimeStr.substring(0, dateTimeStr.indexOf("+")));
            }

            if ((!importSendinBlue.getIsImportLatest() && !importSendinBlue.getIsNoUpdate())
                || (importSendinBlue.getIsImportLatest()
                    && (createdAt != null && lastImportDateTime.isBefore(createdAt)
                        || (modifiedAt != null && lastImportDateTime.isBefore(modifiedAt))))
                || (importSendinBlue.getIsNoUpdate()
                    && createdAt != null
                    && lastImportDateTime.isBefore(createdAt))) {
              createTemplate(template);
              totalImportRecord++;
            }
          }
        } else {
          total = 0;
        }
      } while (total > 0);
    } catch (ApiException e) {
      TraceBackService.trace(e);
    }
    logWriter.append(String.format("%nTotal Templates Imported : %s", totalImportRecord));
  }

  private void createTemplate(GetSmtpTemplateOverview template) {
    Template messageTemplate = templateRepo.findBySendinBlueId(template.getId());
    if (messageTemplate == null) {
      messageTemplate = new Template();
      messageTemplate.setSendinBlueId(template.getId());
    }
    messageTemplate.setName(template.getName());
    messageTemplate.setSubject(template.getSubject());
    messageTemplate.setMediaTypeSelect(TemplateRepository.MEDIA_TYPE_EMAILING);
    messageTemplate.setReplyToRecipients(template.getReplyTo());
    GetSmtpTemplateOverviewSender sender = template.getSender();
    messageTemplate.setFromAdress(sender.getEmail());
    messageTemplate.setToRecipients(template.getToField());
    String content = getABSContent(messageTemplate, template.getHtmlContent());
    messageTemplate.setContent(content);
    templateRepo.save(messageTemplate);
  }

  public String getABSContent(Template messageTemplate, String content) {
    MetaModel metaModel = null;
    if (!StringUtils.isBlank(content)) {
      Map<String, MetaField> partnerFieldMap = new HashMap<>();
      Map<String, MetaField> leadFieldMap = new HashMap<>();
      String patternStr = "(\\{\\{ Contact\\.)(.+?)( \\}\\})";
      Pattern pat = Pattern.compile(patternStr);
      Matcher matcher = pat.matcher(content);
      while (matcher.find()) {
        String field = matcher.group(2);
        if (!StringUtils.isBlank(field)) {
          List<MetaField> metaFields =
              Beans.get(MetaFieldRepository.class)
                  .all()
                  .filter(
                      "(self.metaModel.name = 'Partner' OR self.metaModel.name = 'Lead') AND LOWER(self.name) = ?",
                      field.toLowerCase())
                  .fetch();
          if (metaFields != null) {
            for (MetaField metaField : metaFields) {
              if (metaField.getMetaModel().getName().equals("Partner")) {
                partnerFieldMap.put(field, metaField);
              } else if (metaField.getMetaModel().getName().equals("Lead")) {
                leadFieldMap.put(field, metaField);
              }
            }
          }
        }
      }

      if (!leadFieldMap.isEmpty() && partnerFieldMap.size() < leadFieldMap.size()) {
        for (String key : leadFieldMap.keySet()) {
          String oldValue = "{{ Contact." + key + " }}";
          String newValue = "$Lead." + leadFieldMap.get(key).getName() + "$";
          content.replace(oldValue, newValue);
        }
        Optional<Entry<String, MetaField>> entry = leadFieldMap.entrySet().stream().findFirst();
        if (entry.isPresent()) {
          metaModel = entry.get().getValue().getMetaModel();
          messageTemplate.setMetaModel(metaModel);
          content = content.replaceAll(patternStr, "\\$Lead.$2\\$");
        }
      } else {
        for (String key : partnerFieldMap.keySet()) {
          String oldValue = "{{ Contact." + key + " }}";
          String newValue = "$Partner." + partnerFieldMap.get(key).getName() + "$";
          content = content.replace(oldValue, newValue);
        }
        Optional<Entry<String, MetaField>> entry = partnerFieldMap.entrySet().stream().findFirst();
        if (entry.isPresent()) {
          metaModel = entry.get().getValue().getMetaModel();
          messageTemplate.setMetaModel(metaModel);
          content = content.replaceAll(patternStr, "\\$Partner.$2\\$");
        }
      }
    }
    return content;
  }
}
