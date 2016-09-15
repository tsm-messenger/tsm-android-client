package com.tsm_messenger.activities.service;

import com.tsm_messenger.protocol.registration.Response;

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
public abstract class ServiceParameters {
    public static final String BACKGROUNDACTION = "backact";
    public static final String MODE = "mode";
    public static final String REGISTRATION = "reg";
    public static final String SIGNIN = "signin";
    public static final String ACTION = "ACTION";
    public static final String PARAM = "param";
    public static final String STATE = "state";
    public static final String OK = Response.Result.OK;
    public static final String FAIL = Response.Result.FAIL;
    public static final String TSM_BROADCAST_REGISTRATION = "tsm_reg";
    public static final String TSM_USER_PUBLIC_LIST = "usr_pub";
    public static final String USER_LIST = "usrList";
    public static final String RECONNECT = "reconnect";

    private ServiceParameters() {
    }

    /**
     * a class to construct background tasks with different action effects
     */
    public abstract static class BackgroundTask {
        public static final String NEW_KEYS = "newkey";
        public static final String GETSERVERPUBLICKEY = "getPB";
        public static final String REGISTRATION = "reg";
        public static final String USERKEY = "upk";
        public static final String TRANSACT_URL = "t_url";

        private BackgroundTask() {
        }
    }

    /**
     * a class to get parameters for registration actions
     */
    public abstract static class RegistrationBroadcast {
        public static final String TSM_REG_ACTION_CHECKUSER = "tsmbc_chkUsr";
        public static final String TSM_REG_ACTION_REGISTRATION = "tsmbc_reg";
        public static final String TSM_REG_ACTION_GETSERVERKEY = "tsmbc_getKey";

        private RegistrationBroadcast() {
        }
    }
}
