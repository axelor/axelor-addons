package com.axelor.apps.gsuite.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {

  private StringUtils() {}

  public static List<String> parseEmails(String text) {
    List<String> emails = new ArrayList<>();
    final String EMAIL_PATTERN =
        "[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})";
    Pattern pattern = Pattern.compile(EMAIL_PATTERN);
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      emails.add(matcher.group().trim());
    }
    return emails;
  }
}
