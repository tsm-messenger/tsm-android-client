package com.tsm_messenger.connection;

import android.content.SharedPreferences;

import com.tsm_messenger.activities.BuildConfig;
import com.tsm_messenger.activities.main.MainActivity;
import com.tsm_messenger.data.storage.DbPreKey;
import com.tsm_messenger.protocol.transaction.Operation;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.protocol.transaction.Request;
import com.tsm_messenger.protocol.transaction.Request.AnswerNotificationRequest;
import com.tsm_messenger.protocol.transaction.Request.AuthorizeRequest;
import com.tsm_messenger.protocol.transaction.Request.BaseRequest;
import com.tsm_messenger.protocol.transaction.Request.ChunkGetRequest;
import com.tsm_messenger.protocol.transaction.Request.ChunkSendRequest;
import com.tsm_messenger.protocol.transaction.Request.FileAnswerRequest;
import com.tsm_messenger.protocol.transaction.Request.FileSendRequest;
import com.tsm_messenger.protocol.transaction.Request.GetLikeNamesRequest;
import com.tsm_messenger.protocol.transaction.Request.HistoryRequest;
import com.tsm_messenger.protocol.transaction.Request.InitSessionRequest;
import com.tsm_messenger.protocol.transaction.Request.InvitationRequest;
import com.tsm_messenger.protocol.transaction.Request.LeaveChatRequest;
import com.tsm_messenger.protocol.transaction.Request.MessageReadRequest;
import com.tsm_messenger.protocol.transaction.Request.MessageRequest;
import com.tsm_messenger.protocol.transaction.Request.NewContactRequest;
import com.tsm_messenger.protocol.transaction.Request.PreKeysRequest;
import com.tsm_messenger.protocol.transaction.Request.SendNotificationRequest;
import com.tsm_messenger.protocol.transaction.Request.SessionKeysRequest;
import com.tsm_messenger.protocol.transaction.Request.UsersRelationsDeleteRequest;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.SharedPreferencesAccessor;

import java.util.Date;
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
 */

public class MessagePostman {
    private static final MessagePostman INSTANCE = new MessagePostman();

    /**
     * Returns the current MessagePostman instance
     *
     * @return the current MessagePostman instance
     */
    public static MessagePostman getInstance() {
        return INSTANCE;
    }


