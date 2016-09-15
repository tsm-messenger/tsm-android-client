package com.tsm_messenger.server;

import com.tsm_messenger.service.UniversalHelper;

import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

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

public class CryptoHelper {

    private static final CryptoHelper INSTANCE = new CryptoHelper();

    private CryptoHelper() {
    }

    /**
     * Gets a current active instance of CryptoHelper
     *
     * @return an active instance of CryptoHelper
     */
    public static CryptoHelper getInstance() {
        return INSTANCE;
    }

    /**
     * Generates a new SecretKey instance with povided password and salt
     *
     * @param password a password used for SecretKey instance generating
     * @param salt     a string used to make a SecretKey instance more secure
     * @return an initialized instance of SecretKey
     * @throws GeneralSecurityException
     */
    public static SecretKey getSecretKey(String password, String salt) throws GeneralSecurityException {
        if (UniversalHelper.checkNotNull(2, password, salt)) {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 1024, 256);
            SecretKey tmp = factory.generateSecret(spec);
            return new SecretKeySpec(tmp.getEncoded(), "AES");
        } else {
            throw new GeneralSecurityException("Pass or salt is null");
        }
    }

}
