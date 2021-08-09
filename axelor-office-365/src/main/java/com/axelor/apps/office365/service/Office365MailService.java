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

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.AppOffice365;
import com.axelor.apps.base.db.ICalendarEvent;
import com.axelor.apps.base.db.repo.AppOffice365Repository;
import com.axelor.apps.base.db.repo.ICalendarEventRepository;
import com.axelor.apps.base.service.message.MessageBaseService;
import com.axelor.apps.crm.db.Event;
import com.axelor.apps.crm.db.repo.EventRepository;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.message.service.MessageService;
import com.axelor.apps.office.db.MailFolder;
import com.axelor.apps.office.db.OfficeAccount;
import com.axelor.apps.office.db.OfficeMail;
import com.axelor.apps.office.db.repo.MailFolderRepository;
import com.axelor.apps.office.db.repo.OfficeMailRepository;
import com.axelor.apps.office365.translation.ITranslation;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.common.io.ByteSource;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class Office365MailService {

  @Inject private EmailAddressRepository emailAddressRepo;
  @Inject private MailFolderRepository mailFolderRepo;
  @Inject private MessageRepository messageRepo;
  @Inject private UserRepository userRepo;
  @Inject private ICalendarEventRepository eventRepo;
  @Inject private DMSFileRepository dmsFileRepo;
  @Inject private OfficeMailRepository officeMailRepo;

  @Inject private Office365Service office365Service;
  @Inject private MetaFiles metaFiles;
  @Inject private MessageService messageService;
  @Inject private MessageBaseService messageBaseService;

  private static final Map<Integer, String> MAIL_FOLDER_MAP =
      new HashMap<Integer, String>() {
        private static final long serialVersionUID = 1L;

        {
          put(MessageRepository.STATUS_IN_PROGRESS, ITranslation.OFFICE_MAIL_FOLDER_SENT_ITEMS);
          put(MessageRepository.STATUS_SENT, ITranslation.OFFICE_MAIL_FOLDER_SENT_ITEMS);
          put(MessageRepository.STATUS_DELETED, ITranslation.OFFICE_MAIL_FOLDER_DELETED_ITEMS);
        }
      };

  @SuppressWarnings("unchecked")
  public void syncMailFolder(OfficeAccount officeAccount, String accessToken, List<String> emails) {

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("includeHiddenFolders", "true");
    JSONArray mailFolderJsonArray =
        office365Service.fetchData(
            Office365Service.MAIL_FOLDER_URL, accessToken, true, queryParams, "mailFolders");
    if (mailFolderJsonArray == null) {
      return;
    }

    for (Object mailFolderObject : mailFolderJsonArray) {
      JSONObject mailFolderJsonObject = (JSONObject) mailFolderObject;
      MailFolder mailFolder = createMailFolder(mailFolderJsonObject, officeAccount);
      if (mailFolder == null || StringUtils.isBlank(mailFolder.getOffice365Id())) {
        continue;
      }

      String mailFolderOfficeId = mailFolder.getOffice365Id();
      office365Service.syncMails(
          officeAccount, accessToken, mailFolderOfficeId, mailFolder, emails);

      int totalChild = (int) mailFolderJsonObject.getOrDefault("childFolderCount", 0);
      if (totalChild > 0) {
        syncChildMailFolder(mailFolderOfficeId, officeAccount, accessToken, queryParams, emails);
      }
    }
  }

  @SuppressWarnings("unchecked")
  public void syncChildMailFolder(
      String parentFolderId,
      OfficeAccount officeAccount,
      String accessToken,
      Map<String, String> queryParams,
      List<String> emails) {

    JSONArray childMailFolderJsonArray =
        office365Service.fetchData(
            String.format(Office365Service.MAIL_CHILD_FOLDER_URL, parentFolderId),
            accessToken,
            true,
            queryParams,
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

      String mailFolderOfficeId = mailFolder.getOffice365Id();
      office365Service.syncMails(
          officeAccount, accessToken, mailFolderOfficeId, mailFolder, emails);

      int totalChild = (int) childMailFolderJsonObject.getOrDefault("childFolderCount", 0);
      if (totalChild > 0) {
        syncChildMailFolder(
            mailFolder.getOffice365Id(), officeAccount, accessToken, queryParams, emails);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private MailFolder createMailFolder(JSONObject jsonObject, OfficeAccount officeAccount) {

    if (jsonObject == null) {
      return null;
    }

    try {
      String name = office365Service.processJsonValue("displayName", jsonObject);
      if (I18n.get(ITranslation.OFFICE_MAIL_FOLDER_DRAFTS).equals(name)) {
        return null;
      }

      String officeMailFolderId = office365Service.processJsonValue("id", jsonObject);
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
  public void createMessage(
      JSONObject jsonObject,
      OfficeAccount officeAccount,
      LocalDateTime lastSyncOn,
      MailFolder mailFolder,
      String accessToken,
      List<String> emails) {

    try {
      if (jsonObject == null
          || jsonObject.getBoolean("isDraft")
          || !isEmailIncluded(jsonObject, emails)) {
        return;
      }

      String officeId = office365Service.processJsonValue("id", jsonObject);
      String messageId = office365Service.processJsonValue("internetMessageId", jsonObject);
      Message message = messageRepo.findByMessageId(messageId);
      if (message == null) {
        message = new Message();
        message.setMessageId(messageId);
        message.addOfficeAccountSetItem(officeAccount);
      } else {
        message.addOfficeAccountSetItem(officeAccount);
        manageOfficeMail(officeId, message, mailFolder);
        if (!office365Service.needUpdation(
            jsonObject, lastSyncOn, message.getCreatedOn(), message.getUpdatedOn())) {
          return;
        }
      }

      manageOfficeMail(officeId, message, mailFolder);
      message.setSubject(office365Service.processJsonValue("subject", jsonObject));
      message.setMediaTypeSelect(MessageRepository.MEDIA_TYPE_EMAIL);
      message.setSentDateT(
          office365Service.processLocalDateTimeValue(
              jsonObject, "sentDateTime", ZoneId.systemDefault()));
      message.setReceivedDateT(
          office365Service.processLocalDateTimeValue(
              jsonObject, "receivedDateTime", ZoneId.systemDefault()));

      JSONObject bodyJsonObj = this.getJSONObject(jsonObject, "body");
      if (bodyJsonObj != null) {
        if (bodyJsonObj.containsKey("content")) {
          message.setContent(office365Service.processJsonValue("content", bodyJsonObj));
        } else {
          message.setContent(office365Service.processJsonValue("bodyPreview", jsonObject));
        }
      }

      if (!jsonObject.getBoolean("isDraft")) {
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

      messageBaseService.manageRelatedTo(message);
      manageAttachments(jsonObject, message, accessToken, officeId, lastSyncOn);
      Office365Service.LOG.debug(
          String.format(
              I18n.get(ITranslation.OFFICE365_OBJECT_SYNC_SUCESS), "mail", message.toString()));
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  private boolean isEmailIncluded(JSONObject jsonObject, List<String> emails) {

    try {
      String email = getEmailAdd(getJSONObject(jsonObject, "from"));
      if (emails.contains(email)) {
        return true;
      }

      if (emails.stream().filter(getEmailAddList(jsonObject, "toRecipients")::contains).count()
          > 0) {
        return true;
      }
      if (emails.stream().filter(getEmailAddList(jsonObject, "ccRecipients")::contains).count()
          > 0) {
        return true;
      }
      if (emails.stream().filter(getEmailAddList(jsonObject, "bccRecipients")::contains).count()
          > 0) {
        return true;
      }
    } catch (Exception e) {
    }
    return false;
  }

  private void manageOfficeMail(String officeId, Message message, MailFolder mailFolder) {

    if (StringUtils.isBlank(officeId) || message == null || mailFolder == null) {
      return;
    }

    OfficeMail officeMail = officeMailRepo.findByOffice365Id(officeId);
    if (officeMail == null) {
      officeMail = new OfficeMail();
      officeMail.setOffice365Id(officeId);
    }
    officeMail.setMailFolder(mailFolder);
    message.addOfficeMailListItem(officeMail);
  }

  public void createOffice365Mail(
      Message message, OfficeAccount officeAccount, String accessToken, List<String> emails) {

    try {
      if (!isEmailIncluded(message, emails)) {
        return;
      }

      String mailFolderOfficeId = null;
      MailFolder mailFolder = null;
      if (MAIL_FOLDER_MAP.containsKey(message.getStatusSelect())) {
        String folderName = I18n.get(MAIL_FOLDER_MAP.get(message.getStatusSelect()));
        mailFolder = mailFolderRepo.findByName(folderName, officeAccount);
        if (mailFolder != null) {
          mailFolderOfficeId = mailFolder.getOffice365Id();
        }
      }

      JSONObject messageJsonObject = setOffice365MailValues(message);
      String url;
      if (StringUtils.isBlank(mailFolderOfficeId)) {
        url = Office365Service.MAIL_URL;
      } else {
        url = String.format(Office365Service.FOLDER_MAILS_URL, mailFolderOfficeId);
        messageJsonObject.put("parentFolderId", mailFolderOfficeId);
      }

      String office365Id =
          office365Service.createOffice365Object(
              url, messageJsonObject, accessToken, null, "messages", "mail");

      manageOfficeMail(office365Id, message, mailFolder);
      message.addOfficeAccountSetItem(officeAccount);
      manageMessageAttachment(message.getId(), office365Id, accessToken);
      messageRepo.save(message);

    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  private boolean isEmailIncluded(Message message, List<String> emails) {

    if (message.getFromEmailAddress() != null
        && emails.contains(message.getFromEmailAddress().getAddress())) {
      return true;
    }

    if (ObjectUtils.notEmpty(message.getToEmailAddressSet())
        && message.getToEmailAddressSet().stream()
                .map(EmailAddress::getAddress)
                .filter(emails::contains)
                .count()
            > 0) {
      return true;
    }

    if (ObjectUtils.notEmpty(message.getToEmailAddressSet())
        && message.getCcEmailAddressSet().stream()
                .map(EmailAddress::getAddress)
                .filter(emails::contains)
                .count()
            > 0) {
      return true;
    }

    if (ObjectUtils.notEmpty(message.getToEmailAddressSet())
        && message.getBccEmailAddressSet().stream()
                .map(EmailAddress::getAddress)
                .filter(emails::contains)
                .count()
            > 0) {
      return true;
    }

    return false;
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

  public void manageMessageAttachment(
      Long relatedId, String messageOffice365Id, String accessToken) {

    if (StringUtils.isBlank(messageOffice365Id)) {
      return;
    }

    try {
      List<DMSFile> attachments =
          dmsFileRepo
              .all()
              .filter(
                  "self.relatedId = :relatedId AND self.relatedModel = :relatedModel "
                      + "AND self.isDirectory = false AND self.office365Id IS NULL AND self.metaFile IS NOT NULL")
              .bind("relatedId", relatedId)
              .bind("relatedModel", Message.class.getName())
              .fetch();

      for (DMSFile dmsFile : attachments) {
        MetaFile metaFile = dmsFile.getMetaFile();
        File file = new File(AppSettings.get().get("file.upload.dir"), metaFile.getFilePath());
        if (file == null || !file.exists()) {
          continue;
        }

        byte[] byteContent = Base64.getEncoder().encode(FileUtils.readFileToByteArray(file));
        JSONObject attachementJsonObject = new JSONObject();
        attachementJsonObject.put("@odata.type", "#microsoft.graph.fileAttachment");
        attachementJsonObject.put("name", dmsFile.getFileName());
        attachementJsonObject.put("contentBytes", new String(byteContent));
        attachementJsonObject.put("contentType", metaFile.getFileType());

        String url = String.format(Office365Service.MAIL_ATTACHMENT_URL, messageOffice365Id);
        String office365Id =
            office365Service.createOffice365Object(
                url,
                attachementJsonObject,
                accessToken,
                dmsFile.getOffice365Id(),
                "mailAttachments",
                "attachment");
        dmsFile.setOffice365Id(office365Id);
        dmsFileRepo.save(dmsFile);
      }
    } catch (Exception e) {
    }
  }

  private EmailAddress getEmailAddress(JSONObject jsonObject) throws JSONException {

    EmailAddress emailAddress = null;
    String emailAddressStr = getEmailAdd(jsonObject);
    if (!StringUtils.isBlank(emailAddressStr)) {
      emailAddress = emailAddressRepo.findByAddress(emailAddressStr);
    }

    if (emailAddress == null) {
      emailAddress = new EmailAddress();
      emailAddress.setAddress(emailAddressStr);
    }
    JSONObject emailAddJsonObj = this.getJSONObject(jsonObject, "emailAddress");
    emailAddress.setName(emailAddJsonObj.getString("name"));

    return emailAddress;
  }

  private String getEmailAdd(JSONObject jsonObject) throws JSONException {

    if (jsonObject == null) {
      return null;
    }

    JSONObject emailAddJsonObj = this.getJSONObject(jsonObject, "emailAddress");
    if (emailAddJsonObj == null) {
      return null;
    }

    return emailAddJsonObj.getString("address");
  }

  private List<String> getEmailAddList(JSONObject jsonObject, String key) throws JSONException {

    List<String> emails = new ArrayList<>();
    JSONArray jsonArr = jsonObject.getJSONArray(key);
    if (jsonArr != null && jsonArr.size() > 0) {
      for (Object obj : jsonArr) {
        JSONObject jsonObj = (JSONObject) obj;
        emails.add(getEmailAdd(jsonObj));
      }
    }

    return emails;
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

    String name = emailAddressJsonObj.getString("name");
    User user = userRepo.findByCode(name);
    message.setSenderUser(user);
  }

  @SuppressWarnings("unchecked")
  private void manageAttachments(
      JSONObject jsonObject,
      Message message,
      String accessToken,
      String messageOffice365Id,
      LocalDateTime lastSyncOn) {

    if (!(boolean) jsonObject.getOrDefault("hasAttachments", false)) {
      return;
    }

    JSONArray mailAttachmentJsonArray =
        office365Service.fetchData(
            String.format(Office365Service.MAIL_ATTACHMENT_URL, messageOffice365Id),
            accessToken,
            true,
            null,
            "mailAttachments");
    if (mailAttachmentJsonArray == null) {
      return;
    }

    for (Object object : mailAttachmentJsonArray) {
      JSONObject attachmentJsonObject = (JSONObject) object;
      try {
        String office365Id = office365Service.processJsonValue("id", attachmentJsonObject);
        DMSFile dmsFile =
            dmsFileRepo
                .all()
                .filter("self.office365Id = :office365Id")
                .bind("office365Id", office365Id)
                .fetchOne();
        if (dmsFile != null) {
          continue;
        }

        String name = office365Service.processJsonValue("name", attachmentJsonObject);
        String contentBytes =
            office365Service.processJsonValue("contentBytes", attachmentJsonObject);
        byte[] bytes = Base64.getDecoder().decode(contentBytes);
        InputStream is = ByteSource.wrap(bytes).openStream();
        dmsFile = metaFiles.attach(is, name, message);
        dmsFile.setOffice365Id(office365Id);
        dmsFileRepo.save(dmsFile);
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }
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

  @Transactional
  @SuppressWarnings("unchecked")
  public void createEmailMessage(Map<String, Object> mailObj) {

    if (mailObj != null) {
      try {
        String messageId = mailObj.get("internetMessageId").toString();
        Message message = messageRepo.findByMessageId(messageId);

        if (message == null) {
          message = new Message();
          message.setMessageId(messageId);
        }

        message.setSubject(mailObj.get("subject").toString());
        message.setContent(mailObj.get("bodyPreview").toString());
        message.setMediaTypeSelect(MessageRepository.MEDIA_TYPE_EMAIL);

        if ((boolean) mailObj.get("isDraft")) {
          message.setStatusSelect(MessageRepository.STATUS_DRAFT);
        } else if ((boolean) mailObj.get("isRead")) {
          message.setTypeSelect(MessageRepository.TYPE_RECEIVED);
        } else {
          message.setStatusSelect(MessageRepository.STATUS_SENT);
          message.setTypeSelect(MessageRepository.TYPE_SENT);
        }

        Map<String, Object> fromMap = (Map<String, Object>) mailObj.get("from");
        message.setFromEmailAddress(getMessageEmailAddress(fromMap));

        message.setToEmailAddressSet(getMessageEmailAddressSet(mailObj, "toRecipients"));
        message.setReplyToEmailAddressSet(getMessageEmailAddressSet(mailObj, "replyTo"));
        message.setCcEmailAddressSet(getMessageEmailAddressSet(mailObj, "ccRecipients"));
        message.setBccEmailAddressSet(getMessageEmailAddressSet(mailObj, "bccRecipients"));

        try {
          LocalDateTime sentDateTime = LocalDateTime.parse(mailObj.get("sentDateTime").toString());
          LocalDateTime receivedDateTime =
              LocalDateTime.parse(mailObj.get("receivedDateTime").toString());
          message.setSentDateT(sentDateTime);
          message.setReceivedDateT(receivedDateTime);
        } catch (Exception e) {
          TraceBackService.trace(e);
        }

        AppOffice365 appOffice365 = Beans.get(AppOffice365Repository.class).all().fetchOne();
        if (appOffice365.getManageMessageRelatedTo() != null
            && appOffice365.getManageMessageRelatedTo()) {
          if (mailObj.containsKey("relatedTo")) {
            String relatedToSelect = mailObj.get("relatedTo").toString();
            Long relatedToSelectId = Long.parseLong(mailObj.get("relatedTo1SelectId").toString());
            messageService.addMessageRelatedTo(message, relatedToSelect, relatedToSelectId);
          }
        }
        scheduleEvent(message, messageId);

        messageRepo.save(message);
        messageBaseService.manageRelatedTo(message);
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }
  }

  @Transactional
  public void scheduleEvent(Message message, String messageId) {

    if (message.getStatusSelect() == MessageRepository.STATUS_DRAFT) {
      return;
    }

    try {
      ICalendarEvent event;
      event = eventRepo.findByOffice365Id(messageId);
      if (event == null) {
        event = new Event();
        event.setOffice365Id(messageId);
        event.setTypeSelect(EventRepository.TYPE_EVENT);
      }

      event.setStartDateTime(message.getSentDateT());
      event.setEndDateTime(message.getSentDateT());
      event.setSubject(message.getSubject());
      event.setDescription(message.getContent());
      eventRepo.save(event);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  private Set<EmailAddress> getMessageEmailAddressSet(Map<String, Object> map, String key)
      throws JSONException {

    Set<EmailAddress> toEmailAddressSet = null;
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> toList = (List<Map<String, Object>>) map.get(key);
    if (CollectionUtils.isEmpty(toList)) {
      return toEmailAddressSet;
    }

    toEmailAddressSet = new HashSet<>();
    for (Object obj : toList) {
      @SuppressWarnings("unchecked")
      Map<String, Object> toMap = (Map<String, Object>) obj;
      toEmailAddressSet.add(getMessageEmailAddress(toMap));
    }

    return toEmailAddressSet;
  }

  private EmailAddress getMessageEmailAddress(Map<String, Object> map) throws JSONException {

    EmailAddress emailAddress = null;
    if (map == null) {
      return emailAddress;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> emailAddMap = (Map<String, Object>) map.get("emailAddress");

    String emailAddressStr = emailAddMap.get("address").toString();
    if (!StringUtils.isBlank(emailAddressStr)) {
      emailAddress = emailAddressRepo.findByAddress(emailAddressStr);
    }

    if (emailAddress == null) {
      emailAddress = new EmailAddress();
      emailAddress.setAddress(emailAddressStr);
    }
    emailAddress.setName(emailAddMap.get("name").toString());

    return emailAddress;
  }
}
