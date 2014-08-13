/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.lei.myutils.R;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.PorterDuff.Mode;
import android.media.CamcorderProfile;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.Browser;
import android.provider.MediaStore;
import android.provider.ContactsContract.Contacts;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.telephony.PhoneNumberUtils;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.URLSpan;
import android.util.Log;


/**
 * An utility class for managing messages.
 */
public class MessageUtils {
	//add by hr start
	public static final String MESSAGE_CLASS_PERSONAL_STR = "personal";
    public static final String MESSAGE_CLASS_ADVERTISEMENT_STR = "advertisement";
    public static final String MESSAGE_CLASS_INFORMATIONAL_STR = "informational";
    public static final String MESSAGE_CLASS_AUTO_STR = "auto";
	//add by hr end

    private static final String TAG = "MessageUtils";
    private static String sLocalNumber;

    // Cache of both groups of space-separated ids to their full
    // comma-separated display names, as well as individual ids to
    // display names.
    // TODO: is it possible for canonical address ID keys to be
    // re-used?  SQLite does reuse IDs on NULL id_ insert, but does
    // anything ever delete from the mmssms.db canonical_addresses
    // table?  Nothing that I could find.
    private static final Map<String, String> sRecipientAddress =
            new ConcurrentHashMap<String, String>(20 /* initial capacity */);


    /**
     * MMS address parsing data structures
     */
    // allowable phone number separators
    private static final char[] NUMERIC_CHARS_SUGAR = {
        '-', '.', ',', '(', ')', ' ', '/', '\\', '*', '#', '+'
    };

    private static HashMap numericSugarMap = new HashMap (NUMERIC_CHARS_SUGAR.length);

    static {
        for (int i = 0; i < NUMERIC_CHARS_SUGAR.length; i++) {
            numericSugarMap.put(NUMERIC_CHARS_SUGAR[i], NUMERIC_CHARS_SUGAR[i]);
        }
    }


    private MessageUtils() {
        // Forbidden being instantiated.
    }







    public static String formatTimeStampString(Context context, long when) {
        Time now = new Time();
        now.setToNow();
        now.toMillis(false);
        
        return (String)getRelativeTimeSpanString(context,when,DateUtils.SECOND_IN_MILLIS,true);//显示方式为：刚刚 多少分钟前...
    }

