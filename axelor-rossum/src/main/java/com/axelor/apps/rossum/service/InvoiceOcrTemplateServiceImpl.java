/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
package com.axelor.apps.rossum.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.AppRossumRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.CurrencyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.readers.DataReaderFactory;
import com.axelor.apps.rossum.db.InvoiceOcrTemplate;
import com.axelor.apps.rossum.db.repo.InvoiceOcrTemplateRepository;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaFile;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.opencsv.CSVReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import wslite.json.JSONException;

public class InvoiceOcrTemplateServiceImpl implements InvoiceOcrTemplateService {

  protected AppRossumService rossumApiService;
  protected InvoiceOcrTemplateRepository invoiceOcrTemplateRepository;
  protected AppRossumRepository appRossumRepository;
  protected MetaFiles metaFiles;
  protected MetaField metaField;
  protected DataReaderFactory dataReaderFactory;

  @Inject
  public InvoiceOcrTemplateServiceImpl(
      AppRossumService rossumApiService,
      InvoiceOcrTemplateRepository invoiceOcrTemplateRepository,
      AppRossumRepository appRossumRepository,
      MetaFiles metaFiles,
      MetaField metaField,
      DataReaderFactory dataReaderFactory) {
    this.rossumApiService = rossumApiService;
    this.invoiceOcrTemplateRepository = invoiceOcrTemplateRepository;
    this.appRossumRepository = appRossumRepository;
    this.metaFiles = metaFiles;
    this.metaField = metaField;
    this.dataReaderFactory = dataReaderFactory;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void createTemplate(InvoiceOcrTemplate invoiceOcrTemplate)
      throws AxelorException, IOException, InterruptedException, JSONException {

    String exportTypeSelect = invoiceOcrTemplate.getExportTypeSelect();

    List<MetaFile> metaFileList = new ArrayList<>();
    metaFileList.add(invoiceOcrTemplate.getTemplateFile());

    if (!Strings.isNullOrEmpty(exportTypeSelect)
        && (exportTypeSelect.equals(InvoiceOcrTemplateRepository.EXPORT_TYPE_SELECT_CSV)
            || exportTypeSelect.equals(InvoiceOcrTemplateRepository.EXPORT_TYPE_SELECT_XML))) {

      Map<MetaFile, File> metaFileFileMap =
          rossumApiService.extractInvoiceDataMetaFile(
              metaFileList,
              invoiceOcrTemplate.getTimeout(),
              invoiceOcrTemplate.getQueue(),
              exportTypeSelect);

      File exportedFile = metaFileFileMap.entrySet().iterator().next().getValue();
      if (exportedFile != null) {
        invoiceOcrTemplate.setExportedFile(metaFiles.upload(exportedFile));
        invoiceOcrTemplateRepository.save(invoiceOcrTemplate);
      }
    }
  }

  @Override
  @Transactional(rollbackOn = {IOException.class})
  public Invoice generateInvoiceFromCSV(InvoiceOcrTemplate invoiceOcrTemplate) throws IOException {

    Invoice invoice = null;
    File file = null;
    if (invoiceOcrTemplate.getExportedFile() != null
        && invoiceOcrTemplate.getExportedFile().getFileType().equals("text/csv")) {

      file = MetaFiles.getPath(invoiceOcrTemplate.getExportedFile()).toFile();

      Reader reader = new FileReader(file);
      CSVReader csvReader = new CSVReader(reader, ',');

      List<String[]> csvRows = csvReader.readAll();

      if (!csvRows.isEmpty()) {

        invoice = new Invoice();
        invoice.setStatusSelect(InvoiceRepository.STATUS_DRAFT);
        invoice.setOperationTypeSelect(invoiceOcrTemplate.getInvoiceOperationTypeSelect());
        invoice.setOperationSubTypeSelect(invoiceOcrTemplate.getInvoiceOperationSubTypeSelect());

        String[] headerRow = csvRows.get(0);
        Map<String, Integer> headerTitleMap = new HashMap<>();

        for (Integer i = 0; i < headerRow.length; i++) {
          String key = headerRow[i].toLowerCase();
          if (headerTitleMap.containsKey(key) && key.equals("total amount")) {
            key = "line total amount";
          }
          headerTitleMap.put(key, i);
        }

        for (Integer i = 1; i < csvRows.size(); i++) {
          String[] dataRow = csvRows.get(i);

          if (i == 1) {
            invoice.setInvoiceId(dataRow[headerTitleMap.get("invoice number")]);
            invoice.setExternalReference(dataRow[headerTitleMap.get("order number")]);

            invoice.setInvoiceDate(
                !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("issue date")])
                    ? LocalDate.parse(dataRow[headerTitleMap.get("issue date")])
                    : null);

            invoice.setDueDate(
                !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("due date")])
                    ? LocalDate.parse(dataRow[headerTitleMap.get("due date")])
                    : null);

            invoice.setExTaxTotal(
                !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("total without tax")])
                    ? new BigDecimal(dataRow[headerTitleMap.get("total without tax")])
                    : BigDecimal.ZERO);

