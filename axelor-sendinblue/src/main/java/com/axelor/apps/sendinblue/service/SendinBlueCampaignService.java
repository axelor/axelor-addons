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

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.crm.db.Lead;
import com.axelor.apps.crm.db.repo.LeadRepository;
import com.axelor.apps.marketing.db.Campaign;
import com.axelor.apps.marketing.db.SendinBlueCampaign;
import com.axelor.apps.marketing.db.repo.CampaignRepository;
import com.axelor.apps.marketing.db.repo.SendinBlueCampaignRepository;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.Template;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.apps.message.db.repo.TemplateRepository;
import com.axelor.apps.sendinblue.db.ExportSendinBlue;
import com.axelor.apps.sendinblue.db.ImportSendinBlue;
import com.axelor.apps.sendinblue.db.repo.SendinBlueCampaignStatRepository;
import com.axelor.apps.sendinblue.translation.ITranslation;
import com.axelor.apps.tool.service.TranslationService;
import com.axelor.auth.AuthService;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.gson.internal.LinkedTreeMap;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import sendinblue.ApiException;
import sendinblue.ApiResponse;
import sibApi.ContactsApi;
import sibApi.EmailCampaignsApi;
import sibApi.SendersApi;
import sibModel.AddContactToList;
import sibModel.CreateEmailCampaign;
import sibModel.CreateEmailCampaignRecipients;
import sibModel.CreateEmailCampaignSender;
import sibModel.CreateModel;
import sibModel.CreateSender;
import sibModel.GetContacts;
import sibModel.GetEmailCampaign;
import sibModel.GetEmailCampaigns;
import sibModel.GetSendersList;
import sibModel.UpdateEmailCampaign;
import sibModel.UpdateEmailCampaignRecipients;
import sibModel.UpdateEmailCampaignSender;

public class SendinBlueCampaignService {

  @Inject SendinBlueCampaignRepository sendinBlueCampaignRepo;
  @Inject EmailAccountRepository emailAccountRepo;
  @Inject TemplateRepository templateRepo;
  @Inject PartnerRepository partnerRepo;
  @Inject LeadRepository leadRepo;

  @Inject SendinBlueContactService sendinBlueContactService;
  @Inject SendinBlueTemplateService sendinBlueTemplateService;
  @Inject TranslationService translationService;
  @Inject UserService userService;

  protected static final Integer DATA_FETCH_LIMIT = 100;

  protected String userLanguage;
  protected ArrayList<Long> partnerRecipients, leadRecipients;
  private long totalExportRecord, totalImportRecord;

  public void exportCampaign(
      ExportSendinBlue exportSendinBlue, LocalDateTime lastExportDateTime, StringBuilder logWriter)
      throws AxelorException {
    totalExportRecord = 0;
    List<String> senderEmails = getSenders();
    userLanguage = userService.getUser().getLanguage();
    Query<Campaign> campaignQuery = Beans.get(CampaignRepository.class).all();
    if (exportSendinBlue.getIsExportLatest() && lastExportDateTime != null) {
      campaignQuery =
          campaignQuery.filter(
              "self.emailing = true AND (self.createdOn > ?1 OR self.updatedOn > ?1)",
              lastExportDateTime);
    } else if (exportSendinBlue.getIsNoUpdate() && lastExportDateTime != null) {
      campaignQuery =
          campaignQuery.filter("self.emailing = true AND self.createdOn > ?1", lastExportDateTime);
    }

    if (campaignQuery != null) {
      int totalCampaign = (int) campaignQuery.count();
      List<Campaign> campaigns;
      int offset = 0;
      while (totalCampaign > 0) {
        campaigns = campaignQuery.fetch(DATA_FETCH_LIMIT, offset);
        if (campaigns != null) {
          totalCampaign = campaigns.size();
          if (!campaigns.isEmpty()) {
            offset += totalCampaign;
            for (Campaign dataObject : campaigns) {
              exportCampaignDataObject(dataObject, senderEmails);
            }
          }
        }
      }
      logWriter.append(String.format("%nTotal Campaign Exported : %s%n", totalExportRecord));
    }
  }

