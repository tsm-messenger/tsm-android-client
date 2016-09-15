package com.tsm_messenger.data.storage;

import com.tsm_messenger.crypto.HashMaker;
import com.tsm_messenger.crypto.Salsa20CryptoHelper;
import com.tsm_messenger.server.TigerHelper;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.UniversalHelper;

import org.bouncycastle.util.encoders.Hex;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;


/**
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
public class TsmPasswordStorage {

    private static final String KEY_FILE = "hash.key";
    private static TsmPasswordStorage instance = null;
    private final TigerHelper tiger = TigerHelper.getInstance();

    /**
     * A constructor for a TsmPasswordStorage instance
     */
    private TsmPasswordStorage() {
    }

    /**
     * Gets the current working instance of TsmPasswordStorage
     *
     * @return the current working instance
     */
    public static TsmPasswordStorage getInstance() {
        if (instance == null) {
            instance = new TsmPasswordStorage();
        }
        return instance;
    }

    private byte[] getHash(String password, String login) {
        // TODO generate hash
        byte[] hash;
        byte result[];
        String randomString;
        String randomHash;

        randomString = Hex.toHexString(SecureRandom.getSeed(32));
        try {
            String salt = Hex.toHexString(tiger.create(login.toLowerCase()));
            hash = Hex.decode(HashMaker.get_SHA_256_SecurePassword(password, salt).getBytes());
            randomHash = Hex.toHexString(tiger.create(randomString));


            randomString += randomHash;
            result = Hex.decode(randomString.getBytes());
            Salsa20CryptoHelper.process(result, hash);
        } catch (NoSuchAlgorithmException e) {
            UniversalHelper.logException(e);
            return new byte[0];
        }

        return result;
    }

    /**
     * Checks if the provided password is correct
     *
     * @param password a password to validate
     * @return true if current password is correct, returns false if not
     */
    public boolean validate(String password, String login) {
        boolean ret = false;
        byte[] buffer = null;
        byte[] passHash;
        String validHash;

        String randomString, randomHash;
        String checkHash;
        int bytesRead = 0;
        File file = new File(ActivityGlobalManager.getInstance().getFilesDir(), KEY_FILE); //for ex foo.txt
        if (file.exists()) {
            BufferedInputStream bufferedInput = null;
            FileInputStream in = null;
            try {
                in = new FileInputStream(file.getPath());
                bufferedInput = new BufferedInputStream(in);

                buffer = new byte[(int) file.length()];
                int count;
                while((count = bufferedInput.read(buffer)) > 0){
                    bytesRead += count;
                }

                if (bytesRead == file.length()) {
                    try {
                        String salt = Hex.toHexString(tiger.create(login.toLowerCase()));
                        passHash = Hex.decode(HashMaker.get_SHA_256_SecurePassword(password, salt).getBytes());

                        Salsa20CryptoHelper.process(buffer, passHash);
                        validHash = Hex.toHexString(buffer);
                        randomString = validHash.substring(0, 64);
                        randomHash = validHash.substring(64);
                        checkHash = Hex.toHexString(tiger.create(randomString));
                        ret = randomHash.equals(checkHash);
                    } catch (GeneralSecurityException e) {
                        UniversalHelper.logException(e);
                    }
                }
                in.close();
                bufferedInput.close();
            } catch (IOException e) {
                UniversalHelper.logException(e);

            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                    if (bufferedInput != null) {
                        bufferedInput.close();
                    }
                } catch (IOException e) {
                    UniversalHelper.logException(e);
                }
            }
        }
        return ret;
    }

    /**
     * Creates the reference file for the next password validations
     *
     * @param password a password to check next times
     */
    public void initKey(String password, String login) {
        try {
            byte[] hash = getHash(password, login);
            if (hash != null) {
                File file = new File(ActivityGlobalManager.getInstance().getFilesDir(), KEY_FILE);
                createFileIfNecessary(file);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(hash);
                fos.flush();
                fos.close();
            }
        } catch (IOException ioe) {
            UniversalHelper.logException(ioe);
        }
    }

    private void createFileIfNecessary(File file) throws IOException {
        if (!file.exists()) {
            file.createNewFile();
        }
    }
}

