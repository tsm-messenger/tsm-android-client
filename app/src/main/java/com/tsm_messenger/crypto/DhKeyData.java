package com.tsm_messenger.crypto;

import com.tsm_messenger.service.UniversalHelper;

import org.bouncycastle.util.encoders.Hex;

/**
 * **********************************************************************
 * <p>
 * TELESENS CONFIDENTIAL
 * __________________
 * <p>
 * [2014] Telesens International Limited
 * All Rights Reserved.
 * <p>
 * NOTICE:  All information contained herein is, and remains
 * the property of Telesens International Limited and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Telesens International Limited
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Telesens International Limited.
 * <p>
 */

public class DhKeyData {
    private final String receiverKey;
    private final String ownSecretKey;
    byte[] sharedKeyBytes;
    private String receiverPersIdent;
    private String receiverKeyId;
    private String ownSecretKeyId;

    /**
     * Creates a new object for generate shared key
     *
     * @param receiverPersIdent the Participant ID
     * @param receiverKeyId     the Participant key ID
     * @param receiverKey       the Participant public key
     * @param ownSecretKeyId    the Owner key ID
     * @param ownSecretKey      the Owner private key
     */
    public DhKeyData(String receiverPersIdent,
                     String receiverKeyId,
                     String receiverKey,
                     String ownSecretKeyId,
                     String ownSecretKey) {

        this.receiverPersIdent = receiverPersIdent;
        this.receiverKeyId = receiverKeyId;
        this.receiverKey = receiverKey;
        this.ownSecretKeyId = ownSecretKeyId;
        this.ownSecretKey = ownSecretKey;
    }

    /**
     * Creates an object for calculate shared  key which participant has generated
     *
     * @param receiverKey  the Participant public key
     * @param ownSecretKey the Owner private key
     */
    public DhKeyData(String receiverKey, String ownSecretKey) {
        this.receiverKey = receiverKey;
        this.ownSecretKey = ownSecretKey;
    }

    /**
     * Returns participant ID
     *
     * @return participant ID
     */
    public String getReceiverPersIdent() {
        return receiverPersIdent;
    }

    /**
     * Returns Participant key ID
     *
     * @return key ID
     */
    public String getReceiverKeyId() {
        return receiverKeyId;
    }

    /**
     * Returns Owner private key
     *
     * @return private key
     */
    public String getOwnSecretKeyId() {
        return ownSecretKeyId;
    }

    /**
     * Calculates new shared key for encrypt/decrypt messages
     *
     * @return the shared key
     */
    public byte[] getSharedKey() {
        if (ownSecretKey == null) {
            throw new KeyAccessException("Secret key is null");
        }
        if (sharedKeyBytes == null && UniversalHelper.checkNotNull(2, receiverKey, ownSecretKey)) {
            byte[] receiverKeyBytes = Hex.decode(receiverKey.getBytes());
            byte[] ownKeyBytes = Hex.decode(ownSecretKey.getBytes());
            sharedKeyBytes = new byte[32];

            Curve25519.curve(sharedKeyBytes, ownKeyBytes, receiverKeyBytes);
        }
        return sharedKeyBytes;
    }

    /**
     * Custom exception class used for a specified error reporting
     */
    public class KeyAccessException extends RuntimeException {
        public KeyAccessException(String message) {
            super(message);
        }
    }
}
