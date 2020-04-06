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

import com.axelor.apps.gsuite.db.DriveGoogleAccount;
import com.axelor.apps.gsuite.db.GoogleAccount;
import com.axelor.apps.gsuite.db.repo.DriveGoogleAccountRepository;
import com.axelor.apps.gsuite.db.repo.GoogleAccountRepository;
import com.axelor.apps.gsuite.exception.IExceptionMessage;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteDriveServiceImpl implements GSuiteDriveService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static Drive drive;

  @Inject private GoogleAccountRepository googleAccountRepo;

  @Inject private GSuiteService gSuiteService;

  @Inject private DMSFileRepository dmsFileRepo;

  @Inject private DriveGoogleAccountRepository driveGoogleAccountRepo;

  @Override
  @Transactional
  public GoogleAccount sync(GoogleAccount googleAccount) throws AxelorException {

    if (googleAccount == null) {
      return null;
    }

    LocalDateTime syncDate = googleAccount.getDocSyncToGoogleDate();
    LOG.debug("Last sync date: {}", syncDate);
    googleAccount = googleAccountRepo.find(googleAccount.getId());
    String accountName = googleAccount.getName();

    try {

      List<DMSFile> dmsFiles;
      if (syncDate == null) {
        dmsFiles = dmsFileRepo.all().fetch();
      } else {
        dmsFiles =
            dmsFileRepo
                .all()
                .filter("self.updatedOn is null OR self.updatedOn > ?1", syncDate)
                .fetch();
      }
      for (DMSFile dmsFile : dmsFiles) {
        DriveGoogleAccount driveAccount = new DriveGoogleAccount();
        String googleDriveId = null;
        for (DriveGoogleAccount account : dmsFile.getDriveGoogleAccounts()) {
          if (googleAccount.equals(account.getGoogleAccount())) {
            driveAccount = account;
            googleDriveId = account.getGoogleDriveId();
            break;
          }
        }

        LOG.debug("Google Drive id: {}", googleDriveId);
        if (googleDriveId != null) {
          continue;
        }

        drive = gSuiteService.getDrive(googleAccount.getId());
        googleDriveId =
            updateGoogleDrive(dmsFile, new String[] {googleDriveId, accountName}, false);
        driveAccount.setDms(dmsFile);
        driveAccount.setGoogleAccount(googleAccount);
        driveAccount.setGoogleDriveId(googleDriveId);

        driveGoogleAccountRepo.save(driveAccount);
      }
      googleAccount.setDocSyncToGoogleDate(LocalDateTime.now());
    } catch (IOException | ServiceException e) {
      googleAccount.setDocSyncToGoogleLog("\n" + ExceptionUtils.getStackTrace(e));
    }

    return googleAccountRepo.save(googleAccount);
  }

  @Override
  @Transactional
  public DMSFile sync(DMSFile dmsFile, boolean remove) throws AxelorException {

    List<GoogleAccount> accounts = googleAccountRepo.all().filter("self.authorized = true").fetch();

    try {
      for (GoogleAccount googleAccount : accounts) {
        String accountName = googleAccount.getName();

        DriveGoogleAccount driveAccount = new DriveGoogleAccount();
        String googleDriveId = null;

        if (dmsFile.getDriveGoogleAccounts() != null) {
          for (DriveGoogleAccount account : dmsFile.getDriveGoogleAccounts()) {
            if (googleAccount.equals(account.getGoogleAccount())) {
              driveAccount = account;
              googleDriveId = account.getGoogleDriveId();
              break;
            }
          }
        }
        drive = gSuiteService.getDrive(googleAccount.getId());
        googleDriveId =
            updateGoogleDrive(dmsFile, new String[] {googleDriveId, accountName}, remove);

        if (!remove) {
          driveAccount.setGoogleAccount(googleAccount);
          driveAccount.setGoogleDriveId(googleDriveId);
          if (driveAccount.getDms() == null) {
            dmsFile.addDriveGoogleAccount(driveAccount);
          }
        }
      }
    } catch (IOException | ServiceException e) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.DRIVE_UPDATE_EXCEPTION),
          e.getLocalizedMessage());
    }

    return dmsFile;
  }

  @Override
  public String updateGoogleDrive(DMSFile dmsFile, String[] account, boolean remove)
      throws IOException, ServiceException, AxelorException {

    LOG.debug("Exporting google drive id: {}", dmsFile.getId());
    com.google.api.services.drive.model.File googleDrive =
        new com.google.api.services.drive.model.File();

    if (remove) {
      if (account[0] != null) {
        drive.files().delete(account[0]).execute();
      }
      return null;
    }
    if (account[0] != null) {
      try {
        googleDrive = drive.files().get(account[0]).execute();
      } catch (GoogleJsonResponseException e) {
        LOG.debug("Google Drive Contents Not found: {}", account[0]);
        googleDrive = new com.google.api.services.drive.model.File();
        account[0] = null;
      }
    }
    if (account[0] != null) {
      LOG.debug("Updating google Drive Id: {}", googleDrive.getId());
      googleDrive = extractDMSFile(dmsFile, googleDrive, account);

    } else {
      googleDrive = extractDMSFile(dmsFile, googleDrive, account);
      LOG.debug("Google Drive Files created: {}", googleDrive.getId());
    }
    return googleDrive.getId();
  }

  @Override
  public com.google.api.services.drive.model.File extractDMSFile(
      DMSFile dmsFile, com.google.api.services.drive.model.File googleDrive, String[] account) {

    // root folder
    if (dmsFile.getIsDirectory() && dmsFile.getParent() == null) {
      googleDrive = rootFolder(dmsFile, googleDrive, account);
    }

    // folder inside root folder
    if (dmsFile.getIsDirectory() && dmsFile.getParent() != null) {
      googleDrive = folderInsideFolder(dmsFile, googleDrive, account);
    }

    // file inside folder
    if (!dmsFile.getIsDirectory() && dmsFile.getParent() != null) {
      googleDrive = fileInsideFolder(dmsFile, googleDrive, account);
    }

    // file outside any directory
    if (!dmsFile.getIsDirectory() && dmsFile.getParent() == null) {
      googleDrive = fileWithoutFolder(dmsFile, googleDrive, account);
    }

    return googleDrive;
  }

  @Override
  public com.google.api.services.drive.model.File rootFolder(
      DMSFile dmsFile, com.google.api.services.drive.model.File googleDrive, String[] account) {

    File fileMetadata = new File();

    fileMetadata.setName(dmsFile.getFileName());
    fileMetadata.setMimeType("application/vnd.google-apps.folder");
    try {
      if (account[0] != null) {
        googleDrive = drive.files().update(account[0], googleDrive).execute();
      }
      googleDrive = drive.files().create(fileMetadata).setFields("id").execute();
    } catch (IOException e) {
      LOG.trace(e.getMessage(), e);
    }
    return googleDrive;
  }

  @Override
  public com.google.api.services.drive.model.File folderInsideFolder(
      DMSFile dmsFile, com.google.api.services.drive.model.File googleDrive, String[] account) {

    String parentGoogleDriveId = null;
    File fileMetadata = new File();

    parentGoogleDriveId = findParentFolder(dmsFile);
    LOG.debug("Parent id:{}", parentGoogleDriveId);
    fileMetadata.setName(dmsFile.getFileName());
    fileMetadata.setMimeType("application/vnd.google-apps.folder");
    fileMetadata.setParents(Collections.singletonList(parentGoogleDriveId));
    try {
      if (account[0] != null) {
        googleDrive = drive.files().update(account[0], googleDrive).execute();
      }
      googleDrive = drive.files().create(fileMetadata).setFields("id").execute();
    } catch (IOException e) {
      LOG.trace(e.getMessage(), e);
    }
    return googleDrive;
  }

  @Override
  public com.google.api.services.drive.model.File fileInsideFolder(
      DMSFile dmsFile, com.google.api.services.drive.model.File googleDrive, String[] account) {

    String parentGoogleDriveId = null;
    File fileMetadata = new File();

    parentGoogleDriveId = findParentFolder(dmsFile);
    LOG.debug("Parent id:{}", parentGoogleDriveId);
    fileMetadata.setName(dmsFile.getFileName());
    fileMetadata.setMimeType(dmsFile.getMetaFile().getFileType());
    fileMetadata.setParents(Collections.singletonList(parentGoogleDriveId));
    java.io.File file_path = MetaFiles.getPath(dmsFile.getMetaFile()).toFile();
    FileContent mediaContent = new FileContent(dmsFile.getMetaFile().getFileType(), file_path);
    try {
      if (account[0] != null) {
        googleDrive = drive.files().update(account[0], googleDrive).execute();
      }
      googleDrive =
          drive.files().create(fileMetadata, mediaContent).setFields("id, parents").execute();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return googleDrive;
  }

  @Override
  public com.google.api.services.drive.model.File fileWithoutFolder(
      DMSFile dmsFile, com.google.api.services.drive.model.File googleDrive, String[] account) {

    File fileMetadata = new File();

    fileMetadata.setName(dmsFile.getFileName());
    fileMetadata.setMimeType(dmsFile.getMetaFile().getFileType());
    java.io.File file_path = MetaFiles.getPath(dmsFile.getMetaFile()).toFile();
    FileContent mediaContent = new FileContent(dmsFile.getMetaFile().getFileType(), file_path);
    try {
      if (account[0] != null) {
        googleDrive = drive.files().update(account[0], googleDrive).execute();
      }
      googleDrive = drive.files().create(fileMetadata, mediaContent).execute();
    } catch (IOException e) {
      LOG.trace(e.getMessage(), e);
    }
    return googleDrive;
  }

  @Override
  public String findParentFolder(DMSFile dmsFile) {

    String parentGoogleDriveId = null;
    List<Long> inputs = new ArrayList<Long>();
    List<DriveGoogleAccount> list = driveGoogleAccountRepo.all().fetch();

    for (DriveGoogleAccount driveAccount : list) {
      if (dmsFile.getParent().getId() == driveAccount.getDms().getId()
          && driveAccount.getGoogleDriveId() != null) {
        inputs.add(driveAccount.getId());
      }
    }
    long recentAccountId = Collections.max(inputs);
    LOG.debug("recentAccountId: {}", recentAccountId);
    for (DriveGoogleAccount driveAccount : list) {
      if (driveAccount.getId() == recentAccountId) {
        parentGoogleDriveId = driveAccount.getGoogleDriveId();
      }
    }
    return parentGoogleDriveId;
  }
}