    private SharedPreferences getAppPreference() {
        return ActivityGlobalManager.getInstance().getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE);
    }

    private Map<String, Object> genPersConHardMap() {
        SharedPreferences settings = getAppPreference();
        Map<String, Object> requestMap = genPersHardMap();
        String conIdent = settings.getString(BaseRequest.CON_IDENT, "");
        requestMap.put(BaseRequest.CON_IDENT, conIdent);

        return requestMap;
    }

    private Map<String, Object> genPersHardMap() {
        SharedPreferences settings = getAppPreference();
        Map<String, Object> requestMap = new HashMap<>();

        if (BuildConfig.DEBUG) {
            requestMap.put(SharedPreferencesAccessor.USER_ID, settings.getString(SharedPreferencesAccessor.USER_ID, ""));
        }

        return requestMap;
    }

    /**
     * Sends a getToken request to a the server
     */
    public void sendGetTokenRequest() {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.GET_TOKEN);
        Date now = new Date();

        MessageQueue.getInstance().sendMessage(String.valueOf("d:000;GT ** " + now.getTime()), requestMap, OutcomingMessage.PRIORITY_IMMEDIATE);
    }

    /**
     * Sends an initSession request to the server
     *
     * @param token      a security token received from the server
     * @param firstStart a flag showing if it's needed to show that app is started the moment before
     */
    public void sendInitSessionRequest(String token, boolean firstStart) {

        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.INIT_SESSION);
        requestMap.put(InitSessionRequest.TOKEN, token);
        requestMap.put(InitSessionRequest.APP_VERSION, BuildConfig.appVersionName);
        if (firstStart) {
            requestMap.put(InitSessionRequest.SET_TIME, Param.IsNeedOrIsTrue.SURE);
        }
        Date now = new Date();

        MessageQueue.getInstance().sendMessage(String.valueOf("d:000;IS ** " + now.getTime()), requestMap, OutcomingMessage.PRIORITY_IMMEDIATE);

    }

    /**
     * Sends a new send_notification operation to the server
     *
     * @param acceptors a list of users that are needed to notify about this request
     */
    public void sendNotificationRequest(List<String> acceptors) {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.SEND_NOTIFICATION);
        requestMap.put(
                SendNotificationRequest.TYPE,
                SendNotificationRequest.NotificationType.CONTACT_REQUEST);
        requestMap.put(
                SendNotificationRequest.ACCEPTORS, acceptors);

        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Sends a new searchContact operation to the server
     *
     * @param mask a filter word for usernames search
     */
    public void sendSearchContactRequest(String mask) {
        Map<String, Object> requestMap = genPersConHardMap();
        //get registration data from SharedPreferences
        requestMap.put(Param.OPERATION, Operation.GET_LIKE_NAMES);
        requestMap.put(GetLikeNamesRequest.NAME_PART, mask);

        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Sends a new request for a friendship to the server
     *
     * @param contact a selected contact for a friendship
     */
    public void sendCreateContactRequest(Map<String, Object> contact) {

        Map<String, Object> requestMap = genPersConHardMap();
        //get registration data from SharedPreferences
        requestMap.put(Param.OPERATION, Operation.NEW_CONTACT);
        requestMap.put(
                NewContactRequest.VALUES, contact);

        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Sends a new message to the server
     *
     * @param chatId    the String object with chat ID to send a message
     * @param messageId the unique message ID to identify it in chat
     * @param sessionId the ID of session to request a session key for message decryption
     * @param message   the String object with message text
     */
    public void sendChatMessageRequest(
            String chatId,
            String messageId,
            String sessionId,
            String message) {

        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.MESSAGE);
        requestMap.put(MessageRequest.CHAT_ID, chatId);
        requestMap.put(MessageRequest.MESSAGE_ID, messageId);
        requestMap.put(MessageRequest.SESSION_ID, sessionId);
        requestMap.put(MessageRequest.MESSAGE, message);

        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Creates a new chat at the server
     *
     * @param chatId           the unique chat ID
     * @param participantsList the list of chat participant's names
     */
    public void sendChatInvitationRequest(String chatId, List<String> participantsList) {

        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.INVITE);
        requestMap.put(
                InvitationRequest.CHAT_ID, chatId);
        requestMap.put(InvitationRequest.PARTICIPANTS, participantsList);

        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Sends a new authorize operation to the server
     *
     * @param chatId   a unique id for chat
     * @param chatName the String object which contains a custom string chat name
     * @param groupId
     */
    public void sendAuthorizeMessage(String chatId, String chatName,
                                     String groupId,
                                     List<String> participantsList,
                                     String secureLevel) {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.AUTHORIZE);
        requestMap.put(AuthorizeRequest.CHAT_ID, chatId);
        requestMap.put(AuthorizeRequest.GROUP_ID, groupId);
        requestMap.put(AuthorizeRequest.PARTICIPANTS, participantsList);
        requestMap.put(AuthorizeRequest.CHAT_NAME, chatName);
        requestMap.put(AuthorizeRequest.SECURE_LEVEL, secureLevel);
        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Sends a new answer for friendship request to the server
     *
     * @param acceptorsPersIdent the own login
     * @param answer             the answer that can be accept or decline
     */
    public void sendAnswerNotificationMessage(
            String acceptorsPersIdent, String answer) {

        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.ANSWER_NOTIFICATION);
        requestMap.put(AnswerNotificationRequest.ACCEPTOR, acceptorsPersIdent);
        requestMap.put(AnswerNotificationRequest.ANSWER, answer);

        ActivityGlobalManager manager = ActivityGlobalManager.getInstance();
        manager.minusOneUnreadRequest();
        if (manager.getCurrentActivity() instanceof MainActivity) {
            ((MainActivity) manager.getCurrentActivity()).updateUnreadRequestsCount();
        }

        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Sends a confirm for any operation to the server
     *
     * @param param     the prepared confirm request
     * @param messageId the string representation of a confirmed message ID
     */
    public void sendConfirm(Map<String, Object> param, String messageId) {

        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.CONFIRM);
        requestMap.putAll(param);

        MessageQueue.getInstance().sendMessage(messageId, requestMap);

    }

    /**
     * Sends a request for chat history to the server
     *
     * @param chatId              the ID of chat needed to load history
     * @param messageLastServerId the ID of a last message, received from the server
     * @param toDate              the date which is the end of history-load period
     */
    public void sendHistoryRequest(String chatId,
                                   String messageLastServerId,
                                   String toDate) {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.GET_HISTORY);
        requestMap.put(HistoryRequest.CHAT_ID, chatId);
        if (messageLastServerId != null)
            requestMap.put(HistoryRequest.MESSAGE_ID, messageLastServerId);
        if (toDate != null)
            requestMap.put(HistoryRequest.TO_DATE, toDate);
        requestMap.put(HistoryRequest.RECORDS_LIMIT, "30");

        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * sends a new preKeys package to the server
     *
     * @param newPreKeys a list of new preKeys, prepared for storage at the server
     */
    public void sendPreKeysPackage(List<DbPreKey.PairDbPreKey> newPreKeys) {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.PRE_KEYS);
        requestMap.put(PreKeysRequest.PRE_KEYS, newPreKeys);

        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Asks the server for the session key
     *
     * @param chatId    the ID of the chat, where the session is used
     * @param sessionId the ID of the requested session
     */
    public void sendSessionKeyAskRequest(String chatId,
                                         String sessionId) {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.SESSION_KEYS);
        requestMap.put(SessionKeysRequest.CHAT_ID, chatId);
        if (sessionId != null)
            requestMap.put(SessionKeysRequest.SESSION_ID, sessionId);

        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Sends an encrypted session key to store at the server
     *
     * @param chatId          the chat ID, where session is used
     * @param sessionId       the ID of a session having current encryption key
     * @param cloudSessionKey an encrypted session key
     */
    public void sendSessionKeyToCloudRequest(
            String chatId,
            String sessionId,
            String cloudSessionKey) {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.SESSION_KEYS);
        requestMap.put(SessionKeysRequest.CHAT_ID, chatId);
        requestMap.put(SessionKeysRequest.SESSION_ID, sessionId);
        requestMap.put(SessionKeysRequest.SESSION_SECRET_KEY, cloudSessionKey);
        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Sends a new session key to the server to set it as an encryption key for the current session
     *
     * @param chatId          ID of a chat, where current session is used
     * @param sessionId       ID of a session where the key is set
     * @param messageId       ID of a message encrypted with the new session key
     * @param fileId          ID of a file encrypted with the new session key
     * @param cloudSessionKey encrypted new session key to store it in cloud
     * @param keyBundle       copies of the session key, encrypted for each chat participant with his public key
     */
    public void sendSessionKeyInitRequest(
            String chatId,
            String sessionId,
            String messageId,
            String fileId,
            String cloudSessionKey,
            List<Map<String, String>> keyBundle) {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.SESSION_KEYS);
        requestMap.put(SessionKeysRequest.CHAT_ID, chatId);
        requestMap.put(SessionKeysRequest.SESSION_ID, sessionId);
        requestMap.put(SessionKeysRequest.MESSAGE_ID, messageId);
        requestMap.put(SessionKeysRequest.FILE_ID, fileId);
        requestMap.put(SessionKeysRequest.SESSION_SECRET_KEY, cloudSessionKey);
        requestMap.put(SessionKeysRequest.SSK_BUNDLE, keyBundle);
        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Sends a new leave chat request to the server
     *
     * @param chatId ID of a chat to leave
     */
    public void sendLeaveChatRequest(String chatId) {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.LEAVE);
        requestMap.put(LeaveChatRequest.CHAT_ID, chatId);
        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Sends a new send file request to the server
     *
     * @param fileName    the file name to show it at receiver's side
     * @param fileSize    the file size to check it for size limit and to show it at receiver's side
     * @param sendMode    the String value that defines a send scenario for file. Can be online or offline
     * @param localFileId the unique file ID to identify it at the server side and at receiver's side
     * @param sessionId   the ID of a session key which is used for file encryption
     */
    public void sendFileRequest(String fileName, long fileSize,
                                String sendMode,
                                String localFileId, String sessionId) {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.FILE_SEND);

        requestMap.put(FileSendRequest.FILE_NAME, fileName);
        requestMap.put(FileSendRequest.FILE_SIZE, String.valueOf(fileSize));
        requestMap.put(FileSendRequest.MODE, sendMode);
        requestMap.put(FileSendRequest.FILE_ID, localFileId);
        requestMap.put(FileSendRequest.SESSION_ID, sessionId);

        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Sends a new file chunk to the the server
     *
     * @param chunk       a string representation of a portion of bytes called chunk
     * @param chunkNumber the number of a portion of bytes
     * @param fileId      the unique id of a file, containing current chunk
     */
    public void sendFileChunkRequest(
            String chunk,
            String chunkNumber,
            String fileId) {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.CHUNK_SEND);
        requestMap.put(ChunkSendRequest.CHUNK, chunk);
        requestMap.put(ChunkSendRequest.CHUNK_ID, chunkNumber);
        requestMap.put(ChunkSendRequest.FILE_ID, fileId);


        MessageQueue.getInstance().sendMessage(null, requestMap, OutcomingMessage.PRIORITY_LOW);
    }

    /**
     * Sends an answer for file transfer proposal to the server
     *
     * @param answer   the answer can be accept or decline.
     *                 If accept - then file transfer is started, else - file transfer is canceled.
     * @param fileId   the unique id of a file to transfer or cancel
     * @param sendMode the String value that defines a send scenario for file. Can be online or offline
     * @param fileSize the file size to check it for size limit
     */
    public void sendFileAnswerRequest(String answer,
                                      String fileId,
                                      String sendMode,
                                      String fileSize) {

        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.FILE_ANSWER);

        requestMap.put(FileAnswerRequest.ANSWER, answer);
        requestMap.put(FileAnswerRequest.FILE_ID, fileId);
        requestMap.put(FileAnswerRequest.MODE, sendMode);
        requestMap.put(FileAnswerRequest.FILE_SIZE, fileSize);

        MessageQueue.getInstance().sendMessage(null, requestMap);
        if (Request.FileAnswerRequest.AnswerType.ACCEPT.equals(answer)) {
            ActivityGlobalManager.getInstance().addTransferringFile(fileId);
        }
    }

    /**
     * Sends a self-delete account request to the server
     */
    public void sendDeleteAccountRequest() {

        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.DELETE_USER_ACCOUNT);

        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Sends a request to break friendship with somebody to the server
     *
     * @param userLogin login of a friend to break friendship relation
     */
    public void sendDeleteRelationRequest(String userLogin) {

        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.DELETE_RELATIONS);
        requestMap.put(UsersRelationsDeleteRequest.RECIPIENT, userLogin);

        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Requests a new chunk of file from the server
     *
     * @param chunkId unique id of a current file chunk
     * @param fileId  unique id of a file containing current chunk
     */
    public void sendGetChunkRequest(String chunkId, String fileId) {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.CHUNK_GET);

        requestMap.put(ChunkGetRequest.CHUNK_ID, chunkId);
        requestMap.put(ChunkGetRequest.FILE_ID, fileId);

        MessageQueue.getInstance().sendMessage(null, requestMap, OutcomingMessage.PRIORITY_LOW);
    }

    /**
     * Sends a flag, that file send is finished, to the server
     *
     * @param fileId unique id of current finished file
     */
    public void sendFileFinish(String fileId) {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.FILE_IS_DELIVERED);
        requestMap.put(ChunkGetRequest.FILE_ID, fileId);

        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Sends a command to cancel file transfer to the server
     *
     * @param fileId an unique ID of a file to cancel
     */
    public void sendFileCancel(String fileId) {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.FILE_IS_CANCELED);
        requestMap.put(ChunkGetRequest.FILE_ID, fileId);

        MessageQueue.getInstance().sendMessage(null, requestMap);
    }

    /**
     * Sends a flag, that message is received and decrypted, to the server
     *
     * @param serverMessageId an unique ID of current message
     */
    public void sendReadConfirmRequest(String serverMessageId) {
        Map<String, Object> requestMap = genPersConHardMap();
        requestMap.put(Param.OPERATION, Operation.MESSAGE_HAS_BEEN_READ);

        requestMap.put(MessageReadRequest.SERVER_MESSAGE_ID, serverMessageId);
        MessageQueue.getInstance().sendMessage(null, requestMap);
    }
}



