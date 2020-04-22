package com.axelor.apps.office365.service;

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class Office365MailService {

  @Inject private EmailAddressRepository emailAddressRepo;
  @Inject private EmailAccountRepository emailAccountRepo;
  @Inject private MessageRepository messageRepo;
  @Inject private UserRepository userRepo;
  @Inject private PartnerRepository partnerRepository;

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
}
