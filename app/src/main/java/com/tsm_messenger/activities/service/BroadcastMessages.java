package com.tsm_messenger.activities.service;

import com.tsm_messenger.protocol.transaction.Operation;

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
public abstract class BroadcastMessages {

    public static final String ALARM_OWNER_KEY = "tsm.alarm.ownerkey";
    public static final String ALARM_PARTICIPANT_KEY = "tsm.alarm.participantkey";
    public static final String UI_BROADCAST = "_UI";
    public static final String WS_PARAM = "bundle";
    public static final String WS_NEWMESSAGE = "tsm.NEW_MESSAGE";
    public static final String WS_NEWMESSAGE_HISTORY = "tsm.NEW_MESSAGE.history";
    public static final String WS_FILERECEIVE = "tsm.fileReceive";
    public static final String WS_FILEANSWER = "tsm.fileAnswer";
    public static final String WS_FILEACTION = "tsm.fileAction";
    public static final String WS_ERROR = "tsm.error";
    public static final String WS_CONTACT_STATUS = "tsm.contact_status";
    public static final String WS_SHOW_LOGIN_TIME = "tsm.show_login_time";

    public static final String WS_SEND_ADDRESS_BOOK = "tsm.send_address_book";
    public static final String WS_NET_STATE = "tsm.net_status";
    public static final String WS_DELETE_ACCOUNT = "tsm.delete_user_account";
    public static final String WS_DELETE_RELATION = "tsm.delete_relation";

    public static final String WS_NET_STATE_VAL = "status";
    public static final String NTF_REQUEST_CONTACT_ACCEPT = "tsm.notification.request_contact_accept";
    public static final String NTF_REQUEST_CONTACT_DECLINE = "tsm.notification.request_contact_decline";
    public static final String NTF_REQUEST_CONTACT_SENT = "tsm.notification.request_contact_sent";
    public static final String NTF_ANSWER_REQUEST_CONTACT = "tsm.notification.answer_request_contact";
    public static final String NTF_NEW_MESSAGE = "tsm.notification.new_message";
    public static final String NTF_NEW_CONTACT = "tsm.notification.new_contact";

    public static final String CONNECT_PROHIBITED = "tsm.notification.connect_prohibited";

    private BroadcastMessages() {
    }

    /**
     * gets the name of a broadcast message according to operation name
     *
     * @param operation an operation to get
     * @return returns a broadcast message name
     */
    public static String getBroadcastOperation(Operation operation) {
        return "tsm.op_" + operation.toString();
    }

    /**
     * a class to represent probable states of a network connection
     */
    public abstract static class ConnectionState {
        public static final int ONLINE = 1;
        public static final int OFFLINE = 0;

        private ConnectionState() {
        }
    }

    /**
     * a class to construct broadcastMessages with parameters
     */
    public abstract static class NotificationParam {
        public static final String ACTION = "ACTION";

        private NotificationParam() {
        }
    }

    /**
     * a class to fill broadcastMessages with parameters
     */
    public abstract static class MessagesParam {
        public static final String SHOW_NOTIFICATION = "showNot";
        public static final String CHAT_NAME = "chatName";
        public static final String UNREAD = "unread";

        private MessagesParam() {
        }
    }

}
