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

import com.axelor.apps.base.db.AppMarketing;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.marketing.db.SendinBlueCampaign;
import com.axelor.apps.marketing.db.repo.SendinBlueCampaignRepository;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.sendinblue.db.ImportSendinBlue;
import com.axelor.apps.sendinblue.db.SendinBlueCampaignStat;
import com.axelor.apps.sendinblue.db.SendinBlueContactStat;
import com.axelor.apps.sendinblue.db.SendinBlueEvent;
import com.axelor.apps.sendinblue.db.SendinBlueReport;
import com.axelor.apps.sendinblue.db.SendinBlueTag;
import com.axelor.apps.sendinblue.db.repo.SendinBlueCampaignStatRepository;
import com.axelor.apps.sendinblue.db.repo.SendinBlueContactStatRepository;
import com.axelor.apps.sendinblue.db.repo.SendinBlueEventRepository;
import com.axelor.apps.sendinblue.db.repo.SendinBlueReportRepository;
import com.axelor.apps.sendinblue.db.repo.SendinBlueTagRepository;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import sendinblue.ApiException;
import sibApi.ContactsApi;
import sibApi.EmailCampaignsApi;
import sibApi.SmtpApi;
import sibModel.GetContactCampaignStats;
import sibModel.GetContactCampaignStatsClicked;
import sibModel.GetContactCampaignStatsOpened;
import sibModel.GetContactCampaignStatsUnsubscriptions;
import sibModel.GetEmailCampaign;
import sibModel.GetEmailEventReport;
import sibModel.GetEmailEventReportEvents;
import sibModel.GetExtendedContactDetailsStatisticsLinks;
import sibModel.GetExtendedContactDetailsStatisticsMessagesSent;
import sibModel.GetExtendedContactDetailsStatisticsUnsubscriptionsAdminUnsubscription;
import sibModel.GetExtendedContactDetailsStatisticsUnsubscriptionsUserUnsubscription;
import sibModel.GetReports;
import sibModel.GetReportsReports;

public class SendinBlueReportService {

  @Inject AppSendinBlueService appSendinBlueService;

  @Inject SendinBlueEventRepository sendinBlueEventRepo;
  @Inject SendinBlueReportRepository sendinBlueReportRepo;
  @Inject SendinBlueCampaignRepository sendinBlueCampaignRepo;
  @Inject SendinBlueCampaignStatRepository sendinBlueCampaignStatRepo;
  @Inject SendinBlueContactStatRepository sendinBlueContactStatRepo;

  protected static final Long DATA_FETCH_LIMIT = 100L;

  protected List<SendinBlueReport> sendinBlueReportList = new ArrayList<>();
  protected List<SendinBlueEvent> sendinBlueEventList = new ArrayList<>();
  protected List<SendinBlueContactStat> sendinBlueContactStatList = new ArrayList<>();

  private long totalEventImported,
      totalCampaignReportImported,
      totalCampaignStatImported,
      totalContactStatImported;

  public void importReport(
      AppMarketing appMarketing,
      ImportSendinBlue importSendinBlue,
      LocalDateTime lastImportDateTime,
      StringBuilder logWriter)
      throws AxelorException {
    totalEventImported =
        totalCampaignReportImported = totalCampaignStatImported = totalContactStatImported = 0;
    if (appMarketing.getManageSendinBlueApiEmailingReporting()) {
      importEvents();
      logWriter.append(String.format("%nTotal Events Imported : %s", totalEventImported));

      importCampaignReport();
      logWriter.append(
          String.format("%nTotal Campaign Reports Imported : %s", totalCampaignReportImported));

      importCampaignStat();
      logWriter.append(
          String.format("%nTotal Campaign Statistics Imported : %s", totalCampaignStatImported));

      importContactStat();
      logWriter.append(
          String.format("%nTotal Contact Statistics Imported : %s%n", totalContactStatImported));
    }
  }

