/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.gsuite.service.drive;

import com.axelor.apps.gsuite.service.GSuiteService;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.AxelorException;
import com.axelor.meta.MetaFiles;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteDriveExportServiceImpl implements GSuiteDriveExportService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static Drive drive;

  protected EmailAccountRepository emailAccountRepo;
  protected GSuiteService gSuiteService;
  protected DMSFileRepository dmsFileRepo;

  @Inject
  public GSuiteDriveExportServiceImpl(
      EmailAccountRepository emailAccountRepo,
      GSuiteService gSuiteService,
      DMSFileRepository dmsFileRepo) {
    this.emailAccountRepo = emailAccountRepo;
    this.gSuiteService = gSuiteService;
    this.dmsFileRepo = dmsFileRepo;
  }

  @Override
  @Transactional
  public EmailAccount sync(EmailAccount emailAccount) throws AxelorException {

    if (emailAccount == null) {
      return null;
    }

    LocalDateTime syncDate = emailAccount.getDocSyncToGoogleDate();
    LOG.debug("Last sync date: {}", syncDate);
    emailAccount = emailAccountRepo.find(emailAccount.getId());
    String accountName = emailAccount.getName();

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
        String googleDriveId = dmsFile.getGoogleDriveId();

        LOG.debug("Google Drive id: {}", googleDriveId);
        if (ObjectUtils.notEmpty(googleDriveId)) {
          continue;
        }

        drive = gSuiteService.getDrive(emailAccount.getId());
        googleDriveId =
            updateGoogleDrive(dmsFile, new String[] {googleDriveId, accountName}, false);

        dmsFile.setGoogleDriveId(googleDriveId);
        dmsFile.setEmailAccount(emailAccount);
      }
      emailAccount.setDocSyncToGoogleDate(LocalDateTime.now());
    } catch (IOException e) {
      emailAccount.setDocSyncToGoogleLog("\n" + ExceptionUtils.getStackTrace(e));
    }

    return emailAccountRepo.save(emailAccount);
  }

  @Override
  public String updateGoogleDrive(DMSFile dmsFile, String[] account, boolean remove)
      throws IOException, AxelorException {

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
    java.io.File filePath = MetaFiles.getPath(dmsFile.getMetaFile()).toFile();
    FileContent mediaContent = new FileContent(dmsFile.getMetaFile().getFileType(), filePath);
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
    java.io.File filePath = MetaFiles.getPath(dmsFile.getMetaFile()).toFile();
    FileContent mediaContent = new FileContent(dmsFile.getMetaFile().getFileType(), filePath);
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
    return dmsFile.getParent().getGoogleDriveId();
  }
}
