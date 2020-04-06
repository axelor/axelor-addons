package com.axelor.apps.gsuite.service;

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.PartnerServiceImpl;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.db.JPA;
import com.google.inject.Inject;
import java.util.List;

public class PartnerGSuiteServiceImpl extends PartnerServiceImpl {

  @Inject
  public PartnerGSuiteServiceImpl(PartnerRepository partnerRepo, AppBaseService appBaseService) {
    super(partnerRepo, appBaseService);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<Long> findMailsFromPartner(Partner partner) {
    String query =
        "SELECT DISTINCT(email.id) FROM Message as email JOIN email.relatedList relatedList  WHERE email.mediaTypeSelect = 2 AND "
            + "(email.relatedTo1Select = 'com.axelor.apps.base.db.Partner' AND email.relatedTo1SelectId = "
            + partner.getId()
            + ") "
            + "OR (email.relatedTo2Select = 'com.axelor.apps.base.db.Partner' AND email.relatedTo2SelectId = "
            + partner.getId()
            + ")"
            + "OR (relatedList.relatedToSelect = 'com.axelor.apps.base.db.Partner' AND relatedList.relatedToSelectId = "
            + partner.getId()
            + ")";

    if (partner.getEmailAddress() != null) {
      query += "OR (email.fromEmailAddress.id = " + partner.getEmailAddress().getId() + ")";
    }
    return JPA.em().createQuery(query).getResultList();
  }
}
