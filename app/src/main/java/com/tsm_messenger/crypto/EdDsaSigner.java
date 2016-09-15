package com.tsm_messenger.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.tsm_messenger.activities.BuildConfig;
import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.control.TsmMessageDialog;
import com.tsm_messenger.protocol.dto.DummyDto;
import com.tsm_messenger.server.CryptoHelper;
import com.tsm_messenger.server.TigerHelper;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.SharedPreferencesAccessor;
import com.tsm_messenger.service.UniversalHelper;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.util.encoders.Hex;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

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

public class EdDsaSigner {
    private static EdDsaSigner instance;
    private byte[] userKeyEncryptPassword;
    private byte[] encryptedPrivateKey;
    private byte[] encryptedPublicUserKey;
    private byte[] encryptedRegistrationKey;
    private byte[] encryptedCloudKey;
    private byte[] privateUserKey;
    private byte[] publicUserKey;
    private byte[] registrationKey;
    private byte[] cloudKey;
    private byte[] loginBytes;
    private String publicServerKey;
    private boolean isDecryptNeeded = false;
    private boolean keyImported = false;

    private EdDsaSigner() {

    }

    /**
     * Gets instance of EdDsaSigner
     *
     * @return instance of EdDsaSigner
     */
    public static EdDsaSigner getInstance() {
        if (instance == null) {
            instance = new EdDsaSigner();
        }

        return instance;
    }

    /**
     * Destroy instance when application is stopped
     */
    public static void destroyInstance() {
        instance = null;
    }

    /**
     * Returns the flag that the key was imported
     *
     * @return true if the key was imported or false if the key was generated
     */
    public boolean isKeyImported() {
        return keyImported;
    }

    /**
     * Generates new keys for new the user registration
     */
    public void generateKey() {
        clearUserKeys();

        try {
            String seed = EdDSAHelper.getInstance().getSeed();
            String publicKey = EdDSAHelper.getInstance().getPublicKey();

            if (UniversalHelper.checkNotNull(2, seed, publicKey)) {
                privateUserKey = Hex.decode(seed.getBytes());
                publicUserKey = Hex.decode(publicKey.getBytes());

                cloudKey = SecureRandom.getSeed(32);
                registrationKey = SecureRandom.getSeed(32);
            } else {
                throw new GeneralSecurityException("seed or publicKey are null");
            }
        } catch (Exception e) {
            UniversalHelper.logException(e);
        }
    }

    /**
     * Encrypts user keys for export
     *
     * @param srcKey The Key to be encrypted
     * @return an encrypted key
     */
    private byte[] encryptKey(byte[] srcKey) {
        byte[] ecryptKey;
        ecryptKey = srcKey.clone();
        Salsa20CryptoHelper.process(ecryptKey, userKeyEncryptPassword);

        return ecryptKey;
    }

    /**
     * Returns user registration key
     *
     * @return a registration key
     */
    public byte[] getRegistrationKey() {
        return registrationKey;
    }

    /**
     * Checks user password and prepare secret key for encrypt or decrypt operations
     *
     * @param pin        user password
     * @param qrCode     user password Hash
     * @param activity   activity to show results
     * @param decryptKey the flag to check if the keys decryption is needed
     */
    public void initPassword(String pin, String qrCode, IKeyExportImporter activity, boolean decryptKey) {
        if (UniversalHelper.checkNotNull(3, pin, qrCode, activity)) {
            SecretKey encryptKey;

            try {
                encryptKey = CryptoHelper.getSecretKey(pin,
                        TigerHelper.getInstance().getHexString(TigerHelper.getInstance().create(qrCode))
                );
                userKeyEncryptPassword = encryptKey.getEncoded();
            } catch (Exception e) {
                UniversalHelper.logException(e);
                try {
                    activity.showResults(IKeyExportImporter.Result.ERR_IMPORT_KEY_DECRYPT);
                } catch (Exception e1) {
                    UniversalHelper.logException(e1);
                }
                return;
            }
            if (decryptKey) {
                decryptKey();
            }
        } else {
            if (activity != null) {
                activity.showResults(IKeyExportImporter.Result.ERR_IMPORT_KEY_DECRYPT);
            } else {
                UniversalHelper.logException(new Exception("error when exporting key"));
            }
        }
    }

