package com.tsm_messenger.service;


import android.os.Environment;

import com.tsm_messenger.activities.BuildConfig;
import com.tsm_messenger.protocol.transaction.Response;
import com.tsm_messenger.protocol.transaction.Response.SendAddressBookResponse;

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
 */

public abstract class CustomValuesStorage {
    /**
     * A default directory to store exported key files
     */
    public static final String KEYS_DIRECTORY = Environment.getExternalStorageDirectory().getPath() + BuildConfig.StoragePath;

    /**
     * A key combination group ids are beginning with
     */
    public static final String GROUP_DESCRIPTIOR = "%GRP=";

    /**
     * Shared preferences keys
     */
    public static final String HARDWARE_STRING = "hardware_string";
    public static final String REG_STATE = "regState";
    public static final String HARDWARE_ID = "hardwareId";

    /**
     * Categories of contact persons and groups
     */
    public static final int CATEGORY_INVITATION = 2;
    public static final int CATEGORY_NOTCONNECT =
            SendAddressBookResponse.NumbersParamsResponse.StatusParamResponse.CATEGORY_NOTCONNECT;
    public static final int CATEGORY_CONNECT =
            SendAddressBookResponse.NumbersParamsResponse.StatusParamResponse.CATEGORY_CONNECT;
    public static final int CATEGORY_DELETE = 8;
    public static final int CATEGORY_CONFIRM_IN =
            SendAddressBookResponse.NumbersParamsResponse.StatusParamResponse.CATEGORY_CONFIRM_IN;
    public static final int CATEGORY_CONFIRM_OUT =
            SendAddressBookResponse.NumbersParamsResponse.StatusParamResponse.CATEGORY_CONFIRM_OUT;
    public static final int CATEGORY_CONFIRM = CATEGORY_CONFIRM_IN + CATEGORY_CONFIRM_OUT + CATEGORY_NOTCONNECT + CATEGORY_DELETE;
    public static final int CATEGORY_MESSENGER = 124;
    public static final int CATEGORY_GROUP = 128;
    public static final int CATEGORY_ALL = 127;
    public static final int CATEGORY_UNKNOWN = 256;

    /**
     * Default time to show QR-code with user's exported keys at screen
     */
    public static final int USERKEY_SHOW_TIME_DEFAULT = 10;

    private CustomValuesStorage() {
    }

    /**
     * Enum used to indicate users' online statuses
     */
    public enum UserStatus {
        ONLINE, OFFLINE, UNREACHABLE;


        public static UserStatus getByName(String value) {
            UserStatus retVal;
            if(value != null) {
                switch (value) {
                    case Response.AnswerNotificationResponse.NotificationStatus.ONLINE:
                        retVal = UserStatus.ONLINE;
                        break;
                    case Response.AnswerNotificationResponse.NotificationStatus.OFFLINE:
                        retVal = OFFLINE;
                        break;
                    default:
                        retVal = UNREACHABLE;
                        break;
                }
            }else{
                retVal = UNREACHABLE;
            }
            return retVal;
        }
    }

    /**
     * Class used for indicating the closed activity type by activity result
     */
    public abstract class ActivityResult {
        public static final int RESULT_GRP_OK = -2;
        public static final int RESULT_CHT_OK = -3;
        public static final int RESULT_REQ_OK = -4;

        private ActivityResult() {
        }
    }

    /**
     * Headers for extras, which are put into started intents
     */
    public abstract class IntentExtras {
        public static final String INTENT_KEY_GRP_ID = "grpId";
        public static final String INTENT_KEY_MODE = "createMode";
        public static final String INTENT_CREATE_CHAT = "mode_createChat";
        public static final String INTENT_UNIT_ID = "unitId";
        public static final String INTENT_MESSENGER_ID = "messengerId";
        public static final String INTENT_CONTACT_LIST = "contactList";
        public static final String INTENT_CHAT_LIST = "chatList";
        public static final String INTENT_ERROR_TEXT = "errText";
        public static final String INTENT_RESULT = "RESULT";
        public static final String INTENT_SHOW_DIALOG = "showDlg";

        private IntentExtras() {
        }

    }
}