  /*
   * Import statistics per Day :
   *   - Used to show statistics on Dashboard
   *   - Chart : show eventTotal for each Event Filter by Date
   *
   * */
  public void importCampaignReport() throws AxelorException {
    LocalDate localDate = null;
    org.threeten.bp.LocalDate startDate = null;
    org.threeten.bp.LocalDate endDate = null;
    sendinBlueReportList = sendinBlueReportRepo.all().fetch();
    Optional<SendinBlueReport> maxDateReport =
        sendinBlueReportList.stream().max(Comparator.comparing(SendinBlueReport::getReportDate));
    if (maxDateReport.isPresent()) {
      localDate = maxDateReport.get().getReportDate();
      if (localDate.compareTo(Beans.get(AppBaseService.class).getTodayDate()) < 0) {
        localDate = localDate.plusDays(1L);
      }
      if (localDate != null) {
        startDate = org.threeten.bp.LocalDate.parse(localDate.toString());
        if (startDate != null) {
          LocalDate today = Beans.get(AppBaseService.class).getTodayDate();
          if (today == null) {
            endDate = org.threeten.bp.LocalDate.now();
          } else {
            endDate = org.threeten.bp.LocalDate.parse(today.toString());
          }
        }
      }
    }
    SmtpApi apiInstance = new SmtpApi();
    try {
      long offset = 0L;
      int total = 0;
      do {
        GetReports result =
            apiInstance.getSmtpReport(DATA_FETCH_LIMIT, offset, startDate, endDate, null, null);
        if (result != null && result.getReports() != null) {
          total = result.getReports().size();
          offset += total;
          for (GetReportsReports report : result.getReports()) {
            createSendinBlueReport(report);
          }
        } else {
          total = 0;
        }
      } while (total > 0);
    } catch (ApiException e) {
      TraceBackService.trace(e);
    }
  }

  @Transactional
  public void createSendinBlueReport(GetReportsReports report) {
    SendinBlueReport sendinBlueReport = null;
    LocalDate reportDate = LocalDate.parse(report.getDate().toString());
    Optional<SendinBlueReport> optSendinBlueReport =
        sendinBlueReportList
            .stream()
            .filter(rpt -> rpt.getReportDate().compareTo(reportDate) == 0)
            .findFirst();
    if (optSendinBlueReport.isPresent()) {
      sendinBlueReport = optSendinBlueReport.get();
    } else {
      sendinBlueReport = new SendinBlueReport();
      sendinBlueReport.setReportDate(reportDate);
    }
    sendinBlueReport.setRequests(report.getRequests());
    sendinBlueReport.setDelivered(report.getDelivered());
    sendinBlueReport.setHardBounces(report.getHardBounces());
    sendinBlueReport.setSoftBounces(report.getSoftBounces());
    sendinBlueReport.setClicks(report.getClicks());
    sendinBlueReport.setUniqueClicks(report.getUniqueClicks());
    sendinBlueReport.setOpens(report.getOpens());
    sendinBlueReport.setUniqueOpens(report.getUniqueOpens());
    sendinBlueReport.setSpamReports(report.getSpamReports());
    sendinBlueReport.setBlocked(report.getBlocked());
    sendinBlueReport.setInvalid(report.getInvalid());
    sendinBlueReportRepo.save(sendinBlueReport);
    totalCampaignReportImported++;
  }

