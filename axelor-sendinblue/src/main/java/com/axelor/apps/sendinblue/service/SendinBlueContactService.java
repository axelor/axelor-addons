/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.sendinblue.service;

import com.axelor.apps.base.db.AppSendinblue;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.crm.db.Lead;
import com.axelor.apps.crm.db.repo.LeadRepository;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.sendinblue.db.ExportSendinBlue;
import com.axelor.apps.sendinblue.db.ImportSendinBlue;
import com.axelor.apps.sendinblue.db.repo.SendinBlueContactStatRepository;
import com.axelor.apps.tool.service.TranslationService;
import com.axelor.auth.db.AuditableModel;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.db.annotations.NameColumn;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.gson.internal.LinkedTreeMap;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.threeten.bp.OffsetDateTime;
import sendinblue.ApiException;
import sibApi.ContactsApi;
import sibModel.CreateContact;
import sibModel.CreateList;
import sibModel.CreateModel;
import sibModel.CreateUpdateFolder;
import sibModel.GetContacts;
import sibModel.GetExtendedContactDetails;
import sibModel.GetFolderLists;
import sibModel.GetFolders;
import sibModel.UpdateContact;

public class SendinBlueContactService {

  @Inject EmailAddressRepository emailAddressRepo;
  @Inject PartnerRepository partnerRepo;

  @Inject TranslationService translationService;
  @Inject UserService userService;
  @Inject SendinBlueFieldService sendinBlueFieldService;
  @Inject SendinBlueContactStatRepository sendinBlueContactStatRepository;

  protected static final Integer DATA_FETCH_LIMIT = 100;
  protected static final List<String> metaModels =
      new ArrayList<String>(Arrays.asList("Partner", "Lead"));

  @SuppressWarnings("rawtypes")
  protected static final List<Class> SIMPLE_CLASSES =
      new ArrayList<>(Arrays.asList(String.class, Integer.class, Long.class, BigDecimal.class));

  @SuppressWarnings("rawtypes")
  protected static final List<Class> CALENDAR_CLASSES =
      new ArrayList<>(
          Arrays.asList(
              LocalDate.class, LocalTime.class, LocalDateTime.class, ZonedDateTime.class));

  protected String userLanguage;
  protected ArrayList<Long> partnerRecipients, leadRecipients;
  private long totalExportRecord, totalImportRecord, totalContactRecords, totalLeadRecords;

  public void exportContact(
      AppSendinblue appSendinblue,
      ExportSendinBlue exportSendinBlue,
      LocalDateTime lastExportDateTime,
      StringBuilder logWriter)
      throws AxelorException {
    ContactsApi contactApiInstance = new ContactsApi();
    userLanguage = userService.getUser().getLanguage();

    Long folderId = createFolder(contactApiInstance);
    Long listId = null;
    List<AuditableModel> dataList;
    sendinBlueFieldService.fetchAttributes();
    for (String metaModelStr : metaModels) {
      MetaModel metaModel =
          Beans.get(MetaModelRepository.class)
              .all()
              .filter("self.name = ?", metaModelStr)
              .fetchOne();
      if (metaModel != null) {
        totalExportRecord = 0;
        try {
          @SuppressWarnings("unchecked")
          Class<AuditableModel> klass =
              (Class<AuditableModel>) Class.forName(metaModel.getFullName());
          int offset = 0;
          long total = JPA.all(klass).count();
          while (total > 0) {
            Query<AuditableModel> query = JPA.all(klass);
            if (exportSendinBlue.getIsExportLatest() && lastExportDateTime != null) {
              query =
                  query.filter("self.createdOn > ?1 OR self.updatedOn > ?1", lastExportDateTime);
            } else if (exportSendinBlue.getIsNoUpdate() && lastExportDateTime != null) {
              query = query.filter("self.createdOn > ?1", lastExportDateTime);
            }
            dataList = query.fetch(DATA_FETCH_LIMIT, offset);
            if (dataList != null) {
              total = dataList.size();
              if (!dataList.isEmpty()) {
                offset += dataList.size();
                if (folderId != null) {
                  listId = createList(metaModelStr, contactApiInstance, folderId);
                }
                exportMetaModel(dataList, appSendinblue.getPartnerFieldSet(), listId);
              }
            }
          }
        } catch (ClassNotFoundException e) {
          TraceBackService.trace(e);
        }
        logWriter.append(
            String.format(
                "%nTotal Contact(%s) Exported : %s", metaModel.getFullName(), totalExportRecord));
      }
    }
  }

