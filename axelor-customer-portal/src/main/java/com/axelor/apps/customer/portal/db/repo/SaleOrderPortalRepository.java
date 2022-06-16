package com.axelor.apps.customer.portal.db.repo;

import com.axelor.apps.businessproject.db.repo.SaleOrderProjectRepository;
import com.axelor.apps.client.portal.db.PortalQuotation;
import com.axelor.apps.client.portal.db.repo.PortalQuotationRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.inject.Beans;
import java.util.Map;

public class SaleOrderPortalRepository extends SaleOrderProjectRepository {

  @Override
  public Map<String, Object> populate(Map<String, Object> json, Map<String, Object> context) {
    Map<String, Object> map = super.populate(json, context);

    Long id = Long.parseLong(json.get("id").toString());
    SaleOrder saleOrder = find(id);
    PortalQuotation portalQuotation =
        Beans.get(PortalQuotationRepository.class)
            .all()
            .filter("self.saleOrder = :saleOrder")
            .bind("saleOrder", saleOrder)
            .order("-id")
            .fetchOne();
    if (portalQuotation != null) {
      map.put("$quotationTypeSelect", portalQuotation.getTypeSelect());
      map.put("$quotationStatusSelect", portalQuotation.getStatusSelect());
    }

    return map;
  }
}
