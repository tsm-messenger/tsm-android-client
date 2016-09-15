package com.tsm_messenger.crypto;

import android.content.Context;

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
public interface IKeyExportImporter {

    /**
     * Initiates needed actions to start key export from UI
     *
     * @param showChooseExportMode a flag that shows if "choose export mode" dialog is needed
     */
    void exportPrivateUserKey(boolean showChooseExportMode);

    /**
     * Initiates needed actions to start key import from UI
     */
    void importPrivateUSerKey();

    /**
     * Shows the results of an operation
     *
     * @param result an integer code of the result which is used to show different messages
     */
    void showResults(int result);

    /**
     * Gets current context to use it for dialogs building etc.
     *
     * @return current active contects for dialogs building etc.
     */
    Context getContext();

    /**
     * Initiates scanner start from an active activity
     */
    void scanQrCodeForImport();

    /**
     * starts an open file activity to get a chosen URI as an activity result
     */
    void chooseFile();

    /**
     * The set of possible export and import results to report and process
     */
    final class Result {
        public static final int RESULT_OK = -1;
        public static final int RESULT_CANCELED = 0;
        public static final int ERR_EXPORT_KEYGEN = 1;
        public static final int ERR_EXPORT_FILE_WRITE = 2;
        public static final int ERR_IMPORT_FILE_READ = 3;
        public static final int ERR_IMPORT_SCAN = 4;
        public static final int ERR_IMPORT_KEY_DECRYPT = 5;
        public static final int ERR_IMPORT_INCORRECT_LOGIN = 6;

        private Result() {
        }
    }
}
