package com.tsm_messenger.data.storage;

import com.tsm_messenger.crypto.DhKeyData;
import com.tsm_messenger.crypto.EdDSAHelper;
import com.tsm_messenger.crypto.EdDsaSigner;
import com.tsm_messenger.crypto.Salsa20CryptoHelper;
import com.tsm_messenger.protocol.transaction.Request;
import com.tsm_messenger.service.UniversalHelper;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.util.encoders.Hex;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

public class DbSessionKey {

    public static final int ST_OK = 1;
    public static final int ST_WAIT = 2;
    public static final String KEY_IN_CLOUD = "cloud";
    public static final String KEY_DH = "dhKey";

    private final List<SskBundle> participantKey = new ArrayList<>();
    private Integer sessionId;
    private String keyType;
    private String ownerKeyId;
    private String participantPublicKey;
    private byte[] sessionKey;
    private boolean ready;
    private int keyStatus;

    /**
     * A constructor initializing all key data
     */
    public DbSessionKey() {
        ready = false;
        sessionKey = new byte[32];
    }

    @Override
    public boolean equals(Object o) {
        boolean ret;
        ret = o != null
                && (o instanceof DbSessionKey)
                && this.getSessionId().equals(((DbSessionKey) o).getSessionId());
        return ret;
    }

    @Override
    public int hashCode() {
        return getSessionId();
    }

    /**
     * Gets the status of a key: if it is free or is waiting
     *
     * @return an integer representation of ST_WAIT or ST_OK
     */
    public int getKeyStatus() {
        return keyStatus;
    }

    /**
     * Sets the status of a key: if it is free or is waiting
     *
     * @param keyStatus an integer representation of ST_WAIT or ST_OK
     */
    public void setKeyStatus(int keyStatus) {
        this.keyStatus = keyStatus;
    }

    /**
     * Gets the session id where current key is used for encryption
     *
     * @return an integer representation of session id
     */
    public Integer getSessionId() {
        return sessionId;
    }

    /**
     * Sets the session id where current key is used for encryption
     *
     * @param sessionKeyId an integer representation of session id
     */
    public void setSessionId(Integer sessionKeyId) {
        this.sessionId = sessionKeyId;
    }

    /**
     * Gets the type of a key: cloud or Diffie-Hellman key
     *
     * @return a string representation of a key type
     */
    public String getKeyType() {
        return keyType;
    }

    /**
     * Sets the type of a key: cloud or Diffie-Hellman key
     *
     * @param keyType a string representation of a key type
     */
    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    /**
     * Gets the id of the key for the session creator
     *
     * @return a string representation of a key id
     */
    public String getOwnerKeyId() {
        return ownerKeyId;
    }

    /**
     * Sets the id of the key for the session creator
     *
     * @param ownerKeyId a string representation of a key id
     */
    public void setOwnerKeyId(String ownerKeyId) {
        this.ownerKeyId = ownerKeyId;
    }

    /**
     * Gets the public part of the key for the chat participant
     *
     * @return a string representation of a public key
     */
    public String getParticipantPublicKey() {
        return participantPublicKey;
    }

    /**
     * Sets the public part of the key for the chat participant
     *
     * @param participantPublicKey a string representation of a public key
     */
    public void setParticipantPublicKey(String participantPublicKey) {
        this.participantPublicKey = participantPublicKey;
    }

    /**
     * Checks the source for the correct signature using public key
     *
     * @param source    a message with a signature
     * @param publicKey a public key to perform validation
     * @return true if provided message with a signature was validated successfully,
     * returns false if provided message vilidation was failed
     */
    public boolean verifyPublicKey(String source, String publicKey) {
        String key;
        String signature;
        if (source.length() != 192 || publicKey == null || publicKey.isEmpty()) {
            return false;
        }
        key = source.substring(0, 64);
        signature = source.substring(64);
        if (UniversalHelper.checkNotNull(3, publicKey, key, signature)) {
            return EdDSAHelper.getInstance().verify(publicKey, key, signature);
        } else {
            return false;
        }
    }