  /*
   * Import statistics By Tag :
   *   - Used to show statistics on Dashboard
   *   - Chart : show total eventCount Filter By Tag
   * */
  public void importEvents() throws AxelorException {
    LocalDateTime localDateTime = null;
    org.threeten.bp.LocalDate startDate = null;
    org.threeten.bp.LocalDate endDate = null;
    sendinBlueEventList = sendinBlueEventRepo.all().fetch();
    Optional<SendinBlueEvent> maxDateReport =
        sendinBlueEventList.stream().max(Comparator.comparing(SendinBlueEvent::getEventDate));
    if (maxDateReport.isPresent()) {
      localDateTime = maxDateReport.get().getEventDate();
      if (localDateTime.compareTo(Beans.get(AppBaseService.class).getTodayDate().atStartOfDay())
          < 0) {
        localDateTime = localDateTime.plusDays(1L);
      }
      if (localDateTime != null) {
        startDate = org.threeten.bp.LocalDate.parse(localDateTime.toLocalDate().toString());
        if (startDate != null) {
          LocalDate today = Beans.get(AppBaseService.class).getTodayDate();
          if (today == null) {
            endDate = org.threeten.bp.LocalDate.now();
          } else {
            endDate = org.threeten.bp.LocalDate.parse(today.toString());
          }
        }
      }
    }
    SmtpApi apiInstance = new SmtpApi();
    try {
      long offset = 0L;
      int total = 0;
      do {
        GetEmailEventReport result =
            apiInstance.getEmailEventReport(
                DATA_FETCH_LIMIT, offset, startDate, endDate, null, null, null, null, null, null);
        if (result != null && result.getEvents() != null) {
          total = result.getEvents().size();
          offset += total;
          for (GetEmailEventReportEvents event : result.getEvents()) {
            createSendinBlueEvent(event);
          }
        } else {
          total = 0;
        }
      } while (total > 0);
    } catch (ApiException e) {
      TraceBackService.trace(e);
    }
  }

  @Transactional
  public void createSendinBlueEvent(GetEmailEventReportEvents event) {
    LocalDateTime eventDate = LocalDateTime.parse(event.getDate().toLocalDateTime().toString());
    EmailAddress emailAddress =
        Beans.get(EmailAddressRepository.class).findByAddress(event.getEmail());
    SendinBlueEvent sendinBlueEvent = null;
    Optional<SendinBlueEvent> optSendinBlueEvent =
        sendinBlueEventList
            .stream()
            .filter(
                evnt ->
                    evnt.getEventDate().compareTo(eventDate) == 0
                        && evnt.getEvent().equals(event.getEvent().getValue())
                        && evnt.getEmail().equals(event.getEmail())
                        && (emailAddress == null
                            || (emailAddress != null
                                && evnt.getEmailAddress().getId().equals(emailAddress.getId()))))
            .findFirst();
    if (optSendinBlueEvent.isPresent()) {
      sendinBlueEvent = optSendinBlueEvent.get();
    } else {
      sendinBlueEvent = new SendinBlueEvent();
      sendinBlueEvent.setEventDate(eventDate);
      sendinBlueEvent.setEmail(event.getEmail());
      if (event.getEvent() != null) {
        sendinBlueEvent.setEvent(event.getEvent().getValue());
      }
    }
    String eventTag = event.getTag();
    if (!StringUtils.isBlank(eventTag)) {
      SendinBlueTag sendinBlueTag = Beans.get(SendinBlueTagRepository.class).findByName(eventTag);
      if (sendinBlueTag == null) {
        sendinBlueTag = new SendinBlueTag();
        sendinBlueTag.setName(eventTag);
      }
      sendinBlueEvent.setTag(sendinBlueTag);
    }
    sendinBlueEvent.setTagStr(eventTag);
    sendinBlueEvent.setIp(event.getIp());
    sendinBlueEvent.setReason(event.getReason());

    if (emailAddress != null) {
      sendinBlueEvent.setEmailAddress(emailAddress);
      sendinBlueEvent.setPartner(emailAddress.getPartner());
      sendinBlueEvent.setLead(emailAddress.getLead());
    }
    sendinBlueEventList.add(sendinBlueEvent);
    sendinBlueEventRepo.save(sendinBlueEvent);
    totalEventImported++;
  }

