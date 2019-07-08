package com.axelor.apps.maintenance.service;

import com.axelor.apps.optaplanner.service.ManufOrderPlanServiceImpl;
import com.axelor.apps.production.db.ManufOrder;
import com.axelor.apps.production.service.operationorder.OperationOrderWorkflowService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManufOrderPlanMaintenanceServiceImpl extends ManufOrderPlanServiceImpl {

  @Inject
  public ManufOrderPlanMaintenanceServiceImpl(
      OperationOrderWorkflowService operationOrderWorkflowService) {
    super(operationOrderWorkflowService);
  }

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void optaPlan(List<ManufOrder> manufOrderListToPlan, boolean quickSolve)
      throws AxelorException {
    manufOrderListToPlan.removeIf(manufOrder -> manufOrder.getType().equals(2));
    super.optaPlan(manufOrderListToPlan, quickSolve);
  }
}
