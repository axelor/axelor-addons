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
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteDriveImportServiceImpl implements GSuiteDriveImportService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().getClass());

  private static Drive drive;
  private EmailAccount emailAccount;
  private Map<String, List<String>> exportFormats;

  protected GSuiteService gSuiteService;
  protected EmailAccountRepository emailAccountRepo;
  protected DMSFileRepository dmsFileRepo;
  protected MetaFiles metaFiles;

  @Inject
  public GSuiteDriveImportServiceImpl(
      GSuiteService gSuiteService,
      EmailAccountRepository emailAccountRepo,
      DMSFileRepository dmsFileRepo,
      MetaFiles metaFiles) {
    this.gSuiteService = gSuiteService;
    this.emailAccountRepo = emailAccountRepo;
    this.dmsFileRepo = dmsFileRepo;
    this.metaFiles = metaFiles;
  }

  @Override
  @Transactional
  public EmailAccount sync(EmailAccount emailAccount) throws AxelorException {
    if (emailAccount == null) {
      return null;
    }
    this.emailAccount = emailAccount;
    LocalDateTime syncDate = LocalDateTime.now();
    log.debug("Last sync date: {}", syncDate);
    emailAccount = emailAccountRepo.find(emailAccount.getId());
    this.syncDocs(emailAccount);
    return emailAccountRepo.save(emailAccount);
  }

  @Override
  public void syncDocs(EmailAccount emailAccount) throws AxelorException {
    this.emailAccount = emailAccount;

    try {
      String nextPageToken = null;
      int totalFolders = 0;
      int totalFiles = 0;

      do {
        FileList result =
            getDrive()
                .files()
                .list()
                .setFields("nextPageToken, files(id,shared,name,parents,mimeType)")
                .setQ("trashed=false")
                .setPageSize(40)
                .setPageToken(nextPageToken)
                .execute();

        List<File> resultFileList = result.getFiles();
        List<File> folders = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (File file : resultFileList) {
          // exclude files shared with user
          if (file.getShared()) {
            continue;
          }
          if (StringUtils.containsIgnoreCase(
              file.getMimeType(), "application/vnd.google-apps.folder")) {
            folders.add(file);
          } else {
            files.add(file);
          }
        }

        this.createFolders(folders, emailAccount);
        this.createFiles(files, emailAccount);
        this.updateParent(resultFileList, emailAccount);

        totalFiles += files.size();
        totalFolders += folders.size();
        nextPageToken = result.getNextPageToken();
      } while (nextPageToken != null);

      log.debug("folders: {}", totalFolders);
      log.debug("files: {}", totalFiles);

    } catch (IOException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }
  }

  @Override
  @Transactional
  public void updateParent(List<File> allData, EmailAccount emailAccount) {
    for (File file : allData) {
      String googleDriveId = file.getId();
      DMSFile dmsFile = findByDriveIdAndAccount(googleDriveId, emailAccount);
      if (dmsFile != null
          && file.getParents() != null
          && findByDriveIdAndAccount(file.getParents().get(0), emailAccount) != null) {
        DMSFile parent = findByDriveIdAndAccount(file.getParents().get(0), emailAccount);
        dmsFile.setParent(parent);
        dmsFileRepo.save(dmsFile);
      }
    }
  }

  @Override
  @Transactional
  public void createFiles(List<File> files, EmailAccount emailAccount) throws AxelorException {
    for (File file : files) {

      try {
        DMSFile dmsFile = findByDriveIdAndAccount(file.getId(), emailAccount);
        if (dmsFile == null) {
          dmsFile = new DMSFile();
          dmsFile.setGoogleDriveId(file.getId());
          dmsFile.setEmailAccount(emailAccount);
        }
        dmsFile.setFileName(file.getName());
        dmsFile.setIsDirectory(false);
        MetaFile metaFile = null;
        java.io.File fileDownloaded =
            MetaFiles.createTempFile(file.getName(), file.getFileExtension()).toFile();
        getFileContent(file, fileDownloaded);
        metaFile = metaFiles.upload(fileDownloaded);
        dmsFile.setMetaFile(metaFile);

        if (file.getParents() != null
            && findByDriveIdAndAccount(file.getParents().get(0), emailAccount) != null) {
          DMSFile parent = findByDriveIdAndAccount(file.getParents().get(0), emailAccount);
          dmsFile.setParent(parent);
        }
        dmsFileRepo.save(dmsFile);
      } catch (IOException e) {
        log.error(e.getMessage());
        TraceBackService.trace(e);
      }
    }
  }

  @Override
  @Transactional
  public void createFolders(List<File> folders, EmailAccount emailAccount) {
    for (File file : folders) {
      DMSFile dmsFile = findByDriveIdAndAccount(file.getId(), emailAccount);
      if (dmsFile == null) {
        dmsFile = new DMSFile();
        dmsFile.setGoogleDriveId(file.getId());
        dmsFile.setEmailAccount(emailAccount);
      }
      dmsFile.setIsDirectory(true);
      dmsFile.setFileName(file.getName());
      dmsFileRepo.save(dmsFile);
    }
  }

  protected java.io.File getFileContent(File in, java.io.File out)
      throws AxelorException, IOException {

    try (OutputStream outStream = new FileOutputStream(out); ) {
      Files files = getDrive().files();
      String mimeType = in.getMimeType();

      if (StringUtils.containsIgnoreCase(mimeType, "application/vnd.google-apps.")) {
        Files.Export request = files.export(in.getId(), getExportFormat(mimeType));
        request.executeMediaAndDownloadTo(outStream);
      } else {
        Files.Get request = files.get(in.getId());
        request.executeMediaAndDownloadTo(outStream);
      }
    }
    return out;
  }

  protected Drive getDrive() throws AxelorException {
    return drive == null ? gSuiteService.getDrive(emailAccount.getId()) : drive;
  }

  protected String getExportFormat(String mimeType) throws IOException, AxelorException {
    String exportType = null;
    for (Entry<String, List<String>> entry : getExportFormats().entrySet()) {

      if (entry.getKey().equals(mimeType)) {
        List<String> v = entry.getValue();
        // TODO to return exact type instead of related random type
        exportType = v.get(0);
        break;
      }
    }
    return exportType;
  }

  protected Map<String, List<String>> getExportFormats() throws IOException, AxelorException {
    com.google.api.services.drive.Drive.About.Get get =
        getDrive().about().get().setFields("exportFormats");
    return exportFormats == null ? get.execute().getExportFormats() : exportFormats;
  }

  protected DMSFile findByDriveIdAndAccount(String googleDriveId, EmailAccount emailAccount) {
    return dmsFileRepo
        .all()
        .filter("self.googleDriveId = :googleDriveId AND self.emailAccount = :emailAccount")
        .bind("googleDriveId", googleDriveId)
        .bind("emailAccount", emailAccount)
        .fetchOne();
  }
}
