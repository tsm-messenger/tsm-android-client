package com.tsm_messenger.data.storage;

import com.tsm_messenger.connection.MessagePostman;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.FileProgressListener;
import com.tsm_messenger.service.UniversalHelper;

import org.bouncycastle.util.encoders.Hex;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import com.tsm_messenger.helpers.FileHelper;

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
 * <p/>
 * <p/>
 */
public class DataFileStorage {
    public static final int ERR_FILE_MOVED_OR_DELETED = 1;
    public static final int ERR_FILE_WRITE = 3;
    public static final int ERR_CHUNK_EXIST = 4;
    public static final int RESULT_EOF = -1;
    public static final int RESULT_OK = 0;
    private final HashMap<String, FileData> filesMap = new HashMap<>();
    private final ArrayList<FileData> filesList = new ArrayList<>();
    private final TsmDatabaseHelper db;

    /**
     * A constructor that initializes all inner lists
     *
     * @param db a database helper instance to connect app database
     */
    public DataFileStorage(TsmDatabaseHelper db) {
        this.db = db;
        filesList.addAll(db.selectDownloads());
        sortFilesList();
        for (FileData file : filesList) {
            filesMap.put(file.getFileId(), file);
        }
    }

    private static byte[] readFileChunk(String fileName, long position, int chunkSize) throws IOException {
        byte[] result = new byte[chunkSize];

        File currentFile = new File(fileName);
        RandomAccessFile raf = new RandomAccessFile(currentFile, "r");
        raf.seek(position);
        raf.read(result, 0, chunkSize);
        raf.close();

        return result;
    }

    private static void checkFileTree(File fileToWrite) throws IOException {
        if (!fileToWrite.getParentFile().exists()) {
            fileToWrite.getParentFile().mkdirs();
        } else {
            if (fileToWrite.exists()) {
                fileToWrite.delete();
            }
        }
        if (!fileToWrite.createNewFile()) {
            throw new IOException();
        }
    }

    /**
     * Makes a record about a file in a memory and database
     *
     * @param newFileData a file to save
     */
    public void saveFileData(FileData newFileData) {
        int index = 0;
        filesMap.put(newFileData.getFileId(), newFileData);
        if (filesList.contains(newFileData)) {
            index = filesList.indexOf(newFileData);
            filesList.remove(newFileData);
        }
        DbChatMessage message = newFileData.getMessage();
        if (message != null && !message.getLogin().equals(ActivityGlobalManager.getInstance().getOwnLogin())) {
            filesList.add(index, newFileData);
        }
        db.saveFile(newFileData);
    }

    private void sortFilesList() {
        Collections.sort(filesList, new Comparator<FileData>() {
            @Override
            public int compare(FileData f1, FileData f2) {
                Date time1 = f1.getMessage().getTimeStamp();
                if (time1 == null) {
                    time1 = new Date();
                }

                Date time2 = f2.getMessage().getTimeStamp();
                if (time2 == null) {
                    time2 = new Date();
                }
                return time2.compareTo(time1);
            }
        });
    }

    /**
     * Gets a file instance by it's id
     *
     * @param fileId an id of a needed file
     * @return a file instance matching the provided id
     */
    public FileData get(String fileId) {
        FileData file = filesMap.get(fileId);
        if (file == null) {
            file = db.selectFileData(fileId);
            filesMap.put(fileId, file);
        }
        return file;
    }

    /**
     * Removes a file from memory and database
     *
     * @param fileId the ID of a file to remove
     */
    public void removeFileData(String fileId) {
        db.deleteFile(get(fileId));
        filesList.remove(get(fileId));
        filesMap.remove(fileId);
    }

    /**
     * Sets a time file was started to be sent
     *
     * @param fileId an id of a file to set the time
     * @param time   a time to set
     */
    public void setOnlineFileTime(String fileId, String time) {
        FileData dFile = get(fileId);
        if (dFile.getMessage() != null && time != null) {
            dFile.getMessage().setTimeStamp(time);
        }
    }

    /**
     * Reads a chunk of file and sends it to the server
     *
     * @param fileId id of a file to read
     * @param neededChunkNumber an offset to start chunk reading
     * @param time a time to refresh file info
     */
    public void readFileForSend(final String fileId, final int neededChunkNumber, String time) {
        if (get(fileId) == null) {
            return;
        }
        String filePath = get(fileId).getFilePath();
        File selectedFile = new File(filePath);
        String fileChunk;

        int count = FileHelper.getMaxChunkSize();

        long fileSize = selectedFile.length();
        long position = (long) count * neededChunkNumber;
        boolean isEof = false;
        if (position + FileHelper.getMaxChunkSize() >= fileSize) {
            count = (int) (fileSize - position);
            isEof = true;
        }

        FileData dFile = get(fileId);
        if (dFile.getMessage() != null && time != null) {
            dFile.getMessage().setTimeStamp(time);
            if (dFile.getMessage().getServerstate() == DbChatMessage.FileServerStatus.ERROR) {
                return;
            }
        }
        ActivityGlobalManager aManager = ActivityGlobalManager.getInstance();
        if (position <= fileSize) {
            try {
                DbSessionKey sessionKey = (TsmDatabaseHelper.getInstance(null)).
                        sessionKeySelect(Integer.valueOf(dFile.getSessionid()));

                fileChunk = Hex.toHexString(readFileChunk(filePath, position, count));
                fileChunk = sessionKey.encryptMessage(fileChunk);

                final String finalFileChunk = fileChunk;
                MessagePostman.getInstance().sendFileChunkRequest(
                        finalFileChunk, String.valueOf(neededChunkNumber), fileId);


            } catch (IOException e) {
                UniversalHelper.logException(e);

            }
            dFile.setCurrentChunk((long) (isEof ? neededChunkNumber + 1 : neededChunkNumber));
            if (!isEof) {
                aManager.addTransferringFile(fileId);
            } else {
                aManager.removeTranstferringFile(fileId);
            }

        } else {
            dFile.setCurrentChunk((long) (neededChunkNumber + 1));
            aManager.removeTranstferringFile(fileId);
        }


        aManager.getDbFileStorage().saveFileData(dFile);
        FileProgressListener fileProgressListener = aManager.getFileProgressListener();
        if (fileProgressListener != null) {
            fileProgressListener.fileProgressEvent(Integer.valueOf(dFile.getChatId()));
        }


    }

    /**
     * Appends a file with a new chunk
     *
     * @param chunk a byte array to append a file
     * @param fileId an id of a file to append
     * @param chunkId a number of chunk in an original file
     */
    public int writeChunk(byte[] chunk, String fileId, String chunkId) {
        String filePath = get(fileId).getFilePath();

        File fileToWrite = new File(filePath);

        if (!fileToWrite.exists() && !"0".equals(chunkId)) {
            return ERR_FILE_MOVED_OR_DELETED;
        } else try {
            if ("0".equals(chunkId)) {
                checkFileTree(fileToWrite);
            }
            RandomAccessFile raf = new RandomAccessFile(fileToWrite, "rw");

            long curLength = FileHelper.getMaxChunkSize() * Integer.valueOf(chunkId).longValue();
            raf.seek(curLength);
            raf.write(chunk);
            raf.close();
        } catch (IOException e) {
            UniversalHelper.logException(e);
            return ERR_FILE_WRITE;
        }
        long expectedSize = get(fileId).getFileSize();
        if (fileToWrite.length() >= expectedSize) {
            return RESULT_EOF;
        }
        return 0;
    }

    /**
     * Gets the list of all files for adapter
     *
     * @return a list of all files for adapter
     */
    public List<FileData> getAdapterList() {
        return filesList;
    }
}