  /*
   * Import statistics per Campaign :
   *   - Used to show statistics on Campaign view
   *   - Dashlet : show eventTotal for each CampaignType (Partner , Lead , PartnerReminder , LeadReminder)
   * */
  @Transactional
  public void importCampaignStat() throws AxelorException {
    EmailCampaignsApi apiInstance = new EmailCampaignsApi();
    List<SendinBlueCampaign> campaigns = sendinBlueCampaignRepo.all().fetch();
    SendinBlueCampaignStat sendinBlueCampaignStat = null;
    for (SendinBlueCampaign sendinBlueCampaign : campaigns) {
      sendinBlueCampaignStat =
          sendinBlueCampaignStatRepo.findBySendinBlueCampaign(sendinBlueCampaign);
      if (sendinBlueCampaignStat == null) {
        sendinBlueCampaignStat = new SendinBlueCampaignStat();
        sendinBlueCampaignStat.setSendinBlueCampaign(sendinBlueCampaign);
      }
      createCampaignStat(sendinBlueCampaignStat, sendinBlueCampaign, apiInstance);
    }
  }

  private void createCampaignStat(
      SendinBlueCampaignStat sendinBlueCampaignStat,
      SendinBlueCampaign sendinBlueCampaign,
      EmailCampaignsApi apiInstance) {

    try {
      GetEmailCampaign campaign =
          apiInstance.getEmailCampaign(sendinBlueCampaign.getSendinBlueId());
      if (campaign != null) {
        if (campaign.getStatistics() != null) {
          createSendinBlueCampaignStat(sendinBlueCampaignStat, campaign);
        }
      }
    } catch (Exception e) {
    }
  }

  private void createSendinBlueCampaignStat(
      SendinBlueCampaignStat sendinBlueCampaignStat, GetEmailCampaign campaign) {
    Long defaultValue = new Long(0L);
    @SuppressWarnings("unchecked")
    Map<String, Object> stats = (Map<String, Object>) campaign.getStatistics();
    if (stats.containsKey("globalStats")) {
      @SuppressWarnings("unchecked")
      Map<String, Object> globalStats = (Map<String, Object>) stats.getOrDefault("globalStats", 0L);
      if (globalStats != null) {
        try {
          sendinBlueCampaignStat.setUniqueClicks(
              getLongValue(globalStats.getOrDefault("uniqueClicks", defaultValue)));
          sendinBlueCampaignStat.setClickers(
              getLongValue(globalStats.getOrDefault("clickers", defaultValue)));
          sendinBlueCampaignStat.setComplaints(
              getLongValue(globalStats.getOrDefault("complaints", defaultValue)));
          sendinBlueCampaignStat.setDelivered(
              getLongValue(globalStats.getOrDefault("delivered", defaultValue)));
          sendinBlueCampaignStat.setSent(
              getLongValue(globalStats.getOrDefault("sent", defaultValue)));
          sendinBlueCampaignStat.setSoftBounces(
              getLongValue(globalStats.getOrDefault("softBounces", defaultValue)));
          sendinBlueCampaignStat.setHardBounces(
              getLongValue(globalStats.getOrDefault("hardBounces", defaultValue)));
          sendinBlueCampaignStat.setUniqueViews(
              getLongValue(globalStats.getOrDefault("uniqueViews", defaultValue)));
          sendinBlueCampaignStat.setUnsubscriptions(
              getLongValue(globalStats.getOrDefault("unsubscriptions", defaultValue)));
          sendinBlueCampaignStat.setViewed(
              getLongValue(globalStats.getOrDefault("viewed", defaultValue)));
          sendinBlueCampaignStatRepo.save(sendinBlueCampaignStat);
          totalCampaignStatImported++;
        } catch (Exception e) {
          TraceBackService.trace(e);
        }
      }
    }
  }

  private Long getLongValue(Object value) {
    if (value == null || value.toString().equals("0.0")) {
      return new Long(0L);
    }
    return new Long(value.toString().substring(0, value.toString().indexOf(".")));
  }

