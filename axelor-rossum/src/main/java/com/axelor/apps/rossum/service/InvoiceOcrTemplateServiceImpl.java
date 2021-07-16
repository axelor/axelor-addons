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

import com.axelor.app.AppSettings;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.AppRossumRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.CurrencyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.SequenceRepository;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.rossum.db.Annotation;
import com.axelor.apps.rossum.db.InvoiceOcrTemplate;
import com.axelor.apps.rossum.db.repo.AnnotationRepository;
import com.axelor.apps.rossum.db.repo.InvoiceOcrTemplateManagementRepository;
import com.axelor.apps.rossum.exception.IExceptionMessage;
import com.axelor.apps.rossum.service.annotation.AnnotationService;
import com.axelor.apps.rossum.service.app.AppRossumService;
import com.axelor.apps.tool.reader.DataReaderFactory;
import com.axelor.common.StringUtils;
import com.axelor.dms.db.DMSFile;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
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
import java.io.PrintWriter;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class InvoiceOcrTemplateServiceImpl implements InvoiceOcrTemplateService {

  protected AppRossumService rossumApiService;
  protected InvoiceOcrTemplateManagementRepository invoiceOcrTemplateRepository;
  protected AppRossumRepository appRossumRepository;
  protected MetaFiles metaFiles;
  protected MetaField metaField;
  protected DataReaderFactory dataReaderFactory;
  protected AnnotationService annotationService;
  protected AnnotationRepository annotationRepo;

  @Inject
  public InvoiceOcrTemplateServiceImpl(
      AppRossumService rossumApiService,
      InvoiceOcrTemplateManagementRepository invoiceOcrTemplateRepository,
      AppRossumRepository appRossumRepository,
      MetaFiles metaFiles,
      MetaField metaField,
      DataReaderFactory dataReaderFactory,
      AnnotationService annotationService,
      AnnotationRepository annotationRepo) {
    this.rossumApiService = rossumApiService;
    this.invoiceOcrTemplateRepository = invoiceOcrTemplateRepository;
    this.appRossumRepository = appRossumRepository;
    this.metaFiles = metaFiles;
    this.metaField = metaField;
    this.dataReaderFactory = dataReaderFactory;
    this.annotationService = annotationService;
    this.annotationRepo = annotationRepo;
  }

  @Override
  public void createTemplate(InvoiceOcrTemplate invoiceOcrTemplate)
      throws AxelorException, IOException, InterruptedException, JSONException {

    String exportTypeSelect = invoiceOcrTemplate.getExportTypeSelect();

    List<MetaFile> metaFileList = new ArrayList<>();
    metaFileList.add(invoiceOcrTemplate.getTemplateFile());

    if (!Strings.isNullOrEmpty(exportTypeSelect)
        && (exportTypeSelect.equals(InvoiceOcrTemplateManagementRepository.EXPORT_TYPE_SELECT_CSV)
            || exportTypeSelect.equals(
                InvoiceOcrTemplateManagementRepository.EXPORT_TYPE_SELECT_XML))) {

      Map<MetaFile, Pair<String, File>> metaFileAnnotationLinkFilePairMap =
          rossumApiService.extractInvoiceDataMetaFile(
              metaFileList,
              invoiceOcrTemplate.getTimeout(),
              invoiceOcrTemplate.getQueue(),
              exportTypeSelect);

      Entry<MetaFile, Pair<String, File>> entry =
          metaFileAnnotationLinkFilePairMap.entrySet().iterator().next();
      File exportedFile = entry.getValue().getRight();
      if (exportedFile != null) {
        this.extractDataFromExportedFile(
            entry.getValue().getLeft(), exportedFile, invoiceOcrTemplate);
      }
    }
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  protected void extractDataFromExportedFile(
      String annotationsLink, File exportedFile, InvoiceOcrTemplate invoiceOcrTemplate)
      throws IOException, JSONException, AxelorException {

    annotationService.createOrUpdateAnnotationFromLink(annotationsLink);

    Reader reader = new FileReader(exportedFile);
    CSVReader csvReader = new CSVReader(reader, ',');

    List<String[]> csvRows = csvReader.readAll();

    if (!csvRows.isEmpty()) {

      String[] headerRow = csvRows.get(0);
      Map<String, Integer> headerTitleMap = new HashMap<>();

      for (Integer i = 0; i < headerRow.length; i++) {
        String key = headerRow[i].toLowerCase();
        if (headerTitleMap.containsKey(key) && key.equals("total amount")) {
          key = "line total amount";
        }
        headerTitleMap.put(key, i);
      }

      String[] dataRow = csvRows.get(1);

      invoiceOcrTemplate.setInvoiceNumber(dataRow[headerTitleMap.get("invoice number")]);
      invoiceOcrTemplate.setOrderNumber(dataRow[headerTitleMap.get("order number")]);
      invoiceOcrTemplate.setIssueDate(
          !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("issue date")])
              ? LocalDate.parse(dataRow[headerTitleMap.get("issue date")])
              : null);
      invoiceOcrTemplate.setDueDate(
          !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("due date")])
              ? LocalDate.parse(dataRow[headerTitleMap.get("due date")])
              : null);
      invoiceOcrTemplate.setTotalAmount(
          !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("total amount")])
              ? new BigDecimal(dataRow[headerTitleMap.get("total amount")])
              : BigDecimal.ZERO);
      invoiceOcrTemplate.setTotalWithoutTax(
          !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("total without tax")])
              ? new BigDecimal(dataRow[headerTitleMap.get("total without tax")])
              : BigDecimal.ZERO);
      invoiceOcrTemplate.setTotalTax(
          !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("total tax")])
              ? new BigDecimal(dataRow[headerTitleMap.get("total tax")])
              : BigDecimal.ZERO);

      Currency currency =
          Beans.get(CurrencyRepository.class)
              .findByCode(dataRow[headerTitleMap.get("currency")].toUpperCase());
      invoiceOcrTemplate.setCurrency(
          currency != null ? currency : Beans.get(CurrencyRepository.class).findByCode("EUR"));

      invoiceOcrTemplate.setSenderName(
          headerTitleMap.containsKey("vendor name")
              ? dataRow[headerTitleMap.get("vendor name")]
              : null);
      invoiceOcrTemplate.setVendorVatNumber(
          headerTitleMap.containsKey("vendor vat number")
              ? dataRow[headerTitleMap.get("vendor vat number")]
              : null);
      invoiceOcrTemplate.setCustomerName(
          headerTitleMap.containsKey("customer name")
              ? dataRow[headerTitleMap.get("customer name")]
              : null);
      invoiceOcrTemplate.setCustomerVatNumber(
          headerTitleMap.containsKey("customer vat number")
              ? dataRow[headerTitleMap.get("customer vat number")]
              : null);
    }
    csvReader.close();

    invoiceOcrTemplate.setIsCorrected(false);
    invoiceOcrTemplate.setIsValidated(!invoiceOcrTemplate.getQueue().getUseConfirmedState());
    invoiceOcrTemplate.setAnnotaionUrl(annotationsLink);
    invoiceOcrTemplate.setExportedFile(metaFiles.upload(exportedFile));
    if (Strings.isNullOrEmpty(invoiceOcrTemplate.getInlineUrl())) {
      DMSFile dmsFile =
          metaFiles.attach(
              invoiceOcrTemplate.getTemplateFile(),
              invoiceOcrTemplate.getTemplateFile().getFileName(),
              invoiceOcrTemplate);
      invoiceOcrTemplate.setInlineUrl(String.format("ws/dms/inline/%d", dmsFile.getId()));
    }
    invoiceOcrTemplateRepository.save(invoiceOcrTemplate);
  }

  @Override
  @Transactional(rollbackOn = {IOException.class})
  public Invoice generateInvoiceFromCSV(InvoiceOcrTemplate invoiceOcrTemplate)
      throws IOException, AxelorException {

    Invoice invoice = new Invoice();
    invoice.setStatusSelect(InvoiceRepository.STATUS_DRAFT);
    invoice.setOperationTypeSelect(invoiceOcrTemplate.getInvoiceOperationTypeSelect());
    invoice.setOperationSubTypeSelect(invoiceOcrTemplate.getInvoiceOperationSubTypeSelect());

    Company company = invoiceOcrTemplate.getCompany();
    Partner partner = invoiceOcrTemplate.getSupplier();
    Currency currency = invoiceOcrTemplate.getCurrency();

    invoice.setCompany(company);
    invoice.setCurrency(currency != null ? currency : company.getCurrency());
    invoice.setPartner(partner);
    invoice.setSupplierInvoiceNb(invoiceOcrTemplate.getInvoiceNumber());

    if (!invoiceOcrTemplate.getIsInvoiceLineConsolidated()
        && invoiceOcrTemplate.getExportedFile() != null
        && invoiceOcrTemplate.getExportedFile().getFileType().equals("text/csv")) {

      File file = MetaFiles.getPath(invoiceOcrTemplate.getExportedFile()).toFile();

      Reader reader = new FileReader(file);
      CSVReader csvReader = new CSVReader(reader, ',');

      List<String[]> csvRows = csvReader.readAll();

      if (!csvRows.isEmpty()) {
        String[] headerRow = csvRows.get(0);
        Map<String, Integer> headerTitleMap = new HashMap<>();

        for (Integer i = 0; i < headerRow.length; i++) {
          String key = headerRow[i].toLowerCase();
          if (headerTitleMap.containsKey(key) && key.equals("total amount")) {
            key = "line total amount";
          }
          headerTitleMap.put(key, i);
        }

        for (Integer i = 2; i < csvRows.size(); i++) {
          String[] dataRow = csvRows.get(i);

          if (headerTitleMap.containsKey("description")
              && !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("description")])) {
            InvoiceLine invoiceLine = new InvoiceLine();

            invoiceLine.setProductName(dataRow[headerTitleMap.get("description")]);

            BigDecimal qty =
                headerTitleMap.containsKey("quantity")
                    ? !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("quantity")])
                        ? new BigDecimal(dataRow[headerTitleMap.get("quantity")])
                        : BigDecimal.ONE
                    : BigDecimal.ONE;

            BigDecimal price =
                headerTitleMap.containsKey("unit price without vat")
                    ? !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("unit price without vat")])
                        ? new BigDecimal(dataRow[headerTitleMap.get("unit price without vat")])
                        : BigDecimal.ZERO
                    : BigDecimal.ZERO;

            BigDecimal exTaxTotal = qty.multiply(price);
            BigDecimal companyExTaxTotal =
                Beans.get(InvoiceLineService.class).getCompanyExTaxTotal(exTaxTotal, invoice);

            invoiceLine.setQty(qty);
            invoiceLine.setPrice(price);
            //            invoiceLine.setInTaxTotal(
            //                !Strings.isNullOrEmpty(dataRow[headerTitleMap.get("line total
            // amount")])
            //                    ? new BigDecimal(dataRow[headerTitleMap.get("line total amount")])
            //                    : BigDecimal.ZERO);
            invoiceLine.setPriceDiscounted(price);
            invoiceLine.setExTaxTotal(exTaxTotal);
            invoiceLine.setCompanyExTaxTotal(companyExTaxTotal);

            // Currently both inTaxTotal and exTaxTotal are same
            invoiceLine.setInTaxTotal(exTaxTotal);
            invoiceLine.setCompanyInTaxTotal(companyExTaxTotal);
            invoice.addInvoiceLineListItem(invoiceLine);
          }
        }
      }
      csvReader.close();
    } else {
      InvoiceLine invoiceLine = new InvoiceLine();

      invoiceLine.setQty(BigDecimal.ONE);

      BigDecimal price = invoiceOcrTemplate.getTotalWithoutTax();
      BigDecimal exTaxTotal = BigDecimal.ONE.multiply(price);
      BigDecimal companyExTaxTotal =
          Beans.get(InvoiceLineService.class).getCompanyExTaxTotal(exTaxTotal, invoice);

      invoiceLine.setPrice(price);
      invoiceLine.setPriceDiscounted(price);
      invoiceLine.setExTaxTotal(exTaxTotal);
      invoiceLine.setCompanyExTaxTotal(companyExTaxTotal);

      // Currently both inTaxTotal and exTaxTotal are same
      invoiceLine.setInTaxTotal(exTaxTotal);
      invoiceLine.setCompanyInTaxTotal(companyExTaxTotal);
      invoice.addInvoiceLineListItem(invoiceLine);
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
      Beans.get(InvoiceService.class).compute(invoice);
      invoice = invoiceRepository.save(invoice);
      metaFiles.attach(
          invoiceOcrTemplate.getExportedFile(),
          invoiceOcrTemplate.getExportedFile().getFileName(),
          invoice);
    }

    invoiceOcrTemplate.setInvoice(invoice);
    invoiceOcrTemplate.setInvoiceNumber(
        String.format("%s - %s", invoice.getInvoiceId(), invoiceOcrTemplate.getInvoiceNumber()));
    invoiceOcrTemplateRepository.save(invoiceOcrTemplate);

    return invoice;
  }

  @Override
  @Transactional
  public InvoiceOcrTemplate setInvoiceOcrTemplateSeq(InvoiceOcrTemplate invoiceOcrTemplate) {

    if (StringUtils.isEmpty(invoiceOcrTemplate.getInvoiceOcrTemplateId())) {
      invoiceOcrTemplate.setInvoiceOcrTemplateId(
          Beans.get(SequenceService.class)
              .getSequenceNumber(SequenceRepository.INVOICE_OCR_TEMPLATE));
    }

    return invoiceOcrTemplate;
  }

  @Override
  @Transactional
  public String getDocumentUrl(InvoiceOcrTemplate invoiceOcrTemplate)
      throws AxelorException, URISyntaxException, IOException, JSONException {
    Annotation annotation = annotationRepo.findByUrl(invoiceOcrTemplate.getAnnotaionUrl());

    if (annotation == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.ANNOTATION_NOT_FOUND),
          invoiceOcrTemplate.getAnnotaionUrl());
    }

    URIBuilder uriBuilder =
        new URIBuilder("https://elis.rossum.ai/document/" + annotation.getAnnotationId());

    String documentUrl = uriBuilder.build().toString();

    if (!Strings.isNullOrEmpty(documentUrl)) {
      invoiceOcrTemplate.setIsCorrected(true);
      invoiceOcrTemplateRepository.save(invoiceOcrTemplate);
    }

    return documentUrl;
  }

  @SuppressWarnings("static-access")
  @Override
  public void fetchUpdatedDetails(InvoiceOcrTemplate invoiceOcrTemplate)
      throws AxelorException, IOException, JSONException {
    Annotation annotation = annotationRepo.findByUrl(invoiceOcrTemplate.getAnnotaionUrl());

    if (annotation == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.ANNOTATION_NOT_FOUND),
          invoiceOcrTemplate.getAnnotaionUrl());
    }

    if (invoiceOcrTemplate.getQueue() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get(IExceptionMessage.QUEUE_NOT_FOUND));
    }

    String url =
        String.format(
            "%s" + "%s" + "%s" + "%s" + "%s",
            invoiceOcrTemplate.getQueue().getQueueUrl(),
            "/export?format=",
            invoiceOcrTemplate.getExportTypeSelect(),
            "&id=",
            annotation.getAnnotationId().toString());

    AppRossum appRossum = rossumApiService.getAppRossum();
    rossumApiService.login(appRossum);

    CloseableHttpClient httpClient = rossumApiService.httpClient;

    HttpGet httpGet = new HttpGet(url);
    httpGet.addHeader("Authorization", "token " + appRossum.getToken());
    httpGet.addHeader(HTTP.CONTENT_TYPE, "text/csv");

    CloseableHttpResponse response = httpClient.execute(httpGet);

    File file = null;

    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK
        && response.getEntity() != null) {
      String content = EntityUtils.toString(response.getEntity());

      String defaultPath = AppSettings.get().getPath("file.upload.dir", "");
      String dirPath = defaultPath + "/rossum";

      File dir = new File(dirPath);
      if (!dir.isDirectory()) dir.mkdir();

      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyyHHmm");
      String fileName =
          "Rossum_export_"
              + annotation.getAnnotationId().toString()
              + "_"
              + LocalDateTime.now().format(formatter)
              + ".csv";

      file = new File(dir.getAbsolutePath(), fileName);
      PrintWriter pw = new PrintWriter(file);

      pw.write(content);
      pw.close();
    }

    if (file != null) {
      this.extractDataFromExportedFile(
          invoiceOcrTemplate.getAnnotaionUrl(), file, invoiceOcrTemplate);
    }
  }

  @SuppressWarnings("static-access")
  @Override
  @Transactional(rollbackOn = {AxelorException.class, IOException.class, JSONException.class})
  public void validateRossumData(InvoiceOcrTemplate invoiceOcrTemplate)
      throws AxelorException, IOException, JSONException {

    AppRossum appRossum = rossumApiService.getAppRossum();
    rossumApiService.login(appRossum);

    CloseableHttpClient httpClient = rossumApiService.httpClient;

    Annotation annotation = annotationRepo.findByUrl(invoiceOcrTemplate.getAnnotaionUrl());

    if (annotation == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.ANNOTATION_NOT_FOUND),
          invoiceOcrTemplate.getAnnotaionUrl());
    }

    HttpPut httpPut = new HttpPut(annotation.getAnnotationUrl());
    httpPut.addHeader("Authorization", "token " + appRossum.getToken());
    httpPut.addHeader(HTTP.CONTENT_TYPE, "application/json");

    JSONObject annotationObject = new JSONObject(annotation.getAnnotationResult());

    JSONObject annotationUpdateObject = new JSONObject();
    annotationUpdateObject.put("document", annotationObject.get("document"));
    annotationUpdateObject.put("queue", annotationObject.get("queue"));
    annotationUpdateObject.put("status", "exported");

    StringEntity stringEntity = new StringEntity(annotationUpdateObject.toString());
    httpPut.setEntity(stringEntity);

    CloseableHttpResponse response = httpClient.execute(httpPut);

    if (response.getEntity() != null) {
      JSONObject obj = new JSONObject(EntityUtils.toString(response.getEntity()));
      String annotaionStatus = obj.getString("status");

      annotation.setStatusSelect(annotaionStatus);
      annotationRepo.save(annotation);

      invoiceOcrTemplate.setIsValidated(true);
      invoiceOcrTemplateRepository.save(invoiceOcrTemplate);
    }
  }

  @Override
  @Transactional
  public void recogniseData(InvoiceOcrTemplate invoiceOcrTemplate) {
    Company company =
        Beans.get(CompanyRepository.class)
            .all()
            .filter(
                "LOWER(self.name) = LOWER(?1) OR self.partner.taxNbr =?2",
                invoiceOcrTemplate.getCustomerName(),
                invoiceOcrTemplate.getCustomerVatNumber())
            .fetchOne();

    Partner partner =
        Beans.get(PartnerRepository.class)
            .all()
            .filter(
                "LOWER(self.name) = LOWER(?1) OR self.registrationCode = ?1 OR self.taxNbr = ?2",
                invoiceOcrTemplate.getSenderName(),
                invoiceOcrTemplate.getVendorVatNumber())
            .fetchOne();

    invoiceOcrTemplate.setCompany(company);
    invoiceOcrTemplate.setSupplier(partner);

    invoiceOcrTemplateRepository.save(invoiceOcrTemplate);
  }
}