    private void decryptKey() {
        if (UniversalHelper.checkNotNull(5, privateUserKey, registrationKey, cloudKey,
                publicUserKey, userKeyEncryptPassword)) {
            if (isDecryptNeeded) {
                isDecryptNeeded = false;
                Salsa20CryptoHelper.process(privateUserKey, userKeyEncryptPassword);
                if (registrationKey != null) {
                    Salsa20CryptoHelper.process(registrationKey, userKeyEncryptPassword);
                }
                if (cloudKey != null) {
                    Salsa20CryptoHelper.process(cloudKey, userKeyEncryptPassword);
                }
                if (publicUserKey != null) {
                    Salsa20CryptoHelper.process(publicUserKey, userKeyEncryptPassword);
                }
            }
        } else {
            String str =
                    (privateUserKey == null ? "privateUserKey" : "") +
                            (registrationKey == null ? "registrationKey" : "") +
                            (cloudKey == null ? "cloudKey" : "") +
                            (publicUserKey == null ? "publicUserKey" : "") +
                            (userKeyEncryptPassword == null ? "userKeyEncryptPassword" : "");

            UniversalHelper.debugLog(Log.WARN, "SRVST", " Null key= " + str);

        }
        UniversalHelper.debugLog(Log.WARN, "SRVST", " private decrypted key= " + Hex.toHexString(privateUserKey));
    }

    /**
     * checks if provided login matches the login marker at
     *
     * @param providedLogin
     * @return
     */
    public boolean checkLogin(String providedLogin) {
        try {
            if (loginBytes != null && loginBytes.length > 0) {
                String otherLoginBytes = new String(
                        TigerHelper.getInstance().create(providedLogin.toLowerCase()));
                return new String(loginBytes).equals(otherLoginBytes);
            } else {
                return true;
            }
        } catch (Exception e) {
            UniversalHelper.logException(e);
            return true;
        }
    }

    private byte[] prepareForExport(String login) {
        try {
            loginBytes = TigerHelper.getInstance().create(login.toLowerCase());
        } catch (Exception e) {
            UniversalHelper.logException(e);
            loginBytes = new byte[0];
        }
        ByteBuffer bb = ByteBuffer.allocate(128 + loginBytes.length);

        bb.put(encryptKey(privateUserKey));
        bb.put(encryptKey(publicUserKey));
        bb.put(encryptKey(registrationKey));
        bb.put(encryptKey(cloudKey));
        bb.put(loginBytes);

        return bb.array();
    }

    private byte[] prepareForExport() {
        ByteBuffer bb = ByteBuffer.allocate(128);

        bb.put(encryptKey(privateUserKey));
        bb.put(encryptKey(publicUserKey));
        bb.put(encryptKey(registrationKey));
        bb.put(encryptKey(cloudKey));

        return bb.array();
    }

    /**
     * prepares an encrypted keys package and writes them to a file with a provided name and path
     *
     * @param destination a path to save a file
     * @param login a file name
     * @return returns a result code -1 if operation was done successfully, or an error code if not
     */
    public int writeKeysToTextFile(String destination, String login) {
        String placeToSave = destination;
        if (placeToSave == null) {
            placeToSave = Environment.getExternalStorageDirectory().getPath() + BuildConfig.StoragePath;
        }
        File dirToSave = new File(placeToSave);
        dirToSave.mkdirs();
        int ret;
        String exportPath;
        String fileName = login + "_key";
        final String EXT = ".pub";
        try {
            File file = null;
            boolean fileCreated = false;
            int equalCount = 0;
            exportPath = fileName + EXT;
            while (!fileCreated) {
                file = new File(placeToSave, exportPath);
                if (file.exists()) {
                    exportPath = fileName + "(" + ++equalCount + ")" + EXT;
                } else {
                    fileCreated = file.createNewFile();
                }
            }
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(prepareForExport(login));
            fos.flush();
            fos.close();
            ret = IKeyExportImporter.Result.RESULT_OK;
        } catch (Exception e) {
            UniversalHelper.logException(e);
            ret = IKeyExportImporter.Result.ERR_EXPORT_FILE_WRITE;
        }
        return ret;
    }