  private void exportMetaModel(
      List<AuditableModel> contactList, Set<MetaField> metaFields, Long listId) {
    try {
      AuditableModel obj = contactList.stream().findFirst().get();
      Mapper metaModelMapper = Mapper.of(obj.getClass());
      Property[] properties = metaModelMapper.getProperties();
      for (AuditableModel dataObject : contactList) {
        exportContactDataObject(
            dataObject, properties, metaModelMapper, obj.getClass().getSimpleName(), listId);
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  protected Long createFolder(ContactsApi apiInstance) {
    try {
      Long folderId = getABSFolderId(apiInstance);
      if (folderId != null) {
        return folderId;
      }

      CreateUpdateFolder folder = new CreateUpdateFolder();
      folder.setName("ABS");
      CreateModel result = apiInstance.createFolder(folder);
      return result.getId();

    } catch (ApiException e) {
      TraceBackService.trace(e);
    }
    return null;
  }

  private Long getABSFolderId(ContactsApi apiInstance) {
    try {
      GetFolders folders = apiInstance.getFolders(2L, 0L);
      List<Object> folderList = folders.getFolders();
      for (Object object : folderList) {
        @SuppressWarnings("unchecked")
        Map<String, Object> obj = (Map<String, Object>) object;
        if (obj.containsKey("name") && obj.get("name").equals("ABS") && obj.containsKey("id")) {
          Integer id = ((Double) obj.get("id")).intValue();
          return Long.parseLong(id.toString());
        }
      }
    } catch (ApiException e) {
      TraceBackService.trace(e);
    }
    return null;
  }

  protected Long createList(String metaModelName, ContactsApi apiInstance, Long folderId) {
    try {
      GetFolderLists lists =
          apiInstance.getFolderLists(folderId, Long.parseLong(DATA_FETCH_LIMIT.toString()), 0L);
      List<Object> listList = lists.getLists();
      for (Object object : listList) {
        @SuppressWarnings("unchecked")
        Map<String, Object> obj = (Map<String, Object>) object;
        if (obj.containsKey("name")
            && obj.get("name").equals(metaModelName + "List")
            && obj.containsKey("id")) {
          Integer id = ((Double) obj.get("id")).intValue();
          return Long.parseLong(id.toString());
        }
      }

      CreateList list = new CreateList();
      list.setName(metaModelName + "List");
      list.setFolderId(folderId);
      CreateModel result = apiInstance.createList(list);
      return result.getId();
    } catch (ApiException e) {
      TraceBackService.trace(e);
    }
    return null;
  }

  private void exportContactDataObject(
      Object dataObject,
      Property[] properties,
      Mapper metaModelMapper,
      String metaModelName,
      Long listId) {
    ContactsApi contactApiInstance = new ContactsApi();
    EmailAddress emailAddress = (EmailAddress) metaModelMapper.get(dataObject, "emailAddress");
    if (emailAddress != null) {
      String emailAddressStr = emailAddress.getAddress();
      if (!StringUtils.isBlank(emailAddressStr) && isValid(emailAddressStr)) {
        List<Long> listIds = null;
        Map<String, Object> attributes = getAttributes(properties, metaModelName, dataObject);
        GetExtendedContactDetails existEmail = null;
        try {
          existEmail = contactApiInstance.getContactInfo(emailAddressStr);
          listIds = existEmail.getListIds();
          if (!listIds.contains(listId)) {
            listIds.add(listId);
          }
        } catch (ApiException e) {
        }

        if (existEmail == null) {
          createContact(dataObject, emailAddressStr, attributes, listId);
        } else {
          updateContact(emailAddressStr, attributes, listIds);
        }
      }
    }
  }

  private Map<String, Object> getAttributes(
      Property[] properties, String metaModelName, Object dataObject) {
    Map<String, Object> attributes = new HashMap<>();
    if (metaModelName.equals("Lead")) {
      attributes.put("isLead", true);
    }
    for (Property property : properties) {
      addAttribute(property, dataObject, attributes);
    }
    return attributes;
  }

  @Transactional
  public void createContact(
      Object dataObject, String emailAddressStr, Map<String, Object> attributes, Long listId) {
    ContactsApi contactApiInstance = new ContactsApi();
    CreateContact createContact = new CreateContact();
    createContact.setEmail(emailAddressStr);
    createContact.setAttributes(attributes);
    if (listId != null) {
      createContact.setListIds(new ArrayList<Long>(Arrays.asList(listId)));
    }
    try {
      CreateModel result = contactApiInstance.createContact(createContact);
      totalExportRecord++;
      try {
        Method setMethod = dataObject.getClass().getMethod("setSendinBlueId", Long.class);
        setMethod.invoke(dataObject, result.getId());
        JPA.save((AuditableModel) dataObject);
      } catch (Exception e) {
      }
    } catch (ApiException e) {
      TraceBackService.trace(e);
    }
  }

  private void updateContact(
      String emailAddressStr, Map<String, Object> attributes, List<Long> listIds) {
    ContactsApi contactApiInstance = new ContactsApi();
    UpdateContact updateContact = new UpdateContact();
    updateContact.setAttributes(attributes);
    if (listIds != null && !listIds.isEmpty()) {
      updateContact.setListIds(listIds);
    }
    try {
      contactApiInstance.updateContact(emailAddressStr, updateContact);
      totalExportRecord++;
    } catch (ApiException e) {
      TraceBackService.trace(e);
    }
  }

  private boolean isValid(String email) {
    String emailRegex =
        "^[a-zA-Z0-9_+&*-]+(?:\\."
            + "[a-zA-Z0-9_+&*-]+)*@"
            + "(?:[a-zA-Z0-9-]+\\.)+[a-z"
            + "A-Z]{2,7}$";
    Pattern pat = Pattern.compile(emailRegex);
    if (email == null) return false;
    return pat.matcher(email).matches();
  }

  private void addAttribute(Property property, Object dataObject, Map<String, Object> attributes) {
    String propertyName = "";
    Object value = property.get(dataObject);
    if (value != null) {
      if (SIMPLE_CLASSES.contains(property.getJavaType())) {
        propertyName = property.getName().toUpperCase();
        if (SendinBlueFieldService.attributeList.contains(propertyName)) {
          value = translationService.getValueTranslation(value.toString(), userLanguage);
          attributes.put(propertyName, value);
        }
      } else if (property.getJavaType().equals(Boolean.class)) {
        propertyName = property.getName().toUpperCase();
        if (SendinBlueFieldService.attributeList.contains(propertyName)) {
          value = (Boolean) value ? true : false;
          attributes.put(propertyName, value);
        }
      } else if (CALENDAR_CLASSES.contains(property.getJavaType())) {
        try {
          propertyName = property.getName().toUpperCase();
          if (SendinBlueFieldService.attributeList.contains(propertyName)) {
            attributes.put(
                propertyName, getFormatedDate(value, property.getJavaType().getSimpleName()));
          }
        } catch (Exception e) {
          TraceBackService.trace(e);
        }
      } else {
        List<Field> fields =
            FieldUtils.getFieldsListWithAnnotation(property.getJavaType(), NameColumn.class);
        if (fields != null && !fields.isEmpty()) {
          propertyName = property.getName().toUpperCase() + "NAME";
          if (SendinBlueFieldService.attributeList.contains(propertyName)) {
            Field field = fields.get(0);
            try {
              field.setAccessible(true);
              Object nameColumnValue = field.get(value);
              field.setAccessible(false);
              if (nameColumnValue != null) {
                nameColumnValue =
                    translationService.getValueTranslation(
                        nameColumnValue.toString(), userLanguage);
                attributes.put(propertyName, nameColumnValue);
              }
            } catch (IllegalArgumentException | IllegalAccessException e) {
              TraceBackService.trace(e);
            }
          }
        }
      }
    }
  }

  private String getFormatedDate(Object value, String type) throws ParseException {
    try {
      Date outputDate = null;
      String outputStr = null;
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      switch (type) {
        case "LocalDateTime":
          LocalDateTime ldtValue = (LocalDateTime) value;
          outputDate = Date.from(ldtValue.atZone(ZoneId.systemDefault()).toInstant());
          break;
        case "LocalDate":
          LocalDate ldValue = (LocalDate) value;
          outputDate = Date.from(ldValue.atStartOfDay(ZoneId.systemDefault()).toInstant());
          break;
        case "ZonedDateTime":
          ZonedDateTime zdtValue = (ZonedDateTime) value;
          outputDate = Date.from(zdtValue.toInstant());
          break;
      }
      outputStr = sdf.format(outputDate);
      return outputStr;
    } catch (Exception e) {
    }
    return null;
  }

  @Transactional
  public void importContact(
      ImportSendinBlue importSendinBlue, LocalDateTime lastImportDateTime, StringBuilder logWriter)
      throws AxelorException {
    totalImportRecord = 0;
    totalContactRecords = 0;
    totalLeadRecords = 0;
    ContactsApi apiInstance = new ContactsApi();
    OffsetDateTime modifiedSince = null;
    if (lastImportDateTime != null
        && (importSendinBlue.getIsImportLatest() || importSendinBlue.getIsNoUpdate())) {
      modifiedSince =
          OffsetDateTime.parse(
              lastImportDateTime
                  .atZone(ZoneId.systemDefault())
                  .toString()
                  .replaceAll("\\[.*\\]", ""));
    }
    try {
      long offset = 0L;
      int total = 0;
      do {
        GetContacts result =
            apiInstance.getContacts((long) DATA_FETCH_LIMIT, offset, modifiedSince);
        if (result != null && result.getContacts() != null) {
          total = result.getContacts().size();
          offset += total;
          for (Object contact : result.getContacts()) {
            @SuppressWarnings("unchecked")
            LinkedTreeMap<String, Object> conObj = (LinkedTreeMap<String, Object>) contact;
            // ---
            createEmailAddress(conObj);
            /*if (conObj.containsKey("attributes")) {
              createEmailAddress(conObj);
            }*/
          }
        } else {
          total = 0;
        }
      } while (total > 0);
    } catch (ApiException e) {
      TraceBackService.trace(e);
    }
    logWriter.append(String.format("%nTotal Contacts Imported : %s", totalContactRecords));
    logWriter.append(String.format("%nTotal Contacts Leads : %s", totalLeadRecords));
  }

  private void createEmailAddress(LinkedTreeMap<String, Object> conObj) {
    String name = "";
    Partner partner = null;
    Lead lead = null;

    String emailAddressStr = conObj.get("email").toString();
    Long id = ((Double) conObj.get("id")).longValue();
    EmailAddress emailAddress = emailAddressRepo.findByAddress(emailAddressStr);
    if (emailAddress == null) {
      emailAddress = new EmailAddress();
      emailAddress.setAddress(emailAddressStr);
    } else {
      partner = emailAddress.getPartner();
      lead = emailAddress.getLead();
    }
    // ---
    if (conObj.containsKey("attributes")) {
      @SuppressWarnings("unchecked")
      LinkedTreeMap<String, Object> attributes =
          (LinkedTreeMap<String, Object>) conObj.get("attributes");
      if (attributes.containsKey("NAME")) {
        name = attributes.get("NAME").toString();
      }
      setLead(attributes, name, emailAddress, id, lead);
      setPartner(attributes, name, emailAddress, id, partner);

      emailAddressRepo.save(emailAddress);
      totalImportRecord++;
    }

    /*@SuppressWarnings("unchecked")
    LinkedTreeMap<String, Object> attributes =
        (LinkedTreeMap<String, Object>) conObj.get("attributes");
    if (attributes.containsKey("NAME")) {
      name = attributes.get("NAME").toString();
    }
    setLead(attributes, name, emailAddress, id, lead);
    setPartner(attributes, name, emailAddress, id, partner);
    if (emailAddress.getPartner() != null || emailAddress.getLead() != null) {
      emailAddressRepo.save(emailAddress);
      totalImportRecord++;
    }*/
  }

  private void setPartner(
      LinkedTreeMap<String, Object> attributes,
      String name,
      EmailAddress emailAddress,
      Long id,
      Partner partner) {
    boolean isPartner = false;
    if (partner == null) {
      partner = new Partner();
    }
    if (attributes.containsKey("ISCONTACT")) {
      isPartner = (boolean) attributes.get("ISCONTACT");
      partner.setIsContact(isPartner);
    }
    if (attributes.containsKey("ISCUSTOMER")) {
      isPartner = isPartner ? isPartner : (boolean) attributes.get("ISCUSTOMER");
      partner.setIsCustomer((boolean) attributes.get("ISCUSTOMER"));
    }
    if (isPartner) {
      setAttributes(partner, attributes);
      partner.setSendinBlueId(id);
      partner.setEmailAddress(emailAddress);
      partner.setName(name);
      emailAddress.setPartner(partner);
      totalContactRecords++;
    }
  }

  private void setLead(
      LinkedTreeMap<String, Object> attributes,
      String name,
      EmailAddress emailAddress,
      Long id,
      Lead lead) {
    boolean isLead = false;
    if (attributes.containsKey("ISLEAD")) {
      isLead = (boolean) attributes.get("ISLEAD");
    }
    if (lead == null) {
      lead = new Lead();
    }
    if (isLead) {
      setAttributes(lead, attributes);
      lead.setSendinBlueId(id);
      lead.setEmailAddress(emailAddress);
      lead.setName(name);
      lead.setStatusSelect(LeadRepository.LEAD_STATUS_NEW);
      emailAddress.setLead(lead);
      totalLeadRecords++;
    }
  }

  private void setAttributes(AuditableModel obj, LinkedTreeMap<String, Object> attributes) {
    Mapper metaModelMapper = Mapper.of(obj.getClass());
    List<Property> properties = Arrays.asList(metaModelMapper.getProperties());

    for (String key : attributes.keySet()) {
      List<Property> keyProperty =
          properties
              .stream()
              .filter(property -> property.getName().equalsIgnoreCase(key))
              .collect(Collectors.toList());
      if (keyProperty != null && keyProperty.size() == 1) {
        Property property = keyProperty.get(0);
        Object value = attributes.get(key);
        try {
          if (property.getJavaType().equals(Long.class) && !property.getName().equals("id")) {
            value = ((Double) value).longValue();
          }
          if (!property.getName().equals("id")) {
            property.set(obj, value);
          }
        } catch (Exception e) {
          TraceBackService.trace(e);
        }
      }
    }
  }

  @Transactional
  public void deleteSendinBlueContactLeadStatistics(Lead lead) {
    if (lead.getEmailAddress() != null && lead.getEmailAddress().getId() != null) {
      sendinBlueContactStatRepository
          .all()
          .filter("self.emailAddress = ?", lead.getEmailAddress().getId())
          .remove();
    }
  }

  @Transactional
  public void deleteSendinBlueContactPartnerStatistics(Partner partner) {
    if (partner.getEmailAddress() != null && partner.getEmailAddress().getId() != null) {
      sendinBlueContactStatRepository
          .all()
          .filter("self.emailAddress = ?", partner.getEmailAddress().getId())
          .remove();
    }
  }
}
