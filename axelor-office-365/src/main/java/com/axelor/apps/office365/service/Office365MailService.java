package com.axelor.apps.office365.service;

import com.axelor.apps.base.db.AppOffice365;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.AppOffice365Repository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.crm.db.Event;
import com.axelor.apps.crm.db.repo.EventRepository;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class Office365MailService {

  @Inject private EmailAddressRepository emailAddressRepo;
  @Inject private EmailAccountRepository emailAccountRepo;
  @Inject private MessageRepository messageRepo;
  @Inject private UserRepository userRepo;
  @Inject private PartnerRepository partnerRepository;
  @Inject private EventRepository eventRepo;

  @Transactional
  @SuppressWarnings("unchecked")
  public void createMessage(JSONObject jsonObject) {

    if (jsonObject != null) {
      try {
        String officeMessageId = jsonObject.getOrDefault("id", "").toString();
        Message message = messageRepo.findByOffice365Id(officeMessageId);

        if (message == null) {
          message = new Message();
          message.setOffice365Id(officeMessageId);
        }
        message.setSubject(jsonObject.getOrDefault("subject", "").toString());
        message.setContent(jsonObject.getOrDefault("bodyPreview", "").toString());
        message.setMediaTypeSelect(MessageRepository.MEDIA_TYPE_EMAIL);

        LocalDateTime sentDateTime =
            parseDateTime(jsonObject.getOrDefault("sentDateTime", "").toString());
        LocalDateTime receivedDateTime =
            parseDateTime(jsonObject.getOrDefault("receivedDateTime", "").toString());
        message.setSentDateT(sentDateTime);
        message.setReceivedDateT(receivedDateTime);

        if (jsonObject.getBoolean("isDraft")) {
          message.setStatusSelect(MessageRepository.STATUS_DRAFT);
        } else if (jsonObject.getBoolean("isRead")) {
          message.setTypeSelect(MessageRepository.TYPE_RECEIVED);
        } else {
          message.setStatusSelect(MessageRepository.STATUS_SENT);
          message.setTypeSelect(MessageRepository.TYPE_SENT);
        }

        JSONObject fromJsonObj = (JSONObject) jsonObject.getOrDefault("from", JSONObject.NULL);
        message.setFromEmailAddress(getEmailAddress(fromJsonObj));

        message.setToEmailAddressSet(getEmailAddressSet(jsonObject, "toRecipients"));
        message.setReplyToEmailAddressSet(getEmailAddressSet(jsonObject, "replyTo"));
        message.setCcEmailAddressSet(getEmailAddressSet(jsonObject, "ccRecipients"));
        message.setBccEmailAddressSet(getEmailAddressSet(jsonObject, "bccRecipients"));
        setSender(jsonObject, message);

        messageRepo.save(message);
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }
  }

  private EmailAddress getEmailAddress(JSONObject jsonObject) throws JSONException {

    EmailAddress emailAddress = null;
    if (jsonObject != JSONObject.NULL) {
      JSONObject emailAddJsonObj = jsonObject.getJSONObject("emailAddress");

      String emailAddressStr = emailAddJsonObj.getString("address");
      if (!StringUtils.isBlank(emailAddressStr)) {
        emailAddress = emailAddressRepo.findByAddress(emailAddressStr);
      }

      if (emailAddress == null) {
        emailAddress = new EmailAddress();
        emailAddress.setAddress(emailAddressStr);
      }
      emailAddress.setName(emailAddJsonObj.getString("name"));
    }

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

  private void setSender(JSONObject jsonObject, Message message) throws JSONException {

    JSONObject senderJsonObj = jsonObject.getJSONObject("sender");
    if (senderJsonObj != JSONObject.NULL) {

      JSONObject emailAddressJsonObj = senderJsonObj.getJSONObject("emailAddress");
      String email = emailAddressJsonObj.getString("address");
      String name = emailAddressJsonObj.getString("name");

      User user = userRepo.findByCode(name);
      if (user == null) {
        user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setCode(name);
        user.setPassword(name);

        EmailAddress emailAddress = getEmailAddress(senderJsonObj);
        Partner partner =
            partnerRepository
                .all()
                .filter("self.emailAddress = :emailAddress")
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
  }

  private LocalDateTime parseDateTime(String dateTimeStr) {

    if (!StringUtils.isBlank(dateTimeStr)) {
      DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

      return LocalDateTime.parse(dateTimeStr, format)
          .atZone(ZoneId.systemDefault())
          .toLocalDateTime();
    }

    return null;
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public void createEmailMessage(Map<String, Object> mailObj) {

    if (mailObj != null) {
      try {
        String officeMessageId = mailObj.get("id").toString();
        Message message = messageRepo.findByOffice365Id(officeMessageId);

        if (message == null) {
          message = new Message();
          message.setOffice365Id(officeMessageId);
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
            String relatedTo1Select = mailObj.get("relatedTo").toString();
            Long relatedTo1SelectId = Long.parseLong(mailObj.get("relatedTo1SelectId").toString());
            message.setRelatedTo1Select(relatedTo1Select);
            message.setRelatedTo1SelectId(relatedTo1SelectId);
          }
        }
        scheduleEvent(message);

        messageRepo.save(message);
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }
  }

  @Transactional
  public void scheduleEvent(Message message) {

    if (message.getStatusSelect() == MessageRepository.STATUS_DRAFT) {
      return;
    }

    try {
      Event event;
      String office365Id = message.getOffice365Id();
      event = eventRepo.findByOffice365Id(office365Id);
      if (event == null) {
        event = new Event();
        event.setOffice365Id(office365Id);
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
