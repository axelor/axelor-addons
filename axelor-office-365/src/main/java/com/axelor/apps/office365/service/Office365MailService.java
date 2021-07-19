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
package com.axelor.apps.office365.service;

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.office.db.MailFolder;
import com.axelor.apps.office.db.OfficeAccount;
import com.axelor.apps.office.db.repo.MailFolderRepository;
import com.axelor.apps.office365.translation.ITranslation;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class Office365MailService {

  @Inject private EmailAddressRepository emailAddressRepo;
  @Inject private EmailAccountRepository emailAccountRepo;
  @Inject private MailFolderRepository mailFolderRepo;
  @Inject private MessageRepository messageRepo;
  @Inject private UserRepository userRepo;
  @Inject private PartnerRepository partnerRepository;

  @Inject private Office365Service office365Service;

  private static final Map<Integer, String> MAIL_FOLDER_MAP =
      new HashMap<Integer, String>() {
        private static final long serialVersionUID = 1L;

        {
          put(MessageRepository.STATUS_DRAFT, ITranslation.OFFICE_MAIL_FOLDER_DRAFTS);
          put(MessageRepository.STATUS_IN_PROGRESS, ITranslation.OFFICE_MAIL_FOLDER_DRAFTS);
          put(MessageRepository.STATUS_SENT, ITranslation.OFFICE_MAIL_FOLDER_SENT_ITEMS);
          put(MessageRepository.STATUS_DELETED, ITranslation.OFFICE_MAIL_FOLDER_DELETED_ITEMS);
        }
      };

  @SuppressWarnings("unchecked")
  public void syncMailFolder(
      OfficeAccount officeAccount, String accessToken, List<Long> removedMailIdList) {

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("includeHiddenFolders", "true");
    JSONArray mailFolderJsonArray =
        office365Service.fetchData(
            Office365Service.MAIL_FOLDER_URL, accessToken, true, queryParams, "mailFolders");
    if (mailFolderJsonArray == null) {
      return;
    }

    List<Long> mailFolderIdList = new ArrayList<>();
    for (Object mailFolderObject : mailFolderJsonArray) {
      JSONObject mailFolderJsonObject = (JSONObject) mailFolderObject;
      MailFolder mailFolder = createMailFolder(mailFolderJsonObject, officeAccount);
      if (mailFolder == null || StringUtils.isBlank(mailFolder.getOffice365Id())) {
        continue;
      }

      if (!mailFolderIdList.contains(mailFolder.getId())) {
        mailFolderIdList.add(mailFolder.getId());
      }

      String mailFolderOfficeId = mailFolder.getOffice365Id();
      office365Service.syncMails(
          officeAccount, accessToken, mailFolderOfficeId, mailFolder, removedMailIdList);

      int totalChild = (int) mailFolderJsonObject.getOrDefault("childFolderCount", 0);
      if (totalChild > 0) {
        syncChildMailFolder(
            mailFolderOfficeId, officeAccount, accessToken, mailFolderIdList, removedMailIdList);
      }
    }

    removeMailFolder(officeAccount, mailFolderIdList);
  }

  @SuppressWarnings("unchecked")
  public void syncChildMailFolder(
      String parentFolderId,
      OfficeAccount officeAccount,
      String accessToken,
      List<Long> mailFolderIdList,
      List<Long> removedMailIdList) {

    JSONArray childMailFolderJsonArray =
        office365Service.fetchData(
            String.format(Office365Service.MAIL_CHILD_FOLDER_URL, parentFolderId),
            accessToken,
            true,
            null,
            "child mailFolders");
    if (childMailFolderJsonArray == null) {
      return;
    }

    for (Object childMailFolderObject : childMailFolderJsonArray) {
      JSONObject childMailFolderJsonObject = (JSONObject) childMailFolderObject;
      MailFolder mailFolder = createMailFolder(childMailFolderJsonObject, officeAccount);
      if (mailFolder == null || StringUtils.isBlank(mailFolder.getOffice365Id())) {
        continue;
      }

      if (!mailFolderIdList.contains(mailFolder.getId())) {
        mailFolderIdList.add(mailFolder.getId());
      }

      String mailFolderOfficeId = mailFolder.getOffice365Id();
      office365Service.syncMails(
          officeAccount, accessToken, mailFolderOfficeId, mailFolder, removedMailIdList);

      int totalChild = (int) childMailFolderJsonObject.getOrDefault("childFolderCount", 0);
      if (totalChild > 0) {
        syncChildMailFolder(
            mailFolder.getOffice365Id(),
            officeAccount,
            accessToken,
            mailFolderIdList,
            removedMailIdList);
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public MailFolder createMailFolder(JSONObject jsonObject, OfficeAccount officeAccount) {

    if (jsonObject == null) {
      return null;
    }

    try {
      String officeMailFolderId = jsonObject.getOrDefault("id", "").toString();
      MailFolder mailFolder = mailFolderRepo.findByOffice365Id(officeMailFolderId);
      if (mailFolder == null) {
        mailFolder = new MailFolder();
        mailFolder.setOffice365Id(officeMailFolderId);
        mailFolder.setOfficeAccount(officeAccount);
      }

      mailFolder.setName(office365Service.processJsonValue("displayName", jsonObject));
      mailFolder.setParentFolderId(office365Service.processJsonValue("parentFolderId", jsonObject));
      mailFolder.setIsHidden((boolean) jsonObject.getOrDefault("isHidden", false));
      mailFolderRepo.save(mailFolder);

      Office365Service.LOG.debug(
          String.format(
              I18n.get(ITranslation.OFFICE365_OBJECT_SYNC_SUCESS),
              "mailFolder",
              mailFolder.toString()));
      return mailFolder;
    } catch (Exception e) {
      TraceBackService.trace(e);
      return null;
    }
  }

  @Transactional
  public void removeMailFolder(OfficeAccount officeAccount, List<Long> mailFolderIdList) {

    if (ObjectUtils.isEmpty(mailFolderIdList)) {
      return;
    }

    List<MailFolder> mailFolders =
        mailFolderRepo
            .all()
            .filter(
                "self.id NOT IN :ids AND self.office365Id IS NOT NULL AND self.officeAccount = :officeAccount")
            .bind("ids", mailFolderIdList)
            .bind("officeAccount", officeAccount)
            .fetch();
    for (MailFolder mailFolder : mailFolders) {
      try {
        messageRepo
            .all()
            .filter("self.mailFolder =:mailFolder")
            .bind("mailFolder", mailFolder)
            .delete();
        mailFolderRepo.remove(mailFolder);
      } catch (Exception e) {
      }
    }
  }

  @Transactional
  public Message createMessage(
      JSONObject jsonObject,
      OfficeAccount officeAccount,
      LocalDateTime lastSyncOn,
      MailFolder mailFolder) {

    if (jsonObject == null) {
      return null;
    }

    try {
      String officeMessageId = office365Service.processJsonValue("id", jsonObject);
      Message message = messageRepo.findByOffice365Id(officeMessageId);
      if (message == null) {
        message = new Message();
        message.setOffice365Id(officeMessageId);
        message.setOfficeAccount(officeAccount);

      } else if (!office365Service.needUpdation(
          jsonObject, lastSyncOn, message.getCreatedOn(), message.getUpdatedOn())) {
        return message;
      }

      message.setSubject(office365Service.processJsonValue("subject", jsonObject));
      message.setMediaTypeSelect(MessageRepository.MEDIA_TYPE_EMAIL);
      message.setSentDateT(
          office365Service.processLocalDateTimeValue(
              jsonObject, "sentDateTime", ZoneId.systemDefault()));
      message.setReceivedDateT(
          office365Service.processLocalDateTimeValue(
              jsonObject, "receivedDateTime", ZoneId.systemDefault()));
      message.setMailFolder(mailFolder);

      JSONObject bodyJsonObj = this.getJSONObject(jsonObject, "body");
      if (bodyJsonObj != null) {
        if (bodyJsonObj.containsKey("content")) {
          message.setContent(office365Service.processJsonValue("content", bodyJsonObj));
        } else {
          message.setContent(office365Service.processJsonValue("bodyPreview", jsonObject));
        }
      }

      if (jsonObject.getBoolean("isDraft")) {
        message.setStatusSelect(MessageRepository.STATUS_DRAFT);
      } else if (jsonObject.getBoolean("isRead")) {
        message.setTypeSelect(MessageRepository.TYPE_RECEIVED);
      } else {
        message.setStatusSelect(MessageRepository.STATUS_SENT);
        message.setTypeSelect(MessageRepository.TYPE_SENT);
      }

      JSONObject fromJsonObj = this.getJSONObject(jsonObject, "from");
      message.setFromEmailAddress(getEmailAddress(fromJsonObj));

      message.setToEmailAddressSet(getEmailAddressSet(jsonObject, "toRecipients"));
      message.setReplyToEmailAddressSet(getEmailAddressSet(jsonObject, "replyTo"));
      message.setCcEmailAddressSet(getEmailAddressSet(jsonObject, "ccRecipients"));
      message.setBccEmailAddressSet(getEmailAddressSet(jsonObject, "bccRecipients"));
      setSender(jsonObject, message, officeAccount.getOwnerUser());

      messageRepo.save(message);
      Office365Service.LOG.debug(
          String.format(
              I18n.get(ITranslation.OFFICE365_OBJECT_SYNC_SUCESS), "mail", message.toString()));
      return message;
    } catch (Exception e) {
      TraceBackService.trace(e);
      return null;
    }
  }

  @Transactional
  public void createOffice365Mail(
      Message message, OfficeAccount officeAccount, String accessToken) {

    try {
      String mailFolderOfficeId = null;
      if (message.getMailFolder() != null
          && StringUtils.notBlank(message.getMailFolder().getOffice365Id())) {
        mailFolderOfficeId = message.getMailFolder().getOffice365Id();
      }

      String url = null;
      if (StringUtils.isBlank(mailFolderOfficeId)) {
        if (MAIL_FOLDER_MAP.containsKey(message.getStatusSelect())) {
          String folderName = I18n.get(MAIL_FOLDER_MAP.get(message.getStatusSelect()));
          MailFolder mailFolder = mailFolderRepo.findByName(folderName);
          message.setMailFolder(mailFolder);
          mailFolderOfficeId = mailFolder.getOffice365Id();
        }

        url = String.format(Office365Service.FOLDER_MAILS_URL, mailFolderOfficeId);
        if (StringUtils.isBlank(mailFolderOfficeId)) {
          url = Office365Service.MAIL_URL;
        }
      }

      JSONObject messageJsonObject = setOffice365MailValues(message);
      String office365Id =
          office365Service.createOffice365Object(
              url, messageJsonObject, accessToken, message.getOffice365Id(), "messages", "mail");

      message.setOffice365Id(office365Id);
      message.setOfficeAccount(officeAccount);
      messageRepo.save(message);

    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public void removeMessage(
      JSONObject jsonObject, List<Long> removedMailIdList, MailFolder mailFolder) {

    try {
      JSONObject removedJsonObject = (JSONObject) jsonObject.get("@removed");
      String reason = removedJsonObject.getOrDefault("reason", "").toString();
      if (!"deleted".equalsIgnoreCase(reason)) {
        return;
      }

      String office365Id = jsonObject.getOrDefault("id", "").toString();
      if (StringUtils.isBlank(office365Id)) {
        return;
      }

      Message message = messageRepo.findByOffice365Id(office365Id);
      if (message == null) {
        return;
      }

      message.setOffice365Id(null);
      removedMailIdList.add(message.getId());
      messageRepo.remove(message);

    } catch (JSONException e) {
      TraceBackService.trace(e);
    }
  }

  private JSONObject setOffice365MailValues(Message message) throws JSONException {

    JSONObject messageJsonObject = new JSONObject();
    office365Service.putObjValue(messageJsonObject, "subject", message.getSubject());

    JSONObject bodyJsonObject = new JSONObject();
    bodyJsonObject.put("content", message.getContent());
    bodyJsonObject.put("contentType", "html");
    messageJsonObject.put("body", (Object) bodyJsonObject);
    messageJsonObject.put("importance", "Low");

    if (MessageRepository.STATUS_DRAFT == message.getStatusSelect()) {
      messageJsonObject.put("isDraft", true);
    }

    if (message.getSentDateT() != null) {
      LocalDateTime sentDateT =
          LocalDateTime.ofInstant(
              Instant.parse(message.getSentDateT().toString() + "Z"), ZoneId.of("UTC"));
      messageJsonObject.put("sentDateTime", sentDateT.toString() + "Z");
    }

    manageOffice365EmailAddresses(
        messageJsonObject, "bccRecipients", message.getBccEmailAddressSet());
    manageOffice365EmailAddresses(
        messageJsonObject, "ccRecipients", message.getCcEmailAddressSet());
    manageOffice365EmailAddresses(
        messageJsonObject, "replyTo", message.getReplyToEmailAddressSet());
    manageOffice365EmailAddresses(
        messageJsonObject, "toRecipients", message.getToEmailAddressSet());

    office365Service.putUserEmailAddress(message.getSenderUser(), messageJsonObject, "sender");
    JSONObject fromJsonObj = createOffice365EmailAddress(message.getFromEmailAddress());
    if (fromJsonObj != null) {
      messageJsonObject.put("from", (Object) fromJsonObj);
    }

    return messageJsonObject;
  }

  private EmailAddress getEmailAddress(JSONObject jsonObject) throws JSONException {

    if (jsonObject == null) {
      return null;
    }

    JSONObject emailAddJsonObj = this.getJSONObject(jsonObject, "emailAddress");
    if (emailAddJsonObj == null) {
      return null;
    }

    EmailAddress emailAddress = null;
    String emailAddressStr = emailAddJsonObj.getString("address");
    if (!StringUtils.isBlank(emailAddressStr)) {
      emailAddress = emailAddressRepo.findByAddress(emailAddressStr);
    }

    if (emailAddress == null) {
      emailAddress = new EmailAddress();
      emailAddress.setAddress(emailAddressStr);
    }
    emailAddress.setName(emailAddJsonObj.getString("name"));

    return emailAddress;
  }

  private Set<EmailAddress> getEmailAddressSet(JSONObject jsonObject, String key)
      throws JSONException {

    Set<EmailAddress> toEmailAddressSet = null;
    JSONArray toJsonArr = jsonObject.getJSONArray(key);
    if (toJsonArr != null && toJsonArr.size() > 0) {
      toEmailAddressSet = new HashSet<>();
      for (Object obj : toJsonArr) {
        JSONObject toJsonObj = (JSONObject) obj;
        toEmailAddressSet.add(getEmailAddress(toJsonObj));
      }
    }

    return toEmailAddressSet;
  }

  private JSONObject getJSONObject(JSONObject jsonObject, String key) {

    try {
      if (jsonObject.containsKey(key)) {
        return jsonObject.getJSONObject(key);
      }
    } catch (JSONException e) {
    }

    return null;
  }

  private void setSender(JSONObject jsonObject, Message message, User ownerUser)
      throws JSONException {

    JSONObject senderJsonObj = this.getJSONObject(jsonObject, "sender");
    if (senderJsonObj == null) {
      message.setSenderUser(ownerUser);
      return;
    }

    JSONObject emailAddressJsonObj = senderJsonObj.getJSONObject("emailAddress");
    if (emailAddressJsonObj == null) {
      return;
    }

    String email = emailAddressJsonObj.getString("address");
    String name = emailAddressJsonObj.getString("name");

    User user = userRepo.findByCode(name);
    if (user == null) {
      user = office365Service.getUser(name, email);
      EmailAddress emailAddress = getEmailAddress(senderJsonObj);
      Partner partner =
          partnerRepository
              .all()
              .filter(
                  "self.emailAddress = :emailAddress AND self NOT IN (SELECT partner FROM User)")
              .bind("emailAddress", emailAddress)
              .fetchOne();
      user.setPartner(partner);
    }
    message.setSenderUser(user);

    EmailAccount emailAccount = emailAccountRepo.findByName(name);
    if (emailAccount == null) {
      emailAccount = new EmailAccount();
      emailAccount.setServerTypeSelect(EmailAccountRepository.SERVER_TYPE_SMTP);
      emailAccount.setSecuritySelect(EmailAccountRepository.SECURITY_NONE);
      emailAccount.setName(name);
      emailAccount.setLogin(email);
      emailAccount.setHost("Microsoft");
    }
    message.setMailAccount(emailAccount);
  }

  private void manageOffice365EmailAddresses(
      JSONObject jsonObject, String key, Set<EmailAddress> emailAddressSet) throws JSONException {

    if (ObjectUtils.isEmpty(emailAddressSet)) {
      return;
    }

    JSONArray emailJsonArr = new JSONArray();
    for (EmailAddress emailAddress : emailAddressSet) {
      JSONObject emailJsonObject = createOffice365EmailAddress(emailAddress);
      if (emailJsonObject == null) {
        continue;
      }

      emailJsonArr.add(emailJsonObject);
    }
    jsonObject.put(key, (Object) emailJsonArr);
  }

  private JSONObject createOffice365EmailAddress(EmailAddress emailAddress) throws JSONException {

    if (emailAddress == null || StringUtils.isBlank(emailAddress.getAddress())) {
      return null;
    }

    JSONObject emailJsonObj = new JSONObject();
    office365Service.putObjValue(emailJsonObj, "address", emailAddress.getAddress());
    office365Service.putObjValue(emailJsonObj, "name", emailAddress.getName());

    JSONObject emailAddressObj = new JSONObject();
    emailAddressObj.put("emailAddress", (Object) emailJsonObj);

    return emailAddressObj;
  }
}
