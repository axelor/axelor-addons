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
package com.axelor.apps.gsuite.service;

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.crm.db.Lead;
import com.axelor.apps.crm.db.repo.LeadRepository;
import com.axelor.apps.gsuite.db.EmailGoogleAccount;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.MessageRelatedSelect;
import com.axelor.apps.gsuite.db.repo.EmailGoogleAccountRepository;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.exception.IExceptionMessage;
import com.axelor.apps.gsuite.service.app.AppGSuiteService;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.message.service.MailAccountService;
import com.axelor.apps.tool.date.DateTool;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.mail.MailParser;
import com.axelor.meta.MetaFiles;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import javax.activation.DataSource;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteAOSMessageServiceImpl implements GSuiteAOSMessageService {

  @Inject private GoogleAccountRepository googleAccountRepo;
  @Inject protected GSuiteService gSuiteService;
  @Inject protected MessageRepository messageRepo;
  @Inject protected EmailAddressRepository mailAddressRepo;
  @Inject protected MailAccountService accountService;
  @Inject protected LeadRepository leadRepo;
  @Inject protected PartnerRepository partnerRepo;
  @Inject protected UserRepository userRepo;
  @Inject protected EmailGoogleAccountRepository emailGoogleRepo;
  @Inject protected AppGSuiteService appGSuiteService;

  private Gmail service;
  private final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  @Transactional
  public GoogleAccount sync(GoogleAccount account) throws AxelorException {
    try {
      service = gSuiteService.getGmail(account.getId());
      String query = getFilterQuery(account);
      syncMessages(service, service.users().getProfile("me").getUserId(), query, account);
    } catch (IOException | AxelorException | MessagingException e) {
      throw new AxelorException(
          e,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.GMAIL_SYNC_FAILURE));
    } catch (Exception e) {
      throw new AxelorException(
          e,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.GMAIL_SYNC_FAILURE));
    }
    return googleAccountRepo.save(account);
  }

  private String getFilterQuery(GoogleAccount account) {
    Set<String> addressSet = appGSuiteService.getRelatedEmailAddressSet();
    String leadEmailsQuery =
        addressSet
            .stream()
            .map(address -> String.format("%s", address))
            .collect(Collectors.joining(" "))
            .trim();
    String query =
        String.format(
            "(from:{%s} OR to:{%s}) (%s)", leadEmailsQuery, leadEmailsQuery, "in:inbox OR in:sent");

    if (account.getGmailSyncFromGoogleDate() != null) {
      query =
          String.format(
              "%s (after:%s)", query, account.getGmailSyncFromGoogleDate().format(DATE_FORMAT));
    }
    log.debug(query);
    return query;
  }

  @Override
  @Transactional
  public List<Message> syncMessages(
      Gmail service, String userId, String query, GoogleAccount googleAccount)
      throws IOException, MessagingException {

    // TODO fetch all Email at once in raw format
    ListMessagesResponse response = service.users().messages().list(userId).setQ(query).execute();

    List<com.google.api.services.gmail.model.Message> gmailMessages = new ArrayList<>();
    List<Message> messages = new ArrayList<>();
    while (response.getMessages() != null) {
      gmailMessages.addAll(response.getMessages());
      if (response.getNextPageToken() != null) {
        String pageToken = response.getNextPageToken();
        response =
            service.users().messages().list(userId).setQ(query).setPageToken(pageToken).execute();
      } else {
        break;
      }
    }

    log.debug("Number of emails found is {}", gmailMessages.size());

    for (com.google.api.services.gmail.model.Message gmailMessage : gmailMessages) {
      gmailMessage = getMessage(service, userId, gmailMessage.getId(), "raw");
      messages.add(createOrUpdateMessage(gmailMessage, googleAccount));
      log.debug("Message created for Gmail message {}", gmailMessage.getId());
    }

    return messages;
  }

  @Override
  @Transactional
  public com.google.api.services.gmail.model.Message getMessage(
      Gmail service, String userId, String messageId, String format) throws IOException {
    return service.users().messages().get(userId, messageId).setFormat(format).execute();
  }

  protected Message createOrUpdateMessage(
      com.google.api.services.gmail.model.Message gmailMessage, GoogleAccount googleAccount)
      throws MessagingException, IOException {

    Properties props = new Properties();
    Session session = Session.getDefaultInstance(props, null);
    MimeMessage email =
        new MimeMessage(session, new ByteArrayInputStream(gmailMessage.decodeRaw()));
    MailParser parser = new MailParser((MimeMessage) email);
    parser.parse();

    EmailGoogleAccount emailGoogleAccount =
        emailGoogleRepo.findByGoogleMessageId(gmailMessage.getId());
    Message message =
        emailGoogleAccount != null && emailGoogleAccount.getMessage() != null
            ? emailGoogleAccount.getMessage()
            : new Message();

    message.setStatusSelect(MessageRepository.STATUS_SENT);
    message.setMediaTypeSelect(MessageRepository.MEDIA_TYPE_EMAIL);

    int typeSelect = 0;
    List<String> lables = gmailMessage.getLabelIds();
    if (lables.contains("SENT")) {
      typeSelect = MessageRepository.TYPE_SENT;
    } else if (lables.contains("INBOX")) {
      typeSelect = MessageRepository.TYPE_RECEIVED;
    }
    message.setTypeSelect(typeSelect);

    message.setFromEmailAddress(getEmailAddress(parser.getFrom()));
    message.setCcEmailAddressSet(getEmailAddressSet(parser.getCc()));
    message.setBccEmailAddressSet(getEmailAddressSet(parser.getBcc()));
    message.setToEmailAddressSet(getEmailAddressSet(parser.getTo()));
    message.addReplyToEmailAddressSetItem(getEmailAddress(parser.getReplyTo()));
    message.setContent(parser.getHtml());
    message.setSubject(parser.getSubject());
    message.setSentDateT(DateTool.toLocalDateT(email.getSentDate()));
    message.setSenderUser(userRepo.findByEmail(message.getFromEmailAddress().getAddress()));
    setRelatedTo(message);
    setRelatedUsers(message, googleAccount.getUserList());

    message = messageRepo.save(message);

    final MetaFiles files = Beans.get(MetaFiles.class);
    for (DataSource ds : parser.getAttachments()) {
      log.info("attaching file: {}", ds.getName());
      files.attach(ds.getInputStream(), ds.getName(), message);
    }

    if (emailGoogleAccount == null) {
      emailGoogleAccount = new EmailGoogleAccount();
      emailGoogleAccount.setMessage(message);
      emailGoogleAccount.setGoogleEmailMessageId(gmailMessage.getId());
      emailGoogleAccount.setGoogleAccount(googleAccount);
      emailGoogleRepo.save(emailGoogleAccount);
    }

    return message;
  }

  private void setRelatedUsers(Message message, List<User> userList) {

    for (User user : userList) {
      MessageRelatedSelect related = new MessageRelatedSelect();
      related.setRelatedToSelect(User.class.getName());
      related.setRelatedToSelectId(user.getId());
      message.addRelatedListItem(related);
    }
  }

  private void setRelatedTo(Message message) {

    Set<EmailAddress> addresses = new HashSet<>();

    addresses.add(message.getFromEmailAddress());
    addresses.addAll(message.getToEmailAddressSet());
    addresses.addAll(message.getReplyToEmailAddressSet());

    for (EmailAddress emailAddress : addresses) {
      List<Lead> leads = findRelatedByEmailAddress(Lead.class, emailAddress);
      if (ObjectUtils.notEmpty(leads)) {
        for (Lead lead : leads) {
          MessageRelatedSelect related = new MessageRelatedSelect();
          related.setRelatedToSelect(Lead.class.getName());
          related.setRelatedToSelectId(lead.getId());
          message.addRelatedListItem(related);
        }
      }

      List<Partner> partners = findRelatedByEmailAddress(Partner.class, emailAddress);
      if (ObjectUtils.notEmpty(partners)) {
        for (Partner partner : partners) {
          MessageRelatedSelect related = new MessageRelatedSelect();
          related.setRelatedToSelect(Partner.class.getName());
          related.setRelatedToSelectId(partner.getId());
          message.addRelatedListItem(related);
        }
      }
    }
  }

  private EmailAddress getEmailAddress(InternetAddress address) {

    EmailAddress mailAddress = mailAddressRepo.findByAddress(address.getAddress());
    if (mailAddress == null) {
      mailAddress = new EmailAddress();
      mailAddress.setAddress(address.getAddress());
      mailAddressRepo.save(mailAddress);
    }
    return mailAddress;
  }

  private Set<EmailAddress> getEmailAddressSet(List<InternetAddress> addresses) {

    Set<EmailAddress> addressSet = new HashSet<>();

    if (addresses == null) {
      return addressSet;
    }

    for (InternetAddress address : addresses) {
      EmailAddress emailAddress = getEmailAddress(address);
      addressSet.add(emailAddress);
    }
    return addressSet;
  }

  private <T extends Model> List<T> findRelatedByEmailAddress(
      Class<T> modelConcerned, EmailAddress emailAddress) {
    return Query.of(modelConcerned)
        .filter("self.emailAddress !=null AND self.emailAddress.address = :emailAddress")
        .bind("emailAddress", emailAddress.getAddress())
        .fetch();
  }
}