    /**
     * 一天内时间显示格式
     * 
     */
    public static String formatDayTimeStampString(Context context, long when) {
        int format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT |
                           DateUtils.FORMAT_ABBREV_ALL |
                           DateUtils.FORMAT_CAP_AMPM;

            format_flags |= DateUtils.FORMAT_SHOW_TIME;

        return DateUtils.formatDateTime(context, when, format_flags);
    }
    
    
    public static String formatTimeStampString(Context context, long when,int customFormatFlags, boolean fullFormat) {
    	
    	int format_flags = 0;
    	if(customFormatFlags==0)//为0 表示使用本默认的标志
    	{
    		Time then = new Time();
	        then.set(when);
	        Time now = new Time();
	        now.setToNow();
        

	        // Basic settings for formatDateTime() we want for all cases.
	        format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT |
	                           DateUtils.FORMAT_ABBREV_ALL |
	                           DateUtils.FORMAT_CAP_AMPM;
	
	        
	        // If the message is from a different year, show the date and year.
	        if (then.year != now.year) {
	            format_flags |= DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE;
	        } else if (then.yearDay != now.yearDay) {
	            // If it is from a different day than today, show only the date.
	            format_flags |= DateUtils.FORMAT_SHOW_DATE;
	        } else {
	            // Otherwise, if the message is from today, show the time.
	            format_flags |= DateUtils.FORMAT_SHOW_TIME;
	        }
	
	        // If the caller has asked for full details, make sure to show the date
	        // and time no matter what we've determined above (but still make showing
	        // the year only happen if it is a different year from today).
	        if (fullFormat) {
	            format_flags |= (DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
	        }
    	}else{
    		format_flags = customFormatFlags;
    	}

        return DateUtils.formatDateTime(context, when, format_flags).replace(" ", "");
    }

    public static CharSequence getRelativeTimeSpanString(Context context,long startTime,long minResolution,boolean showExtertime) {
        return getRelativeTimeSpanString(context,startTime, System.currentTimeMillis(), minResolution,showExtertime);
    }

    /**
     * 
     * @param context
     * @param time
     * @param now
     * @param minResolution
     * @param showExtertime
     * @return
     */
    public static CharSequence getRelativeTimeSpanString(Context context,long time, long now, long minResolution,boolean showExtertime) {
        Resources r = context.getResources();
        
        boolean past = (now >= time);
        long duration = Math.abs(now - time);

        int resId=0;
        long count=0;
        String format="";
        
        if (duration < DateUtils.MINUTE_IN_MILLIS && minResolution < DateUtils.MINUTE_IN_MILLIS) {//一分钟以内显示刚刚
            return context.getString(R.string.now);
        } 
        
        if (duration < DateUtils.HOUR_IN_MILLIS && minResolution < DateUtils.HOUR_IN_MILLIS) {//小于1个小时，显示多少分钟以内
            count = duration / DateUtils.MINUTE_IN_MILLIS;
            if (past) {
                    resId = R.plurals.num_minutes_ago;
            } else {
                    resId = R.plurals.in_num_minutes;
            }
        	format = r.getQuantityString(resId, (int) count);
        	return String.format(format, count).replace(" ", "");
        } 

        if (duration < DateUtils.DAY_IN_MILLIS && minResolution < DateUtils.DAY_IN_MILLIS) {//小于24小时
        	
        	boolean toDay = getDate(context,now).equals(getDate(context,time));//是否与现在时间同一天
        	
        	StringBuilder date = new StringBuilder();
        	String strTime = formatDayTimeStampString(context,time);
        	
        	if(toDay){//今天
        		if(showExtertime){
        			date.append(strTime);
        		}
        		else{
            		date.append(context.getString(R.string.today));
        		}
        	}else if(past){//昨天 XX点
        		date.append(context.getString(R.string.yesterday));
        	}else{
        		date.append(context.getString(R.string.tomorrow));
        	}
        	
        	return date.toString();
        }
        
        
        if (duration < 2*DateUtils.DAY_IN_MILLIS && minResolution < DateUtils.DAY_IN_MILLIS) {//小于48小时显示昨天或明天
        	StringBuilder date = new StringBuilder();
            if (past) {//可能出现  昨天**点 或 前天**点
            	boolean yesterday = getDate(context,now-DateUtils.DAY_IN_MILLIS).equals(getDate(context,time));//是否是昨天
            	
            	if(yesterday){
            		date.append(context.getString(R.string.yesterday));
            	}else{
            		date.append(context.getString(R.string.bef_yesterday));
            	}
            } else {//可能出现  明天**点 或 后天**点
            	boolean tomorrow = getDate(context,now+DateUtils.DAY_IN_MILLIS).equals(getDate(context,time));//是否是明天
            	
            	if(tomorrow){
            		date.append(context.getString(R.string.tomorrow));
            	}else{
            		date.append(context.getString(R.string.after_tomorrow));
            	}
            }
            return date.toString();
        }
        
        if (duration < 3*DateUtils.DAY_IN_MILLIS  && minResolution < DateUtils.DAY_IN_MILLIS ) {//小于72小时，可能出现前天**点 及 后天**点
        	StringBuilder date = new StringBuilder();
            if (past) {
            	boolean bef_yesterday = getDate(context,now-2*DateUtils.DAY_IN_MILLIS).equals(getDate(context,time));//是否是前天
            	
            	if(bef_yesterday){//前天
            		date.append(context.getString(R.string.bef_yesterday));
            	}else{//比前天更早的时间，显示具体日期就可         
            		date.append(formatTimeStampString(context, time,0, false));
            	}
            	
            } else {
            	boolean after_tomorrow = getDate(context,now+2*DateUtils.DAY_IN_MILLIS).equals(getDate(context,time));//是否是后天
            	
            	if(after_tomorrow){
            		date.append(context.getString(R.string.after_tomorrow));
            	}else{//比后天更晚的时间，显示具体日期就可
            		date.append(formatTimeStampString(context, time, 0,false));
            	}
            }
            return date.toString();
        } else {//其它的显示具体日期了
        	return formatTimeStampString(context, time,0,false);
        }
    }
    
    public static String getDate(Context context,long when){//用于每条消息的标题头部，显示年月日
        
        int format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT |
        DateUtils.FORMAT_ABBREV_ALL;
        
        format_flags |= DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE;
        
        return DateUtils.formatDateTime(context,when,format_flags);
    }
    



    public static void selectAudio(Context context, int requestCode) {
        if (context instanceof Activity) {
        	
        	/**
        	 * changed by alanhuang 2012-01-11
        	 */
        	Intent intent = new Intent("android.intent.action.PICK");
        	Uri uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI; // the media uri refer the mobile media provider
        	intent.setDataAndType(uri, "vnd.android.cursor.dir/audio");
        	((Activity) context).startActivityForResult(intent, requestCode);
        	
        	/**
        	 * end
        	 */
        }
    }

/*    public static void recordSound(Context context, int requestCode, long sizeLimit) {
        if (context instanceof Activity) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType(ContentType.AUDIO_AMR);
            intent.setClassName("com.android.soundrecorder",
                    "com.android.soundrecorder.SoundRecorder");
            intent.putExtra(android.provider.MediaStore.Audio.Media.EXTRA_MAX_BYTES, sizeLimit);

            ((Activity) context).startActivityForResult(intent, requestCode);
        }
    }*/

    public static void recordVideo(Context context, int requestCode, long sizeLimit) {
        if (context instanceof Activity) {
            int durationLimit = getVideoCaptureDurationLimit();
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
            intent.putExtra("android.intent.extra.sizeLimit", sizeLimit);
            intent.putExtra("android.intent.extra.durationLimit", durationLimit);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, TempFileProvider.SCRAP_CONTENT_URI);

            ((Activity) context).startActivityForResult(intent, requestCode);
        }
    }

