package com.axelor.apps.gsuite.service.app;

import com.axelor.apps.base.db.AppGsuite;
import java.util.Set;

public interface AppGSuiteService {

  public AppGsuite getAppGSuite();

  public Set<String> getRelatedEmailAddressSet();
}
