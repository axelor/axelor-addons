package com.axelor.apps.redmine.service.sync.timeentries.pojo;

import com.axelor.studio.db.AppRedmine;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class TimeEntriesRedmineParameters {
  private Consumer<Object> onSuccess;
  private Consumer<Throwable> onError;
  private AppRedmine appRedmine;
  private HashMap<Integer, String> redmineUserMap;
  private HashMap<String, String> redmineUserLoginMap;
  private List<Object[]> errorObjList;
  private LocalDateTime lastBatchUpdatedOn;
  private ZonedDateTime lastBatchEndDate;
  private String failedRedmineTimeEntriesIds;
  private String failedAosTimesheetLineIds;

  public TimeEntriesRedmineParameters() {}

  public TimeEntriesRedmineParameters(
      Consumer<Object> onSuccess,
      Consumer<Throwable> onError,
      AppRedmine appRedmine,
      HashMap<Integer, String> redmineUserMap,
      HashMap<String, String> redmineUserLoginMap,
      List<Object[]> errorObjList,
      LocalDateTime lastBatchUpdatedOn,
      ZonedDateTime lastBatchEndDate,
      String failedRedmineTimeEntriesIds,
      String failedAosTimesheetLineIds) {
    this.onSuccess = onSuccess;
    this.onError = onError;
    this.appRedmine = appRedmine;
    this.redmineUserMap = redmineUserMap;
    this.redmineUserLoginMap = redmineUserLoginMap;
    this.errorObjList = errorObjList;
    this.lastBatchUpdatedOn = lastBatchUpdatedOn;
    this.lastBatchEndDate = lastBatchEndDate;
    this.failedRedmineTimeEntriesIds = failedRedmineTimeEntriesIds;
    this.failedAosTimesheetLineIds = failedAosTimesheetLineIds;
  }

  public Consumer<Object> getOnSuccess() {
    return onSuccess;
  }

  public void setOnSuccess(Consumer<Object> onSuccess) {
    this.onSuccess = onSuccess;
  }

  public Consumer<Throwable> getOnError() {
    return onError;
  }

  public void setOnError(Consumer<Throwable> onError) {
    this.onError = onError;
  }

  public AppRedmine getAppRedmine() {
    return appRedmine;
  }

  public void setAppRedmine(AppRedmine appRedmine) {
    this.appRedmine = appRedmine;
  }

  public HashMap<Integer, String> getRedmineUserMap() {
    return redmineUserMap;
  }

  public void setRedmineUserMap(HashMap<Integer, String> redmineUserMap) {
    this.redmineUserMap = redmineUserMap;
  }

  public HashMap<String, String> getRedmineUserLoginMap() {
    return redmineUserLoginMap;
  }

  public void setRedmineUserLoginMap(HashMap<String, String> redmineUserLoginMap) {
    this.redmineUserLoginMap = redmineUserLoginMap;
  }

  public List<Object[]> getErrorObjList() {
    return errorObjList;
  }

  public void setErrorObjList(List<Object[]> errorObjList) {
    this.errorObjList = errorObjList;
  }

  public LocalDateTime getLastBatchUpdatedOn() {
    return lastBatchUpdatedOn;
  }

  public void setLastBatchUpdatedOn(LocalDateTime lastBatchUpdatedOn) {
    this.lastBatchUpdatedOn = lastBatchUpdatedOn;
  }

  public ZonedDateTime getLastBatchEndDate() {
    return lastBatchEndDate;
  }

  public void setLastBatchEndDate(ZonedDateTime lastBatchEndDate) {
    this.lastBatchEndDate = lastBatchEndDate;
  }

  public String getFailedRedmineTimeEntriesIds() {
    return failedRedmineTimeEntriesIds;
  }

  public void setFailedRedmineTimeEntriesIds(String failedRedmineTimeEntriesIds) {
    this.failedRedmineTimeEntriesIds = failedRedmineTimeEntriesIds;
  }

  public String getFailedAosTimesheetLineIds() {
    return failedAosTimesheetLineIds;
  }

  public void setFailedAosTimesheetLineIds(String failedAosTimesheetLineIds) {
    this.failedAosTimesheetLineIds = failedAosTimesheetLineIds;
  }
}
