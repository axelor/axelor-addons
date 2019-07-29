/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.redmine.imports.service;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.TimeEntryManager;
import com.taskadapter.redmineapi.bean.TimeEntryActivity;
import java.util.List;
import java.util.function.Consumer;
import javax.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportActivityServiceImpl extends ImportService implements ImportActivityService {

  @Inject ProductRepository productRepo;

  Logger LOG = LoggerFactory.getLogger(getClass());

  @Transactional
  @Override
  public void importActivity(
      Batch batch,
      RedmineManager redmineManager,
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError) {
    this.redmineManager = redmineManager;
    this.isReset = batch.getRedmineBatch().getUpdateAlreadyImported();
    this.batch = batch;

    try {
      TimeEntryManager tm = redmineManager.getTimeEntryManager();
      List<TimeEntryActivity> activityList = tm.getTimeEntryActivities();

      if (activityList != null && !activityList.isEmpty()) {
        for (TimeEntryActivity redmineActivity : activityList) {
          Product product = getProduct(redmineActivity);
          try {
            if (product != null) {
              productRepo.save(product);
              onSuccess.accept(product);
              success++;
            }
          } catch (PersistenceException e) {
            JPA.em().getTransaction().rollback();
            JPA.em().getTransaction().begin();
            onError.accept(e);
            fail++;
          } catch (Exception e) {
            onError.accept(e);
            fail++;
            TraceBackService.trace(e, "", batch.getId());
          }
        }
      }
    } catch (RedmineException e) {
      onError.accept(e);
      fail++;
      TraceBackService.trace(e, "", batch.getId());
    }
    String resultStr =
        String.format(
            "Redmine Activity -> ABS Product (Service) : Success: %d Fail: %d", success, fail);
    result += String.format("%s \n", resultStr);
    LOG.debug(resultStr);
    success = fail = 0;
  }

  protected Product getProduct(TimeEntryActivity redmineActivity) {
    Product existProduct =
        productRepo
            .all()
            .filter(
                "self.code = ? AND (self.redmineId IS NULL OR self.redmineId = 0)",
                redmineActivity.getName().toUpperCase())
            .fetchOne();
    if (existProduct != null) {
      existProduct.setRedmineId(redmineActivity.getId());
      return existProduct;
    }

    Product product = productRepo.findByRedmineId(redmineActivity.getId());
    if (product == null) {
      product = new Product();
      product.setRedmineId(redmineActivity.getId());
    }

    product.setName(redmineActivity.getName());
    product.setCode(redmineActivity.getName().toUpperCase());
    product.setIsActivity(true);
    product.setProductTypeSelect(ProductRepository.PRODUCT_TYPE_SERVICE);

    return product;
  }
}
