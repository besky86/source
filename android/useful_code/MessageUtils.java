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

package com.android.mms.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Activity;
import android.app.AlarmManager;
import com.tita.app.AlertDialog;
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
import android.database.sqlite.SqliteWrapper;
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
import com.tita.widget.Toast;

import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.TempFileProvider;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.data.WorkingMessage;
import com.android.mms.model.MediaModel;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.transaction.MmsMessageSender;
import com.android.mms.transaction.SmsMessageSender;
import com.android.mms.transaction.TimerMsgService;
import com.android.mms.transaction.TransactionService;
import com.android.mms.util.AddressUtils;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.MultimediaMessagePdu;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.RetrieveConf;
import com.google.android.mms.pdu.SendReq;

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
    interface ResizeImageResultCallback {
        void onResizeResult(PduPart part, boolean append);
    }

    private static final String TAG = LogTag.TAG;
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

    public static String getMessageDetails(Context context, Cursor cursor, int size,String Names) {
        if (cursor == null) {
            return null;
        }

        if ("mms".equals(cursor.getString(SingleMsgListAdapter.COLUMN_MSG_TYPE))) {
            int type = cursor.getInt(SingleMsgListAdapter.COLUMN_MMS_MESSAGE_TYPE);
            switch (type) {
                case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND:
                    return getNotificationIndDetails(context, cursor);
                case PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF:
                case PduHeaders.MESSAGE_TYPE_SEND_REQ:
                    return getMultimediaMessageDetails(context, cursor, size,Names);
                default:
                    Log.w(TAG, "No details could be retrieved.");
                    return "";
            }
        } else {
            return getTextMessageDetails(context, cursor,Names);
        }
    }

    private static String getNotificationIndDetails(Context context, Cursor cursor) {
        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        long id = cursor.getLong(SingleMsgListAdapter.COLUMN_ID);
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, id);
        NotificationInd nInd;

        try {
            nInd = (NotificationInd) PduPersister.getPduPersister(
                    context).load(uri);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to load the message: " + uri, e);
            return context.getResources().getString(R.string.cannot_get_details);
        }

        // Message Type: Mms Notification.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.multimedia_notification));

        // From: ***
        String from = extractEncStr(context, nInd.getFrom());
        details.append('\n');
        details.append(res.getString(R.string.from_label));
        details.append(!TextUtils.isEmpty(from)? from:
                                 res.getString(R.string.hidden_sender_address));

        // Date: ***
        details.append('\n');
        details.append(res.getString(
                                R.string.expire_on,
                                MessageUtils.formatTimeStampString(
                                        context, nInd.getExpiry() * 1000L, 0,true)));

        // Subject: ***
        details.append('\n');
        details.append(res.getString(R.string.subject_label));

        EncodedStringValue subject = nInd.getSubject();
        if (subject != null) {
            details.append(subject.getString());
        }

        // Message class: Personal/Advertisement/Infomational/Auto
        details.append('\n');
        details.append(res.getString(R.string.message_class_label));
        //commented by hr
