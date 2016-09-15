package com.tsm_messenger.activities.control;

import android.app.Activity;
import android.content.res.Resources;

import com.tsm_messenger.activities.R;

import java.util.HashMap;

/**
 * **********************************************************************
 * <p/>
 * TELESENS CONFIDENTIAL
 * __________________
 * <p/>
 * [2014] Telesens International Limited
 * All Rights Reserved.
 * <p/>
 * NOTICE:  All information contained herein is, and remains
 * the property of Telesens International Limited and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Telesens International Limited
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Telesens International Limited.
 * <p/>
 */

public class TsmNotification {

    public static final int NEWMESSAGE_ID = 100000;
    public static final int NEWREQUEST_ID = 2;
    public static final int NEWANSWER_ID = 21;
    public static final int NEWINFORM_ID = 3;
    public static final int RELATIONBROKE_ID = 7;
    public static final int NEWCONTACT_ID = 8;
    public static final int NEWFILE_ID = 9;
    private static final String NEWREQUEST = "newRequest";
    private static final String NEWINFORM = "newInform";
    private static final String NEWMESSAGE = "NEW_MESSAGE";
    private static final String CONNECTIONLOST = "connectionLost";
    private static final String MESSAGEQUEUE = "queue";
    private static final String FILETRANSFER = "file";
    private static final int CONNECTIONLOST_ID = 4;
    private static final int MESSAGEQUEUE_ID = 5;
    private static final int FILETRANSFER_ID = 6;

    /**
     * A constructor to initialize a TsmNotification
     *
     * @param parent an activity to generate a notification
     */
    public TsmNotification(Activity parent) {
        Resources res = parent.getResources();

        NotificationInfo notify = new NotificationInfo(NEWMESSAGE_ID,
                res.getString(R.string.notification_title_new_messages),
                res.getString(R.string.notification_new_messages), R.drawable.ic_info_small) {
            @Override
            public String showText() {
                String msg;
                if (count == 1)
                    msg = String.format(text, count);
                else
                    msg = String.format(textExt, count, countExt);
                return msg;
            }
        };
        notify.textExt = res.getString(R.string.notification_new_messages_in_some_chats);
        HashMap<String, NotificationInfo> notificationList = new HashMap<>();
        notificationList.put(NEWMESSAGE, notify);

        notify = new NotificationInfo(NEWREQUEST_ID,
                res.getString(R.string.notification_title_request),
                res.getString(R.string.notification_new_contact_request), R.drawable.ic_info_small);
        notificationList.put(NEWREQUEST, notify);

        notify = new NotificationInfo(NEWINFORM_ID,
                res.getString(R.string.notification_title_inform),
                res.getString(R.string.notification_new_inform), R.drawable.ic_info_small);
        notificationList.put(NEWINFORM, notify);

        notify = new NotificationInfo(CONNECTIONLOST_ID,
                res.getString(R.string.notification_title_connection),
                res.getString(R.string.notification_connection_lost), R.drawable.ic_info_small);
        notificationList.put(CONNECTIONLOST, notify);

        notify = new NotificationInfo(FILETRANSFER_ID,
                res.getString(R.string.notification_title_file),
                res.getString(R.string.notification_file), R.drawable.ic_info_small);
        notificationList.put(FILETRANSFER, notify);

        notify = new NotificationInfo(MESSAGEQUEUE_ID,
                res.getString(R.string.notification_title_queue),
                res.getString(R.string.notification_message_queue), R.drawable.ic_info_small);
        notificationList.put(MESSAGEQUEUE, notify);

    }

    /**
     * A class to fill all information needed to post a notification
     */
    public class NotificationInfo {
        public final String title;
        public final String text;


        public final int notificationId;
        public final int icon;
        public final int count;
        protected int countExt;
        protected String textExt;

        public NotificationInfo(int notificationId, String title, String text, int icon) {
            this.title = title;
            this.text = text;
            this.icon = icon;
            this.notificationId = notificationId;
            this.count = 0;
        }

        public String showText() {
            return text;
        }

    }
}