            invoice.setTaxTotal(
                !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("total tax")])
                    ? new BigDecimal(dataRow[headerTitleMap.get("total tax")])
                    : BigDecimal.ZERO);

            invoice.setInTaxTotal(
                !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("total amount")])
                    ? new BigDecimal(dataRow[headerTitleMap.get("total amount")])
                    : BigDecimal.ZERO);

            Currency currency =
                Beans.get(CurrencyRepository.class)
                    .findByCode(dataRow[headerTitleMap.get("currency")].toUpperCase());
            invoice.setCurrency(
                currency != null
                    ? currency
                    : Beans.get(CurrencyRepository.class).findByCode("EUR"));

            PartnerRepository partnerRepository = Beans.get(PartnerRepository.class);
            Partner partner =
                partnerRepository
                    .all()
                    .filter(
                        "self.name = ?1 OR self.registrationCode = ?1 OR self.taxNbr =?2",
                        dataRow[headerTitleMap.get("vendor company id")],
                        dataRow[headerTitleMap.get("vendor vat number")])
                    .fetchOne();

            if (partner == null) {
              partner = partnerRepository.all().fetchOne();
            }

            invoice.setPartner(partner);

            CompanyRepository companyRepository = Beans.get(CompanyRepository.class);
            Company company =
                companyRepository
                    .all()
                    .filter(
                        "self.name = ?1 OR self.partner.taxNbr = ?2",
                        dataRow[headerTitleMap.get("customer company id")],
                        dataRow[headerTitleMap.get("customer vat number")])
                    .fetchOne();

            if (company == null) {
              company =
                  AuthUtils.getUser().getActiveCompany() != null
                      ? AuthUtils.getUser().getActiveCompany()
                      : companyRepository.all().fetchOne();
            }

            invoice.setCompany(company);

            invoice.setNote(dataRow[headerTitleMap.get("notes")]);
          }

          if (!Strings.isNullOrEmpty(dataRow[headerTitleMap.get("description")])) {
            InvoiceLine invoiceLine = new InvoiceLine();

            invoiceLine.setProductName(dataRow[headerTitleMap.get("description")]);
            invoiceLine.setQty(
                !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("quantity")])
                    ? new BigDecimal(dataRow[headerTitleMap.get("quantity")])
                    : BigDecimal.ZERO);
            invoiceLine.setPrice(
                !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("unit price without vat")])
                    ? new BigDecimal(dataRow[headerTitleMap.get("unit price without vat")])
                    : BigDecimal.ZERO);
            invoiceLine.setInTaxTotal(
                !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("line total amount")])
                    ? new BigDecimal(dataRow[headerTitleMap.get("line total amount")])
                    : BigDecimal.ZERO);
            invoice.addInvoiceLineListItem(invoiceLine);
          }
        }
      }
      csvReader.close();
    }

    InvoiceRepository invoiceRepository = Beans.get(InvoiceRepository.class);
    if (invoice != null) {
      if (invoiceRepository
              .all()
              .filter(
                  "self.invoiceId = ?1 AND self.company =?2",
                  invoice.getInvoiceId(),
                  invoice.getCompany())
              .fetchOne()
          != null) {
        invoice.setInvoiceId(null);
      }
      invoiceRepository.save(invoice);
      metaFiles.attach(
          invoiceOcrTemplate.getExportedFile(),
          invoiceOcrTemplate.getExportedFile().getFileName(),
          invoice);
    }

    return invoice;
  }
}