//        details.append(new String(nInd.getMessageClass()));
        //add by hr start
        String MessageClass = new String(nInd.getMessageClass());
        if(MessageClass.equals(MESSAGE_CLASS_PERSONAL_STR)){
        	String strShow = context.getResources().getString(R.string.personal);
        	details.append(strShow);
        }else if(MessageClass.equals(MESSAGE_CLASS_ADVERTISEMENT_STR)){
        	String strShow = context.getResources().getString(R.string.advertisement);
        	details.append(strShow);
        }else if(MessageClass.equals(MESSAGE_CLASS_INFORMATIONAL_STR)){
        	String strShow = context.getResources().getString(R.string.informational);
        	details.append(strShow);
        }else if(MessageClass.equals(MESSAGE_CLASS_AUTO_STR)){
        	String strShow = context.getResources().getString(R.string.auto);
        	details.append(strShow);
        }        
        //add by hr end

        // Message size: *** KB
        details.append('\n');
        details.append(res.getString(R.string.message_size_label));
        details.append(String.valueOf((nInd.getMessageSize() + 1023) / 1024));
        details.append(context.getString(R.string.kilobyte));

        return details.toString();
    }

    private static String getMultimediaMessageDetails(
            Context context, Cursor cursor, int size,String Names) {
        int type = cursor.getInt(SingleMsgListAdapter.COLUMN_MMS_MESSAGE_TYPE);
        if (type == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
            return getNotificationIndDetails(context, cursor);
        }

        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        long id = cursor.getLong(SingleMsgListAdapter.COLUMN_ID);
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, id);
        MultimediaMessagePdu msg;

        try {
            msg = (MultimediaMessagePdu) PduPersister.getPduPersister(
                    context).load(uri);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to load the message: " + uri, e);
            return context.getResources().getString(R.string.cannot_get_details);
        }

        // Message Type: Text message.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.multimedia_message));

        if (msg instanceof RetrieveConf) {
            // From: ***
            String from = extractEncStr(context, ((RetrieveConf) msg).getFrom());
            details.append('\n');
            details.append(res.getString(R.string.from_label));
            details.append(!TextUtils.isEmpty(from)? from:
                                  res.getString(R.string.hidden_sender_address));
        }

        // To: ***
        details.append('\n');
        details.append(res.getString(R.string.to_address_label));
        EncodedStringValue[] to = msg.getTo();
        if (to != null) {
            details.append(EncodedStringValue.concat(to));
        }
        else {
            Log.w(TAG, "recipient list is empty!");
        }


        // Bcc: ***
        if (msg instanceof SendReq) {
            EncodedStringValue[] values = ((SendReq) msg).getBcc();
            if ((values != null) && (values.length > 0)) {
                details.append('\n');
                details.append(res.getString(R.string.bcc_label));
                details.append(EncodedStringValue.concat(values));
            }
        }

        // Date: ***
        details.append('\n');
        int msgBox = cursor.getInt(SingleMsgListAdapter.COLUMN_MMS_MESSAGE_BOX);
        if (msgBox == Mms.MESSAGE_BOX_DRAFTS) {
            details.append(res.getString(R.string.saved_label));
        } else if (msgBox == Mms.MESSAGE_BOX_INBOX) {
            details.append(res.getString(R.string.received_label));
        } else {
            details.append(res.getString(R.string.sent_label));
        }

        details.append(MessageUtils.formatTimeStampString(
                context, msg.getDate() * 1000L,0, true));

        // Subject: ***
        details.append('\n');
        details.append(res.getString(R.string.subject_label));

        EncodedStringValue subject = msg.getSubject();
        if (subject != null) {
            String subStr = subject.getString();
            // Message size should include size of subject.
            size += subStr.length();
            details.append(subStr);
        }

        // Priority: High/Normal/Low
        details.append('\n');
        details.append(res.getString(R.string.priority_label));
        details.append(getPriorityDescription(context, msg.getPriority()));

        // Message size: *** KB
        details.append('\n');
        details.append(res.getString(R.string.message_size_label));
        details.append(size/1024 + 1);
        details.append(" KB");

        return details.toString();
    }

    private static String getTextMessageDetails(Context context, Cursor cursor,String Names) {
        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        // Message Type: Text message.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.text_message));

        // Address: ***
        details.append('\n');
        int smsType = cursor.getInt(SingleMsgListAdapter.COLUMN_SMS_TYPE);
        if (Sms.isOutgoingFolder(smsType)) {
            details.append(res.getString(R.string.to_address_label));
        } else {
            details.append(res.getString(R.string.from_label));
        }
//        Names
        String s = TextUtils.join("\n", Names.split(","));
        details.append(s);

        // Sent: ***
        if (smsType == Sms.MESSAGE_TYPE_INBOX) {
            long date_sent = cursor.getLong(SingleMsgListAdapter.COLUMN_SMS_SEND_DATE);
            if (date_sent > 0) {
                details.append('\n');
                details.append(res.getString(R.string.sent_label));
                details.append(MessageUtils.formatTimeStampString(context, date_sent, 0,true));
            }
        }
        
        // Received: ***
        details.append('\n');
        if (smsType == Sms.MESSAGE_TYPE_DRAFT) {
            details.append(res.getString(R.string.saved_label));
        } else if (smsType == Sms.MESSAGE_TYPE_INBOX) {
            details.append(res.getString(R.string.received_label));
        } else {
            details.append(res.getString(R.string.sent_label));
        }

        long date = cursor.getLong(SingleMsgListAdapter.COLUMN_SMS_DATE);
        details.append(MessageUtils.formatTimeStampString(context, date, 0,true));

        // Error code: ***
        int errorCode = cursor.getInt(SingleMsgListAdapter.COLUMN_SMS_ERROR_CODE);
        if (errorCode != 0) {
            details.append('\n')
                .append(res.getString(R.string.error_code_label))
                .append(errorCode);
        }

        return details.toString();
    }

    static private String getPriorityDescription(Context context, int PriorityValue) {
        Resources res = context.getResources();
        switch(PriorityValue) {
            case PduHeaders.PRIORITY_HIGH:
                return res.getString(R.string.priority_high);
            case PduHeaders.PRIORITY_LOW:
                return res.getString(R.string.priority_low);
            case PduHeaders.PRIORITY_NORMAL:
            default:
                return res.getString(R.string.priority_normal);
        }
    }

    public static int getAttachmentType(SlideshowModel model) {
        if (model == null) {
            return WorkingMessage.TEXT;
        }

        int numberOfSlides = model.size();
        if (numberOfSlides > 1) {
            return WorkingMessage.SLIDESHOW;
        } else if (numberOfSlides == 1) {
            // Only one slide in the slide-show.
            SlideModel slide = model.get(0);
            if (slide.hasVideo()) {
                return WorkingMessage.VIDEO;
            }

            if (slide.hasAudio() && slide.hasImage()) {
                return WorkingMessage.SLIDESHOW;
            }

            if (slide.hasAudio()) {
                return WorkingMessage.AUDIO;
            }

            if (slide.hasImage()) {
                return WorkingMessage.IMAGE;
            }

            if (slide.hasText()) {
                return WorkingMessage.TEXT;
            }
        }

        return WorkingMessage.TEXT;
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
    

    /**
     * @parameter recipientIds space-separated list of ids
     */
    public static String getRecipientsByIds(Context context, String recipientIds,
                                            boolean allowQuery) {
        String value = sRecipientAddress.get(recipientIds);
        if (value != null) {
            return value;
        }
        if (!TextUtils.isEmpty(recipientIds)) {
            StringBuilder addressBuf = extractIdsToAddresses(
                    context, recipientIds, allowQuery);
            if (addressBuf == null) {
                // temporary error?  Don't memoize.
                return "";
            }
            value = addressBuf.toString();
        } else {
            value = "";
        }
        sRecipientAddress.put(recipientIds, value);
        return value;
    }

    private static StringBuilder extractIdsToAddresses(Context context, String recipients,
                                                       boolean allowQuery) {
        StringBuilder addressBuf = new StringBuilder();
        String[] recipientIds = recipients.split(" ");
        boolean firstItem = true;
        for (String recipientId : recipientIds) {
            String value = sRecipientAddress.get(recipientId);

            if (value == null) {
                if (!allowQuery) {
                    // when allowQuery is false, if any value from sRecipientAddress.get() is null,
                    // return null for the whole thing. We don't want to stick partial result
                    // into sRecipientAddress for multiple recipient ids.
                    return null;
                }

                Uri uri = Uri.parse("content://mms-sms/canonical-address/" + recipientId);
                Cursor c = SqliteWrapper.query(context, context.getContentResolver(),
                                               uri, null, null, null, null);
                if (c != null) {
                    try {
                        if (c.moveToFirst()) {
                            value = c.getString(0);
                            sRecipientAddress.put(recipientId, value);
                        }
                    } finally {
                    	if(c!=null){
                    		c.close();
                    	}
                    }
                }
            }
            if (value == null) {
                continue;
            }
            if (firstItem) {
                firstItem = false;
            } else {
                addressBuf.append(",");
            }
            addressBuf.append(value);
        }

        return (addressBuf.length() == 0) ? null : addressBuf;
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

    public static void recordSound(Context context, int requestCode, long sizeLimit) {
        if (context instanceof Activity) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType(ContentType.AUDIO_AMR);
            intent.setClassName("com.android.soundrecorder",
                    "com.android.soundrecorder.SoundRecorder");
            intent.putExtra(android.provider.MediaStore.Audio.Media.EXTRA_MAX_BYTES, sizeLimit);

            ((Activity) context).startActivityForResult(intent, requestCode);
        }
    }

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

    public static void selectVideo(Context context, int requestCode) {
        selectMediaByType(context, requestCode, ContentType.VIDEO_UNSPECIFIED, true);
    }

    public static void selectImage(Context context, int requestCode) {
        selectMediaByType(context, requestCode, ContentType.IMAGE_UNSPECIFIED, false);
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

    public static void viewSimpleSlideshow(Context context, SlideshowModel slideshow) {
        if (!slideshow.isSimple()) {
            throw new IllegalArgumentException(
                    "viewSimpleSlideshow() called on a non-simple slideshow");
        }
        SlideModel slide = slideshow.get(0);
        MediaModel mm = null;
        if (slide.hasImage()) {
            mm = slide.getImage();
        } else if (slide.hasVideo()) {
            mm = slide.getVideo();
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra("SingleItemOnly", true); // So we don't see "surrounding" images in Gallery

        String contentType;
        if (mm.isDrmProtected()) {
            contentType = mm.getDrmObject().getContentType();
        } else {
            contentType = mm.getContentType();
        }
        intent.setDataAndType(mm.getUri(), contentType);
        context.startActivity(intent);
    }

    public static void showErrorDialog(Context context,
            String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        builder.setIcon(R.drawable.ic_sms_mms_not_delivered);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
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

    public static void resizeImageAsync(final Context context,
            final Uri imageUri, final Handler handler,
            final ResizeImageResultCallback cb,
            final boolean append) {

        // Show a progress toast if the resize hasn't finished
        // within one second.
        // Stash the runnable for showing it away so we can cancel
        // it later if the resize completes ahead of the deadline.
        final Runnable showProgress = new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, R.string.compressing, Toast.LENGTH_SHORT).show();
            }
        };
        // Schedule it for one second from now.
        handler.postDelayed(showProgress, 1000);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final PduPart part;
                try {
                    UriImage image = new UriImage(context, imageUri);
                    int widthLimit = MmsConfig.getMaxImageWidth();
                    int heightLimit = MmsConfig.getMaxImageHeight();
                    // In mms_config.xml, the max width has always been declared larger than the max
                    // height. Swap the width and height limits if necessary so we scale the picture
                    // as little as possible.
                    if (image.getHeight() > image.getWidth()) {
                        int temp = widthLimit;
                        widthLimit = heightLimit;
                        heightLimit = temp;
                    }

                    part = image.getResizedImageAsPart(
                        widthLimit,
                        heightLimit,
                        MmsConfig.getMaxMessageSize() - MESSAGE_OVERHEAD);
                } finally {
                    // Cancel pending show of the progress toast if necessary.
                    handler.removeCallbacks(showProgress);
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onResizeResult(part, append);
                    }
                });
            }
        }).start();
    }

    public static void showDiscardDraftConfirmDialog(Context context,
            OnClickListener listener) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.discard_message)
                .setMessage(R.string.discard_message_reason)
                .setPositiveButton(R.string.yes, listener)
                .setNegativeButton(R.string.no, null)
                .show();
    }

    public static String getLocalNumber() {
        if (null == sLocalNumber) {
            sLocalNumber = MmsApp.getApplication().getTelephonyManager().getLine1Number();
        }
        return sLocalNumber;
    }

    public static boolean isLocalNumber(String number) {
        if (number == null) {
            return false;
        }

        // we don't use Mms.isEmailAddress() because it is too strict for comparing addresses like
        // "foo+caf_=6505551212=tmomail.net@gmail.com", which is the 'from' address from a forwarded email
        // message from Gmail. We don't want to treat "foo+caf_=6505551212=tmomail.net@gmail.com" and
        // "6505551212" to be the same.
        if (number.indexOf('@') >= 0) {
            return false;
        }

        return PhoneNumberUtils.compare(number, getLocalNumber());
    }

    public static void handleReadReport(final Context context,
            final Collection<Long> threadIds,
            final int status,
            final Runnable callback) {
        StringBuilder selectionBuilder = new StringBuilder(Mms.MESSAGE_TYPE + " = "
                + PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF
                + " AND " + Mms.READ + " = 0"
                + " AND " + Mms.READ_REPORT + " = " + PduHeaders.VALUE_YES);

        String[] selectionArgs = null;
        if (threadIds != null) {
            String threadIdSelection = null;
            StringBuilder buf = new StringBuilder();
            selectionArgs = new String[threadIds.size()];
            int i = 0;

            for (long threadId : threadIds) {
                if (i > 0) {
                    buf.append(" OR ");
                }
                buf.append(Mms.THREAD_ID).append("=?");
                selectionArgs[i++] = Long.toString(threadId);
            }
            threadIdSelection = buf.toString();

            selectionBuilder.append(" AND (" + threadIdSelection + ")");
        }

        final Cursor c = SqliteWrapper.query(context, context.getContentResolver(),
                        Mms.Inbox.CONTENT_URI, new String[] {Mms._ID, Mms.MESSAGE_ID},
                        selectionBuilder.toString(), selectionArgs, null);

        if (c == null) {
            return;
        }

        final Map<String, String> map = new HashMap<String, String>();
        try {
            if (c.getCount() == 0) {
                if (callback != null) {
                    callback.run();
                }
                return;
            }

            while (c.moveToNext()) {
                Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, c.getLong(0));
                map.put(c.getString(1), AddressUtils.getFrom(context, uri));
            }
        } finally {
        	if(c!=null){
        		c.close();
        	}
        }

        OnClickListener positiveListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                for (final Map.Entry<String, String> entry : map.entrySet()) {
                    MmsMessageSender.sendReadRec(context, entry.getValue(),
                                                 entry.getKey(), status);
                }

                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        OnClickListener negativeListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        OnCancelListener cancelListener = new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        confirmReadReportDialog(context, positiveListener,
                                         negativeListener,
                                         cancelListener);
    }

    private static void confirmReadReportDialog(Context context,
            OnClickListener positiveListener, OnClickListener negativeListener,
            OnCancelListener cancelListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(true);
        builder.setTitle(R.string.confirm);
        builder.setMessage(R.string.message_send_read_report);
        builder.setPositiveButton(R.string.yes, positiveListener);
        builder.setNegativeButton(R.string.no, negativeListener);
        builder.setOnCancelListener(cancelListener);
        builder.show();
    }

    public static String extractEncStrFromCursor(Cursor cursor,
            int columnRawBytes, int columnCharset) {
        String rawBytes = cursor.getString(columnRawBytes);
        int charset = cursor.getInt(columnCharset);

        if (TextUtils.isEmpty(rawBytes)) {
            return "";
        } else if (charset == CharacterSets.ANY_CHARSET) {
            return rawBytes;
        } else {
            return new EncodedStringValue(charset, PduPersister.getBytes(rawBytes)).getString();
        }
    }

    public static boolean isHasAttachment(Cursor cursor,
            int columnRawBytes, int columnCharset) {
        String rawBytes = cursor.getString(columnRawBytes);
        int charset = cursor.getInt(columnCharset);

        if (!TextUtils.isEmpty(rawBytes) && charset == CharacterSets.ANY_CHARSET) {
            return false;
        } 
        return true;
    }
    
    private static String extractEncStr(Context context, EncodedStringValue value) {
        if (value != null) {
            return value.getString();
        } else {
            return "";
        }
    }

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
     * Play/view the message attachments.
     * TOOD: We need to save the draft before launching another activity to view the attachments.
     *       This is hacky though since we will do saveDraft twice and slow down the UI.
     *       We should pass the slideshow in intent extra to the view activity instead of
     *       asking it to read attachments from database.
     * @param context
     * @param msgUri the MMS message URI in database
     * @param slideshow the slideshow to save
     * @param persister the PDU persister for updating the database
     * @param sendReq the SendReq for updating the database
     */
    public static void viewMmsMessageAttachment(Context context, Uri msgUri,
            SlideshowModel slideshow,boolean needSync) {
        viewMmsMessageAttachment(context, msgUri, slideshow, 0,needSync);
    }

    private static void viewMmsMessageAttachment(Context context, Uri msgUri,
            SlideshowModel slideshow, int requestCode,boolean needSync) {
    	   
        boolean isSimple = (slideshow == null) ? false : slideshow.isSimple();
        if (isSimple) {
            // In attachment-editor mode, we only ever have one slide.
            MessageUtils.viewSimpleSlideshow(context, slideshow);
        } else {
            // If a slideshow was provided, save it to disk first.
            if (slideshow != null && needSync) {
                PduPersister persister = PduPersister.getPduPersister(context);
                try {
                    PduBody pb = slideshow.toPduBody();
                    persister.updateParts(msgUri, pb);
                    slideshow.sync(pb);
                } catch (MmsException e) {
                    Log.e(TAG, "Unable to save message for preview");
                    return;
                }
            }
            
            
            // Launch the slideshow activity to play/view.
            Intent intent = new Intent();
            intent.setData(msgUri);
            
            if(slideshow.size()>1)//幻灯片
            {
            	intent.setClass(context, SlideshowListActivity.class);
            }else{//音乐等
            	intent.setClass(context, SlideshowActivity.class);
            }
            if (slideshow.size()<2 && requestCode > 0 && context instanceof Activity) {
                ((Activity)context).startActivityForResult(intent, requestCode);
            } else {
                context.startActivity(intent);
            }
        }
    }

    public static void viewMmsMessageAttachment(Context context, WorkingMessage msg,
            int requestCode) {
        SlideshowModel slideshow = msg.getSlideshow();
        if (slideshow == null) {
            throw new IllegalStateException("msg.getSlideshow() == null");
        }
        if (slideshow.isSimple()) {
            MessageUtils.viewSimpleSlideshow(context, slideshow);
        } else {
            Uri uri = msg.saveAsMms(false);
            if (uri != null) {
                // Pass null for the slideshow paramater, otherwise viewMmsMessageAttachment
                // will persist the slideshow to disk again (we just did that above in saveAsMms)
//                viewMmsMessageAttachment(context, uri, null, requestCode);
            	viewMmsMessageAttachment(context, uri, slideshow,true);
            }
        }
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

    // An alias (or commonly called "nickname") is:
    // Nickname must begin with a letter.
    // Only letters a-z, numbers 0-9, or . are allowed in Nickname field.
    public static boolean isAlias(String string) {
        if (!MmsConfig.isAliasEnabled()) {
            return false;
        }

        int len = string == null ? 0 : string.length();

        if (len < MmsConfig.getAliasMinChars() || len > MmsConfig.getAliasMaxChars()) {
            return false;
        }

        if (!Character.isLetter(string.charAt(0))) {    // Nickname begins with a letter
            return false;
        }
        for (int i = 1; i < len; i++) {
            char c = string.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.')) {
                return false;
            }
        }

        return true;
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

    /**
     * Returns true if the address passed in is a valid MMS address.
     */
    public static boolean isValidMmsAddress(String address) {
        String retVal = parseMmsAddress(address);
        return (retVal != null);
    }

    /**
     * parse the input address to be a valid MMS address.
     * - if the address is an email address, leave it as is.
     * - if the address can be parsed into a valid MMS phone number, return the parsed number.
     * - if the address is a compliant alias address, leave it as is.
     */
    public static String parseMmsAddress(String address) {
        // if it's a valid Email address, use that.
        if (Mms.isEmailAddress(address)) {
            return address;
        }

        // if we are able to parse the address to a MMS compliant phone number, take that.
        String retVal = parsePhoneNumberForMms(address);
        if (retVal != null) {
            return retVal;
        }

        // if it's an alias compliant address, use that.
        if (isAlias(address)) {
            return address;
        }

        // it's not a valid MMS address, return null
        return null;
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
    
    public static boolean isNetworkConnected(Context context){   
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getMobileDataEnabled();  
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
    
	public static MessageItemMenuDialog getMenuDialogByUri(final Context mContext,final Handler handler,
			final String url) {
		MessageItemMenuDialog itemDialog = new MessageItemMenuDialog(mContext);

		if (url.startsWith("tel:"))// 类型为电话
		{
			final String phoneNum = PhoneNumberUtils.formatNumber(url
					.substring("tel:".length()));// 电话

			itemDialog.setTitle(phoneNum);
			//Contact c = Contact.get(phoneNum, false);
			//c.reload();
			//boolean isExist =c.existsInDatabase();
			Runnable dialRunnable = new Runnable() {
				public void run() {
					Uri uri = Uri.parse(url);
					Intent intent = new Intent(Intent.ACTION_CALL, uri);
					intent.putExtra(Browser.EXTRA_APPLICATION_ID,
							mContext.getPackageName());
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
					intent.putExtra("com.android.contacts.titacall", true);
					mContext.startActivity(intent);
				}
			};
			itemDialog.addMenuItem(R.string.menu_call, dialRunnable);

			Runnable editBoforeDialRunnable = new Runnable() {
				public void run() {
					Intent intent = new Intent("android.intent.action.DIAL");
					intent.setClassName("com.android.contacts",
							"com.android.contacts.DialtactsActivity");
					Uri uri = Uri.parse(url);
					intent.setData(uri);
					mContext.startActivity(intent);
				}
			};
			itemDialog.addMenuItem(R.string.edit_before_call,
					editBoforeDialRunnable);

			Runnable smsRunnable = new Runnable() {
				public void run() {
					Intent intent = new Intent(Intent.ACTION_SENDTO,
							Uri.fromParts("sms", phoneNum, null));
					intent.setClassName("com.android.mms",
							"com.android.mms.ui.HandleIntentMessageActivity");
					mContext.startActivity(intent);

				}
			};
			itemDialog.addMenuItem(R.string.send_msg, smsRunnable);

			Runnable clipRunnable = new Runnable() {
				public void run() {
					ClipboardManager clip = (ClipboardManager) mContext
							.getSystemService(Context.CLIPBOARD_SERVICE);
					clip.setText(phoneNum);
					handler.post(new Runnable() {
						public void run() {
							Toast.makeText(mContext,
									mContext.getString(R.string.copy_already),
									Toast.LENGTH_SHORT).show();
						}
					});
				}
			};
			itemDialog.addMenuItem(R.string.copy_text, clipRunnable);

			long personId;
			boolean isExist;
			try{
				Conversation c = Conversation.get(phoneNum);
				personId = Long.valueOf(c.getPersonIds());
			}catch (Exception e) {
				personId = 0;
			}
			isExist = personId > 0;
			final Uri contactUri= ContentUris.withAppendedId(Contacts.CONTENT_URI, personId);
			if (isExist)// 存在通讯录中
			{
				//final Uri contactUri = Contact.get(phoneNum, false).getUri();
				Runnable viewContactRunnable = new Runnable() {// 查看联系人
					public void run() {
						Intent intent = new Intent(Intent.ACTION_VIEW, contactUri);
		                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		                intent.setComponent(new ComponentName("com.android.contacts", "com.android.contacts.activities.ContactDetailActivity"));
		                mContext.startActivity(intent);
					}
				};
				itemDialog.addMenuItem(R.string.menu_view_contact,
						viewContactRunnable);

			} else {// 不存在通讯录中
				Runnable newContactRunnable = new Runnable() {// 新建到联系人
					public void run() {
						Intent intent = SingleMessageActivity
								.newContactIntent(phoneNum);
						mContext.startActivity(intent);
					}
				};
				itemDialog.addMenuItem(R.string.new_contact,
						newContactRunnable);
				
				Runnable addContactRunnable = new Runnable() {// 添加到联系人
					public void run() {
						Intent intent = SingleMessageActivity
								.addContactIntent(phoneNum);
						mContext.startActivity(intent);
					}
				};
				itemDialog.addMenuItem(R.string.menu_add_to_contacts,
						addContactRunnable);
			}

		} else if (url.startsWith("float:"))// 类型float数字
		{
			final String floatNumber = url.substring("float:".length());
			itemDialog.setTitle(floatNumber);
			Runnable clipRunnable = new Runnable() {
				public void run() {
					ClipboardManager clip = (ClipboardManager) mContext
							.getSystemService(Context.CLIPBOARD_SERVICE);
					clip.setText(floatNumber);
					handler.post(new Runnable() {
						public void run() {
							Toast.makeText(mContext,
									mContext.getString(R.string.copy_already),
									Toast.LENGTH_SHORT).show();
						}
					});
				}
			};
			itemDialog.addMenuItem(R.string.copy_text, clipRunnable);
		} else if (url.startsWith("geo:"))// 地图
		{
			itemDialog.setTitle(url);
			Runnable openRunnable = new Runnable() {
				public void run() {
					Uri uri = Uri.parse(url);
					Intent intent = new Intent(Intent.ACTION_VIEW, uri);
					intent.putExtra(Browser.EXTRA_APPLICATION_ID,
							mContext.getPackageName());
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
					mContext.startActivity(intent);
				}
			};
			itemDialog.addMenuItem(R.string.sim_view, openRunnable);
		} else {// 默认当做网页来处理
			final String webUrl = url;
			itemDialog.setTitle(webUrl);
			Runnable openRunnable = new Runnable() {
				public void run() {
					Uri uri = Uri.parse(url);
					Intent intent = new Intent(Intent.ACTION_VIEW, uri);
					intent.putExtra(Browser.EXTRA_APPLICATION_ID,
							mContext.getPackageName());
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
					mContext.startActivity(intent);
				}
			};
			itemDialog.addMenuItem(R.string.open_url, openRunnable);

			Runnable clipRunnable = new Runnable() {
				public void run() {
					ClipboardManager clip = (ClipboardManager) mContext
							.getSystemService(Context.CLIPBOARD_SERVICE);
					clip.setText(webUrl);

					handler.post(new Runnable() {
						public void run() {
							Toast.makeText(mContext,
									mContext.getString(R.string.copy_already),
									Toast.LENGTH_SHORT).show();
						}
					});
				}
			};
			itemDialog.addMenuItem(R.string.copy_text, clipRunnable);
		}
		return itemDialog;
	}
	
	public static MessageItemMenuDialog getTimerMsgMenuDialog(
			final Context context, final MessageItem messageItem) {
		MessageItemMenuDialog itemDialog = new MessageItemMenuDialog(context);



		String currentDate = (String) MessageUtils
				.getRelativeTimeSpanString(context, messageItem.getTime(),
						DateUtils.HOUR_IN_MILLIS, false);
		StringBuilder sbTitle = new StringBuilder();
		sbTitle.append(currentDate);
		sbTitle.append(" ");
		sbTitle.append(MessageUtils.formatDayTimeStampString(context,
				messageItem.mDate));

		String title = String.format(
				context.getString(R.string.timer_msg_send_date), sbTitle);
		itemDialog.setTitle(title);

		Runnable reSetTimerRunnable = new Runnable() {
			public void run() {
				ArrayList<String> msgUriList;
				if (messageItem instanceof GroupMessageItem) {
					GroupMessageItem groupMessageItem = (GroupMessageItem) messageItem;
					msgUriList = groupMessageItem.getMsgUris();
				} else {
					msgUriList = messageItem.getMsgUris();
				}
				Intent intent = new Intent(context, DateTimeActivity.class);
				intent.putExtra("time", messageItem.mDate);
				intent.putStringArrayListExtra("uri", msgUriList);
				intent.putExtra("msg_type",
						messageItem.isMms() ? DateTimeActivity.MSG_TYPE_MMS
								: DateTimeActivity.MSG_TYPE_SMS);
				context.startActivity(intent);
			}
		};
		itemDialog.addMenuItem(R.string.changed_date, reSetTimerRunnable);

		Runnable sendRunnable = new Runnable() {
			public void run() {
				if (messageItem.isMms()) {
					TimerMsgService.updateMmsMsgStatus(context,
							messageItem.mMessageUri);
					Intent service = new Intent(
							TransactionService.ACTION_ONALARM, null, context,
							TransactionService.class);
					context.startService(service);
					TimerMsgService.cancelTimerMessage(context,ContentUris.withAppendedId(Uri.parse("content://mms"), messageItem.mMsgId),DateTimeActivity.MSG_TYPE_MMS);
				} else {
					ArrayList<String> msgIdList;
					ArrayList<Uri> uriList = new ArrayList<Uri>();
					
					if(messageItem instanceof GroupMessageItem){
						GroupMessageItem groupMessageItem = (GroupMessageItem)messageItem;
						msgIdList = groupMessageItem.getMsgIds();
					}else{
						msgIdList = messageItem.getMsgIds();
					}
					
					long time = System.currentTimeMillis();
					
					for (String msgId : msgIdList) {
						long smsId = Long.valueOf(msgId);
						Uri uri = ContentUris.withAppendedId(
								Sms.CONTENT_URI, smsId);
						uriList.add(uri);
						
						ContentValues values = new ContentValues();
						values.put("date", time);
						values.put("timer",0);
						context.getContentResolver()
								.update(uri, values, null, null);
						TimerMsgService.cancelTimerMessage(context,ContentUris.withAppendedId(Uri.parse("content://sms"), smsId),DateTimeActivity.MSG_TYPE_SMS);
					}
					
					SmsMessageSender.reSendMessageList(context,
							uriList);
				}
			}
		};

		itemDialog.addMenuItem(R.string.send_immediately, sendRunnable);
		return itemDialog;
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