  /*
   * Import statistics per Email Address from Partner and Lead :
   *   - Used to show statistics on Partner and Lead view
   *   - Dashlet : Group By Campaign
   *   - Chart : Sum up the counts Filter by Date
   *
   *   - Used to show statistics on Campaign view
   *   - Dashlet : show each message sent for Campaign.
   * */
  @Transactional
  public void importContactStat() throws AxelorException {
    ContactsApi apiInstance = new ContactsApi();
    List<EmailAddress> emailAddresses =
        Beans.get(EmailAddressRepository.class)
            .all()
            // .filter("self.partner IS NOT NULL OR self.lead IS NOT NULL")
            .fetch();
    sendinBlueContactStatList = sendinBlueContactStatRepo.all().fetch();
    if (emailAddresses != null) {
      for (EmailAddress emailAddress : emailAddresses) {
        String email = emailAddress.getAddress();
        try {
          GetContactCampaignStats contactStatObj = apiInstance.getContactStats(email);
          if (contactStatObj != null) {
            importClicked(contactStatObj, emailAddress);
            importComplaints(contactStatObj, emailAddress);
            importHardBounce(contactStatObj, emailAddress);
            importMessageSent(contactStatObj, emailAddress);
            importOpened(contactStatObj, emailAddress);
            importSoftBounce(contactStatObj, emailAddress);
            importUnsubscriptions(contactStatObj, emailAddress);
          }
        } catch (ApiException e) {
        }
      }
    }
  }

  private void importClicked(GetContactCampaignStats contactStatObj, EmailAddress emailAddress) {
    List<GetContactCampaignStatsClicked> clickedList = contactStatObj.getClicked();
    if (clickedList != null) {
      for (GetContactCampaignStatsClicked clickedObj : clickedList) {
        SendinBlueCampaign sendinBlueCampaign =
            sendinBlueCampaignRepo.findBySendinBlueId(clickedObj.getCampaignId());
        if (sendinBlueCampaign != null) {
          List<GetExtendedContactDetailsStatisticsLinks> clickedLinklist = clickedObj.getLinks();
          for (GetExtendedContactDetailsStatisticsLinks clickedLink : clickedLinklist) {
            SendinBlueContactStat contactStat = null;
            Optional<SendinBlueContactStat> optContactStat =
                sendinBlueContactStatList
                    .stream()
                    .filter(
                        conStat ->
                            conStat.getEventType().equals(SendinBlueEventRepository.CLICKS)
                                && conStat
                                    .getSendinBlueCampaign()
                                    .getId()
                                    .equals(sendinBlueCampaign.getId())
                                && conStat.getEmailAddress().getId().equals(emailAddress.getId())
                                && conStat.getUrl().equals(clickedLink.getUrl())
                                && conStat.getIp().equals(clickedLink.getIp()))
                    .findFirst();
            if (optContactStat.isPresent()) {
              contactStat = optContactStat.get();
            } else {
              contactStat = new SendinBlueContactStat();
              contactStat.setEventType(SendinBlueEventRepository.CLICKS);
              contactStat.setSendinBlueCampaign(sendinBlueCampaign);
              contactStat.setEmailAddress(emailAddress);
              contactStat.setUrl(clickedLink.getUrl());
              contactStat.setIp(clickedLink.getIp());
            }
            contactStat.setEventCount(clickedLink.getCount());
            contactStat.setEventDateTime(
                LocalDateTime.parse(clickedLink.getEventTime().toLocalDateTime().toString()));
            sendinBlueContactStatRepo.save(contactStat);
            totalContactStatImported++;
          }
        }
      }
    }
  }

