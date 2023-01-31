package com.axelor.apps.redmine.service.imports.projects.pojo;

import com.axelor.apps.base.db.Batch;
import com.taskadapter.redmineapi.ProjectManager;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class MethodParameters {

  private Consumer<Throwable> onError;
  private Consumer<Object> onSuccess;
  private Batch batch;
  private List<Object[]> errorObjList;
  private LocalDateTime lastBatchUpdatedOn;
  private HashMap<Integer, String> redmineUserMap;
  private ProjectManager projectManager;

  public MethodParameters(
      Consumer<Throwable> onError,
      Consumer<Object> onSuccess,
      Batch batch,
      List<Object[]> errorObjList,
      LocalDateTime lastBatchUpdatedOn,
      HashMap<Integer, String> redmineUserMap,
      ProjectManager projectManager) {
    this.onError = onError;
    this.onSuccess = onSuccess;
    this.batch = batch;
    this.errorObjList = errorObjList;
    this.lastBatchUpdatedOn = lastBatchUpdatedOn;
    this.redmineUserMap = redmineUserMap;
    this.projectManager = projectManager;
  }

  public Consumer<Throwable> getOnError() {
    return onError;
  }

  public void setOnError(Consumer<Throwable> onError) {
    this.onError = onError;
  }

  public Consumer<Object> getOnSuccess() {
    return onSuccess;
  }

  public void setOnSuccess(Consumer<Object> onSuccess) {
    this.onSuccess = onSuccess;
  }

  public Batch getBatch() {
    return batch;
  }

  public void setBatch(Batch batch) {
    this.batch = batch;
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

  public HashMap<Integer, String> getRedmineUserMap() {
    return redmineUserMap;
  }

  public void setRedmineUserMap(HashMap<Integer, String> redmineUserMap) {
    this.redmineUserMap = redmineUserMap;
  }

  public ProjectManager getProjectManager() {
    return projectManager;
  }

  public void setProjectManager(ProjectManager projectManager) {
    this.projectManager = projectManager;
  }
}
