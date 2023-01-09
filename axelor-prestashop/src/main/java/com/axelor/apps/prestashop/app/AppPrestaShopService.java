/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2022 Axelor (<http://axelor.com>).
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
package com.axelor.apps.prestashop.app;

import com.axelor.studio.db.AppPrestashop;
import java.util.List;

public interface AppPrestaShopService {
  /**
   * Performs access rights check using the given configuration
   *
   * @param appConfig Configuration to check
   * @param errors List that'll be filled with error messages (missing permissions).
   * @param warnings List that'll be filled with warnings (potential issues on export/import)
   * @param info List that'll be filled with informational messages (extraneous access rights).
   */
  public void checkAccess(
      AppPrestashop appConfig,
      final List<String> errors,
      final List<String> warnings,
      final List<String> info);
}
