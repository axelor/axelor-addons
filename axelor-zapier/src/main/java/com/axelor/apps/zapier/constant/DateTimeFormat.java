/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.zapier.constant;

public interface DateTimeFormat {

  /*
   * YYYY => Weekly year count
   * yyyy => Ordinary year
   * MM => Month
   * mm => Minute dd => Monthly count (1-31)
   * DD => Early count (1-365)
   * hh => Hour (0-12)
   * HH => Hour(0-24)
   * ss => Second
   * SSS => Millisecond
   * a => AM/PM
   * z => General TimeZone e.g: EST
   * Z => RFC TimeZone (822) e.g: 0700
   */
  public static final String[] DATE_TIME_FORMATS = {
    "yyyy-MM-dd", // 2020-01-21 		(zapier converted)
    "yyyy/MM/dd", // 2020/01/21
    "yyyy:MM:dd", // 2020:01:21
    "yyyy MMM dd", // 2020 jan 21
    "dd-MM-yyyy", // 21-01-2020		(zapier converted)
    "dd/MM/yy", // 21/01/20			(zapier converted)
    "dd/MM/yyyy", // 21/01/2020 		(zapier converted)
    "dd-mm-yy", // 21-01-2020
    "dd:MM:yyyy", // 21:01:2020
    "DDD MM dd", // Sun Jan 22
    "dd MMM, yyyy", // 21 jan, 2020
    "dd MMM yyyy", // 21 jan 2020
    "dd, MMM yyyy", // 01, jan 2020
    "MM/dd/yyyy", // 01/21/2020 		(zapier converted)
    "MM-dd-yyyy", // 01-21-2020	 	(zapier converted)
    "MM/dd/yy", // 01/21/20	  		(zapier converted)
    "MMMM dd yyyy", // january 22 2020  (zapier converted)
    "MMM dd yyyy", // jan 22 2020		(zapier converted)
    "MM-dd-yy", // 01-21-20
    "MMM, dd-yyyy", // jan, 22-2020
    "MMM dd/yyyy", // jan, 22/2020
    "MMM dd, yyyy", // jan 22, 2020
    "MM dd yyyy", // 01 21 2020
    "MMMM dd yyyy HH:mm:ss", // January 22 2006 23:04:05 		(zapier converted)
    "DDD MMM dd HH:mm:ss Z YYYY", // Sun Jan 22 23:04:05 -0000 2006	(zapier Converted)
    "yyyy-MM-dd'T'HH:mm:ssZ", // 2006-01-22T23:04:05-0000			(zapier converted)
    "yyyy-MM-dd HH:mm:ss Z", // 2006-01-22 23:04:05 -0000 		(zapier converted)
    "yyyy-MM-dd'T'HH:mm:ss", // 2006-01-22T23:04:05
    "yyyy-MM-dd HH:mm:ss", // 2006-01-22 23:04:05
    "yyyy-MM-dd'T'HH:mm", // 2020-01-21T12:55
    "yyyy-MM-dd HH:mm", // 2006-01-22 23:04
    "yyyy-MM-dd HH:mma", // 2020-01-21 10:10AM
    "yyyy-MM-dd HH:mm a", // 2020-01-21 10:10 AM
    "yyyy-MM-dd'T'HH:mm:ss.SSS", // 2020-01-02T18:16:14.827
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", // 2019-12-20T06:48:22.064Z
    "yyyy/MM/dd'T'HH:mm:ssZ", // 2020/01/21T23:04:05-0000
    "yyyy/MM/dd HH:mm:ss Z", // 2020/01/21 23:04:05 -0000
    "yyyy/MM/dd'T'HH:mm:ss", // 2020/01/21T23:04:05
    "yyyy/MM/dd HH:mm:ss", // 2020/01/21 23:04:05
    "yyyy/MM/dd'T'HH:mm", // 2020/01/21T12:55
    "yyyy/MM/dd HH:mm", // 2020/01/21 23:04
    "yyyy/MM/dd HH:mma", // 2020/01/21 10:10AM
    "yyyy/MM/dd HH:mm a", // 2020/01/21 10:10 AM
    "yyyy/MM/dd'T'HH:mm:ss.SSS'Z'", // 2020/01/21T06:48:22.064Z
    "dd-MM-yyyy'T'HH:mm:ssZ", // 21-01-2020T23:04:05-0000
    "dd-MM-yyyy HH:mm:ss Z", // 21-01-2020 23:04:05 -0000
    "dd-MM-yyyy'T'HH:mm:ss", // 21-01-2020T23:04:05
    "dd-MM-yyyy HH:mm:ss", // 21-01-2020 23:04:05
    "dd-MM-yyyy'T'HH:mm", // 21-01-2020T12:55
    "dd-MM-yyyy HH:mm", // 21-01-2020 23:04
    "dd-MM-yyyy HH:mma", // 21-01-2020 10:10AM
    "dd-MM-yyyy HH:mm a", // 21-01-2020 10:10 AM
    "dd-MM-yyyy'T'HH:mm:ss.SSS'Z'", // 21-01-2020T06:48:22.064Z
    "dd/MM/yyyy'T'HH:mm:ssZ", // 21/01/2020T23:04:05-0000
    "dd/MM/yyyy HH:mm:ss Z", // 21/01/2020 23:04:05 -0000
    "dd/MM/yyyy'T'HH:mm:ss", // 21/01/2020T23:04:05
    "dd/MM/yyyy HH:mm:ss", // 21/01/2020 23:04:05
    "dd/MM/yyyy'T'HH:mm", // 21/01/2020T12:55
    "dd/MM/yyyy HH:mm", // 21/01/2020 23:04
    "dd/MM/yyyy HH:mma", // 21/01/2020 10:10AM
    "dd/MM/yyyy HH:mm a", // 21/01/2020 10:10 AM
    "dd/MM/yyyy'T'HH:mm:ss.SSS'Z'", // 21/01/2020T06:48:22.064Z
    "MM-dd-yyyy'T'HH:mm:ssZ", // 01-21-2020T23:04:05-0000
    "MM-dd-yyyy HH:mm:ss Z", // 01-21-2020 23:04:05 -0000
    "MM-dd-yyyy'T'HH:mm:ss", // 01-21-2020T23:04:05
    "MM-dd-yyyy HH:mm:ss", // 01-21-2020 23:04:05
    "MM-dd-yyyy'T'HH:mm", // 01-21-2020T12:55
    "MM-dd-yyyy HH:mm", // 01-21-2020 23:04
    "MM-dd-yyyy HH:mma", // 01-21-2020 10:10AM
    "MM-dd-yyyy HH:mm a", // 01-21-2020 10:10 AM
    "MM-dd-yyyy'T'HH:mm:ss.SSS'Z'", // 01-21-2020T06:48:22.064Z
    "MM/dd/yyyy'T'HH:mm:ssZ", // 01/21/2020T23:04:05-0000
    "MM/dd/yyyy HH:mm:ss Z", // 01/21/2020 23:04:05 -0000
    "MM/dd/yyyy'T'HH:mm:ss", // 01/21/2020T23:04:05
    "MM/dd/yyyy HH:mm:ss", // 01/21/2020 23:04:05
    "MM/dd/yyyy'T'HH:mm", // 01/21/2020T12:55
    "MM/dd/yyyy HH:mm", // 01/21/2020 23:04
    "MM/dd/yyyy HH:mma", // 01/21/2020 10:10AM
    "MM/dd/yyyy HH:mm a", // 01/21/2020 10:10 AM
    "MM/dd/yyyy'T'HH:mm:ss.SSS'Z'", // 01/21/2020T06:48:22.064Z
    "MMM dd, yyyy HH:mma", // jan 01, 2020 10:10AM
    "MMM dd, yyyy hh:mma", // jan 01, 2020	22:10AM
    "MMM dd, yyyy'T'HH:mm:ssZ", // jan 01, 2020T23:04:05-0000
    "MMM dd, yyyy HH:mm:ss Z", // jan 01, 2020 23:04:05 -0000
    "MMM dd, yyyy'T'HH:mm:ss", // jan 01, 2020T23:04:05
    "MMM dd, yyyy HH:mm:ss", // jan 01, 2020 23:04:05
    "MMM dd, yyyy'T'HH:mm", // jan 01, 2020T12:55
    "MMM dd, yyyy HH:mm", // jan 01, 2020 23:04
    "MMM dd, yyyy HH:mm a", // jan 01, 2020 10:10 AM
    "MMM dd, yyyy'T'HH:mm:ss.SSS'Z'" // jan 01, 2020T06:48:22.064Z
  };
}