    private static int getVideoCaptureDurationLimit() {
        CamcorderProfile camcorder = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
        return camcorder == null ? 0 : camcorder.duration;
    }


    private static void selectMediaByType(
            Context context, int requestCode, String contentType, boolean localFilesOnly) {
         if (context instanceof Activity) {

            Intent innerIntent = new Intent(Intent.ACTION_GET_CONTENT);

            innerIntent.setType(contentType);
            if (localFilesOnly) {
                innerIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            }

            Intent wrapperIntent = Intent.createChooser(innerIntent, null);

            ((Activity) context).startActivityForResult(wrapperIntent, requestCode);
        }
    }



    /**
     * The quality parameter which is used to compress JPEG images.
     */
    public static final int IMAGE_COMPRESSION_QUALITY = 95;
    /**
     * The minimum quality parameter which is used to compress JPEG images.
     */
    public static final int MINIMUM_IMAGE_COMPRESSION_QUALITY = 50;

    /**
     * Message overhead that reduces the maximum image byte size.
     * 5000 is a realistic overhead number that allows for user to also include
     * a small MIDI file or a couple pages of text along with the picture.
     */
    public static final int MESSAGE_OVERHEAD = 5000;




    

	public static ArrayList<String> extractUris(URLSpan[] spans) {
		int size = spans.length;
		ArrayList<String> accumulator = new ArrayList<String>();

		for (int i = 0; i < size; i++) {
			String url = spans[i].getURL();

			if (!accumulator.contains(url)) {
				accumulator.add(spans[i].getURL());
			}
		}
		return accumulator;
	}


    /**
     * Debugging
     */
    public static void writeHprofDataToFile(){
        String filename = Environment.getExternalStorageDirectory() + "/mms_oom_hprof_data";
        try {
            android.os.Debug.dumpHprofData(filename);
            Log.i(TAG, "##### written hprof data to " + filename);
        } catch (IOException ex) {
            Log.e(TAG, "writeHprofDataToFile: caught " + ex);
        }
    }


