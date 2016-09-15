package com.tsm_messenger.service;

/*************************************************************************
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

public abstract class SharedPreferencesAccessor {

    public static final String SD_CARD_WRITABLE = "sd_card_writable";
    public static final String PREFS_NAME = "tsm.tsm_preferences";
    public static final int PREFS_MODE = 0;
    public static final String VIBRATE_SILENT_TIME = "vibrate_silent_time";
    public static final String DOWNLOAD_FILE_FOLDER = "download_file_folder";
    public static final String PRIVATEKEY_SHOW_TIME = "privatekey_show_time";
    public static final String USER_ID = "user_login";
    public static final String USER_NICKNAME = "user_nickname";
    public static final String APP_VERSION = "appVersion";
    public static final String LAST_BALANCER_URL = "lastBalancerUrl";
    public static final String FAILED_CONNECT = "failedConnect";
    public static final String INCORRECT_PIN_COUNT = "incorrect_pin_count";
    public static final String UNLOCK_TIMESTAMP = "unlock_timestamp";
    public static final int TRY_CONNECT_COUNT = 5;

    private SharedPreferencesAccessor() {
    }
}