    /**
     * shows a dialog with a QR-code containing an export package of user keys
     *
     * @param context an activity to show dialog
     */
    public void showPrivateUserKeyQr(Context context) {
        if (context != null) {
            SharedPreferences settings = context.getSharedPreferences(
                    SharedPreferencesAccessor.PREFS_NAME,
                    SharedPreferencesAccessor.PREFS_MODE);
            int showKeySeconds = settings.getInt(
                    SharedPreferencesAccessor.PRIVATEKEY_SHOW_TIME,
                    CustomValuesStorage.USERKEY_SHOW_TIME_DEFAULT);

            final TsmMessageDialog userKeyDialog = new TsmMessageDialog(context);
            int layoutSize = context.getResources().getConfiguration().screenLayout &
                    Configuration.SCREENLAYOUT_SIZE_MASK;
            String login = settings.getString(SharedPreferencesAccessor.USER_NICKNAME, "");
            Bitmap userKeyQr = UniversalHelper.bytesArrayToBitmap(prepareForExport(login), layoutSize);
            userKeyDialog.setImage(userKeyQr);
            String message = String.format(context.getString(R.string.info_userkey_show_time), showKeySeconds);
            userKeyDialog.show(context.getString(R.string.title_user_key), message);

            int userKeyShowMilliseconds = showKeySeconds * 1000;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (userKeyDialog.isShowing()) {
                            userKeyDialog.dismiss();
                        }
                    } catch (Exception e) {
                        UniversalHelper.logException(e);
                    }
                }
            }, userKeyShowMilliseconds);
        }
    }

    /**
     * prepares a HEX string containing a package of encrypted user keys
     *
     * @return returns a HEX string with encrypted key bytes
     */
    public String exportDatabase() {
        return Hex.toHexString(prepareForExport());
    }

    /**
     * Signs the message for the server validation
     *
     * @param message message for sign
     * @return a string representation of a signed message
     */
    public String sign(String message) {
        String retVal = null;
        if (privateUserKey != null) {
            UniversalHelper.debugLog(Log.DEBUG, "MSG_SIG", "private key is = " + Hex.toHexString(privateUserKey));
            retVal = EdDSAHelper.getInstance().sign(Hex.toHexString(privateUserKey), message);
        }

        if (retVal == null) {
            throw new NullPointerException("retVal is null");
        }
        return retVal;
    }

    /**
     * Gets User public key
     *
     * @return the user public key
     */
    public String getPublicUserKey() {
        return publicUserKey == null ? "" : Hex.toHexString(publicUserKey);
    }

    /**
     * Saves server public key to keyStore
     *
     * @param serverPublicKey the server public key
     */
    public void saveServerKey(String serverPublicKey) {
        this.publicServerKey = serverPublicKey;
    }

    /**
     * Validates owner public key received from the server
     *
     * @param response the public key received from the server
     * @return true if owner public key is equal to received key
     */
    public boolean validateOwnerPublicKey(String response) {
        return response != null && Hex.toHexString(publicUserKey).equals(response);
    }

    /**
     * Validates the server messages signature
     *
     * @param json the message in JSON string
     * @return true if signature is valid
     */
    public boolean validateServerMessage(String json) {
        if (publicServerKey == null) {
            return true;
        } else {
            if (json != null) {
                Gson gson = new Gson();
                DummyDto dtoFromJson = gson.fromJson(json, DummyDto.class);
                String message = gson.toJson(dtoFromJson.getParams());
                String signature = dtoFromJson.getSignature();
                return EdDSAHelper.getInstance().verify(publicServerKey, message, signature);
            } else {
                return false;
            }
        }
    }

    /**
     * Clears all keys in memory when application is stopped
     */
    public void clearUserKeys() {
        if (privateUserKey != null) {
            Arrays.fill(privateUserKey, 0, privateUserKey.length, (byte) '0');
            privateUserKey = null;
        }
        if (encryptedPrivateKey != null) {
            Arrays.fill(encryptedPrivateKey, 0, encryptedPrivateKey.length, (byte) '0');
            encryptedPrivateKey = null;
        }
        if (publicUserKey != null) {
            Arrays.fill(publicUserKey, 0, publicUserKey.length, (byte) '0');
            publicUserKey = null;
        }
        keyImported = false;
        isDecryptNeeded = false;
        loginBytes = null;
    }

    /**
     * Loads keys from KeyStore
     *
     * @return true private user key was imported successfully,
     * returns false if private user key was not found in keyStore
     */
    public boolean loadKey(String keyStr) {
        byte[] keys = Hex.decode(keyStr.getBytes());

        this.encryptedPrivateKey = new byte[32];
        System.arraycopy(keys, 0, this.encryptedPrivateKey, 0, 32);
        this.privateUserKey = new byte[32];
        System.arraycopy(keys, 0, this.privateUserKey, 0, 32);

        this.publicUserKey = new byte[32];
        System.arraycopy(keys, 32, this.publicUserKey, 0, 32);

        this.registrationKey = new byte[32];
        System.arraycopy(keys, 64, this.registrationKey, 0, 32);

        this.cloudKey = new byte[32];
        System.arraycopy(keys, 96, this.cloudKey, 0, 32);
        isDecryptNeeded = true;
        decryptKey();
        return true;
    }

    /**
     * processes the imported key bytes
     *
     * @param ret the result of an import
     * @param keyBytes the imported key bytes
     * @param importActivity an activity to show result dialog
     */
    public void keyImportResult(int ret, byte[] keyBytes, IKeyExportImporter importActivity) {
        if (ret == IKeyExportImporter.Result.RESULT_OK) {
            if (keyBytes != null) {
                try {
                    clearUserKeys();
                    this.encryptedPrivateKey = new byte[32];
                    System.arraycopy(keyBytes, 0, this.encryptedPrivateKey, 0, 32);

                    this.encryptedPublicUserKey = new byte[32];
                    System.arraycopy(keyBytes, 32, this.encryptedPublicUserKey, 0, 32);

                    this.encryptedRegistrationKey = new byte[32];
                    System.arraycopy(keyBytes, 64, this.encryptedRegistrationKey, 0, 32);

                    this.encryptedCloudKey = new byte[32];
                    System.arraycopy(keyBytes, 96, this.encryptedCloudKey, 0, 32);

                    int loginLength = keyBytes.length - 128;
                    this.loginBytes = new byte[loginLength];
                    System.arraycopy(keyBytes, 128, this.loginBytes, 0, loginLength);

                    privateUserKey = this.encryptedPrivateKey.clone();
                    registrationKey = this.encryptedRegistrationKey.clone();
                    cloudKey = this.encryptedCloudKey.clone();
                    publicUserKey = this.encryptedPublicUserKey.clone();
                    isDecryptNeeded = true;
                    keyImported = true;
                    if (importActivity != null) {
                        importActivity.showResults(IKeyExportImporter.Result.RESULT_OK);
                    }
                } catch (Exception e) {
                    UniversalHelper.logException(e);
                    showImportFail(importActivity, IKeyExportImporter.Result.ERR_IMPORT_FILE_READ);
                }
            } else {
                showImportFail(importActivity, IKeyExportImporter.Result.ERR_IMPORT_FILE_READ);
            }
        } else {
            showImportFail(importActivity, ret);
        }
    }

    /**
     * Shows error when key export is failed
     *
     * @param importActivity activity for show an error
     * @param err            error key
     */
    public void showImportFail(IKeyExportImporter importActivity, int err) {
        if (importActivity != null) {
            importActivity.showResults(err);
        }
    }

    /**
     * Encrypts the session key for save in cloud
     *
     * @param src session key for encryption
     * @return Encrypted key
     */
    public String cloudEncrypt(byte[] src) {
        byte[] dest = new byte[src.length];
        System.arraycopy(src, 0, dest, 0, src.length);
        com.tsm_messenger.crypto.Salsa20CryptoHelper.process(dest, cloudKey);

        return Hex.toHexString(dest);
    }

    /**
     * decrypts the message received from a cloud storage
     *
     * @param src a message to decrypt
     * @return returns a byte array of a decrypted message
     */
    public byte[] cloudDecrypt(String src) {

        byte[] dest = Hex.decode(src.getBytes());
        com.tsm_messenger.crypto.Salsa20CryptoHelper.process(dest, cloudKey);
        return dest;
    }

    /**
     * Decrypts the session key received from cloud
     *
     * @param src Encrypted session key
     * @return the decrypted key
     */
    public String salsaEncrypt(byte[] src, byte[] pass) {
        byte[] dest = new byte[src.length];
        System.arraycopy(src, 0, dest, 0, src.length);

        com.tsm_messenger.crypto.Salsa20CryptoHelper.process(dest, pass);

        return new String(Base64.encodeBase64(dest));
    }

    /**
     * decrypts a message received in real-time (not history) using salsa20 algorithm
     *
     * @param src a message to decrypt
     * @param pass a password to decrypt
     * @return returns a decrypted message string
     */
    public String salsaDecrypt(String src, byte[] pass) {

        byte[] dest = Base64.decodeBase64(src.getBytes());
        com.tsm_messenger.crypto.Salsa20CryptoHelper.process(dest, pass);

        return new String(dest);
    }

    /**
     * Encrypts the private part of Diffie-Hellman key
     *
     * @param src source (plain) key
     * @return the encrypted key
     */
    public String encrypt(String src) {
        return salsaEncrypt(src.getBytes(), privateUserKey);
    }

    /**
     * Decrypts the private part of Diffie-Hellman key received from the server
     *
     * @param src encrypted key
     * @return the decrypted key
     */
    public String decrypt(String src) {
        return salsaDecrypt(src, privateUserKey);
    }

    /**
     * Sets the flag, that the private key must by decrypted, to true
     */
    public void setPrivateKeyNotDecrypted() {
        this.isDecryptNeeded = true;
        System.arraycopy(encryptedPrivateKey, 0, privateUserKey, 0, privateUserKey.length);
        System.arraycopy(encryptedCloudKey, 0, cloudKey, 0, cloudKey.length);
        System.arraycopy(encryptedPublicUserKey, 0, publicUserKey, 0, publicUserKey.length);
        System.arraycopy(encryptedRegistrationKey, 0, registrationKey, 0, registrationKey.length);
    }

    /**
     * Sets the flag, that the key was imported, to false
     */
    public void setKeyNotImported() {
        this.keyImported = false;
    }


}
