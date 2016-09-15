package com.tsm_messenger.data.storage;

import android.graphics.Bitmap;
import android.os.Environment;

import com.tsm_messenger.activities.R;
import com.tsm_messenger.helpers.FileHelper;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.SharedPreferencesAccessor;
import com.tsm_messenger.service.UniversalHelper;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;

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

public class FileData {
    final String fileId;

    private final String fileName;
    private final Date lastModified;
    private final long fileSize;
    private final String filePath;
    private final String chatId;
    private final String mode;
    long currentChunk;
    private String sessionid;
    private DbChatMessage message;
    private boolean isPending = true;
    private Bitmap thumbnail = null;

    /**
     * A constructor that initializes a FileData instance with a random UUID
     *
     * @param fileName     a name of a file source
     * @param lastModified last date the file source was modified
     * @param fileSize     current size of a file source
     * @param filePath     a path to a file source
     * @param sessionId    id of a session key used for file encryption
     * @param chatId       id of a chat containing message with current fileData instance
     * @param mode         send mode for file: online or offline
     */
    public FileData(String fileName, Date lastModified, long fileSize, String filePath,
                    String sessionId, String chatId, String mode) {

        this(UUID.randomUUID().toString(), fileName, lastModified, fileSize, filePath,
                sessionId, chatId, mode);
    }

    /**
     * A constructor to create a new FileData instance
     *
     * @param fileId       the unique id of a FileData instance
     * @param fileName     a name of a file source
     * @param lastModified last date the file source was modified
     * @param fileSize     current size of a file source
     * @param filePath     a path to a file source
     * @param sessionId    id of a session key used for file encryption
     * @param chatId       id of a chat containing message with current fileData instance
     * @param mode         send mode for file: online or offline
     */
    public FileData(String fileId,
                    String fileName,
                    Date lastModified,
                    long fileSize,
                    String filePath,
                    String sessionId,
                    String chatId,
                    String mode) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.lastModified = lastModified;
        this.fileSize = fileSize;
        this.filePath = filePath == null ? ActivityGlobalManager.getInstance().getSettings().getString(
                SharedPreferencesAccessor.DOWNLOAD_FILE_FOLDER,
                ActivityGlobalManager.getInstance().getString(R.string.config_download_folder)) + "/" + fileName
                : filePath;
        this.sessionid = sessionId;
        this.chatId = chatId;
        this.currentChunk = 0L;
        this.mode = mode;
    }

    /**
     * Gets the filename for a new file instance which is not used in a download directory
     *
     * @param srcFileName an expected file name
     * @return an expected file name with a number of in braces making it unique
     */
    public static String findFreeFileName(String srcFileName) {
        File file;

        String placeToSave = ActivityGlobalManager.getInstance().getSettings().getString(
                SharedPreferencesAccessor.DOWNLOAD_FILE_FOLDER,
                ActivityGlobalManager.getInstance().getString(R.string.config_download_folder));
        int lastDotpos = srcFileName.lastIndexOf(".");
        String ext;
        String fileName;


        if (lastDotpos >= 0) {
            ext = srcFileName.substring(lastDotpos);
            fileName = srcFileName.substring(0, lastDotpos);
        } else {
            ext = "";
            fileName = srcFileName;
        }
        String exportPath = fileName + ext;
        boolean fileCreated = false;
        int equalCount = 0;


        File placeToSaveFile = new File(placeToSave);
        if (!placeToSaveFile.exists()) {
            placeToSaveFile.mkdirs();
        }
        if (!placeToSaveFile.exists()) {
            placeToSave = Environment.getExternalStorageDirectory().getPath() + "/Download";
        }
        while (!fileCreated) {
            file = new File(placeToSave, exportPath);
            if (!file.getParentFile().exists())
                file.getParentFile().mkdirs();
            else if (file.exists()) {
                exportPath = fileName + "(" + ++equalCount + ")" + ext;
            } else {
                try {
                    fileCreated = file.createNewFile();
                    file.delete();
                } catch (IOException e) {
                    UniversalHelper.logException(e);
                }
            }
        }


        return exportPath;
    }

    /**
     * Sets the pending state of a file to a provided value
     *
     * @param isPending current pending file state
     */
    public void setIsPending(boolean isPending) {
        this.isPending = isPending;
    }

    /**
     * Gets the pending state of a file
     *
     * @return true if file is being pending at the moment, returns false if not
     */
    public boolean isPending() {
        return isPending;
    }

    /**
     * Gets the mode of file sending: online or offline
     *
     * @return a string representation of the file send mode
     */
    public String getMode() {
        return mode == null ? Param.FileTransferMode.OFFLINE : mode;
    }

    /**
     * Gets the ID of a session key used for file encryption
     *
     * @return a string representation of the current session id
     */
    public String getSessionid() {
        return sessionid;
    }

    /**
     * Sets the id of a session key used for file encryption
     *
     * @param sessionid a string representation of the current session id
     */
    public void setSessionid(String sessionid) {
        this.sessionid = sessionid;
    }

    /**
     * Gets the unique id of a FileData instance
     *
     * @return a string representation of the unique id of a FileData instance
     */
    public String getFileId() {
        return fileId;
    }

    /**
     * Gets a name of a file source
     *
     * @return a string representing a name of a file source
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Gets the last time file source was modified
     *
     * @return a Date instance of last modification time
     */
    public Date getLastModified() {
        return lastModified;
    }

    /**
     * Gets the size of a file
     *
     * @return the file size in bytes
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Gets a path to a file source
     *
     * @return a string representing a path to a file source
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Gets an ID of a chat containing message with current fileData instance
     *
     * @return returns a string representing a path to a file source
     */
    public String getChatId() {
        return chatId;
    }

    /**
     * Gets a number of a current received or current sent chunk
     *
     * @return a number of a current received or current sent chunk
     */
    public long getCurrentChunk() {
        return currentChunk;
    }

    /**
     * Sets a number of a current received or current sent chunk
     *
     * @param currentChunk a number of a current received or current sent chunk
     */
    public void setCurrentChunk(long currentChunk) {
        this.currentChunk = currentChunk;
    }

    /**
     * Gets a progress of a send or receive operation for displaying
     *
     * @return a number of percents complete
     */
    public long getPercentcomplite() {

        float percent;
        percent = (((float) FileHelper.getMaxChunkSize() * (float) currentChunk) / (float) fileSize) * 100L;
        if (percent > 100) {
            percent = 100f;
        }
        return (long) percent;
    }

    /**
     * Gets a message instance containing current instance of FileData
     *
     * @return a DbChatMessage instance containing current instance of FileData
     */
    public DbChatMessage getMessage() {
        return message;
    }

    /**
     * Sets a message instance containing current instance of FileData
     *
     * @param message a DbChatMessage instance containing current instance of FileData
     */
    public void setMessage(DbChatMessage message) {
        this.message = message;
    }

    /**
     * Gets a thumbnail for an image file
     *
     * @return a bitmap containing a thumbnail for a current image file
     */
    public Bitmap getThumbnail() {
        return thumbnail;
    }

    /**
     * Sets a thumbnail for an image file
     *
     * @param thumbnail a bitmap containing a thumbnail for a current image file
     */
    public void setThumbnail(Bitmap thumbnail) {
        this.thumbnail = thumbnail;
    }
}