    /**
     * Gets the fact that session key is fully generated
     *
     * @return true if current session key is fully generated, returns false if not
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Returns a session encryption key
     *
     * @return a string representation of a session key
     */
    public String getSessionKey() {

        return EdDsaSigner.getInstance().cloudEncrypt(sessionKey);
    }

    /**
     * Sets a session encryption key
     *
     * @param sessionKey a string representation of a session key
     */
    public void setSessionKey(String sessionKey) {

        this.sessionKey = EdDsaSigner.getInstance().cloudDecrypt(sessionKey);
        ready = true;
    }

    /**
     * Decrypts an encrypted session key
     *
     * @param ssKey encrypted session key
     * @param dhKey Diffie-Hellman prekey to decrypt session key
     */
    public void decryptSessionKey(String ssKey, DhKeyData dhKey) {
        byte[] password;

        try {

            password = dhKey.getSharedKey();

            this.sessionKey = Hex.decode(ssKey);
            Salsa20CryptoHelper.process(this.sessionKey, password);
        } catch (Exception e) {
            UniversalHelper.logException(e);
        }


    }

    /**
     * Starts a session key parts computing
     *
     * @return a list of generated keys
     */
    public List<Map<String, String>> generateSessionKey() {


        SecureRandom sr = new SecureRandom();
        sr.nextBytes(sessionKey);
        ready = true;


        List<Map<String, String>> list = new ArrayList<>();

        for (SskBundle row : participantKey) {
            byte[] encryptKey = new byte[32];
            byte[] password;
            try {
                password = row.dhKey.getSharedKey();

                System.arraycopy(sessionKey, 0, encryptKey, 0, 32);
                Salsa20CryptoHelper.process(encryptKey, password);

                Map<String, String> rowlist = new HashMap<>();
                rowlist.put(Request.SessionKeysRequest.SessionKeysBundle.RECIPIENT_PERS_IDENT, row.persId);
                rowlist.put(Request.SessionKeysRequest.SessionKeysBundle.RECIPIENT_PREKEY_ID, row.dhKey.getReceiverKeyId());
                rowlist.put(Request.SessionKeysRequest.SessionKeysBundle.SENDER_PREKEY_ID, row.dhKey.getOwnSecretKeyId());
                rowlist.put(Request.SessionKeysRequest.SessionKeysBundle.SECRET_SESSION_KEY, Hex.toHexString(encryptKey));
                list.add(rowlist);
            } catch (Exception e) {
                UniversalHelper.logException(e);
            }
            row.cryptSecretKey = Hex.toHexString(encryptKey);
        }
        return list;
    }

    /**
     * Encrypts message with a current session key
     *
     * @param src unencrypted message
     * @return an encrypted message
     */
    public String encryptMessage(String src) {
        byte decrypt[] = src.getBytes();
        Salsa20CryptoHelper.process(decrypt, sessionKey);
        return new String(Base64.encodeBase64(decrypt));
    }

    /**
     * Decrypts message with a current session key
     *
     * @param src encrypted message
     * @return a decrypted message
     * @throws java.security.GeneralSecurityException
     */
    public String decryptMessage(String src) throws java.security.GeneralSecurityException {

        byte decrypt[] = Base64.decodeBase64(src.getBytes());
        Salsa20CryptoHelper.process(decrypt, sessionKey);
        return new String(decrypt);
    }

    /**
     * Adds a participant's key to a session key instance
     *
     * @param newKey a key to add
     */
    public void addParticipantKey(DhKeyData newKey) {
        SskBundle bundle = new SskBundle();
        bundle.persId = newKey.getReceiverPersIdent();
        bundle.dhKey = newKey;
        participantKey.add(bundle);
    }

    /**
     * A class to make a bundle "login - dhKey"
     */
    public class SskBundle {
        private String persId;
        private DhKeyData dhKey;
        private String cryptSecretKey;
    }
}

