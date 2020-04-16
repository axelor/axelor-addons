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
package com.axelor.apps.prestashop.exports.service;

import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.AppPrestashop;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PartnerAddress;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.db.IPrestaShopBatch;
import com.axelor.apps.prestashop.entities.PrestashopAddress;
import com.axelor.apps.prestashop.entities.PrestashopResourceType;
import com.axelor.apps.prestashop.service.library.PSWebServiceClient;
import com.axelor.apps.prestashop.service.library.PrestaShopWebserviceException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExportAddressServiceImpl implements ExportAddressService {
  private Logger log = LoggerFactory.getLogger(getClass());

  protected PartnerRepository partnerRepo;
  protected PartnerService partnerService;

  @Inject
  public ExportAddressServiceImpl(PartnerRepository partnerRepo, PartnerService partnerService) {
    this.partnerRepo = partnerRepo;
    this.partnerService = partnerService;
  }

  @Override
  @Transactional
  public void exportAddress(AppPrestashop appConfig, Writer logBuffer)
      throws IOException, PrestaShopWebserviceException {
    int done = 0;
    int errors = 0;

    logBuffer.write(String.format("%n====== ADDRESSES ======%n"));

    final PSWebServiceClient ws =
        new PSWebServiceClient(appConfig.getPrestaShopUrl(), appConfig.getPrestaShopKey());

    // Customers already imported
    final List<Partner> customers =
        partnerRepo.all().filter("self.prestaShopId IS NOT NULL").fetch();

    // Addresses already in PS
    final List<PrestashopAddress> remoteAddresses = ws.fetchAll(PrestashopResourceType.ADDRESSES);
    final Map<Integer, PrestashopAddress> remoteAddressesById = new HashMap<>();
    for (PrestashopAddress a : remoteAddresses) {
      remoteAddressesById.put(a.getId(), a);
    }

    for (Partner customer : customers) {
      // export customer addresses
      if (customer.getPartnerAddressList() != null) {
        int i = 0;
        for (PartnerAddress partnerAddress : customer.getPartnerAddressList()) {
          Address localAddress = partnerAddress.getAddress();

          logBuffer.write(
              String.format(
                  "Exporting customer #%d, address : %s – ",
                  customer.getId(), localAddress.getFullName()));

          PrestashopAddress remoteAddress = new PrestashopAddress();

          if (localAddress.getPrestaShopId() != null) {
            /* localAddress already exported before */
            logBuffer.write(String.format("prestashop id=%d - ", localAddress.getPrestaShopId()));
            remoteAddress = remoteAddressesById.get(localAddress.getPrestaShopId());

            if (remoteAddress == null) {
              logBuffer.write(String.format("Not found remotely [ERROR]%n"));
              log.error(
                  "Unable to fetch remote address #{} ({}), something is probably very wrong, skipping",
                  localAddress.getPrestaShopId(),
                  localAddress.getFullName());
              ++errors;
              continue;
            }
          } else {
            /* localAddress not yet exported */
            remoteAddress.setAlias(
                I18n.getBundle(new Locale(partnerService.getPartnerLanguageCode(customer)))
                        .getString("Main address")
                    + " #"
                    + i++);
            remoteAddress.setFirstname(
                Strings.isNullOrEmpty(customer.getFirstName()) ? " " : customer.getFirstName());
            remoteAddress.setLastname(
                customer.getName().matches(".*\\d+.*")
                    ? customer.getName().replaceAll("[*0-9]", "")
                    : customer.getName()); // remove digits from name

            try {
              fillRemoteAddress(logBuffer, remoteAddress, customer, localAddress);
            } catch (NullPointerException e) {
              continue;
            }
          }

          try {
            saveRemoteAddress(localAddress, remoteAddress, ws, logBuffer);
            ++done;
          } catch (PrestaShopWebserviceException e) {
            handlePSException(e, logBuffer, localAddress);
            ++errors;
          }
        }
      }

      // export contacts address
      if (customer.getContactPartnerSet() != null) {
        for (Partner partner : customer.getContactPartnerSet()) {
          if (partner.getMainAddress() != null) {
            Address localAddress = partner.getMainAddress();

            logBuffer.write(
                String.format(
                    "Exporting contact #%d, address : %s – ",
                    partner.getId(), localAddress.getFullName()));

            PrestashopAddress remoteAddress = new PrestashopAddress();

            if (localAddress.getPrestaShopId() != null) {
              /* localAddress already exported before */
              logBuffer.write(String.format("prestashop id=%d - ", localAddress.getPrestaShopId()));
              remoteAddress = remoteAddressesById.get(localAddress.getPrestaShopId());

              if (remoteAddress == null) {
                logBuffer.write(String.format("Not found remotely [ERROR]%n"));
                log.error(
                    "Unable to fetch remote address #{} ({}), something is probably very wrong, skipping",
                    localAddress.getPrestaShopId(),
                    localAddress.getFullName());
                ++errors;
                continue;
              }
            } else {
              /* localAddress not yet exported */
              remoteAddress.setAlias(partner.getFullName());
              remoteAddress.setFirstname(partner.getFirstName());
              remoteAddress.setLastname(partner.getName());

              try {
                fillRemoteAddress(logBuffer, remoteAddress, customer, localAddress);
              } catch (NullPointerException e) {
                continue;
              }
            }

            try {
              saveRemoteAddress(localAddress, remoteAddress, ws, logBuffer);
              ++done;
            } catch (PrestaShopWebserviceException e) {
              handlePSException(e, logBuffer, localAddress);
              ++errors;
            }
          }
        }
      }
    }

    logBuffer.write(
        String.format("%n=== END OF ADDRESSES EXPORT, done: %d, errors: %d ===%n", done, errors));
  }

  /**
   * @throws NullPointerException If localAddress's city, country or country's prestashop ID is null
   */
  private void fillRemoteAddress(
      Writer logBuffer, PrestashopAddress remoteAddress, Partner customer, Address localAddress)
      throws IOException, NullPointerException {
    remoteAddress.setCustomerId(customer.getPrestaShopId());
    remoteAddress.setCompany(customer.getName());
    remoteAddress.setAddress1(localAddress.getAddressL4());
    remoteAddress.setAddress2(localAddress.getAddressL5());
    remoteAddress.setPhone(customer.getFixedPhone());

    if (localAddress.getCity() == null) {
      if (StringUtils.isEmpty(localAddress.getAddressL6())) {
        logBuffer.write(
            String.format("No city filled, it is required for Prestashop, skipping [WARNING]%n"));

        throw new NullPointerException();
      } else {
        // Don't try to split city/zipcode since this can cause more issues than it solves
        remoteAddress.setCity(localAddress.getAddressL6());
      }
    } else {
      remoteAddress.setCity(localAddress.getCity().getName());
      remoteAddress.setZipcode(localAddress.getCity().getZip());
    }

    if (localAddress.getAddressL7Country() == null) {
      logBuffer.write(
          String.format("No country filled, it is required for Prestashop, skipping [WARNING]%n"));

      throw new NullPointerException();
    }
    if (localAddress.getAddressL7Country().getPrestaShopId() == null) {
      logBuffer.write(String.format("Bound country has not be synced yet, skipping [WARNING]%n"));

      throw new NullPointerException();
    }
    remoteAddress.setCountryId(localAddress.getAddressL7Country().getPrestaShopId());
  }

  private void saveRemoteAddress(
      Address localAddress,
      PrestashopAddress remoteAddress,
      PSWebServiceClient ws,
      Writer logBuffer)
      throws IOException, PrestaShopWebserviceException {
    if (!IPrestaShopBatch.IMPORT_ORIGIN_PRESTASHOP.equals(localAddress.getImportOrigin())) {
      // Don't know if we should actually synchronize something on update…
      remoteAddress.setUpdateDate(LocalDateTime.now());
      remoteAddress = ws.save(PrestashopResourceType.ADDRESSES, remoteAddress);
      logBuffer.write(String.format("[SUCCESS]%n"));
      localAddress.setPrestaShopId(remoteAddress.getId());
      localAddress.setPrestaShopVersion(localAddress.getVersion() + 1);
    } else {
      logBuffer.write(
          String.format(" - address was imported from PrestaShop, leave it untouched [SUCCESS]%n"));
    }
  }

  private void handlePSException(
      PrestaShopWebserviceException e, Writer logBuffer, Address localAddress) throws IOException {
    TraceBackService.trace(
        e, I18n.get("Prestashop addresses export"), AbstractBatch.getCurrentBatchId());
    logBuffer.write(
        String.format(
            " [ERROR] %s (full trace is in application logs)%n", e.getLocalizedMessage()));
    log.error(
        String.format(
            "Exception while synchronizing address #%d (%s)",
            localAddress.getId(), localAddress.getFullName()),
        e);
  }
}
