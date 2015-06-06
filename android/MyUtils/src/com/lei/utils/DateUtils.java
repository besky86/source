/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lei.utils;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateFormat;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import com.lei.myutils.R;

/**
 * Utility methods for processing dates.
 */
public class DateUtils {
    public static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone("UTC");

    // All the SimpleDateFormats in this class use the UTC timezone
    public static final SimpleDateFormat NO_YEAR_DATE_FORMAT =
            new SimpleDateFormat("--MM-dd", Locale.US);
    public static final SimpleDateFormat FULL_DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    public static final SimpleDateFormat DATE_AND_TIME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    public static final SimpleDateFormat NO_YEAR_DATE_AND_TIME_FORMAT =
            new SimpleDateFormat("--MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

    // Variations of ISO 8601 date format.  Do not change the order - it does affect the
    // result in ambiguous cases.
    private static final SimpleDateFormat[] DATE_FORMATS = {
        FULL_DATE_FORMAT,
        DATE_AND_TIME_FORMAT,
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US),
        new SimpleDateFormat("yyyyMMdd", Locale.US),
        new SimpleDateFormat("yyyyMMdd'T'HHmmssSSS'Z'", Locale.US),
        new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US),
        new SimpleDateFormat("yyyyMMdd'T'HHmm'Z'", Locale.US),
    };

    private static final java.text.DateFormat FORMAT_WITHOUT_YEAR_MONTH_FIRST =
            new SimpleDateFormat("MMMM dd");

    private static final java.text.DateFormat FORMAT_WITHOUT_YEAR_DATE_FIRST =
            new SimpleDateFormat("dd MMMM");

    static {
        for (SimpleDateFormat format : DATE_FORMATS) {
            format.setLenient(true);
            format.setTimeZone(UTC_TIMEZONE);
        }
        NO_YEAR_DATE_FORMAT.setTimeZone(UTC_TIMEZONE);
        FORMAT_WITHOUT_YEAR_MONTH_FIRST.setTimeZone(UTC_TIMEZONE);
        FORMAT_WITHOUT_YEAR_DATE_FIRST.setTimeZone(UTC_TIMEZONE);
    }

    /**
     * Parses the supplied string to see if it looks like a date. If so,
     * returns the date.  Otherwise, returns null.
     */
    public static Date parseDate(String string) {
        ParsePosition parsePosition = new ParsePosition(0);
        for (int i = 0; i < DATE_FORMATS.length; i++) {
            SimpleDateFormat f = DATE_FORMATS[i];
            synchronized (f) {
                parsePosition.setIndex(0);
                Date date = f.parse(string, parsePosition);
                if (parsePosition.getIndex() == string.length()) {
                    return date;
                }
            }
        }
        return null;
    }

    /**
     * Parses the supplied string to see if it looks like a date. If so,
     * returns the same date in a cleaned-up format for the user.  Otherwise, returns
     * the supplied string unchanged.
     */
    public static String formatDate(Context context, String string) {
        if (string == null) {
            return null;
        }

        string = string.trim();
        if (string.length() == 0) {
            return string;
        }

        ParsePosition parsePosition = new ParsePosition(0);

        Date date;

        synchronized (NO_YEAR_DATE_FORMAT) {
            date = NO_YEAR_DATE_FORMAT.parse(string, parsePosition);
        }

        if (parsePosition.getIndex() == string.length()) {
            java.text.DateFormat outFormat = isMonthBeforeDate(context)
                    ? FORMAT_WITHOUT_YEAR_MONTH_FIRST
                    : FORMAT_WITHOUT_YEAR_DATE_FIRST;
            synchronized (outFormat) {
                return outFormat.format(date);
            }
        }

        for (int i = 0; i < DATE_FORMATS.length; i++) {
            SimpleDateFormat f = DATE_FORMATS[i];
            synchronized (f) {
                parsePosition.setIndex(0);
                date = f.parse(string, parsePosition);
                if (parsePosition.getIndex() == string.length()) {
                    java.text.DateFormat outFormat = DateFormat.getDateFormat(context);
                    outFormat.setTimeZone(UTC_TIMEZONE);
                    return outFormat.format(date);
                }
            }
        }
        return string;
    }

    private static boolean isMonthBeforeDate(Context context) {
        char[] dateFormatOrder = DateFormat.getDateFormatOrder(context);
        for (int i = 0; i < dateFormatOrder.length; i++) {
            if (dateFormatOrder[i] == DateFormat.DATE) {
                return false;
            }
            if (dateFormatOrder[i] == DateFormat.MONTH) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * @param context
     * @param date 要处理的时间
     * @return 处理后的时间String
     * @author froyohuang 2012.6.21
     */
	public static String getCalllogTimeString(Context context, long date) {
		return getCalllogTimeString(context, date, false);
	}

	/**
	 * @param context 
	 * @param date 要处理的时间
	 * @param hasTime 是否需要添加显示具体时间（如：联系人详情中的通话记录中会需要）
	 * @return 处理后的时间String
	 * @author froyohuang 2012.6.21
	 */
	public static String getCalllogTimeString(Context context, long date,
			boolean hasTime) {
		long now = System.currentTimeMillis();
		long duration = now - date;

		String dateText = "";
		// duration为负，应该系统时间被调回，直接返回系统处理的时间
		if (duration < 0) {
			//dateText = android.text.format.DateUtils.getRelativeTimeSpanString(
			//		date, now, android.text.format.DateUtils.MINUTE_IN_MILLIS,
			//		android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE)
			//		.toString();
			mSimpleDateFormat = new SimpleDateFormat(FORMAT_YYYYMMDD);
			dateText = mSimpleDateFormat.format(new Date(date));
		} else {
			int offset = dayOffset(now, date);
			if (offset == 0) { // 今天,显示具体时间
				hasTime = false; // 本日内的，会直接显示具体时间
				SimpleDateFormat sf = new SimpleDateFormat("HH:mm");
				dateText = sf.format(new Date(date));
			} else if (offset == 1) { // 昨天，显示为 昨天
				dateText = context.getResources().getString(R.string.yesterday);
			} else if(offset == -2){ //不在同一年
				//dateText = android.text.format.DateUtils.formatDateTime(
				//		context, date,
				//		android.text.format.DateUtils.FORMAT_SHOW_YEAR)
				//		.toString();
				mSimpleDateFormat = new SimpleDateFormat(FORMAT_YYYYMMDD);
				dateText = mSimpleDateFormat.format(new Date(date));
			} else { // 昨天前，直接显示日期 几月几日
				//dateText = android.text.format.DateUtils.formatDateTime(
				//		context, date,
				//		android.text.format.DateUtils.FORMAT_SHOW_DATE)
				//		.toString();
				mSimpleDateFormat = new SimpleDateFormat(FORMAT_MMDD);
				dateText = mSimpleDateFormat.format(new Date(date));
			}
		}
		
		dateText = dateText.replace(" ", "");

		if (hasTime) {
			SimpleDateFormat sf = new SimpleDateFormat("HH:mm");
			String timeText = sf.format(new Date(date));
			dateText = dateText + " " + timeText;
		}
		return dateText;
	}
	
	/**
	 * @param now
	 * @param time
	 * @return 0:同一天 ;1:昨天;-1:同一天与昨天以外;-2:不在同一年
	 * @author froyohuang 2012.6.21
	 */
	public static int dayOffset(long now, long time) {
		Date nowDate = new Date(now);
		Date timeDate = new Date(time);
		if (nowDate.getYear() == timeDate.getYear()) {
			if (nowDate.getMonth() == timeDate.getMonth()) {
				if (nowDate.getDate() == timeDate.getDate()) {
					return 0;
				} else if ((nowDate.getDate() - timeDate.getDate()) == 1) {
					return 1;
				}
			}
		}else if(nowDate.getYear() != timeDate.getYear()){
			return -2;
		}
		return -1;
	}
	
	public static final String FORMAT_YYYYMMDD = "yyyy-MM-dd";
	public static final String FORMAT_MMDD = "MM-dd";
	public static SimpleDateFormat mSimpleDateFormat ;
	
}
