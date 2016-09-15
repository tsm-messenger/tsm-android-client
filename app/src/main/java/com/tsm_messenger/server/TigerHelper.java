package com.tsm_messenger.server;

import com.tsm_messenger.service.UniversalHelper;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;

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

public class TigerHelper {

    private TigerHelper() {
    }

    /**
     * Gets the current active instance of TigerHelper
     *
     * @return current active TigerHelper instance
     */
    public static TigerHelper getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * Creates the hash of a provided string
     *
     * @param string a string to get the hash
     * @return a byte array representing the hash
     * @throws NoSuchAlgorithmException
     */
    public byte[] create(String string) throws NoSuchAlgorithmException {
        Security.addProvider(new BouncyCastleProvider());
        MessageDigest messageDigest = MessageDigest.getInstance("Tiger", new BouncyCastleProvider());
        try {
            messageDigest.update(string.getBytes("UTF-8"));
        } catch (Exception e) {
            UniversalHelper.logException(e);
        }
        return messageDigest.digest();
    }

    /**
     * Gets a string representing a byte array in hex symbols
     *
     * @param arr array to represent in hex string
     * @return a string containing a hex representation of a provided byte array
     */
    public String getHexString(byte[] arr) {
        String hash = "";
        if (arr != null) {
            for (byte b : arr) {
                hash += String.format("%02x", b);
            }
        }
        return hash;
    }

    private static class SingletonHolder {
        public static final TigerHelper INSTANCE = new TigerHelper();

        private SingletonHolder() {
            //do nothing
        }
    }

}
