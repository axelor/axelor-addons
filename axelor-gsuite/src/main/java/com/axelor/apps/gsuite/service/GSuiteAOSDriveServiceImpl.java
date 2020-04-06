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
package com.axelor.apps.gsuite.service;

import com.axelor.app.AppSettings;
import com.axelor.apps.gsuite.db.DriveGoogleAccount;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.repo.DriveGoogleAccountRepository;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.AxelorException;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteAOSDriveServiceImpl implements GSuiteAOSDriveService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().getClass());
  private static Drive drive;

  @Inject private GSuiteService gSuiteService;

  @Inject private GoogleAccountRepository googleAccountRepo;

  @Inject private DMSFileRepository dmsFileRepo;

  @Inject private DriveGoogleAccountRepository driveGoogleAccountRepo;

  @Inject private MetaFiles metaFiles;

  @Override
  @Transactional
  public GoogleAccount sync(GoogleAccount googleAccount) throws AxelorException {
    if (googleAccount == null) {
      return null;
    }
    LocalDateTime syncDate = googleAccount.getEventSyncFromGoogleDate();
    log.debug("Last sync date: {}", syncDate);
    googleAccount = googleAccountRepo.find(googleAccount.getId());
    try {
      drive = gSuiteService.getDrive(googleAccount.getId());
      this.syncDocs(googleAccount);
      googleAccount.setDocSyncFromGoogleDate(LocalDateTime.now());
      googleAccount.setDocSyncFromGoogleLog(null);
    } catch (IOException e) {
      googleAccount.setDocSyncFromGoogleLog("\n" + ExceptionUtils.getStackTrace(e));
    }
    return googleAccountRepo.save(googleAccount);
  }

  @Override
  public void syncDocs(GoogleAccount googleAccount) {
    FileList result;
    try {
      result =
          drive
              .files()
              .list()
              .setPageSize(100)
              .setFields(
                  "nextPageToken, files(id, name, parents, mimeType, webContentLink, webViewLink)")
              .execute();
      List<File> allData = result.getFiles();
      List<File> files =
          allData
              .stream()
              .filter(file -> !StringUtils.containsIgnoreCase(file.getMimeType(), "folder"))
              .collect(Collectors.toList());
      List<File> folders =
          allData
              .stream()
              .filter(file -> StringUtils.containsIgnoreCase(file.getMimeType(), "folder"))
              .collect(Collectors.toList());
      System.err.println("folders : " + folders.size());
      System.err.println("files : " + files.size());
      this.createFolders(folders, googleAccount);
      this.createFiles(files, googleAccount);
      this.updateParent(allData, googleAccount);
    } catch (IOException e) {
      googleAccount.setDocSyncFromGoogleLog("\n" + ExceptionUtils.getStackTrace(e));
      e.printStackTrace();
    }
  }

  @Override
  @Transactional
  public void updateParent(List<File> allData, GoogleAccount googleAccount) {
    for (File file : allData) {
      DMSFile dmsFile = driveGoogleAccountRepo.findByGoogleDriveId(file.getId()).getDms();
      if (dmsFile != null
          && file.getParents() != null
          && driveGoogleAccountRepo.findByGoogleDriveId(file.getParents().get(0)) != null) {
        DMSFile parent =
            driveGoogleAccountRepo.findByGoogleDriveId(file.getParents().get(0)).getDms();
        dmsFile.setParent(parent);
        dmsFileRepo.save(dmsFile);
      }
    }
  }

  @Override
  @Transactional
  public void createFiles(List<File> files, GoogleAccount googleAccount) throws IOException {
    for (File file : files) {
      DriveGoogleAccount driveGoogleAccount =
          driveGoogleAccountRepo.findByGoogleDriveId(file.getId());
      DMSFile dmsFile = driveGoogleAccount != null ? driveGoogleAccount.getDms() : new DMSFile();
      dmsFile.setIsDirectory(false);
      dmsFile.setFileName(file.getName());
      System.err.println(file.getWebContentLink());
      System.err.println("view link : " + file.getWebViewLink());

      java.io.File localFile =
          new java.io.File(
              AppSettings.get().get("file.upload.dir") + java.io.File.separator + file.getName());
      MetaFile metaFile = new MetaFile();

      if (localFile.createNewFile() || dmsFile.getMetaFile() == null) {
        metaFile =
            metaFiles.upload(
                drive.files().get(file.getId()).executeMediaAsInputStream(), file.getName());
      } else {
        FileUtils.copyToFile(
            drive.files().get(file.getId()).executeMediaAsInputStream(), localFile);
        metaFile = dmsFile.getMetaFile();
      }

      System.out.println(file.getName());
      System.out.println(localFile.isFile());

      dmsFile.setMetaFile(metaFile);
      if (file.getParents() != null
          && driveGoogleAccountRepo.findByGoogleDriveId(file.getParents().get(0)) != null) {
        DMSFile parent =
            driveGoogleAccountRepo.findByGoogleDriveId(file.getParents().get(0)).getDms();
        dmsFile.setParent(parent);
      }
      dmsFileRepo.save(dmsFile);
      this.createDriveGoogleAccount(googleAccount, dmsFile, file, driveGoogleAccount);
    }
  }

  @Override
  @Transactional
  public void createFolders(List<File> folders, GoogleAccount googleAccount) {
    for (File file : folders) {
      DriveGoogleAccount driveGoogleAccount =
          driveGoogleAccountRepo.findByGoogleDriveId(file.getId());
      DMSFile dmsFile = driveGoogleAccount != null ? driveGoogleAccount.getDms() : new DMSFile();
      dmsFile.setIsDirectory(true);
      dmsFile.setFileName(file.getName());
      dmsFileRepo.save(dmsFile);
      this.createDriveGoogleAccount(googleAccount, dmsFile, file, driveGoogleAccount);
    }
  }

  @Override
  @Transactional
  public void createDriveGoogleAccount(
      GoogleAccount googleAccount,
      DMSFile dmsFile,
      File file,
      DriveGoogleAccount driveGoogleAccount) {
    driveGoogleAccount = driveGoogleAccount != null ? driveGoogleAccount : new DriveGoogleAccount();
    driveGoogleAccount.setDms(dmsFile);
    driveGoogleAccount.setGoogleAccount(googleAccount);
    driveGoogleAccount.setGoogleDriveId(file.getId());
    driveGoogleAccountRepo.save(driveGoogleAccount);
  }
}
