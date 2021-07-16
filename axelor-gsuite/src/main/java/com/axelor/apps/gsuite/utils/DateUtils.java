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
package com.axelor.apps.gsuite.utils;

import com.google.api.client.util.DateTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateUtils {

  private static final DateTimeFormatter RFC3339_PATTERN =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.systemDefault());

  private DateUtils() {}

  public static LocalDateTime toLocalDateTime(DateTime dateTime) {
    LocalDateTime localDateTime =
        Instant.ofEpochMilli(dateTime.getValue()).atZone(ZoneId.systemDefault()).toLocalDateTime();
    if (dateTime.isDateOnly()) {
      return localDateTime.with(LocalTime.MIN);
    }
    return localDateTime;
  }

  public static DateTime toGoogleDateTime(LocalDateTime dateTime) {
    return DateTime.parseRfc3339(dateTime.format(RFC3339_PATTERN));
  }

  public static String toRfc3339(LocalDateTime dateTime) {
    return dateTime.atZone(ZoneId.systemDefault()).format(RFC3339_PATTERN);
  }
}