  @Transactional
  public void exportCampaignDataObject(Campaign dataObject, List<String> senderEmails) {
    if (dataObject != null) {
      SendinBlueCampaign sendinBlueCampaign;
      List<SendinBlueCampaign> sendinBlueCampaigns = dataObject.getSendinBlueCampaignList();

      partnerRecipients = getRecipients(dataObject, SendinBlueCampaignRepository.PARTNER_CAMPAIGN);
      leadRecipients = getRecipients(dataObject, SendinBlueCampaignRepository.LEAD_CAMPAIGN);

      for (int i = 1; i <= 4; i++) {
        sendinBlueCampaign = exportCampaign(sendinBlueCampaigns, dataObject, i, senderEmails);
        if (sendinBlueCampaign != null && !sendinBlueCampaigns.contains(sendinBlueCampaign)) {
          sendinBlueCampaign.setCampaign(dataObject);
          sendinBlueCampaigns.add(sendinBlueCampaign);
        }
      }
      dataObject.setSendinBlueCampaignList(sendinBlueCampaigns);
      Beans.get(CampaignRepository.class).save(dataObject);
    }
  }

  public List<String> getSenders() {
    SendersApi senderApiInstance = new SendersApi();
    List<String> senderEmails = new ArrayList<>();
    try {
      GetSendersList senderList = senderApiInstance.getSenders(null, null);
      senderEmails =
          senderList
              .getSenders()
              .stream()
              .map(sender -> sender.getEmail())
              .collect(Collectors.toList());
    } catch (ApiException e) {
    }
    return senderEmails;
  }

  public void createSender(String email) {
    SendersApi senderApiInstance = new SendersApi();
    CreateSender sender = new CreateSender();
    sender.setEmail(email);
    sender.setName(email);
    try {
      senderApiInstance.createSender(sender);
    } catch (ApiException e) {
    }
  }

  private SendinBlueCampaign exportCampaign(
      List<SendinBlueCampaign> sendinBlueCampaigns,
      Campaign dataObject,
      Integer campaignType,
      List<String> senderEmails) {
    EmailCampaignsApi emailCampaignApiInstance = new EmailCampaignsApi();
    Optional<SendinBlueCampaign> partnerCampaign =
        sendinBlueCampaigns
            .stream()
            .filter(campaign -> campaign.getCampaignType() == campaignType)
            .findFirst();
    if (partnerCampaign.isPresent()) {
      SendinBlueCampaign sendinBlueCampaign = partnerCampaign.get();
      if (Optional.ofNullable(sendinBlueCampaign.getSendinBlueId()).orElse(0L) != 0) {
        ApiResponse<GetEmailCampaign> result = null;
        try {
          result =
              emailCampaignApiInstance.getEmailCampaignWithHttpInfo(
                  sendinBlueCampaign.getSendinBlueId());
        } catch (Exception e) {
        }
        if (result != null) {
          updateCampaign(sendinBlueCampaign, dataObject, senderEmails, campaignType);
          return sendinBlueCampaign;
        }
      }
    }
    return createCampaign(dataObject, campaignType, senderEmails);
  }

  private SendinBlueCampaign createCampaign(
      Campaign dataObject, Integer campaignType, List<String> senderEmails) {
    CreateEmailCampaignSender sender = null;
    EmailAccount emailAccount = dataObject.getEmailAccount();
    if (emailAccount != null) {
      if (!senderEmails.stream().anyMatch(email -> email.equals(emailAccount.getLogin()))) {
        createSender(emailAccount.getLogin());
      }
      sender = new CreateEmailCampaignSender();
      sender.setEmail(emailAccount.getLogin());
      sender.setName(emailAccount.getName());
    }

    if (sender == null) {
      TraceBackService.trace(
          new AxelorException(
              TraceBackRepository.CATEGORY_NO_VALUE, I18n.get(ITranslation.CAMPAIGN_SENDER_ERROR)));
      return null;
    }

    String content = getContent(dataObject, campaignType);
    if (content == null) {
      TraceBackService.trace(
          new AxelorException(
              TraceBackRepository.CATEGORY_NO_VALUE,
              I18n.get(ITranslation.CAMPAIGN_TEMPLATE_ERROR)));
      return null;
    }

    CreateEmailCampaignRecipients recipients = null;
    ArrayList<Long> Ids = getRecipient(campaignType);
    if (Ids != null) {
      recipients = new CreateEmailCampaignRecipients();
      recipients.setListIds(Ids);
    }
    if (recipients == null) {
      TraceBackService.trace(
          new AxelorException(
              TraceBackRepository.CATEGORY_NO_VALUE,
              I18n.get(ITranslation.CAMPAIGN_RECIPIENT_ERROR)));
      return null;
    }
    return createSendinBlueCampaign(dataObject, sender, recipients, content, campaignType);
  }

