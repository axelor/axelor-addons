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

import com.axelor.apps.message.db.EmailAccount;
import com.axelor.dms.db.DMSFile;
import com.axelor.exception.AxelorException;
import com.google.api.services.drive.model.File;
import java.io.IOException;

public interface GSuiteDriveExportService {

  EmailAccount sync(EmailAccount emailAccount) throws AxelorException;

  String updateGoogleDrive(DMSFile dmsFile, String[] account, boolean remove)
      throws IOException, AxelorException;

  File extractDMSFile(DMSFile dmsFile, File googleDrive, String[] account);

  File rootFolder(DMSFile dmsFile, File googleDrive, String[] account);

  File folderInsideFolder(DMSFile dmsFile, File googleDrive, String[] account);

  File fileInsideFolder(DMSFile dmsFile, File googleDrive, String[] account);

  File fileWithoutFolder(DMSFile dmsFile, File googleDrive, String[] account);

  String findParentFolder(DMSFile dmsFile);
}
