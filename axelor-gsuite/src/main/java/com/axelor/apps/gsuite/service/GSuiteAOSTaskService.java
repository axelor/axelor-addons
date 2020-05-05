package com.axelor.apps.gsuite.service;

import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.exception.AxelorException;
import java.time.LocalDateTime;

public interface GSuiteAOSTaskService {

  public void sync(GoogleAccount account) throws AxelorException;

  public void sync(GoogleAccount account, LocalDateTime dueDateTMin, LocalDateTime dueDateTMax)
      throws AxelorException;
}