  private SendinBlueCampaign createSendinBlueCampaign(
      Campaign dataObject,
      CreateEmailCampaignSender sender,
      CreateEmailCampaignRecipients recipients,
      String content,
      Integer campaignType) {
    EmailCampaignsApi emailCampaignApiInstance = new EmailCampaignsApi();
    CreateEmailCampaign emailCampaign = new CreateEmailCampaign();
    emailCampaign.setSubject(
        translationService.getValueTranslation(dataObject.getName(), userLanguage));
    emailCampaign.setSender(sender);
    emailCampaign.setName(
        translationService.getValueTranslation(dataObject.getName(), userLanguage)
            + "_"
            + campaignType);
    emailCampaign.setRecipients(recipients);
    emailCampaign.setHtmlContent(content);

    try {
      CreateModel result = emailCampaignApiInstance.createEmailCampaign(emailCampaign);
      totalExportRecord++;
      SendinBlueCampaign sendinBlueCampaign = new SendinBlueCampaign();
      sendinBlueCampaign.setSendinBlueId(result.getId());
      sendinBlueCampaign.setCampaignType(campaignType);
      return sendinBlueCampaign;
    } catch (ApiException e) {
      TraceBackService.trace(e);
    }
    return null;
  }

  private String getContent(Campaign dataObject, Integer campaignType) {
    Template template = null;
    switch (campaignType) {
      case SendinBlueCampaignRepository.PARTNER_CAMPAIGN:
        template = dataObject.getPartnerTemplate();
        break;
      case SendinBlueCampaignRepository.PARTNER_REMINDER_CAMPAIGN:
        template = dataObject.getPartnerReminderTemplate();
        break;
      case SendinBlueCampaignRepository.LEAD_CAMPAIGN:
        template = dataObject.getLeadTemplate();
        break;
      case SendinBlueCampaignRepository.LEAD_REMINDER_CAMPAIGN:
        template = dataObject.getLeadReminderTemplate();
        break;
    }

    if (template != null) {
      return sendinBlueTemplateService.getSendinBlueContent(template.getContent());
    }
    return null;
  }

  private ArrayList<Long> getRecipient(Integer campaignType) {
    switch (campaignType) {
      case SendinBlueCampaignRepository.PARTNER_CAMPAIGN:
      case SendinBlueCampaignRepository.PARTNER_REMINDER_CAMPAIGN:
        return partnerRecipients;
      case SendinBlueCampaignRepository.LEAD_CAMPAIGN:
      case SendinBlueCampaignRepository.LEAD_REMINDER_CAMPAIGN:
        return leadRecipients;
    }
    return null;
  }

  private ArrayList<Long> getRecipients(Campaign dataObject, Integer campaignType) {
    String listName = "";
    List<String> emails = null;
    switch (campaignType) {
      case SendinBlueCampaignRepository.PARTNER_CAMPAIGN:
        emails = getPartnerEmails(dataObject.getPartnerSet());
        listName = "#Partner";
        break;
      case SendinBlueCampaignRepository.LEAD_CAMPAIGN:
        emails = getLeadEmails(dataObject.getLeadSet());
        listName = "#Lead";
        break;
    }

    if (emails != null && !emails.isEmpty()) {
      ContactsApi contactApi = new ContactsApi();
      Long folderId = sendinBlueContactService.createFolder(contactApi);
      Long listId =
          sendinBlueContactService.createList(dataObject.getId() + listName, contactApi, folderId);

      AddContactToList contactEmails = new AddContactToList();
      contactEmails.setEmails(emails);
      try {
        contactApi.addContactToList(listId, contactEmails);
      } catch (ApiException e) {
      }
      return new ArrayList<Long>(Arrays.asList(listId));
    }

    return null;
  }

