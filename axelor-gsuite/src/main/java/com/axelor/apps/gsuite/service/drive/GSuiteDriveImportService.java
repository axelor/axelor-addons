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

import com.axelor.apps.gsuite.db.DriveGoogleAccount;
import com.axelor.apps.message.db.EmailAccount;
import com.axelor.dms.db.DMSFile;
import com.axelor.exception.AxelorException;
import com.google.api.services.drive.model.File;
import java.util.List;

public interface GSuiteDriveImportService {

  EmailAccount sync(EmailAccount emailAccount) throws AxelorException;

  void syncDocs(EmailAccount emailAccount) throws AxelorException;

  void createFolders(List<File> folders, EmailAccount emailAccount);

  void createDriveGoogleAccount(
      EmailAccount emailAccount, DMSFile dmsFile, File file, DriveGoogleAccount driveGoogleAccount);

  void createFiles(List<File> files, EmailAccount emailAccount) throws AxelorException;

  void updateParent(List<File> allData, EmailAccount emailAccount);
}
