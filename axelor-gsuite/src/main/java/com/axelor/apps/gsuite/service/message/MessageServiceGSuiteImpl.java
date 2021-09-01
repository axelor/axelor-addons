package com.axelor.apps.gsuite.service.message;

import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.message.MessageServiceBaseImpl;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.message.service.SendMailQueueService;
import com.axelor.exception.AxelorException;
import com.axelor.meta.db.repo.MetaAttachmentRepository;
import com.google.inject.Inject;
import java.io.IOException;
import wslite.json.JSONException;

public class MessageServiceGSuiteImpl extends MessageServiceBaseImpl {

  @Inject
  public MessageServiceGSuiteImpl(
      MetaAttachmentRepository metaAttachmentRepository,
      MessageRepository messageRepository,
      SendMailQueueService sendMailQueueService,
      UserService userService,
      AppBaseService appBaseService) {
    super(
        metaAttachmentRepository,
        messageRepository,
        sendMailQueueService,
        userService,
        appBaseService);
  }

  @Override
  public Message sendMessage(Message message) throws AxelorException, JSONException, IOException {

    // TODO to send message from gsuite

    return super.sendMessage(message);
  }
}