  private void importComplaints(GetContactCampaignStats contactStatObj, EmailAddress emailAddress) {
    List<GetExtendedContactDetailsStatisticsMessagesSent> compaintList =
        contactStatObj.getComplaints();
    if (compaintList != null) {
      for (GetExtendedContactDetailsStatisticsMessagesSent compaintObj : compaintList) {
        SendinBlueCampaign sendinBlueCampaign =
            sendinBlueCampaignRepo.findBySendinBlueId(compaintObj.getCampaignId());
        if (sendinBlueCampaign != null) {
          SendinBlueContactStat contactStat = null;
          Optional<SendinBlueContactStat> optContactStat =
              sendinBlueContactStatList
                  .stream()
                  .filter(
                      conStat ->
                          conStat.getEventType().equals(SendinBlueEventRepository.COMPLAINTS)
                              && conStat
                                  .getSendinBlueCampaign()
                                  .getId()
                                  .equals(sendinBlueCampaign.getId())
                              && conStat.getEmailAddress().getId().equals(emailAddress.getId()))
                  .findFirst();
          if (optContactStat.isPresent()) {
            contactStat = optContactStat.get();
          } else {
            contactStat = new SendinBlueContactStat();
            contactStat.setEventCount(1L);
            contactStat.setEventDateTime(
                LocalDateTime.parse(compaintObj.getEventTime().toLocalDateTime().toString()));
            contactStat.setEventType(SendinBlueEventRepository.COMPLAINTS);
            contactStat.setSendinBlueCampaign(sendinBlueCampaign);
            contactStat.setEmailAddress(emailAddress);
            sendinBlueContactStatRepo.save(contactStat);
            totalContactStatImported++;
          }
        }
      }
    }
  }

  private void importHardBounce(GetContactCampaignStats contactStatObj, EmailAddress emailAddress) {
    List<GetExtendedContactDetailsStatisticsMessagesSent> hardBounceList =
        contactStatObj.getHardBounces();
    if (hardBounceList != null) {
      for (GetExtendedContactDetailsStatisticsMessagesSent hardBounceObj : hardBounceList) {
        SendinBlueCampaign sendinBlueCampaign =
            sendinBlueCampaignRepo.findBySendinBlueId(hardBounceObj.getCampaignId());
        if (sendinBlueCampaign != null) {
          SendinBlueContactStat contactStat = null;
          Optional<SendinBlueContactStat> optContactStat =
              sendinBlueContactStatList
                  .stream()
                  .filter(
                      conStat ->
                          conStat.getEventType().equals(SendinBlueEventRepository.HARD_BOUNCES)
                              && conStat
                                  .getSendinBlueCampaign()
                                  .getId()
                                  .equals(sendinBlueCampaign.getId())
                              && conStat.getEmailAddress().getId().equals(emailAddress.getId()))
                  .findFirst();
          if (optContactStat.isPresent()) {
            contactStat = optContactStat.get();
          } else {
            contactStat = new SendinBlueContactStat();
            contactStat.setEventCount(1L);
            contactStat.setEventDateTime(
                LocalDateTime.parse(hardBounceObj.getEventTime().toLocalDateTime().toString()));
            contactStat.setEventType(SendinBlueEventRepository.HARD_BOUNCES);
            contactStat.setSendinBlueCampaign(sendinBlueCampaign);
            contactStat.setEmailAddress(emailAddress);
            sendinBlueContactStatRepo.save(contactStat);
            totalContactStatImported++;
          }
        }
      }
    }
  }