  private List<String> getPartnerEmails(Set<Partner> partnerSet) {
    List<String> emails = null;
    if (partnerSet != null && !partnerSet.isEmpty()) {
      emails = new ArrayList<>();
      for (Partner partner : partnerSet) {
        EmailAddress emailAddress = partner.getEmailAddress();
        if (emailAddress != null && !StringUtils.isBlank(emailAddress.getAddress())) {
          emails.add(emailAddress.getAddress());
        }
      }
      return emails;
    }
    return null;
  }

  private List<String> getLeadEmails(Set<Lead> leadSet) {
    List<String> emails = null;
    if (leadSet != null && !leadSet.isEmpty()) {
      emails = new ArrayList<>();
      for (Lead lead : leadSet) {
        EmailAddress emailAddress = lead.getEmailAddress();
        if (emailAddress != null && !StringUtils.isBlank(emailAddress.getAddress())) {
          emails.add(emailAddress.getAddress());
        }
      }
      return emails;
    }
    return null;
  }

  private void updateCampaign(
      SendinBlueCampaign sendinBlueCampaign,
      Campaign dataObject,
      List<String> senderEmails,
      Integer campaignType) {
    UpdateEmailCampaignSender sender = null;
    EmailAccount emailAccount = dataObject.getEmailAccount();
    if (emailAccount != null) {
      if (!senderEmails.stream().anyMatch(email -> email.equals(emailAccount.getLogin()))) {
        createSender(emailAccount.getLogin());
      }
      sender = new UpdateEmailCampaignSender();
      sender.setEmail(emailAccount.getLogin());
      sender.setName(emailAccount.getName());
    }
    if (sender == null) {
      TraceBackService.trace(
          new AxelorException(
              TraceBackRepository.CATEGORY_NO_VALUE, I18n.get(ITranslation.CAMPAIGN_SENDER_ERROR)));
      return;
    }

    String content = getContent(dataObject, campaignType);
    if (content == null) {
      TraceBackService.trace(
          new AxelorException(
              TraceBackRepository.CATEGORY_NO_VALUE,
              I18n.get(ITranslation.CAMPAIGN_TEMPLATE_ERROR)));
      return;
    }

    UpdateEmailCampaignRecipients recipients = null;
    ArrayList<Long> ids = getRecipient(campaignType);
    if (ids != null) {
      recipients = new UpdateEmailCampaignRecipients();
      recipients.setListIds(ids);
    }
    if (recipients == null) {
      TraceBackService.trace(
          new AxelorException(
              TraceBackRepository.CATEGORY_NO_VALUE,
              I18n.get(ITranslation.CAMPAIGN_RECIPIENT_ERROR)));
      return;
    }
    updateSendInBlueCampaign(
        content,
        sender,
        recipients,
        dataObject,
        campaignType,
        sendinBlueCampaign.getSendinBlueId());
  }

  private void updateSendInBlueCampaign(
      String content,
      UpdateEmailCampaignSender sender,
      UpdateEmailCampaignRecipients recipients,
      Campaign dataObject,
      Integer campaignType,
      Long sendinBlueId) {
    EmailCampaignsApi emailCampaignApiInstance = new EmailCampaignsApi();
    UpdateEmailCampaign updateEmailCampaign = new UpdateEmailCampaign();

    updateEmailCampaign.setHtmlContent(content);
    updateEmailCampaign.setSender(sender);
    updateEmailCampaign.setRecipients(recipients);
    updateEmailCampaign.setSubject(
        translationService.getValueTranslation(dataObject.getName(), userLanguage));
    updateEmailCampaign.setName(
        translationService.getValueTranslation(dataObject.getName(), userLanguage)
            + "_"
            + campaignType);

    try {
      emailCampaignApiInstance.updateEmailCampaign(sendinBlueId, updateEmailCampaign);
      totalExportRecord++;
    } catch (ApiException e) {
      TraceBackService.trace(e);
    }
  }

