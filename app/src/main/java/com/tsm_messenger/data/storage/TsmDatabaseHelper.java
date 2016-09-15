package com.tsm_messenger.data.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseArray;

import com.tsm_messenger.crypto.EdDsaSigner;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.UniversalHelper;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;


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

public class TsmDatabaseHelper {

    private static final String DBNAME = "tsmcf.db";
    private static final Map<Integer, DbSessionKey> localKeys = new HashMap<>();
    private static final Map<String, FileData> fileList = new HashMap<>();
    private static final String C_LOGIN_FIELD = "cPersId";
    private static final String CHAT_ID_FIELD = "chatid";
    private static final String SESSION_ID_FIELD = "sessionId";
    private static final String CONTENT_TYPE_FIELD = "contentType";
    private static final String FILE_TRANSFER_TABLE = "FileTransfer";
    private static DbManager dbHelper;
    private static TsmDatabaseHelper instance = null;
    private static SQLiteDatabase dbContent = null;
    private final Map<Integer, DbChatMessage> unsentMessage = new TreeMap<>();
    private Context context;

    private TsmDatabaseHelper(Context context) {
        this.context = context;
        dbHelper = new DbManager(context);
    }

    /**
     * Gets the current instance of a databaseHelper depending on a provided context
     *
     * @param context a current active activity
     * @return an instance of static databaseHelper object
     */
    public static TsmDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new TsmDatabaseHelper(context);
        }
        if (context != null) {
            instance.setContext(context);
        }
        return instance;
    }

    /**
     * Destroys database instance when the user closes the application
     */
    public static void destroyInstance() {
        instance = null;
        if (dbContent != null) {
            dbContent.close();
        }
        dbContent = null;

    }

    private void setContext(Context context) {
        if (this.context != context) {
            this.context = context;
        }
    }

    private synchronized SQLiteDatabase getDataBase() {
        while (dbHelper == null) {
            dbHelper = new DbManager(context);
        }
        try {
            while (dbContent == null || !dbContent.isOpen()) {
                UniversalHelper.debugLog(Log.DEBUG, "SRVST", "getDataBase");
                dbContent = dbHelper.getWritableDatabase(ActivityGlobalManager.getInstance().getEncryptKey());
                if (dbContent == null || !dbContent.isOpen()) {
                    wait(50);
                }
            }

            return dbContent;
        } catch (InterruptedException e) {
            UniversalHelper.logException(e);
            return getDataBase();
        }
    }

    /**
     * Deletes user from a local database
     *
     * @param row User object for delete
     * @return a new persIdent, generated for a deleted user to free his login
     */

    public synchronized String deleteUser(DbMessengerUser row) {
        SQLiteDatabase db = getDataBase();
        String oldPersIdent;
        String newPersIdent = null;
        if (db != null) {
            try {
                oldPersIdent = row.getPersId();
                newPersIdent = UUID.randomUUID().toString();

                db.execSQL("BEGIN  TRANSACTION ;");

                db.execSQL("update GroupMember set cPersId = '" + newPersIdent + "' " +
                        " where cPersId = '" + oldPersIdent + "';");
                db.execSQL("update chat_list set participantid = '" + newPersIdent + "' " +
                        " where participantid = '" + oldPersIdent + "';");
                db.execSQL("update MessengerUser set cPersId = '" + newPersIdent + "' ," +
                        " iStatus = " + row.getDbStatus().toString() +
                        " where cPersId = '" + oldPersIdent + "';");
            } catch (SQLException sqlE) {
                UniversalHelper.logException(sqlE);
                db.execSQL("ROLLBACK TRANSACTION ;");
                newPersIdent = null;
            }
        }
        return newPersIdent;
    }

    /**
     * Updates user information in local database
     *
     * @param row User object for update in database
     */
    public synchronized void updateMessengerUser(DbMessengerUser row) {
        SQLiteDatabase db = getDataBase();
        if (db != null) {

            ContentValues dataValue = new ContentValues();
            dataValue.put(C_LOGIN_FIELD, row.getPersId());
            dataValue.put("cNickName", row.getPersLogin());
            dataValue.put("cPersName", row.getPersName());
            dataValue.put("iStatus", row.getDbStatus());
            dataValue.put("publicKey", row.getPublicKey());
            db.insertWithOnConflict("MessengerUser", null, dataValue, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    /**
     * loads the keys to sign and decrypt messages into memory
     *
     * @return returns a string containing keys read
     */
    public String loadKeys() {
        SQLiteDatabase db = getDataBase();
        String keys = null;
        if (db != null) {
            Cursor cr = db.query("param", null, "", null, "", "", "");
            while (cr.moveToNext()) {
                keys = cr.getString(cr.getColumnIndex("info"));
            }
            cr.close();
        }
        return keys;
    }

    /**
     * saves the current user keys to a database
     */
    public void saveKeys() {
        SQLiteDatabase db = getDataBase();

        if (db != null) {
            String keys = EdDsaSigner.getInstance().exportDatabase();
            db.execSQL("delete from  param  ;");
            db.execSQL("INSERT INTO param (info) values ('" + keys + "') ;");
        }
    }

    /**
     * Selects list of users from a local database
     *
     * @return a map of users. key is userId, value is Bean object with user data
     */
    public synchronized Map<String, DbMessengerUser> selectMessengerUser() {
        Map<String, DbMessengerUser> list = new HashMap<>();
        SQLiteDatabase db = getDataBase();
        if (db != null) {
            Cursor cr = db.query("MessengerUser", null, "", null, "", "", "cPersName asc ");
            while (cr.moveToNext()) {
                DbMessengerUser row = new DbMessengerUser();
                row.setUserLogin(cr.getString(cr.getColumnIndex(C_LOGIN_FIELD)));
                row.setNickName(cr.getString(cr.getColumnIndex("cNickName")));
                row.setPersName(cr.getString(cr.getColumnIndex("cPersName")));
                row.setDbStatus(cr.getInt(cr.getColumnIndex("iStatus")));
                row.setPublicKey(cr.getString(cr.getColumnIndex("publicKey")));
                list.put(row.getPersId(), row);
            }
            cr.close();
        }
        return list;
    }


    /**
     * Selects list of groups, where provided user is a participant, from local database
     *
     * @param messengerUser Map with users which were loaded previously
     * @return the list of  DbGroupChat objects
     */
    public synchronized SparseArray<DbGroupChat> selectGroupChat(Map<String, DbMessengerUser> messengerUser) {
        SparseArray<DbGroupChat> list = new SparseArray<>();
        SQLiteDatabase db = getDataBase();
        if (db != null) {
            Cursor cr = db.query("GroupList", null, "", null, "", "", "");
            while (cr.moveToNext()) {
                DbGroupChat row = new DbGroupChat();
                row.setGroupId(cr.getInt(cr.getColumnIndex("groupId")));
                row.setGroupName(cr.getString(cr.getColumnIndex("cGroupName")));
                row.setType(cr.getString(cr.getColumnIndex("isPermanent")));
                list.put(row.getGroupId(), row);
            }
            cr.close();
            cr = db.query("GroupMember", null, "", null, "", "", "");
            DbGroupChat grp;
            DbMessengerUser user;
            String persIdent;
            Integer isLeave;
            while (cr.moveToNext()) {
                grp = list.get(cr.getInt(cr.getColumnIndex("groupid")));
                persIdent = cr.getString(cr.getColumnIndex(C_LOGIN_FIELD));
                isLeave = cr.getInt(cr.getColumnIndex("isleave"));
                user = messengerUser.get(persIdent);
                if (isLeave.equals(1)) {
                    user.leaveChat(grp.getGroupId());
                }

                if (grp != null) {
                    grp.addMember(persIdent, user);
                }
            }
            cr.close();
        }
        return list;
    }

    /**
     * Updates group data in a local database
     *
     * @param row Group object for save
     */
    public synchronized void updateGroupChat(DbGroupChat row) {
        SQLiteDatabase db = getDataBase();
        if (db == null) {
            return;
        }
        ContentValues dataValue = new ContentValues();
        dataValue.put("groupId", row.getGroupId());
        dataValue.put("cGroupName", row.getGroupName());
        dataValue.put("isPermanent", row.getType());
        try {
            db.execSQL("BEGIN  TRANSACTION ;");
            db.insertWithOnConflict("GroupList", null, dataValue, SQLiteDatabase.CONFLICT_REPLACE);
            if (row.getGroupId() == null)
                row.setGroupId(dbHelper.getLastInsertId(db));

            db.execSQL("delete from GroupMember where groupid =?  ;", new Object[]{row.getGroupId()});
            for (Map.Entry<String, DbMessengerUser> members : row.getMembers().entrySet()) {
                Integer isLeave;
                if (members.getValue().isChatLeave(row.getGroupId())) {
                    isLeave = 1;
                } else
                    isLeave = 0;
                db.execSQL("insert into GroupMember( groupId,cPersId,isleave) values (?,?,?) ; ",
                        new Object[]{row.getGroupId(),
                                members.getKey(),
                                isLeave
                        });
            }
            db.execSQL("COMMIT TRANSACTION ;");
        } catch (SQLException sqlE) {
            UniversalHelper.logException(sqlE);
            db.execSQL("ROLLBACK TRANSACTION ;");
        }
    }

    /**
     * Updates chat info in a local database
     *
     * @param chat Chat object for save
     */
    public synchronized void updateChatUnit(ChatUnit chat) {
        SQLiteDatabase db = getDataBase();
        if (db == null) {
            return;
        }
        if (chat.getSecureType() != null && chat.getSecureType().equals(Param.ChatSecureLevel.NOTHING_KEEP)) {
            return;
        }
        ContentValues dataValue = new ContentValues();
        dataValue.put(CHAT_ID_FIELD, chat.getChatId());
        dataValue.put("participantId", chat.getUnitId());
        dataValue.put("lastmessage", chat.getLastMessageText());
        dataValue.put("lasttime", chat.getLastTime());
        dataValue.put("isoutcast", chat.isOutcast());
        dataValue.put("hasFullHistory", chat.hasFullHistory());
        dataValue.put("lastsessionid", chat.getLastSessionId());
        dataValue.put("secureType", chat.getSecureType());

        db.insertWithOnConflict("chat_list", null, dataValue, SQLiteDatabase.CONFLICT_REPLACE);
        for (DbChatMessage msg : chat.getChatHistory()) {
            if (msg.getDbStatus() != DbChatMessage.MessageDatabaseStatus.SAVED) {
                updateChatMessage(msg, chat.getSecureType());
                chat.notifyChatHistory();
            }
        }
    }


    /**
     * Updates chat message in a local database
     *
     * @param msg            message object for save
     * @param chatsequreType Security type of chat. We don't save messages in the phantom chat
     */
    public synchronized void updateChatMessage(DbChatMessage msg, Integer chatsequreType) {

        if (msg.getContentType() == DbChatMessage.MSG_HISTORY) {
            return;
        }
        if (Integer.valueOf(Param.ChatSecureLevel.NOTHING_KEEP).equals(chatsequreType)) {
            msg.setMsgId(ActivityGlobalManager.getInstance().getPhantomMessageCounter());
        } else {
            SQLiteDatabase db = getDataBase();
            if (db == null || !db.isOpen()) {
                return;
            }
            String text;

            text = msg.getMessage();
            ContentValues dataValue = new ContentValues();
            dataValue.put("id", msg.getMsgId());
            dataValue.put(C_LOGIN_FIELD, msg.getLogin());
            dataValue.put("iType", msg.getType());
            dataValue.put("iTimeStamp", msg.getTimeStampInt());
            dataValue.put("cMessage", text);
            dataValue.put(CHAT_ID_FIELD, msg.getChatId());
            dataValue.put("serverState", msg.getServerstate());
            dataValue.put("srvid", msg.getServerId());
            dataValue.put(SESSION_ID_FIELD, msg.getSessionId());
            dataValue.put(CONTENT_TYPE_FIELD, msg.getContentType());
            dataValue.put("fileCancelCount", msg.getFileCancelCount());
            dataValue.put("fileOkCount", msg.getReceiveCount());
            dataValue.put("service_operation", msg.getServiceOperationCode());

            db.execSQL("BEGIN  TRANSACTION ;");
            db.insertWithOnConflict("chat_current", null, dataValue, SQLiteDatabase.CONFLICT_REPLACE);
            if (msg.getMsgId() == null) {
                msg.setMsgId(dbHelper.getLastInsertId(getDataBase()));
            }
            if ((msg.getServerstate() == DbChatMessage.MessageServerStatus.DECRYPTED) ||
                    (msg.getServerstate() == DbChatMessage.MessageServerStatus.DELIVERED)) {
                msg.setDbStatus(DbChatMessage.MessageDatabaseStatus.SAVED);
            }

            Object[] param = new Object[3];
            param[0] = text;
            param[1] = msg.getTimeStampInt();
            param[2] = msg.getChatId();
            db.execSQL("update chat_list set lastmessage = ? , lasttime = ?  where chatid = ?", param);
            db.execSQL("COMMIT  TRANSACTION ;");
        }
    }

    /**
     * Selects a list of chats from a local database
     *
     * @param messengerUser map of users to link them to the chat
     * @param groupChat     list of groups to link they to the chat
     * @return list of chats
     */

    public synchronized SparseArray<ChatUnit> selectChatList(
            Map<String, DbMessengerUser> messengerUser, SparseArray<DbGroupChat> groupChat) {

        SparseArray<ChatUnit> chatList = new SparseArray<>();
        Map<String, DbMessengerUser> participants;

        SQLiteDatabase db = getDataBase();
        if (db == null) {
            return chatList;
        }
        ChatUnit unit;
        String participantId;
        Integer chatId;
        Integer isOutcast;
        Integer hasFullHistory;
        Long lastTime;
        Integer groupId;
        Integer secureType;
        Calendar delDate = Calendar.getInstance();
        delDate.setTime(new Date());
        delDate.add(Calendar.MONTH, -1);
        Cursor cr = db.query("chat_list", null, "", null, "", "", "");
        while (cr.moveToNext()) {
            try {
                chatId = cr.getInt(cr.getColumnIndex(CHAT_ID_FIELD));
                isOutcast = getIsOutcast(cr);
                hasFullHistory = getHasFullHistory(cr);
                participantId = cr.getString(cr.getColumnIndex("participantid"));
                lastTime = cr.getLong(cr.getColumnIndex("lasttime"));
                DbGroupChat chatGroup = null;
                if (participantId.startsWith(CustomValuesStorage.GROUP_DESCRIPTIOR)) {
                    groupId = Integer.valueOf(participantId.substring(5));
                    participants = groupChat.get(groupId).getMembers();
                    chatGroup = groupChat.get(groupId);
                } else {
                    participants = new HashMap<>();
                    participants.put(participantId, messengerUser.get(participantId));
                }
                ArrayList<DbMessengerUser> users = new ArrayList<>();
                users.addAll(participants.values());

                secureType = cr.getInt(cr.getColumnIndex("secureType"));
                unit = new ChatUnit(chatId, participantId, users, isOutcast, chatGroup, secureType);
                unit.setLastTime(lastTime);
                unit.setLastSessionId(cr.getInt(cr.getColumnIndex("lastSessionId")));
                unit.setHasFullHistory(hasFullHistory == 1);

                chatList.put(chatId, unit);
            } catch (NullPointerException e) {
                UniversalHelper.logException(e);
            }
        }
        cr.close();

        return chatList;
    }

    @NonNull
    private Integer getHasFullHistory(Cursor cr) {
        Integer hasFullHistory;
        try {
            hasFullHistory = cr.getInt(cr.getColumnIndex("hasFullHistory"));
        } catch (NullPointerException ex) {
            UniversalHelper.logException(ex);
            hasFullHistory = 0;
        }
        return hasFullHistory;
    }

    @NonNull
    private Integer getIsOutcast(Cursor cr) {
        Integer isOutcast;
        try {
            isOutcast = cr.getInt(cr.getColumnIndex("isoutcast"));
        } catch (NullPointerException ex) {
            UniversalHelper.logException(ex);
            isOutcast = 0;
        }
        return isOutcast;
    }

    /**
     * Returns a map of messages which were not sent to the server
     *
     * @return sorted map of unsent messages
     */
    public SortedMap<Integer, DbChatMessage> getUnsendedMessage() {
        SortedMap<Integer, DbChatMessage> queue = new TreeMap<>(unsentMessage);
        unsentMessage.clear();
        return queue;
    }

    /**
     * Finds a message in a local database by server id
     *
     * @param srvId an unique server id
     * @return message object if found or null
     */
    public DbChatMessage selectMessageByServerId(String srvId) {
        SQLiteDatabase db = getDataBase();
        DbChatMessage row = null;
        if (db == null) {
            return null;
        }
        Cursor cr = db.query("chat_current", null, "srvid = '" + srvId + "'", null, "", "", "");
        while (cr.moveToNext()) {
            row = getDbChatMessage(cr);
        }
        cr.close();

        return row;
    }

    /**
     * Selects a list of messages for a concrete chat from local database
     *
     * @param chatUnit chat object for chat history loading
     * @return list of messages for a provided chat
     */

    public synchronized List<DbChatMessage> selectChatHistoryById(ChatUnit chatUnit) {
        List<DbChatMessage> chatHistory = new LinkedList<>();

        SQLiteDatabase db = getDataBase();
        if (db == null) {
            return chatHistory;
        }
        Integer chatId = chatUnit.getChatId();
        unsentMessage.clear();
        SparseArray<DbChatMessage> localFileList = new SparseArray<>();

        Cursor cr = db.query("chat_current", null, "chatid = " + chatId.toString(), null, "", "", "");
        DbChatMessage row;
        while (cr.moveToNext()) {
            row = getDbChatMessage(cr);

            if (row.getContentType() == DbChatMessage.MSG_FILE) {
                localFileList.put(row.getMsgId(), row);
            }

            chatHistory.add(row);

            if (chatUnit.getActiveParticipantsCount() > 0 &&
                    !chatUnit.isOutcast() && row.getContentType() != DbChatMessage.MSG_FILE) {
                if ((row.getServerstate() &
                        (DbChatMessage.MessageServerStatus.PREPARE |
                                DbChatMessage.MessageServerStatus.ENCRYPTED)) != 0) {
                    unsentMessage.put(row.getMsgId(), row);
                }
                if (row.getType() == DbChatMessage.MessageType.IN &&
                        (row.getServerstate() & DbChatMessage.MessageServerStatus.DELIVERED) == 0) {
                    unsentMessage.put(row.getMsgId(), row);
                }
            }
        }
        cr.close();
        parseFileMessages(localFileList);
        return chatHistory;
    }

    @NonNull
    private DbChatMessage getDbChatMessage(Cursor cr) {
        DbChatMessage row;
        row = new DbChatMessage();
        row.setMsgId(cr.getInt(cr.getColumnIndex("id")));
        row.setLogin(cr.getString(cr.getColumnIndex(C_LOGIN_FIELD)));
        row.setType(cr.getInt(cr.getColumnIndex("iType")));
        row.setTimeStamp(cr.getLong(cr.getColumnIndex("iTimeStamp")));
        row.setChatId(cr.getInt(cr.getColumnIndex(CHAT_ID_FIELD)));
        row.setMessage(cr.getString(cr.getColumnIndex("cMessage")));
        row.setDbStatus(DbChatMessage.MessageDatabaseStatus.SAVED);
        row.setServerstate(cr.getInt(cr.getColumnIndex("serverState")));
        row.setServerId(cr.getString(cr.getColumnIndex("srvid")));
        row.setSessionId(cr.getInt(cr.getColumnIndex(SESSION_ID_FIELD)));
        row.setReceiveCount(cr.getInt(cr.getColumnIndex("fileOkCount")));
        row.setCancelCount(cr.getInt(cr.getColumnIndex("fileCancelCount")));
        row.setServiceOperationByCode(cr.getInt(cr.getColumnIndex("service_operation")));
        setMessageContentType(cr, row);
        return row;
    }

    private void setMessageContentType(Cursor cr, DbChatMessage row) {
        if (cr.isNull(cr.getColumnIndex(CONTENT_TYPE_FIELD))) {
            row.setContentType(DbChatMessage.MSG_TEXT);
        } else {
            row.setContentType(cr.getInt(cr.getColumnIndex(CONTENT_TYPE_FIELD)));
            if (row.getContentType() == DbChatMessage.MSG_HISTORY)
                row.setContentType(DbChatMessage.MSG_TEXT);
        }
    }

    private void parseFileMessages(SparseArray<DbChatMessage> localFileList) {
        for (int i = 0; i < localFileList.size(); i++) {
            DbChatMessage msg = localFileList.valueAt(i);
            FileData file = selectFileData(msg.getMessage());
            msg.setFileData(file);
            if (msg.getType() == DbChatMessage.MessageType.OUT) {
                if (file.currentChunk == 0) {
                    msg.setServerstate(DbChatMessage.FileServerStatus.ERROR);
                    msg.setDbStatus(DbChatMessage.MessageDatabaseStatus.NEW);
                }
            } else {
                if (msg.getServerstate() != DbChatMessage.FileServerStatus.ERROR &&
                        file.getPercentcomplite() < 100 &&
                        !file.isPending()) {
                    unsentMessage.put(msg.getMsgId(), msg);
                }
            }
        }
    }

    /**
     * Gets a session key matching the provided session id
     *
     * @param sessionId id of the session needed
     * @return the session key object if found or null if not
     */
    public DbSessionKey sessionKeySelect(Integer sessionId) {
        DbSessionKey sessionKey = localKeys.get(sessionId);
        if (sessionKey != null)
            return sessionKey;

        SQLiteDatabase db = getDataBase();
        if (db == null) {
            return null;
        }
        Cursor cr = db.query("SessionKey", null, "sessionid = " + sessionId.toString(), null, "", "", "");
        if (cr.moveToNext()) {
            sessionKey = new DbSessionKey();
            sessionKey.setSessionId(sessionId);

            sessionKey.setKeyType(cr.getString(cr.getColumnIndex("keyType")));
            sessionKey.setOwnerKeyId(cr.getString(cr.getColumnIndex("ownerKeyId")));
            sessionKey.setParticipantPublicKey(cr.getString(cr.getColumnIndex("participantPublicKey")));
            String ssKey = cr.getString(cr.getColumnIndex("sessionKey"));
            sessionKey.setKeyStatus(DbSessionKey.ST_OK);
            sessionKey.setSessionKey(ssKey);
        }
        cr.close();
        localKeys.put(sessionId, sessionKey);
        return sessionKey;
    }

    /**
     * Saves a session key in the local database
     *
     * @param sessionKey a key to save
     */
    public void sessionKeySave(DbSessionKey sessionKey) {
        SQLiteDatabase db = getDataBase();
        if (db == null)
            return;
        ContentValues dataValue = new ContentValues();
        dataValue.put(SESSION_ID_FIELD, sessionKey.getSessionId());
        dataValue.put("keyType", sessionKey.getKeyType());
        dataValue.put("ownerKeyId", sessionKey.getOwnerKeyId());
        dataValue.put("participantPublicKey", sessionKey.getParticipantPublicKey());
        dataValue.put("sessionKey", sessionKey.getSessionKey());
        db.insertWithOnConflict("SessionKey", null, dataValue, SQLiteDatabase.CONFLICT_REPLACE);

        sessionKey.setKeyStatus(DbSessionKey.ST_OK);
        localKeys.put(sessionKey.getSessionId(), sessionKey);
    }

    /**
     * Gets the FileData matching provided file id
     *
     * @param fileId an id of a file needed
     * @return a FileData object if found or null if not
     */
    public FileData selectFileData(String fileId) {
        FileData row = fileList.get(fileId);
        if (row != null)
            return row;
        SQLiteDatabase db = getDataBase();
        if (db == null)
            return null;
        Cursor cr = db.query(FILE_TRANSFER_TABLE, null, "fileId = '" + fileId + "'", null, "", "", "");
        while (cr.moveToNext()) {
            String fileName = cr.getString(cr.getColumnIndex("fileName"));
            Date dateModified = new Date(cr.getLong(cr.getColumnIndex("dateModified")));
            long fileSize = cr.getLong(cr.getColumnIndex("fileSize"));
            String filePath = cr.getString(cr.getColumnIndex("filePath"));
            String sessionid = cr.getString(cr.getColumnIndex(SESSION_ID_FIELD));
            String chatId = cr.getString(cr.getColumnIndex(CHAT_ID_FIELD));
            long currentChunk = cr.getLong(cr.getColumnIndex("currentChunk"));
            String mode = cr.getString(cr.getColumnIndex("mode"));
            boolean isPending;
            try {
                isPending = cr.getLong(cr.getColumnIndex("isPending")) == 1;
            } catch (Exception e) {
                UniversalHelper.logException(e);
                isPending = false;
            }

            row = new FileData(fileId, fileName, dateModified, fileSize, filePath, sessionid, chatId, mode);
            row.setIsPending(isPending);
            row.setCurrentChunk(currentChunk);
            fileList.put(fileId, row);
        }
        cr.close();
        return row;
    }

    /**
     * Gets the list of files being downloaded at the moment
     *
     * @return a list containing all files being downloaded at the moment
     */
    public List<FileData> selectDownloads() {
        ArrayList<FileData> downloadsList = new ArrayList<>();
        FileData tmpFile;
        for (Map.Entry<String, FileData> entry : fileList.entrySet()) {
            tmpFile = entry.getValue();
            DbChatMessage msg = tmpFile.getMessage();
            if (msg.getType() == DbChatMessage.MessageType.IN &&
                    msg.getServerstate() != DbChatMessage.FileServerStatus.ERROR &&
                    (tmpFile.getPercentcomplite() < 100 ||
                            tmpFile.isPending())) {
                downloadsList.add(tmpFile);
            }
        }
        return downloadsList;
    }

    /**
     * Saves the provided FileData in the local database
     *
     * @param file a file to save
     */
    public void saveFile(FileData file) {
        SQLiteDatabase db = getDataBase();
        if (db == null) {
            return;
        }
        DbChatMessage message = file.getMessage();
        if (message != null && message.getType() == DbChatMessage.MessageType.OUT) {
            file.setIsPending(false);
        }
        ContentValues dataValue = new ContentValues();
        dataValue.put("fileId", file.getFileId() + "");
        dataValue.put("dateModified", file.getLastModified().getTime());
        dataValue.put("fileSize", file.getFileSize() + "");
        dataValue.put("filePath", file.getFilePath());
        dataValue.put("fileName", file.getFileName());
        dataValue.put(SESSION_ID_FIELD, file.getSessionid());
        dataValue.put(CHAT_ID_FIELD, file.getChatId());
        dataValue.put("currentChunk", String.valueOf(file.getCurrentChunk()));
        dataValue.put("mode", file.getMode());
        dataValue.put("isPending", file.isPending() ? 1 : 0);


        db.insertWithOnConflict(FILE_TRANSFER_TABLE, null, dataValue, SQLiteDatabase.CONFLICT_REPLACE);
        if (!fileList.containsKey(file.getFileId())) {
            fileList.put(file.getFileId(), file);
        }
    }

    /**
     * Deletes a provided file from a local database
     *
     * @param file a file to delete
     */
    public void deleteFile(FileData file) {
        SQLiteDatabase db = getDataBase();
        if (db == null) {
            return;
        }
        String[] fileIdArray = new String[1];
        fileIdArray[0] = file.getFileId() + "";
        db.delete(FILE_TRANSFER_TABLE, "fileId = ?", fileIdArray);
    }

    /**
     * Deletes a provided chat from a local database
     *
     * @param chatId an id of a chat to delete
     */
    public void dropChatById(int chatId) {
        SQLiteDatabase db = getDataBase();
        if (db == null) {
            return;
        }
        String[] chatIdArray = new String[1];
        chatIdArray[0] = Integer.toString(chatId);
        db.delete("chat_current", "chatid = ?", chatIdArray);
        db.delete("chat_list", "chatid = ?", chatIdArray);
    }

    /**
     * Deletes a provided group from a local database
     *
     * @param groupId an id of a group to delete
     */
    public void deleteGroupChat(int groupId) {
        SQLiteDatabase db = getDataBase();
        if (db == null) {
            return;
        }
        String[] groupIdArray = new String[1];
        groupIdArray[0] = String.valueOf(groupId);
        db.delete("GroupMember", "groupid = ?", groupIdArray);
        db.delete("GroupList", "groupId = ?", groupIdArray);
    }

    /**
     * A class managing a current database instance
     */
    class DbManager extends SQLiteOpenHelper {
        public static final int DATABASE_VERSION = 5;

        public DbManager(Context context) {
            super(context, DBNAME, null, DATABASE_VERSION);
        }

        @Override
        public synchronized void onCreate(SQLiteDatabase db) {
            String sql;
            sql = "CREATE TABLE MessengerUser (" +
                    "   cPersId  CHAR( 200 )   PRIMARY KEY," +
                    "   cNickName CHAR(200) ," +
                    "   cPersName CHAR( 512 )," +
                    "   iStatus  INTEGER ," +
                    "   publicKey CHAR(200) " +
                    "   );";
            db.execSQL(sql);

            sql = "CREATE TABLE chat_list ( " +
                    "    chatid        INTEGER  PRIMARY KEY," +
                    "    lastSessionId INTEGER ," +
                    "    isoutcast     INTEGER," +
                    "    hasFullHistory INTEGER, " +
                    "    participantid CHAR( 255 ) ," +
                    "    lastmessage   CHAR( 30 ) ," +
                    "    lasttime      INTEGER,  " +
                    "    secureType INTEGER " +
                    ");";
            db.execSQL(sql);

            sql = "CREATE TABLE chat_current ( " +
                    "    id         INTEGER     PRIMARY KEY AUTOINCREMENT," +
                    "    cPersId    CHAR( 200 )  REFERENCES MessengerUser ( cPersId ) ON DELETE CASCADE ON UPDATE CASCADE," +
                    "    chatid     INTEGER ," +
                    "    sessionId  INTEGER, " +
                    "    contentType INTEGER," +
                    "    service_operation INTEGER," +
                    "    iType      INT," +
                    "    iTimeStamp INTEGER ," +
                    "    cMessage   TEXT," +
                    "    iCounter   INTEGER, " +
                    "    serverState INTEGER, " +
                    "    fileCancelCount INTEGER, " +
                    "    fileOkCount INTEGER, " +
                    "    srvid     CHAR(100) );";
            db.execSQL(sql);
            sql = "CREATE TABLE GroupList ( " +
                    " groupId    INTEGER      PRIMARY KEY AUTOINCREMENT," +
                    " cGroupName CHAR( 200 ) ," +
                    " isPermanent CHAR (20)  " +
                    ");";
            db.execSQL(sql);
            sql = "CREATE TABLE GroupMember ( " +
                    "    memberid INTEGER     PRIMARY KEY AUTOINCREMENT," +
                    "    groupid  INTEGER," +
                    "    cPersId  CHAR( 200 )," +
                    "    isleave INTEGER  " +
                    ");";
            db.execSQL(sql);

            sql = "CREATE TABLE OutcomingMessagesHistory( " +
                    " messageId   INTEGER  PRIMARY KEY AUTOINCREMENT," +
                    " MESSAGE TEXT " +
                    ");";
            db.execSQL(sql);
            sql = "CREATE TABLE SessionKey( " +
                    " sessionid   INTEGER  PRIMARY KEY  ," +
                    " keyType CHAR(50) , " +
                    " ownerKeyId CHAR(200)," +
                    " participantPublicKey CHAR(200)," +
                    " sessionKey  CHAR(200) " +
                    ");";
            db.execSQL(sql);
            sql = "CREATE TABLE FileTransfer( " +
                    " fileId   CHAR(400)  PRIMARY KEY  ," +
                    " fileName CHAR(200) , " +
                    " dateModified BIGINT," +
                    " fileSize INTEGER," +
                    " filePath VARCHAR(300)," +
                    " sessionId VARCHAR(50)," +
                    " chatid VARCHAR(50)," +
                    " currentChunk INTEGER ," +
                    " mode VARCHAR(20) ,  " +
                    " isPending INTEGER" +
                    ");";
            db.execSQL(sql);

            sql = "CREATE TABLE param(  info  CHAR(400)  );";
            db.execSQL(sql);
        }

        @Override
        public synchronized void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE FileTransfer ADD COLUMN isPending INTEGER");
            }
            if (oldVersion < 4) {
                db.execSQL("CREATE TABLE param(  info  CHAR(400)  );");
                String keys = EdDsaSigner.getInstance().exportDatabase();
                db.execSQL("INSERT INTO param (info) values ('" + keys + "');");
            }
            if (oldVersion < 5) {
                db.execSQL("ALTER TABLE chat_current ADD COLUMN service_operation INTEGER");
            }

        }

        /**
         * Gets the last row index used for insert
         *
         * @param sdb a database instance used for analysis
         * @return an int number of last row id used for insert
         */
        public synchronized int getLastInsertId(SQLiteDatabase sdb) {
            int index = 0;

            Cursor cursor = sdb.rawQuery("select last_insert_rowid();", null);
            if (cursor.moveToFirst()) {
                index = cursor.getInt(0);
            }
            cursor.close();
            return index;
        }

    }

}