  private void importMessageSent(
      GetContactCampaignStats contactStatObj, EmailAddress emailAddress) {
    List<GetExtendedContactDetailsStatisticsMessagesSent> messageSentList =
        contactStatObj.getMessagesSent();
    if (messageSentList != null) {
      for (GetExtendedContactDetailsStatisticsMessagesSent messageSentObj : messageSentList) {
        SendinBlueCampaign sendinBlueCampaign =
            sendinBlueCampaignRepo.findBySendinBlueId(messageSentObj.getCampaignId());
        if (sendinBlueCampaign != null) {
          SendinBlueContactStat contactStat = null;
          Optional<SendinBlueContactStat> optContactStat =
              sendinBlueContactStatList
                  .stream()
                  .filter(
                      conStat ->
                          conStat.getEventType().equals(SendinBlueEventRepository.MESSAGE_SENT)
                              && conStat
                                  .getSendinBlueCampaign()
                                  .getId()
                                  .equals(sendinBlueCampaign.getId())
                              && conStat.getEmailAddress().getId().equals(emailAddress.getId()))
                  .findFirst();
          if (optContactStat.isPresent()) {
            contactStat = optContactStat.get();
          } else {
            LocalDateTime eventDateTime =
                LocalDateTime.parse(messageSentObj.getEventTime().toLocalDateTime().toString());
            contactStat = new SendinBlueContactStat();
            contactStat.setEventCount(1L);
            contactStat.setEventDateTime(eventDateTime);
            contactStat.setEventType(SendinBlueEventRepository.MESSAGE_SENT);
            contactStat.setSendinBlueCampaign(sendinBlueCampaign);
            contactStat.setEmailAddress(emailAddress);
            sendinBlueContactStatRepo.save(contactStat);
            totalContactStatImported++;
          }
        }
      }
    }
  }

  private void importOpened(GetContactCampaignStats contactStatObj, EmailAddress emailAddress) {
    List<GetContactCampaignStatsOpened> openedList = contactStatObj.getOpened();
    if (openedList != null) {
      for (GetContactCampaignStatsOpened openedObj : openedList) {
        SendinBlueCampaign sendinBlueCampaign =
            sendinBlueCampaignRepo.findBySendinBlueId(openedObj.getCampaignId());
        if (sendinBlueCampaign != null) {
          SendinBlueContactStat contactStat = null;
          Optional<SendinBlueContactStat> optContactStat =
              sendinBlueContactStatList
                  .stream()
                  .filter(
                      conStat ->
                          conStat.getEventType().equals(SendinBlueEventRepository.OPENED)
                              && conStat
                                  .getSendinBlueCampaign()
                                  .getId()
                                  .equals(sendinBlueCampaign.getId())
                              && conStat.getEmailAddress().getId().equals(emailAddress.getId())
                              && conStat.getIp().equals(openedObj.getIp()))
                  .findFirst();
          if (optContactStat.isPresent()) {
            contactStat = optContactStat.get();
          } else {
            contactStat = new SendinBlueContactStat();
            contactStat.setEventType(SendinBlueEventRepository.OPENED);
            contactStat.setSendinBlueCampaign(sendinBlueCampaign);
            contactStat.setEmailAddress(emailAddress);
            contactStat.setIp(openedObj.getIp());
          }
          contactStat.setEventCount(openedObj.getCount());
          contactStat.setEventDateTime(
              LocalDateTime.parse(openedObj.getEventTime().toLocalDateTime().toString()));
          sendinBlueContactStatRepo.save(contactStat);
          totalContactStatImported++;
        }
      }
    }
  }

  private void importSoftBounce(GetContactCampaignStats contactStatObj, EmailAddress emailAddress) {
    List<GetExtendedContactDetailsStatisticsMessagesSent> softBounceList =
        contactStatObj.getSoftBounces();
    if (softBounceList != null) {
      for (GetExtendedContactDetailsStatisticsMessagesSent softBounceObj : softBounceList) {
        SendinBlueCampaign sendinBlueCampaign =
            sendinBlueCampaignRepo.findBySendinBlueId(softBounceObj.getCampaignId());
        if (sendinBlueCampaign != null) {
          SendinBlueContactStat contactStat = null;
          Optional<SendinBlueContactStat> optContactStat =
              sendinBlueContactStatList
                  .stream()
                  .filter(
                      conStat ->
                          conStat.getEventType().equals(SendinBlueEventRepository.SOFT_BOUNCES)
                              && conStat
                                  .getSendinBlueCampaign()
                                  .getId()
                                  .equals(sendinBlueCampaign.getId())
                              && conStat.getEmailAddress().getId().equals(emailAddress.getId()))
                  .findFirst();
          if (optContactStat.isPresent()) {
            contactStat = optContactStat.get();
          } else {
            contactStat = new SendinBlueContactStat();
            contactStat.setEventCount(1L);
            contactStat.setEventDateTime(
                LocalDateTime.parse(softBounceObj.getEventTime().toLocalDateTime().toString()));
            contactStat.setEventType(SendinBlueEventRepository.SOFT_BOUNCES);
            contactStat.setSendinBlueCampaign(sendinBlueCampaign);
            contactStat.setEmailAddress(emailAddress);
            sendinBlueContactStatRepo.save(contactStat);
            totalContactStatImported++;
          }
        }
      }
    }
  }

