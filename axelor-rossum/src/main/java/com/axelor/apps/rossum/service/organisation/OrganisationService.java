package com.axelor.apps.rossum.service.organisation;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.exception.AxelorException;
import java.io.IOException;
import wslite.json.JSONException;

public interface OrganisationService {

  public void getOrganisations(AppRossum appRossum)
      throws IOException, JSONException, AxelorException;
}