  @Transactional
  public void importCampaign(
      ImportSendinBlue importSendinBlue, LocalDateTime lastImportDateTime, StringBuilder logWriter)
      throws AxelorException {
    totalImportRecord = 0;
    EmailCampaignsApi apiInstance = new EmailCampaignsApi();
    try {
      long offset = 0L;
      int total = 0;
      do {
        GetEmailCampaigns result =
            apiInstance.getEmailCampaigns(null, null, null, null, (long) DATA_FETCH_LIMIT, offset);
        if (result != null && result.getCampaigns() != null) {
          total = result.getCampaigns().size();
          offset += total;
          for (Object campaign : result.getCampaigns()) {
            createCampaign(campaign, importSendinBlue, lastImportDateTime);
          }
        } else {
          total = 0;
        }
      } while (total > 0);
    } catch (ApiException e) {
      TraceBackService.trace(e);
    }
    logWriter.append(String.format("%nTotal Campaigns Imported : %s", totalImportRecord));
  }

  private void createCampaign(
      Object campaign, ImportSendinBlue importSendinBlue, LocalDateTime lastImportDateTime) {
    @SuppressWarnings("unchecked")
    LinkedTreeMap<String, Object> campaignObj = (LinkedTreeMap<String, Object>) campaign;

    String dateTimeStr = campaignObj.get("createdAt").toString();
    LocalDateTime createdAt =
        LocalDateTime.parse(dateTimeStr.substring(0, dateTimeStr.indexOf("+")));
    LocalDateTime modifiedAt = null;
    if (campaignObj.get("modifiedAt") != null) {
      dateTimeStr = campaignObj.get("modifiedAt").toString();
      modifiedAt = LocalDateTime.parse(dateTimeStr.substring(0, dateTimeStr.indexOf("+")));
    }

    if ((!importSendinBlue.getIsImportLatest() && !importSendinBlue.getIsNoUpdate())
        || (importSendinBlue.getIsImportLatest()
            && (lastImportDateTime != null
                    && createdAt != null
                    && lastImportDateTime.isBefore(createdAt)
                || (lastImportDateTime != null
                    && modifiedAt != null
                    && lastImportDateTime.isBefore(modifiedAt))))
        || (importSendinBlue.getIsNoUpdate()
            && lastImportDateTime != null
            && createdAt != null
            && lastImportDateTime.isBefore(createdAt))) {
      Long id = ((Double) campaignObj.get("id")).longValue();
      SendinBlueCampaign sendinBlueCampaign = sendinBlueCampaignRepo.findBySendinBlueId(id);
      Campaign marketingCampaign = null;
      if (sendinBlueCampaign != null) {
        marketingCampaign = sendinBlueCampaign.getCampaign();
      } else {
        marketingCampaign = new Campaign();
        sendinBlueCampaign = new SendinBlueCampaign();
        sendinBlueCampaign.setSendinBlueId(id);
        sendinBlueCampaign.setCampaign(marketingCampaign);
      }
      marketingCampaign.setName(campaignObj.get("name").toString());
      marketingCampaign.setEmailing(true);
      marketingCampaign.setIsAllowEditingOfTargets(true);
      marketingCampaign.setEmailAccount(getEmailAccount(campaignObj));
      marketingCampaign.setPartnerTemplate(getTemplate(campaignObj));
      setReciepient(campaignObj, marketingCampaign);
      sendinBlueCampaignRepo.save(sendinBlueCampaign);
      totalImportRecord++;
    }
  }