  private void importUnsubscriptions(
      GetContactCampaignStats contactStatObj, EmailAddress emailAddress) {
    GetContactCampaignStatsUnsubscriptions unsubscriptions = contactStatObj.getUnsubscriptions();
    if (unsubscriptions != null) {
      List<GetExtendedContactDetailsStatisticsUnsubscriptionsAdminUnsubscription>
          adminUnsubscriptionList = unsubscriptions.getAdminUnsubscription();
      if (adminUnsubscriptionList != null) {
        for (GetExtendedContactDetailsStatisticsUnsubscriptionsAdminUnsubscription
            adminUnsubscriptionObj : adminUnsubscriptionList) {
          SendinBlueContactStat contactStat = null;
          Optional<SendinBlueContactStat> optContactStat =
              sendinBlueContactStatList
                  .stream()
                  .filter(
                      conStat ->
                          conStat
                                  .getEventType()
                                  .equals(SendinBlueEventRepository.ADMIN_UNSUBSCRIPTION)
                              && conStat.getEmailAddress().getId().equals(emailAddress.getId()))
                  .findFirst();
          if (optContactStat.isPresent()) {
            contactStat = optContactStat.get();
          } else {
            contactStat = new SendinBlueContactStat();
            contactStat.setEventCount(1L);
            contactStat.setEventDateTime(
                LocalDateTime.parse(
                    adminUnsubscriptionObj.getEventTime().toLocalDateTime().toString()));
            contactStat.setEventType(SendinBlueEventRepository.ADMIN_UNSUBSCRIPTION);
            contactStat.setEmailAddress(emailAddress);
            sendinBlueContactStatRepo.save(contactStat);
            totalContactStatImported++;
          }
        }
      }

      List<GetExtendedContactDetailsStatisticsUnsubscriptionsUserUnsubscription>
          userUnsubscriptionList = unsubscriptions.getUserUnsubscription();
      if (userUnsubscriptionList != null) {
        for (GetExtendedContactDetailsStatisticsUnsubscriptionsUserUnsubscription
            userUnsubscriptionObj : userUnsubscriptionList) {
          SendinBlueContactStat contactStat = null;
          Optional<SendinBlueContactStat> optContactStat =
              sendinBlueContactStatList
                  .stream()
                  .filter(
                      conStat ->
                          conStat
                                  .getEventType()
                                  .equals(SendinBlueEventRepository.USER_UNSUBSCRIPTION)
                              && conStat.getEmailAddress().getId().equals(emailAddress.getId()))
                  .findFirst();
          if (optContactStat.isPresent()) {
            contactStat = optContactStat.get();
          } else {
            contactStat = new SendinBlueContactStat();
            contactStat.setEventCount(1L);
            contactStat.setEventDateTime(
                LocalDateTime.parse(
                    userUnsubscriptionObj.getEventTime().toLocalDateTime().toString()));
            contactStat.setEventType(SendinBlueEventRepository.USER_UNSUBSCRIPTION);
            contactStat.setEmailAddress(emailAddress);
            sendinBlueContactStatRepo.save(contactStat);
            totalContactStatImported++;
          }
        }
      }
    }
  }
}