    /**
     * Given a phone number, return the string without syntactic sugar, meaning parens,
     * spaces, slashes, dots, dashes, etc. If the input string contains non-numeric
     * non-punctuation characters, return null.
     */
    private static String parsePhoneNumberForMms(String address) {
        StringBuilder builder = new StringBuilder();
        int len = address.length();

        for (int i = 0; i < len; i++) {
            char c = address.charAt(i);

            // accept the first '+' in the address
            if (c == '+' && builder.length() == 0) {
                builder.append(c);
                continue;
            }

            if (Character.isDigit(c)) {
                builder.append(c);
                continue;
            }

            if (numericSugarMap.get(c) == null) {
                return null;
            }
        }
        return builder.toString();
    }


    private static void log(String msg) {
        Log.d(TAG, "[MsgUtils] " + msg);
    }
    
    /**
     * 
     * @param iconSize
     * @return
     */
    private static Bitmap createShortcutBitmap(int iconSize) {
        return Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
    }
    
    /**
     * 将图片根据指定尺寸进行缩放
     * add by alanhuang 2012-1-11
     * @param photo
     * @param iconSize
     * @return
     */
    public static Bitmap scaleToAppIconSize(Bitmap photo,int iconSize) {
    	// Setup the drawing classes
        Bitmap icon = createShortcutBitmap(iconSize);
        Canvas canvas = new Canvas(icon);
        
        // Copy in the photo
        Paint photoPaint = new Paint();
        photoPaint.setDither(true);
        photoPaint.setFilterBitmap(true);

        Rect src;
        int srcWidth = photo.getWidth();
        int srcHeight = photo.getHeight();
        int start, end;
        if (srcWidth > srcHeight) {
            start = (srcWidth - srcHeight) / 2;
            end = srcWidth - start;
            src = new Rect(start, 0, end, photo.getHeight());
        } else if (srcWidth < srcHeight) {
            start = (srcHeight - srcWidth) / 2;
            end = srcHeight - start;
            src = new Rect(0, start, srcWidth, end);
        } else {
            src = new Rect(0, 0, srcWidth, srcHeight);
        }
        Rect dst = new Rect(0,0, iconSize, iconSize);
        canvas.drawBitmap(photo, src, dst, photoPaint);

        return makeRoundIcon(icon);
    }
    
    public static Bitmap makeRoundIcon(Bitmap bitmap){
//    	return bitmap;
    	final float ICON_ROUND = 6.0f;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, w, h);
        final RectF rectF = new RectF(rect);
        paint.setAntiAlias(true);
        paint.setColor(color);
        
