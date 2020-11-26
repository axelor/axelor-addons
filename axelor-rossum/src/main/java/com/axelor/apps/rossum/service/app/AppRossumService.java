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
package com.axelor.apps.rossum.service.app;

import com.axelor.apps.base.db.AppRossum;
import com.axelor.apps.rossum.db.Queue;
import com.axelor.exception.AxelorException;
import com.axelor.meta.db.MetaFile;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import wslite.json.JSONException;

public interface AppRossumService {

  public static final String API_URL = "https://api.elis.rossum.ai";
  public CloseableHttpClient httpClient = HttpClients.createDefault();

  public AppRossum getAppRossum();

  public void login(AppRossum appRossum) throws IOException, JSONException, AxelorException;

  public void reset(AppRossum appRossum);

  public Map<MetaFile, Pair<String, File>> extractInvoiceDataMetaFile(
      List<MetaFile> metaFileList, Integer timeout, Queue queue, String exportTypeSelect)
      throws AxelorException, IOException, InterruptedException, JSONException;
}