  private EmailAccount getEmailAccount(LinkedTreeMap<String, Object> campaignObj) {
    @SuppressWarnings("unchecked")
    LinkedTreeMap<String, Object> senderObj =
        (LinkedTreeMap<String, Object>) campaignObj.get("sender");
    String senderName = senderObj.get("name").toString();
    String senderEmail = senderObj.get("email").toString();
    EmailAccount emailAccount =
        emailAccountRepo.all().filter("self.login = ?", senderEmail).fetchOne();
    if (emailAccount == null) {
      emailAccount = new EmailAccount();
      emailAccount.setLogin(senderEmail);
    }
    emailAccount.setName(senderName);
    emailAccount.setHost("smtp.gmail.com");
    emailAccount.setServerTypeSelect(EmailAccountRepository.SERVER_TYPE_SMTP);

    createSender(senderName, senderEmail);

    return emailAccount;
  }

  @Transactional
  public void createSender(String senderName, String senderEmail) {
    User user = Beans.get(UserRepository.class).findByCode(senderName.trim().toLowerCase());
    if (user == null) {
      user = new User();
      user.setCode(senderName.trim().toLowerCase());
    }
    user.setName(senderName);
    user.setEmail(senderEmail);
    user.setPassword(Beans.get(AuthService.class).encrypt(senderName));
    Beans.get(UserRepository.class).save(user);
  }

  private Template getTemplate(LinkedTreeMap<String, Object> campaignObj) {
    String content = campaignObj.get("htmlContent").toString();
    Template messageTemplate = new Template();
    content = sendinBlueTemplateService.getABSContent(messageTemplate, content);
    Template existingTemplate = templateRepo.all().filter("self.content = ?", content).fetchOne();
    if (existingTemplate == null) {
      String subject = campaignObj.get("subject").toString();
      messageTemplate.setName(subject);
      messageTemplate.setSubject(subject);
      messageTemplate.setContent(content);
      messageTemplate.setMediaTypeSelect(TemplateRepository.MEDIA_TYPE_EMAILING);
      return messageTemplate;
    }
    return existingTemplate;
  }

  private void setReciepient(
      LinkedTreeMap<String, Object> campaignObj, Campaign marketingCampaign) {
    @SuppressWarnings("unchecked")
    LinkedTreeMap<String, Object> recipients =
        (LinkedTreeMap<String, Object>) campaignObj.get("recipients");
    @SuppressWarnings("unchecked")
    List<Double> lists = (List<Double>) recipients.get("lists");
    long recipientsId;
    Partner partner;
    Lead lead;
    Set<Partner> partnerSet = new HashSet<>();
    Set<Lead> leadSet = new HashSet<>();
    ContactsApi apiInstance = new ContactsApi();
    for (Double listId : lists) {
      recipientsId = (new Double(listId)).longValue();
      try {
        long offset = 0L;
        int total = 0;
        do {
          GetContacts result =
              apiInstance.getContactsFromList(recipientsId, null, (long) DATA_FETCH_LIMIT, offset);
          if (result != null && !result.getContacts().isEmpty()) {
            total = result.getContacts().size();
            offset += total;
            for (Object contact : result.getContacts()) {
              @SuppressWarnings("unchecked")
              LinkedTreeMap<String, Object> contactObj = (LinkedTreeMap<String, Object>) contact;
              Double contactId = (double) contactObj.get("id");
              partner = partnerRepo.findBySendinBlueId(contactId.longValue());
              if (partner != null) {
                partnerSet.add(partner);
              }
              lead = leadRepo.findBySendinBlueId(contactId.longValue());
              if (lead != null) {
                leadSet.add(lead);
              }
            }
          } else {
            total = 0;
          }
        } while (total > 0);
      } catch (ApiException e) {
        TraceBackService.trace(e);
      }
    }
    marketingCampaign.setPartnerSet(partnerSet);
    marketingCampaign.setLeadSet(leadSet);
  }

  @Transactional
  public void deleteSendinBlueCampignStatistics(Campaign campaign) {
    Beans.get(SendinBlueCampaignStatRepository.class)
        .all()
        .filter("self.sendinBlueCampaign.campaign = ?", campaign.getId())
        .remove();
  }
}
