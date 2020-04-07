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
}