        Bitmap output = Bitmap.createBitmap(w, h, Config.ARGB_8888);
        Canvas canvas2 = new Canvas(output);
        canvas2.drawARGB(0, 0, 0, 0);
        canvas2.drawRoundRect(rectF, ICON_ROUND, ICON_ROUND, paint);
        paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        canvas2.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }
    
    
    public static byte[] generateGroupPhotoByte(int photoSize,int divWidth,int padding,ArrayList<Bitmap> bitmapList,Bitmap backgroud){
		if (bitmapList == null || bitmapList.size() <= 0) {
			//return scaleToAppIconSize(backgroud,size);   
			return null;
		}
		int width = photoSize/2 -divWidth;
		int hight =  width;
		Bitmap output = createShortcutBitmap(photoSize - (padding * 2));
    	Canvas canvas = new Canvas(output);
        Paint photoPaint = new Paint();
        photoPaint.setDither(true);
        photoPaint.setFilterBitmap(true);
        
        if(backgroud != null)
        	canvas.drawBitmap(scaleToAppIconSize(backgroud,photoSize- (padding * 2)), 0, 0, photoPaint);
        
		for (int i = 0; i < bitmapList.size() && i<4; i++) {
			if(bitmapList.get(i)!=null){
				if(i<2){
					canvas.save();
					canvas.drawBitmap(scaleToAppIconSize(bitmapList.get(i),width), (i%2)*width+(i%2)*divWidth+(i%2)*padding, 0, photoPaint);
					canvas.restore();
				}else{
					canvas.save();
					canvas.drawBitmap(scaleToAppIconSize(bitmapList.get(i),width), (i%2)*width+(i%2)*divWidth+(i%2)*padding, hight+divWidth, photoPaint);
					canvas.restore();
				}
			}
    	}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();     
		output.compress(Bitmap.CompressFormat.PNG, 100, baos);     
	    return baos.toByteArray();   

    }
    
    public static Bitmap getGroupBitmap(String address){
    	return Cache.getInstance().get(address);
    }
    
    public static void cacheGroupBitmap(String address,Bitmap bitmap){
    	 Cache.getInstance().put(address,bitmap);
    }
    
    public static void invalidateBitmapCache(){
   	 Cache.getInstance().removeAll();
   }
    
    /**
     * Private cache for the use of the various forms of Conversation.get.
     */
    public static class Cache {
        private static Cache sInstance = new Cache();
        static Cache getInstance() { return sInstance; }
        private final HashMap<String,Bitmap> mBitmapCache;
        private Cache() {
        	mBitmapCache = new  HashMap<String,Bitmap>(30);
        }

        static Bitmap get(String address) {
            synchronized (sInstance) {
            if(sInstance.mBitmapCache.containsKey(address))
            	{
            		return sInstance.mBitmapCache.get(address);
            	}
            }
            return null;
        }
        

        /**
         * Put the specified conversation in the cache.  The caller
         * should not place an already-existing conversation in the
         * cache, but rather update it in place.
         */
        static void put(String key,Bitmap c) {
            synchronized (sInstance) {
                // We update cache entries in place so people with long-
                // held references get updated.

                if (sInstance.mBitmapCache.containsKey(key)) {
                	return;
                }
                sInstance.mBitmapCache.put(key, c);
            }
        }
        
        static void removeAll(){
        	sInstance.mBitmapCache.clear();
        }
    }
        
	public static boolean IsUserNumber(String num){
		boolean re = false;
		if(num.length()==11)
		{
			if(num.startsWith("13")){
				re = true;
			}
			else if(num.startsWith("15")){
				re = true;
			}
			else if(num.startsWith("18")){
				re = true;
			}
		}
		return re;
	}
	
	public  static String GetNumber(String num){
		if(num!=null)
		{
		if (num.startsWith("+86"))
        {
			num = num.substring(3);
        }
        else if (num.startsWith("86")){
        	num = num.substring(2);
        }
		}
		else{
			num="";
		}
		return num;
	}
    
    public static int dip2px(Context context, float dipValue){
    	final float scale = context.getResources().getDisplayMetrics().density;
    	return (int)(dipValue * scale + 0.5f);
    	}
    
    public static boolean
    isISODigit (char c) {
        return c >= '0' && c <= '9';
    }
    
    public static String formatAddress(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        boolean countryNum = false;
        String  temp = new String(phoneNumber);
        
        if(phoneNumber.startsWith("+86"))
        {
        	phoneNumber = phoneNumber.substring(3);
			countryNum = true;
        }
        
        if(phoneNumber.contains("-")){
        	phoneNumber = phoneNumber.replace("-", "");
		}
        
        if(phoneNumber.contains(" ")){
        	phoneNumber = phoneNumber.replace(" ", "");
		}
		
        int len = phoneNumber.length();
        if(len == 11)
        {
	        StringBuilder ret = new StringBuilder();
	        char separator=' ';
	        
	        for (int i = 0; i < len; i++) {
	            char c = phoneNumber.charAt(i);
	            if (ret.length() == 3 || ret.length() == 8) {
	            	ret.append(separator);
	            }
	            if(isISODigit(c))
	                ret.append(c);
	        }
	        
	        if(ret.length() == 13){
	        	if(countryNum){
	            	ret.insert(0, "+86 ");
	            }
	            return ret.toString();
	        }
        }
    	return temp;
    }
    
	public static String Dismiss86(String phoneNum) {
		if (TextUtils.isEmpty(phoneNum)) {
			return phoneNum;
		}
		String strTmp = phoneNum;
		String strNum = strTmp.replaceAll("\\s*", "");
		strNum = strNum.replace("-", "");
		int index = -1;
		index = strNum.indexOf("+86");
		if (index >= 0) {
			strNum = strNum.substring(3);
		}
		return strNum;
	}
}
