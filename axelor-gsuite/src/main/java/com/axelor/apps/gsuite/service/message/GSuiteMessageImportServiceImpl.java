/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.gsuite.service.message;

import com.axelor.apps.base.db.repo.ModelEmailLinkRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.message.MessageBaseService;
import com.axelor.apps.crm.db.repo.LeadRepository;
import com.axelor.apps.gsuite.db.EmailGoogleAccount;
import com.axelor.apps.gsuite.db.repo.EmailGoogleAccountRepository;
import com.axelor.apps.gsuite.exception.IExceptionMessage;
import com.axelor.apps.gsuite.service.GSuiteService;
import com.axelor.apps.gsuite.service.app.AppGSuiteService;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.MultiRelated;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.message.service.MailAccountService;
import com.axelor.apps.tool.date.DateTool;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
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
import java.time.LocalDate;
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

public class GSuiteMessageImportServiceImpl implements GSuiteMessageImportService {

  @Inject private EmailAccountRepository emailAccountRepo;
  @Inject protected GSuiteService gSuiteService;
  @Inject protected MessageRepository messageRepo;
  @Inject protected EmailAddressRepository mailAddressRepo;
  @Inject protected MailAccountService accountService;
  @Inject protected LeadRepository leadRepo;
  @Inject protected PartnerRepository partnerRepo;
  @Inject protected UserRepository userRepo;
  @Inject protected EmailGoogleAccountRepository emailGoogleRepo;
  @Inject protected AppGSuiteService appGSuiteService;
  @Inject protected MessageBaseService messageBaseService;

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  @Transactional
  public EmailAccount sync(EmailAccount account) throws AxelorException {
    return sync(account, null);
  }

  @Override
  public EmailAccount sync(EmailAccount account, LocalDate fromDate) throws AxelorException {
    try {
      Gmail service = gSuiteService.getGmail(account.getId());
      String query = getFilterQuery(fromDate);
      syncMessages(service, service.users().getProfile("me").getUserId(), query, account);
    } catch (Exception e) {
      throw new AxelorException(
          e,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.GMAIL_SYNC_FAILURE));
    }
    return emailAccountRepo.save(account);
  }

  protected String getFilterQuery(LocalDate fromDate) {
    Set<String> fromAddressSet =
        appGSuiteService.getRelatedEmailAddressSet(ModelEmailLinkRepository.ADDRESS_TYPE_FROM);
    Set<String> toAddressSet =
        appGSuiteService.getRelatedEmailAddressSet(ModelEmailLinkRepository.ADDRESS_TYPE_TO);
    String fromQuery =
        fromAddressSet.stream().map(String::trim).collect(Collectors.joining(" ")).trim();
    String toQuery =
        toAddressSet.stream().map(String::trim).collect(Collectors.joining(" ")).trim();
    String query =
        String.format("(from:{%s} OR to:{%s}) (%s)", fromQuery, toQuery, "in:inbox OR in:sent");

    if (fromDate != null) {
      query = String.format("%s (after:%s)", query, fromDate.format(DATE_FORMAT));
    }

    log.debug(query);
    return query;
  }

  @Override
  @Transactional
  public List<Message> syncMessages(
      Gmail service, String userId, String query, EmailAccount emailAccount)
      throws IOException, MessagingException {

    // TODO fetch all Email at once in raw format
    com.google.api.services.gmail.Gmail.Users.Messages.List list =
        service.users().messages().list(userId).setQ(query);
    ListMessagesResponse response = list.execute();

    List<com.google.api.services.gmail.model.Message> gmailMessages = new ArrayList<>();
    List<Message> messages = new ArrayList<>();
    while (response.getMessages() != null) {
      gmailMessages.addAll(response.getMessages());
      if (response.getNextPageToken() != null) {
        String pageToken = response.getNextPageToken();
        response = list.setPageToken(pageToken).execute();
      } else {
        break;
      }
    }

    log.debug("Number of emails found is {}", gmailMessages.size());

    for (com.google.api.services.gmail.model.Message gmailMessage : gmailMessages) {
      gmailMessage = getMessage(service, userId, gmailMessage.getId(), "raw");
      messages.add(createOrUpdateMessage(gmailMessage, emailAccount));
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
      com.google.api.services.gmail.model.Message gmailMessage, EmailAccount emailAccount)
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
    message.setMailAccount(emailAccount);

    setRelatedUsers(message, emailAccount.getUser());
    messageBaseService.manageRelatedTo(message);
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
      emailGoogleAccount.setEmailAccount(emailAccount);
      emailGoogleRepo.save(emailGoogleAccount);
    }

    return message;
  }

  private void setRelatedUsers(Message message, User user) {
    MultiRelated related = new MultiRelated();
    related.setRelatedToSelect(User.class.getName());
    related.setRelatedToSelectId(user.getId());
    message.addMultiRelatedListItem(related);
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
}
