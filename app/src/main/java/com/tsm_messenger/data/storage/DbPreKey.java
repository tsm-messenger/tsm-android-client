package com.tsm_messenger.data.storage;

import com.tsm_messenger.crypto.Curve25519;
import com.tsm_messenger.crypto.EdDsaSigner;
import com.tsm_messenger.server.CryptoHelper;
import com.tsm_messenger.service.UniversalHelper;

import org.bouncycastle.util.encoders.Hex;

import java.security.SecureRandom;
import java.util.Date;

import javax.crypto.SecretKey;

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

public class DbPreKey {
    private final String privateKey;
    private final String publicKey;
    private String preKeyId;

    /**
     * A constructor initializing all key data
     */
    public DbPreKey() {
        SecureRandom secureRandom = new SecureRandom();

        byte[] secretBytes = secureRandom.generateSeed(32);
        byte[] publicBytes = secureRandom.generateSeed(32);

        Curve25519.keygen(publicBytes, null, secretBytes);

        privateKey = Hex.toHexString(secretBytes);
        publicKey = Hex.toHexString(publicBytes);

        try {
            SecretKey hash = CryptoHelper.getSecretKey(publicKey, new Date().toString());
            preKeyId = Hex.toHexString(hash.getEncoded());
        } catch (Exception e) {
            UniversalHelper.logException(e);
            preKeyId = (String.valueOf(new Date().getTime()) + publicKey).substring(0, 64);
        }
    }

    /**
     * Gets an instance of a pre-key to share it with chat participants
     *
     * @return an object, containing public part, encrypted private part and an id of a key
     */
    public PairDbPreKey getPublicPart() {
        return new PairDbPreKey(preKeyId, publicKey, EdDsaSigner.getInstance().encrypt(privateKey));
    }

    /**
     * A class for prekeys sharing with chat participants
     */
    public class PairDbPreKey {
        private final String id;
        private final String pubPreKey;
        private final String cryptedPreKey;

        public PairDbPreKey(String id, String pubPreKey, String cryptedPreKey) {
            this.id = id;
            this.pubPreKey = pubPreKey + EdDsaSigner.getInstance().sign(pubPreKey);
            this.cryptedPreKey = cryptedPreKey;
        }
    }
}
