package com.tsm_messenger.connection;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.tsm_messenger.activities.service.BroadcastMessages;
import com.tsm_messenger.activities.service.ServiceParameters;
import com.tsm_messenger.activities.service.TsmBackgroundService;
import com.tsm_messenger.crypto.DhKeyData;
import com.tsm_messenger.crypto.EdDsaSigner;
import com.tsm_messenger.data.storage.ChatUnit;
import com.tsm_messenger.data.storage.DataAddressBook;
import com.tsm_messenger.data.storage.DataChatHistory;
import com.tsm_messenger.data.storage.DataFileStorage;
import com.tsm_messenger.data.storage.DbChatMessage;
import com.tsm_messenger.data.storage.DbMessengerUser;
import com.tsm_messenger.data.storage.DbSessionKey;
import com.tsm_messenger.data.storage.FileData;
import com.tsm_messenger.protocol.dto.DummyDto;
import com.tsm_messenger.protocol.transaction.Operation;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.protocol.transaction.Request;
import com.tsm_messenger.protocol.transaction.Response;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.FileProgressListener;
import com.tsm_messenger.service.SharedPreferencesAccessor;
import com.tsm_messenger.service.UniversalHelper;

import org.bouncycastle.util.encoders.DecoderException;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
public class IncomingMessageAsyncReceiver {
    private final Map<String, HashMap<String, Object>> action = new HashMap<>();
    Context context;
    Map<String, Object> responseMap;
    Operation operationCode;
    String messageId;

    /**
     * Starts parsing the message from the server
     *
     * @param context the link to service
     * @param message the message in JSON string
     */
    public void processIncomingMessages(Context context, String message) {
        this.context = context;
        Bundle bundle = doInBackground(message);
        onPostExecute(bundle);
    }

    protected void onPostExecute(Bundle bundle) {
        String broadcastMessage = bundle.getString(IncomingMessageAsyncReceiver.MessageParam.MESSAGE);

        if (broadcastMessage != null && !broadcastMessage.isEmpty()) {
            Intent wsResult = new Intent(broadcastMessage);
            wsResult.putExtra(BroadcastMessages.WS_PARAM, bundle);
            LocalBroadcastManager.getInstance(context).sendBroadcast(wsResult);
        }
    }

    protected Bundle doInBackground(String... message) {

        Bundle retVal = new Bundle();

        if (message.length < 1) {
            retVal.putBoolean(MessageParam.RESULT, false);
            return retVal;
        }
        DummyDto receiverDto = parseMessage(message[0]);

        if (receiverDto == null) {
            retVal.putBoolean(MessageParam.RESULT, false);
        } else {
            responseMap = receiverDto.getParams();
            messageId = receiverDto.getId();
            getOperationCode();
            boolean processed = false;

            Map<String, Object> confirmMap = new HashMap<>();
            confirmMap.put(Request.ConfirmRequest.OPERATION, operationCode.toString());
            confirmMap.put(Request.ConfirmRequest.REQUEST_TYPE,
                    Request.ConfirmRequest.RequestType.CONFIRMED
            );

            FileProgressListener fileProgressListener = ActivityGlobalManager.getInstance().getFileProgressListener();
            Response.BaseResponse.ErrorCode serverError = responseResultIsOk(responseMap);
            if (serverError == null) {
                switch (operationCode) {
                    case GET_TOKEN:
                        receiveGetToketResponse(responseMap);
                        break;
                    case INIT_SESSION:
                        receiveInitSessionResponse(responseMap, retVal);
                        break;
                    case CONFIRM:
                        processConfirmResponse(retVal);
                        break;
                    default:
                        processed = processAddressBookOperations(
                                operationCode, responseMap, retVal, fileProgressListener, confirmMap);
                }
            } else {
                switch (operationCode) {
                    case CONFIRM:
                        processErrorConfirmResponse(fileProgressListener);
                        break;
                    case MESSAGE_HAS_BEEN_READ:
                        processed = receiveMessageReadResponse(responseMap, retVal);
                        break;
                    case FILE_ANSWER:
                        processed = FileActionsParser.receiveFileAnswer(responseMap, fileProgressListener);
                        break;
                    case GET_TOKEN:
                        String codeString = (String) responseMap.get(Response.BaseResponse.ERROR_CODE);
                        retVal.putString(Response.BaseResponse.ERROR_CODE, codeString);
                        Response.BaseResponse.ErrorCode codeInstance;
                        try {
                            codeInstance = Response.BaseResponse.ErrorCode.valueOf(codeString);
                        } catch (Exception e) {
                            UniversalHelper.logException(e);
                            codeInstance = Response.BaseResponse.ErrorCode.SERVER_ERROR;
                        }
                        if (codeString != null && codeInstance
                                .equals(Response.BaseResponse.ErrorCode.ACCOUNT_IS_DELETED)) {
                            retVal.putString(MessageParam.MESSAGE, BroadcastMessages.WS_DELETE_ACCOUNT);
                        }
                        break;
                    default:
                        retVal.putString(MessageParam.MESSAGE, BroadcastMessages.WS_ERROR);
                        String errorCode = (String) responseMap.get(Response.BaseResponse.ERROR_CODE);
                        retVal.putString(Response.BaseResponse.ERROR_CODE, errorCode);
                        Response.BaseResponse.ErrorCode errCodeInstance;
                        try {
                            errCodeInstance = Response.BaseResponse.ErrorCode.valueOf(errorCode);
                        } catch (Exception e) {
                            UniversalHelper.logException(e);
                            errCodeInstance = Response.BaseResponse.ErrorCode.SERVER_ERROR;
                        }
                        if (errorCode != null && errCodeInstance
                                .equals(Response.BaseResponse.ErrorCode.USER_IS_NOT_CHAT_PARTICIPANT)) {
                            retVal.putInt(Response.InvitationResponse.CHAT_ID, Integer.valueOf((String) responseMap.get(Response.InvitationResponse.CHAT_ID)));
                        }
                        processed = true;
                }
            }
            sendConfirmIfNeeded(processed, confirmMap);

            retVal.putBoolean(MessageParam.RESULT, processed);
        }
        return retVal;

    }

    private void sendConfirmIfNeeded(boolean processed, Map<String, Object> confirmMap) {
        if (processed && messageId != null && messageId.startsWith("s:")) {
            MessagePostman.getInstance().sendConfirm(confirmMap, messageId);
        }
    }

    private void processConfirmResponse(Bundle retVal) {
        final String confirmOperName = (String) responseMap.get(Response.MessageResponse.OPERATION);
        Operation confirmOperation = Operation.getByName(confirmOperName);
        if (confirmOperation == Operation.DELETE_USER_ACCOUNT) {
            retVal.putString(MessageParam.MESSAGE, BroadcastMessages.WS_DELETE_ACCOUNT);
        }
        if (confirmOperation == Operation.DELETE_RELATIONS) {
            receiveDeleteRelationResponse(responseMap, false, retVal);
        }
    }

    private void processErrorConfirmResponse(FileProgressListener fileProgressListener) {
        final String confirmOperName = (String) responseMap.get(Response.MessageResponse.OPERATION);
        Operation confirmOperation = Operation.getByName(confirmOperName);
        if (confirmOperation == Operation.CHUNK_GET ||
                confirmOperation == Operation.CHUNK_SEND) {
            final String chunkId = (String) responseMap.get(Response.ChunkGetResponse.CHUNK_ID);
            final String fileId = (String) responseMap.get(Response.ChunkGetResponse.FILE_ID);
            final String errorName = (String) responseMap.get(Response.BaseResponse.ERROR_CODE);
            final Response.BaseResponse.ErrorCode error = Response.BaseResponse.ErrorCode.getByName(errorName);

            if (error == Response.BaseResponse.ErrorCode.CHUNK_READ_ERROR) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    UniversalHelper.logException(e);
                }
                MessagePostman.getInstance().sendGetChunkRequest(chunkId, fileId);
            } else {
                saveFileError(fileProgressListener, fileId);
            }
        }
    }

    private void saveFileError(FileProgressListener fileProgressListener, String fileId) {
        DataFileStorage dfStore = ActivityGlobalManager.getInstance().getDbFileStorage();
        final FileData dFile = dfStore.get(fileId);
        if (dFile != null && dFile.getMessage() != null) {

            dFile.getMessage().setServerstate(DbChatMessage.FileServerStatus.ERROR);
            dFile.getMessage().setDbStatus(DbChatMessage.MessageDatabaseStatus.NEW);
            ActivityGlobalManager.getInstance().getDbChatHistory().chatSaveMessage(dFile.getMessage().getChatId(), dFile.getMessage(), false);

            if (dFile.getMessage().getChatId() != null && fileProgressListener != null) {
                fileProgressListener.fileProgressEvent(Integer.valueOf(dFile.getChatId()));
            }
        }
    }

    private Response.BaseResponse.ErrorCode responseResultIsOk(Map<String, Object> responseMap) {
        String resStr = (String) responseMap.get(Response.BaseResponse.RESULT);
        Response.BaseResponse.ErrorCode errCode = null;

        if (resStr == null) {
            resStr = Response.BaseResponse.Result.OK;
        }
        boolean result = Response.BaseResponse.Result.OK.equals(resStr);
        if (!result) {
            Operation operation = Operation.getByName((String) responseMap.get(Param.OPERATION));
            try {
                String errName = (String) responseMap.get(Response.BaseResponse.ERROR_CODE);
                errCode = Response.BaseResponse.ErrorCode.valueOf(errName);
            } catch (Exception e) {
                UniversalHelper.logException(e);
                errCode = Response.BaseResponse.ErrorCode.SERVER_ERROR;
            }
            if (operation == Operation.CHUNK_GET ||
                    operation == Operation.CHUNK_RECEIVE ||
                    operation == Operation.CHUNK_SEND ||
                    operation == Operation.FILE_SEND) {
                receiveFileSendError(responseMap, errCode);
            }

        }
        return errCode;
    }

    private void receiveDeleteUserResponse(Map<String, Object> responseMap, Bundle bundle) {

        String userLogin = (String) responseMap.get(Response.DeleteUserAccountResponse.SENDER);
        bundle.putString(Response.DeleteUserAccountResponse.SENDER, userLogin);
        String userNickName = (String) responseMap.get(Response.DeleteUserAccountResponse.SENDER_NAME);
        bundle.putString(Response.DeleteUserAccountResponse.SENDER_NAME, userNickName);
        bundle.putString(MessageParam.MESSAGE, BroadcastMessages.getBroadcastOperation(Operation.DELETE_USER_ACCOUNT));

    }

    private void receiveDeleteRelationResponse(Map<String, Object> responseMap, boolean incoming, Bundle bundle) {

        String userLogin = (String) responseMap.get(Response.UsersRelationsDeleteResponse.SENDER);

        DbMessengerUser messengerContact = getApplication().getDbContact().getMessengerDb().get(userLogin);
        if (messengerContact == null) {
            return;
        }
        messengerContact.setStatusOnline(CustomValuesStorage.UserStatus.UNREACHABLE);
        if (messengerContact.getDbStatus() != CustomValuesStorage.CATEGORY_UNKNOWN) {
            messengerContact.setDbStatus(CustomValuesStorage.CATEGORY_NOTCONNECT);
        }

        ActivityGlobalManager.getInstance().getDbContact().saveMessengerUser(messengerContact);

        if (incoming) {
            bundle.putString(MessageParam.MESSAGE, BroadcastMessages.WS_DELETE_RELATION);
            bundle.putString(Response.UsersRelationsDeleteResponse.SENDER, userLogin);
        } else {
            bundle.putString(MessageParam.MESSAGE, BroadcastMessages.WS_CONTACT_STATUS);
        }

    }

    private void receiveCreateContactResponse(Map<String, Object> responseMap, Bundle bundle) {

        bundle.putString(MessageParam.MESSAGE, BroadcastMessages.getBroadcastOperation(Operation.NEW_CONTACT));
        String result = (String) responseMap.get(Response.NewContactResponse.RESULT);
        bundle.putString(Response.NewContactResponse.RESULT, result);
        if (result.equals(Response.BaseResponse.Result.OK)) {
            Map resultValue;
            resultValue = (Map) responseMap.get(Response.NewContactResponse.VALUES);
            bundle.putString(Response.NewContactResponse.ValuesType.PERS_IDENT,
                    (String) resultValue.get(Response.NewContactResponse.ValuesType.PERS_IDENT));
            bundle.putString(Response.NewContactResponse.ValuesType.PERS_NAME,
                    (String) resultValue.get(Response.NewContactResponse.ValuesType.PERS_NAME));
            bundle.putString(Response.NewContactResponse.ValuesType.PERS_ID,
                    (String) resultValue.get(Response.NewContactResponse.ValuesType.PERS_ID));

        }
    }

    private void receiveFileSendError(Map<String, Object> responseMap, Response.BaseResponse.ErrorCode errorCode) {
        String fileId = (String) responseMap.get(Response.FileSendResponse.FILE_ID);
        ActivityGlobalManager app = ActivityGlobalManager.getInstance();
        DataFileStorage dbFileStorage = app.getDbFileStorage();

        if (errorCode != null && errorCode.equals(Response.BaseResponse.ErrorCode.FILE_TOO_LARGE)) {
            if (fileId != null) {
                dbFileStorage.removeFileData(fileId);
            }
        } else {
            if (fileId == null) {
                return;
            }

            FileData errorFile = dbFileStorage.get(fileId);

            Integer chatId = errorFile.getMessage().getChatId();
            ChatUnit chat = app.getDbChatHistory().getChat(chatId);

            DbChatMessage fileMessage = chat.findMessageByServerId(errorFile.getFileId());
            fileMessage.setServerstate(DbChatMessage.FileServerStatus.ERROR);
            fileMessage.setDbStatus(DbChatMessage.MessageDatabaseStatus.NEW);
            app.getDbChatHistory().chatSaveMessage(chatId, fileMessage, false);

            FileProgressListener fileProgressListener = app.getFileProgressListener();
            if (fileProgressListener != null) {
                fileProgressListener.fileProgressEvent(chat.getChatId());
            }
            app.removeTranstferringFile(fileId);
        }
    }

    private boolean processAddressBookOperations(
            Operation operationCode, Map<String, Object> responseMap, Bundle retVal,
            FileProgressListener fileProgressListener, Map<String, Object> confirmMap) {

        boolean processed;

        switch (operationCode) {
            case GET_LIKE_NAMES:
                retVal.putString(MessageParam.MESSAGE, BroadcastMessages.getBroadcastOperation(Operation.GET_LIKE_NAMES));
                retVal.putStringArrayList(Response.GetLikeNamesResponse.LIKE_NAMES,
                        (ArrayList<String>) responseMap.get(Response.GetLikeNamesResponse.LIKE_NAMES));
                processed = true;
                break;
            case NEW_CONTACT:
                receiveCreateContactResponse(responseMap, retVal);
                processed = true;
                break;
            default:
                processed = processContactEventOperations(
                        operationCode, responseMap, retVal, fileProgressListener, confirmMap);
        }

        return processed;
    }

    private boolean processContactEventOperations(
            Operation operationCode, Map<String, Object> responseMap, Bundle retVal,
            FileProgressListener fileProgressListener, Map<String, Object> confirmMap) {

        boolean processed;

        switch (operationCode) {
            case SEND_NOTIFICATION:
                receiveNotification(responseMap, retVal);
                processed = true;
                break;
            case ANSWER_NOTIFICATION:
                receiveNotificationAnswer(responseMap, retVal);
                processed = true;
                break;
            case CONTACT_ONLINE:
            case CONTACT_OFFLINE:
            case CONTACT_UNREACHABLE:
                processed = true;
                receiveContactStatus(responseMap, operationCode, retVal);
                break;
            case DELETE_USER_ACCOUNT:
                receiveDeleteUserResponse(responseMap, retVal);
                processed = true;
                break;
            case DELETE_RELATIONS:
                receiveDeleteRelationResponse(responseMap, true, retVal);
                processed = true;
                break;
            default:
                processed = processChatOperations(
                        operationCode, responseMap, retVal, fileProgressListener, confirmMap);
        }

        return processed;
    }

    private boolean processChatOperations(
            Operation operationCode, Map<String, Object> responseMap, Bundle retVal,
            FileProgressListener fileProgressListener, Map<String, Object> confirmMap) {

        boolean processed;

        switch (operationCode) {
            case SESSION_KEYS:
                processed = receiveSessionKey(responseMap);
                break;
            case PRE_KEYS:
                processed = sendPreKeysPortion(responseMap);
                break;
            case GET_PEER_PRE_KEY:
                processed = receivePeerPreKeysPackage(responseMap);
                break;
            case INVITE:
                receiveInviteForChat(retVal);
                processed = true;
                break;
            case AUTHORIZE:
                receiveAuthorizeForChat(retVal);
                processed = true;
                break;
            case LEAVE:
                receiveLeaveChatResponse(retVal);
                processed = true;
                break;
            default:
                processed = processMessageOperations(
                        operationCode, responseMap, retVal, fileProgressListener, confirmMap);
        }

        return processed;
    }

    private boolean processMessageOperations(
            Operation operationCode, Map<String, Object> responseMap, Bundle retVal,
            FileProgressListener fileProgressListener, Map<String, Object> confirmMap) {

        boolean processed;

        switch (operationCode) {
            case GET_HISTORY:
                processed = receiveHistory(responseMap, retVal);
                break;
            case MESSAGE:
                processed = receiveChatMessage(responseMap, retVal, confirmMap);
                break;
            case MESSAGE_HAS_BEEN_READ:
                processed = receiveMessageReadResponse(responseMap, retVal);
                break;
            default:
                processed = processFileOperations(
                        operationCode, responseMap, retVal, fileProgressListener);
        }

        return processed;
    }

    private boolean processFileOperations(
            Operation operationCode, Map<String, Object> responseMap, Bundle retVal,
            FileProgressListener fileProgressListener) {

        boolean processed;

        switch (operationCode) {
            case FILE_ANSWER:
                processed = FileActionsParser.receiveFileAnswer(responseMap, fileProgressListener);
                break;
            case FILE_SEND:
                processed = FileActionsParser.receiveFileSendResponse(responseMap, retVal);
                break;
            case CHUNK_GET:
                processed = FileActionsParser.receiveChunkSendResponse(
                        responseMap, Param.FileTransferMode.ONLINE);
                break;
            case CHUNK_SEND:
                processed = FileActionsParser.receiveChunkSendResponse(
                        responseMap, Param.FileTransferMode.OFFLINE);
                break;
            case CHUNK_RECEIVE:
                processed = FileActionsParser.receiveChunkReceiveResponse(responseMap);
                break;
            case FILE_IS_CANCELED:
                processed = FileActionsParser.receiveFileCancel(responseMap, retVal);
                break;
            case FILE_IS_DELIVERED:
                processed = FileActionsParser.receiveFileDelivered(responseMap);
                break;
            case FILE_RECEIVE:
                FileActionsParser.receiveFileReceiveResponse(responseMap, retVal);
                processed = true;
                break;
            default:
                processed = false;
        }

        return processed;
    }

    private void receiveLeaveChatResponse(Bundle retVal) {
        retVal.putString(MessageParam.MESSAGE, BroadcastMessages.getBroadcastOperation(Operation.LEAVE));
        retVal.putInt(Response.LeaveChatResponse.CHAT_ID, Integer.valueOf((String) responseMap.get(Response.LeaveChatResponse.CHAT_ID)));
        retVal.putString(Response.LeaveChatResponse.LEAVE_PERS_IDENT, (String) responseMap.get(Response.LeaveChatResponse.LEAVE_PERS_IDENT));
    }

    private void receiveAuthorizeForChat(Bundle retVal) {
        Integer chatId = Integer.valueOf((String) responseMap.get(Response.AuthorizeResponse.CHAT_ID));
        String groupId = (String) responseMap.get(Response.AuthorizeResponse.GROUP_ID);
        String mode = (String) responseMap.get(Response.AuthorizeResponse.MODE);

        retVal.putString(MessageParam.MESSAGE, BroadcastMessages.getBroadcastOperation(Operation.AUTHORIZE));
        retVal.putInt(Response.AuthorizeResponse.CHAT_ID, chatId);
        retVal.putString(Response.AuthorizeResponse.GROUP_ID, groupId);
        retVal.putString(Response.AuthorizeResponse.MODE, mode);
        String auSecType = (String) responseMap.get(Response.AuthorizeResponse.SECURE_LEVEL);
        Integer auSecureLevel;
        try {
            auSecureLevel = Integer.valueOf(auSecType);
        } catch (NumberFormatException ne) {
            auSecureLevel = -2;
        }
        retVal.putInt(Response.InvitationResponse.SECURITY_LEVEL, auSecureLevel);
    }

    private void receiveInviteForChat(Bundle retVal) {
        retVal.putString(MessageParam.MESSAGE, BroadcastMessages.getBroadcastOperation(Operation.INVITE));
        retVal.putString(Response.AuthorizeResponse.CHAT_ID,
                (String) responseMap.get(Response.AuthorizeResponse.CHAT_ID));

        retVal.putStringArrayList(Response.InvitationResponse.PARTICIPANTS,
                (ArrayList<String>) responseMap.get(Response.InvitationResponse.PARTICIPANTS));

        retVal.putString(Response.InvitationResponse.CHAT_NAME,
                (String) responseMap.get(Response.InvitationResponse.CHAT_NAME));

        retVal.putString(Response.InvitationResponse.INVITORS_PERS_IDENT,
                (String) responseMap.get(Response.InvitationResponse.INVITORS_PERS_IDENT));
        retVal.putString(Response.InvitationResponse.DATE, (String) responseMap.get(Response.InvitationResponse.DATE));
        String secType = (String) responseMap.get(Response.InvitationResponse.SECURITY_LEVEL);
        Integer secureLevel;

        try {
            secureLevel = Integer.valueOf(secType);
        } catch (NumberFormatException ne) {
            secureLevel = -2;
        }
        retVal.putInt(Response.InvitationResponse.SECURITY_LEVEL, secureLevel);
    }

    private void getOperationCode() {
        try {
            String opName = (String) responseMap.get(Param.OPERATION);
            operationCode = Operation.getByName(opName);
        } catch (ClassCastException cce) {
            UniversalHelper.logException(cce);
            operationCode = Operation.LIVE_MESSAGE;
        }
    }

    private boolean receiveChatMessage(Map<String, Object> responseMap, Bundle bundle, Map<String, Object> confirmMap) {
        Integer chatId = Integer.valueOf((String) responseMap.get(Response.MessageResponse.CHAT_ID));
        String responseType = (String) responseMap.get(Response.MessageResponse.RESPONSETYPE);
        DataChatHistory dbChatHistory = ActivityGlobalManager.getInstance().getDbChatHistory();

        HashMap<String, Object> uiParam = new HashMap<>();
        uiParam.put(Response.MessageResponse.RESPONSETYPE, responseType);
        uiParam.put(Response.MessageResponse.CHAT_ID, responseMap.get(Response.MessageResponse.CHAT_ID));
        sendPreKeysPortion(responseMap);

        ChatUnit chat = dbChatHistory.getChat(chatId);
        if (chat == null) {
            // We received MESSAGE to chat wich does not exist in database
            // We need to send authorise for this chat
            chat = new ChatUnit(chatId);
            dbChatHistory.addChat(chat);
            dbChatHistory.saveChat(chat);

            ArrayList<String> participantsList = new ArrayList<>();
            MessagePostman.getInstance().sendChatInvitationRequest(chatId.toString(), participantsList);
        }

        if (responseType.equals(Response.MessageResponse.ResponseType.CONFIRM)) {
            // server confirm MESSAGE
            String msgId = (String) responseMap.get(Response.MessageResponse.MESSAGE_ID);
            DbChatMessage curMsg = chat.getPreparedMessage(msgId);
            if (curMsg == null) {
                curMsg = chat.findMessageById(Integer.valueOf(msgId));
            }

            if (curMsg != null) {
                if (curMsg.getContentType() == DbChatMessage.MSG_TEXT) {
                    //we change these parameters only for text messages
                    curMsg.setServerstate(DbChatMessage.MessageServerStatus.SEND);
                    String serverMessageId = (String) responseMap.get(Response.MessageResponse.SERVER_MESSAGE_ID);
                    curMsg.setServerId(serverMessageId);
                }

                chat.preparedMessageSent(msgId);

                String deliveryDate = (String) responseMap.get(Response.MessageResponse.DATE);
                DateFormat df = new SimpleDateFormat(Param.DATE_FORMAT, Locale.US);

                Date receiveDate;

                try {
                    receiveDate = df.parse(deliveryDate);
                } catch (ParseException pe) {
                    UniversalHelper.logException(pe);
                    receiveDate = new Date();
                }
                curMsg.setTimeStamp(receiveDate.getTime());
                chat.confirmMessage(ChatUnit.SESSION_OK);
                dbChatHistory.saveChat(chat);
                HashMap<String, Object> map = new HashMap<>();
                map.put(Response.MessageResponse.RESPONSETYPE, responseType);
                action.put(MessageParam.Action.REFRESHCHAT, map);
            } else {
                chat.confirmMessage(ChatUnit.SESSION_OK);
            }

            bundle.putString(MessageParam.MESSAGE, BroadcastMessages.WS_FILEACTION);

        } else {
            bundle.putString(MessageParam.MESSAGE, BroadcastMessages.WS_NEWMESSAGE);

            if (responseType.equals(Response.MessageResponse.ResponseType.SEND)) {
                putMessageToChat(responseMap, dbChatHistory, uiParam, chat);
            }
        }

        action.put(MessageParam.Action.REFRESHCHATLIST, uiParam);
        bundle.putInt(Response.MessageResponse.CHAT_ID, chatId);
        bundle.putInt(BroadcastMessages.MessagesParam.UNREAD, chat.getUnreadMessageCount());
        bundle.putString(BroadcastMessages.MessagesParam.CHAT_NAME, chat.getChatName());
        bundle.putBoolean(BroadcastMessages.MessagesParam.SHOW_NOTIFICATION, true);
        bundle.putString(Response.MessageResponse.RESPONSETYPE, responseType);


        if (responseMap.get(Response.MessageResponse.RESPONSETYPE).equals(Response.MessageResponse.ResponseType.SYNC)) {
            confirmMap.put(Request.ConfirmRequest.REQUEST_TYPE,
                    Request.ConfirmRequest.RequestType.SYNCHRONIZED
            );
        } else if (responseMap.get(Response.MessageResponse.RESPONSETYPE).equals(Response.MessageResponse.ResponseType.SEND)) {
            confirmMap.put(Request.ConfirmRequest.REQUEST_TYPE,
                    Request.ConfirmRequest.RequestType.CONFIRMED
            );
        }

        return true;
    }

    private void putMessageToChat(Map<String, Object> responseMap, DataChatHistory dbChatHistory, HashMap<String, Object> uiParam, ChatUnit chat) {
        int type;
        if (responseMap.get(Response.MessageResponse.PERS_IDENT).equals(
                getApplication().getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE)
                        .getString(SharedPreferencesAccessor.USER_ID, "")
        )
                ) {
            type = DbChatMessage.MessageType.OUT;
        } else {
            type = DbChatMessage.MessageType.IN;
        }
        try {
            chat.putMessage(responseMap, type, false, false, dbChatHistory);
            dbChatHistory.saveChat(chat);
            dbChatHistory.sortList(null);
        } catch (Exception e) {
            UniversalHelper.logException(e);
        }

        action.put(MessageParam.Action.NEW_MESSAGE, uiParam);
    }

    private void receiveContactStatus(Map<String, Object> responseMap, Operation op, Bundle bundle) {
        CustomValuesStorage.UserStatus stat;

        switch (op) {
            case CONTACT_ONLINE:
                stat = CustomValuesStorage.UserStatus.ONLINE;
                break;
            case CONTACT_OFFLINE:
                stat = CustomValuesStorage.UserStatus.OFFLINE;
                break;
            case CONTACT_UNREACHABLE:
                stat = CustomValuesStorage.UserStatus.UNREACHABLE;
                break;
            default:
                return;
        }

        String userLogin = (String) responseMap.get(Response.ContactOnlineResponse.PARTICIPANT);
        DbMessengerUser messengerContact = getApplication().getDbContact().getMessengerDb().get(userLogin);
        if (messengerContact == null || messengerContact.getStatus() != CustomValuesStorage.CATEGORY_CONNECT)
            return;
        messengerContact.setStatusOnline(stat);
        bundle.putString(MessageParam.MESSAGE, BroadcastMessages.WS_CONTACT_STATUS);
    }

    private boolean receiveMessageReadResponse(Map<String, Object> responseMap, Bundle bundle) {

        String serverMessageId = (String) responseMap.get(Response.MessageReadResponce.SERVER_MESSAGE_ID);
        String result = (String) responseMap.get(Response.BaseResponse.RESULT);
        DbChatMessage readMessage;
        DataChatHistory dbChatHistory = ActivityGlobalManager.getInstance().getDbChatHistory();
        ChatUnit currentChat;
        Integer chatId;
        if (Response.BaseResponse.Result.FAILED.equals(result)) {

            String serverId = (String) responseMap.get(Response.MessageReadResponce.SERVER_MESSAGE_ID);
            readMessage = dbChatHistory.getMessageByServerId(serverId);

            if (readMessage != null) {
                chatId = readMessage.getChatId();
                currentChat = dbChatHistory.getChat(chatId);
            } else {
                return true;
            }
        } else {
            if (responseMap.get(Response.MessageReadResponce.CHAT_ID) == null) {
                return true;
            }
            chatId = Integer.valueOf((String) responseMap.get(Response.MessageReadResponce.CHAT_ID));
            currentChat = dbChatHistory.getChat(chatId);
            if (currentChat == null) {
                return true;
            }
            readMessage = currentChat.findMessageByServerId(serverMessageId);
        }
        if (readMessage != null) {
            if (readMessage.getType() == DbChatMessage.MessageType.OUT) {
                readMessage.setServerstate(DbChatMessage.MessageServerStatus.DELIVERED);
            } else {
                readMessage.setServerstate(
                        readMessage.getServerstate() |
                                DbChatMessage.MessageServerStatus.DELIVERED);

            }
            readMessage.setDbStatus(DbChatMessage.MessageDatabaseStatus.NEW);
            dbChatHistory.chatSaveMessage(chatId, readMessage, false);

            bundle.putInt(Response.MessageResponse.CHAT_ID, chatId);

            bundle.putInt(BroadcastMessages.MessagesParam.UNREAD, currentChat.getUnreadMessageCount());
            bundle.putString(BroadcastMessages.MessagesParam.CHAT_NAME, currentChat.getChatName());
            bundle.putBoolean(BroadcastMessages.MessagesParam.SHOW_NOTIFICATION, false);

            bundle.putString(MessageParam.MESSAGE, BroadcastMessages.WS_FILEACTION);
        }
        return true;
    }

    private void receiveNotificationAnswer(Map<String, Object> responseMap, Bundle bundle) {
        String userId;
        int type;
        DbMessengerUser messengerUser;
        userId = (String) responseMap.get(Response.AnswerNotificationResponse.SENDER_ID);
        String nickname = (String) responseMap.get(Response.AnswerNotificationResponse.SENDER);

        if (userId.equals(getApplication().getSettings().getString(SharedPreferencesAccessor.USER_ID, ""))) {
            if (responseMap.get(Response.ContactOnlineResponse.PARTICIPANT) instanceof String) {
                userId = (String) responseMap.get(Response.ContactOnlineResponse.PARTICIPANT);
            } else {
                ArrayList<String> list = (ArrayList<String>) responseMap.get(Response.ContactOnlineResponse.PARTICIPANT);
                if (list == null || list.isEmpty()) {
                    return;
                }
                userId = list.get(0);
            }
            type = CustomValuesStorage.CATEGORY_CONFIRM_OUT;
        } else {
            nickname = (String) responseMap.get(Response.AnswerNotificationResponse.SENDER);
            type = CustomValuesStorage.CATEGORY_CONFIRM_IN;

        }

        messengerUser = getApplication().getDbContact().getMessengerDb().get(userId);

        if (messengerUser == null) {
            messengerUser = new DbMessengerUser();
            messengerUser.setUserLogin(userId);
            if (nickname == null || nickname.isEmpty()) {
                nickname = userId;
            }
            messengerUser.setNickName(nickname);
            messengerUser.setPersName(nickname);
        } else {
            if (messengerUser.getPersName() == null
                    || messengerUser.getPersName().isEmpty()
                    || messengerUser.getPersName().equals(messengerUser.getPersId())) {
                messengerUser.setPersName(nickname);
            }
        }

        bundle.putString(Response.AnswerNotificationResponse.SENDER_ID, userId);
        bundle.putString(Response.AnswerNotificationResponse.SENDER, nickname);
        bundle.putInt(Response.SendNotificationResponse.TYPE, type);

        bundle.putString(MessageParam.MESSAGE, BroadcastMessages.getBroadcastOperation(Operation.ANSWER_NOTIFICATION));
        String answer = (String) responseMap.get(Response.AnswerNotificationResponse.ANSWER);
        bundle.putString(Response.AnswerNotificationResponse.ANSWER, answer);

        if (answer.equals(Response.AnswerNotificationResponse.AnwerType.ACCEPT)) {
            if (nickname != null && !nickname.isEmpty()) {
                messengerUser.setNickName(nickname);
            }
            messengerUser.setDbStatus(CustomValuesStorage.CATEGORY_CONNECT);
            messengerUser.setStatusOnline(
                    CustomValuesStorage.UserStatus.getByName(
                            (String) responseMap.get(Response.AnswerNotificationResponse.STATUS)
                    ));
        } else {
            messengerUser.setDbStatus(CustomValuesStorage.CATEGORY_NOTCONNECT);
        }
        getApplication().getDbContact().saveMessengerUser(messengerUser);
        ArrayList<String> list = new ArrayList<>();
        list.add(userId);
        ActivityGlobalManager.getInstance().requestParticipantsPublicKey(list);
    }

    private void receiveNotification(final Map<String, Object> responseMap, Bundle bundle) {
        String senderLogin = (String) responseMap.get(Response.SendNotificationResponse.SENDER);
        String senderId = (String) responseMap.get(Response.SendNotificationResponse.SENDER_ID);
        if (senderId != null) {
            bundle.putString(MessageParam.MESSAGE, BroadcastMessages.getBroadcastOperation(Operation.SEND_NOTIFICATION));
            bundle.putString(Response.SendNotificationResponse.SENDER, senderId);
            String notificationType = (String) responseMap.get(Response.SendNotificationResponse.TYPE);
            String result = (String) responseMap.get(Response.BaseResponse.RESULT);
            ArrayList acceptors = (ArrayList) responseMap.get(Response.SendNotificationResponse.ACCEPTORS);
            ArrayList<String> uninstalled = (ArrayList<String>) responseMap.get(Response.SendNotificationResponse.ACCEPTORS_NOT_SEND);

            DataAddressBook dbContact = getApplication().getDbContact();
            if (senderId.equals(getApplication().getSettings().getString(SharedPreferencesAccessor.USER_ID, ""))) {
                int res;
                if (Response.BaseResponse.Result.OK.equals(result)
                        && acceptors != null
                        && uninstalled != null
                        ) {
                    res = dbContact.receiveNotification(
                            notificationType,
                            acceptors,
                            uninstalled);
                } else {
                    res = 0;
                }
                bundle.putInt(Response.BaseResponse.RESULT, res);
                bundle.putInt(Response.SendNotificationResponse.TYPE, CustomValuesStorage.CATEGORY_CONFIRM_OUT);
                bundle.putStringArrayList(Response.SendNotificationResponse.ACCEPTORS, acceptors);
                bundle.putStringArrayList(Response.SendNotificationResponse.ACCEPTORS_NOT_SEND, uninstalled);
                DbMessengerUser uninstalledUser;
                Map<String, DbMessengerUser> messengerDb = dbContact.getMessengerDb();
                for (String userId : uninstalled) {
                    uninstalledUser = messengerDb.get(userId);
                    if (uninstalledUser != null) {
                        uninstalledUser.setDbStatus(CustomValuesStorage.CATEGORY_NOTCONNECT);
                        dbContact.saveMessengerUser(uninstalledUser);
                    }
                }
            } else {
                bundle.putBoolean(CustomValuesStorage.IntentExtras.INTENT_SHOW_DIALOG, dbContact.showContactInvitation(senderId, senderLogin));
                bundle.putInt(Response.SendNotificationResponse.TYPE, CustomValuesStorage.CATEGORY_CONFIRM_IN);
                bundle.putString(Response.SendNotificationResponse.SENDER, senderLogin);
                bundle.putString(Response.SendNotificationResponse.SENDER_ID, senderId);
            }
        }
    }

    private void receiveGetToketResponse(Map<String, Object> responseMap) {
        final String token = (String) responseMap.get(Response.GetTokenResponce.TOKEN);
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                SocketConnector socketConnector = getApplication().getSocketConnector();
                if (socketConnector != null) {
                    socketConnector.sendInitSession(token);
                } else {
                    handler.postDelayed(this, 200);
                }
            }
        }, 200);
    }

    private synchronized void receiveInitSessionResponse(Map<String, Object> responseMap, Bundle bundle) {
        sendPreKeysPortion(responseMap);
        ArrayList<String> contact = (ArrayList<String>) responseMap.get(Response.InitSessionResponse.CONTACTS_ONLINE);
        Map<String, String> unreachableContact;
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                SocketConnector socketConnector = getApplication().getSocketConnector();
                if (socketConnector != null) {
                    socketConnector.startSession();
                }
            }
        }, 20);

        DataAddressBook dbContact = ActivityGlobalManager.getInstance().getDbContact();
        for (String login : contact) {
            DbMessengerUser tsmUser = getApplication().getDbContact().getMessengerDb().get(login);
            if (tsmUser != null && tsmUser.getStatus() == CustomValuesStorage.CATEGORY_CONNECT) {
                tsmUser.setStatusOnline(CustomValuesStorage.UserStatus.ONLINE);
                dbContact.saveMessengerUser(tsmUser);
            }
        }
        //  check for unreachable user
        try {
            unreachableContact = (Map<String, String>) responseMap.get(Response.InitSessionResponse.CONTACTS_UNREACHABLE);

            makeContactsUnreachable(unreachableContact);
        } catch (Exception e) {
            UniversalHelper.logException(e);
        }

        MessageQueue.getInstance().setInitSessionReady(MessageQueue.READY);
        ArrayList<String> participantsList = new ArrayList<>();
        for (Integer chatId : getApplication().getChatsForInvite()) {
            MessagePostman.getInstance().sendChatInvitationRequest(chatId.toString(), participantsList);
        }
        getApplication().getChatsForInvite().clear();

        bundle.putString(MessageParam.MESSAGE, BroadcastMessages.WS_CONTACT_STATUS);
        //if this is first InitSession - show last login time

        String deliveryDate = (String) responseMap.get(Response.InitSessionResponse.LAST_LOGIN_TIME);
        if (deliveryDate != null) {
            DateFormat df = new SimpleDateFormat(Param.DATE_FORMAT, Locale.US);
            Date receiveDate;
            try {
                receiveDate = df.parse(deliveryDate);
            } catch (ParseException pe) {
                UniversalHelper.logException(pe);
                receiveDate = new Date();
            }
            Boolean lastLoginCurrentDevice = Boolean.valueOf((String) responseMap.get(Response.InitSessionResponse.CURRENT_DEVICE));
            String lastLoginDateString = ActivityGlobalManager.getTimeString(receiveDate);

            bundle.putString(MessageParam.MESSAGE, BroadcastMessages.WS_SHOW_LOGIN_TIME);
            bundle.putString(Response.InitSessionResponse.LAST_LOGIN_TIME, lastLoginDateString);
            bundle.putBoolean(Response.InitSessionResponse.CURRENT_DEVICE, lastLoginCurrentDevice);
        }
    }

    private void makeContactsUnreachable(Map<String, String> unreachableContacts) {
        if (unreachableContacts != null) {
            for (String login : unreachableContacts.keySet()) {
                DbMessengerUser tsmUser = (getApplication()).getDbContact().getMessengerDb().get(login);
                if (tsmUser != null && tsmUser.getStatus() == CustomValuesStorage.CATEGORY_CONNECT) {
                    tsmUser.setStatusOnline(CustomValuesStorage.UserStatus.UNREACHABLE);
                }
            }
        }
    }

    private boolean receiveHistory(Map<String, Object> responseMap, Bundle broadcastParam) {
        action.put(MessageParam.Action.REFRESHCHAT, new HashMap<String, Object>());
        List<Map<String, Object>> bundle = (List<Map<String, Object>>) responseMap.get(Response.HistoryResponce.MESSAGES_PACKAGE);
        if (bundle == null || bundle.isEmpty()) {
            return true;
        }
        Map<String, Object> firstMessage = bundle.get(0);
        //chatId is needed to find chat this history is received
        Integer chatId = Integer.valueOf((String) firstMessage.get(Response.MessageResponse.CHAT_ID));
        DataChatHistory dbChatHistory = ActivityGlobalManager.getInstance().getDbChatHistory();
        ChatUnit currentChat = dbChatHistory.getChat(chatId);

        String ownLogin = ActivityGlobalManager.getInstance().getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE).
                getString(SharedPreferencesAccessor.USER_ID, "");

        String sender;
        Operation opCode;
        int opName, messageType;
        int messagesCount = 0;

        for (Map<String, Object> message : bundle) {
            opName = Integer.valueOf((String) message.get("operation"));
            opCode = Operation.getByValue(opName);
            if (opCode == Operation.MESSAGE) {
                messagesCount++;
                sender = (String) message.get(Response.MessageResponse.PERS_IDENT);
                messageType = sender.equals(ownLogin) ?
                        DbChatMessage.MessageType.OUT : DbChatMessage.MessageType.IN;
                currentChat.putMessage(message, messageType, true, true, dbChatHistory);

            }
        }
        HashMap<String, Object> chatHistoryRefreshMap = new HashMap<>();
        currentChat.sortChatHistory();
        chatHistoryRefreshMap.put(Response.MessageResponse.CHAT_ID, currentChat.getChatId());
        action.put(MessageParam.Action.HISTORY_LOADED, chatHistoryRefreshMap);
        if (messagesCount == 0 || bundle.size() < 30) {
            currentChat.setHasFullHistory(true);
            dbChatHistory.saveChat(currentChat);
            action.put(MessageParam.Action.FULL_HISTORY, new HashMap<String, Object>());
        }
        broadcastParam.putInt(Response.MessageResponse.CHAT_ID, chatId);
        broadcastParam.putString(MessageParam.MESSAGE, BroadcastMessages.WS_NEWMESSAGE_HISTORY);
        return true;
    }

    private boolean receivePeerPreKeysPackage(Map<String, Object> responseMap) {

        //  generate new session key

        DataChatHistory dbChatHistory = ActivityGlobalManager.getInstance().getDbChatHistory();
        DataAddressBook address = ActivityGlobalManager.getInstance().getDbContact();
        sendPreKeysPortion(responseMap);

        Integer chatId = Integer.valueOf((String) responseMap.get(Response.AuthorizeResponse.CHAT_ID));
        String sessionId = (String) responseMap.get(Response.GetPeerPreKeyResponse.SESSION_ID);
        String fileId = (String) responseMap.get(Response.GetPeerPreKeyResponse.FILE_ID);
        String msgId = (String) responseMap.get(Response.GetPeerPreKeyResponse.MESSAGE_ID);

        int keyNeed;
        try {
            String sKeyNeed = (String) responseMap.get(Response.GetPeerPreKeyResponse.PRE_KEYS_NUMBER_NEEDED);
            if (sKeyNeed != null && android.text.TextUtils.isDigitsOnly(sKeyNeed)) {
                keyNeed = Integer.valueOf(sKeyNeed);
            } else {
                keyNeed = 0;
            }
        } catch (Exception e) {
            UniversalHelper.logException(e);
            keyNeed = 0;
        }
        if (keyNeed > 10) {
            Intent intent = new Intent(ActivityGlobalManager.getInstance(), TsmBackgroundService.class);
            intent.putExtra(ServiceParameters.BACKGROUNDACTION, ServiceParameters.BackgroundTask.NEW_KEYS);
            ActivityGlobalManager.getInstance().startService(intent);
        }

        ArrayList<Map<String, String>> peerPreKeys =
                (ArrayList<Map<String, String>>) responseMap.get(
                        Response.GetPeerPreKeyResponse.PRE_KEYS_PACKAGE);


        ChatUnit chat = dbChatHistory.getChat(chatId);

        if (chat == null) {
            return false;
        }
        DbSessionKey newSessionKey = new DbSessionKey();
        newSessionKey.setSessionId(Integer.valueOf(sessionId));
        String ownerKeyId = (String) responseMap.get(Response.GetPeerPreKeyResponse.OWNER_KEY_ID);
        String ownerKey = EdDsaSigner.getInstance().decrypt((String) responseMap.get(Response.GetPeerPreKeyResponse.OWNER_PRIVATE_KEY));
        newSessionKey.setOwnerKeyId(ownerKeyId);
        for (Map<String, String> preKeyMap : peerPreKeys) {

            String peerPreKeyId = preKeyMap.get(Response.GetPeerPreKeyResponse.PeerPreKey.PEER_PRE_KEY_ID);
            String peerPreKey = preKeyMap.get(Response.GetPeerPreKeyResponse.PeerPreKey.PEER_PRE_KEY_PUBLIC_PART);
            String peerLogin = preKeyMap.get(Response.GetPeerPreKeyResponse.PeerPreKey.PEER_PERS_IDENT);
            DbMessengerUser user = address.getMessengerDb().get(peerLogin);
            if (!newSessionKey.verifyPublicKey(peerPreKey, user.getPublicKey())) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        ActivityGlobalManager.getInstance().alarmParticipantKey(null);
                    }
                });


                return false;
            }
            DhKeyData key = new DhKeyData(peerLogin, peerPreKeyId, peerPreKey.substring(0, 64), ownerKeyId, ownerKey);
            newSessionKey.addParticipantKey(key);
        }

        // all keys are ready - can send MESSAGE
        ActivityGlobalManager.getInstance().getDb().sessionKeySave(newSessionKey);
        List<Map<String, String>> bundle = newSessionKey.generateSessionKey();
        MessagePostman.getInstance().sendSessionKeyInitRequest(
                chatId.toString(),
                sessionId,
                msgId,
                fileId,
                newSessionKey.getSessionKey(),
                bundle
        );
        return false;
    }

    private boolean receiveSessionKey(Map<String, Object> responseMap) {
        DbSessionKey newSessionKey;
        String sessionId = (String) responseMap.get(Response.SessionKeysResponce.SESSION_ID);
        String chatId = (String) responseMap.get(Response.SessionKeysResponce.CHAT_ID);
        newSessionKey = ActivityGlobalManager.getInstance().getDb().sessionKeySelect(Integer.valueOf(sessionId));
        String msgId = (String) responseMap.get(Response.SessionKeysResponce.MESSAGE_ID);
        String fileId = (String) responseMap.get(Response.SessionKeysResponce.FILE_ID);

        if (newSessionKey == null) {
            newSessionKey = new DbSessionKey();
        }
        newSessionKey.setSessionId(Integer.valueOf(sessionId));
        String senderPublicKey = (String) responseMap.get(Response.SessionKeysResponce.SENDER_PUBLIC_KEY);
        String ownerKeyId = (String) responseMap.get(Response.SessionKeysResponce.RECIPIENT_KEY_ID);
        String ownerPrivateKey = (String) responseMap.get(Response.SessionKeysResponce.RECIPIENT_PRIVATE_KEY);
        String secretKey = (String) responseMap.get(Response.SessionKeysResponce.SESSION_SECRET_KEY);
        if (senderPublicKey != null && ownerKeyId != null) {
            newSessionKey.setKeyType(DbSessionKey.KEY_DH);

            newSessionKey.setParticipantPublicKey(senderPublicKey.substring(0, 63));
            newSessionKey.setOwnerKeyId(ownerKeyId);

            ownerPrivateKey = EdDsaSigner.getInstance().decrypt(ownerPrivateKey);
            DhKeyData key = new DhKeyData(senderPublicKey, ownerPrivateKey);
            newSessionKey.decryptSessionKey(secretKey, key);

            MessagePostman.getInstance().sendSessionKeyToCloudRequest(chatId, sessionId, newSessionKey.getSessionKey());
        } else {
            newSessionKey.setKeyType(DbSessionKey.KEY_IN_CLOUD);
            newSessionKey.setSessionKey(secretKey);
        }

        ActivityGlobalManager.getInstance().getDb().sessionKeySave(newSessionKey);
        ChatUnit chat = ActivityGlobalManager.getInstance().getDbChatHistory().getChat(Integer.valueOf(chatId));
        if (chat != null) {
            if (fileId != null) {
                FileData newFileData = ActivityGlobalManager.getInstance().getDbFileStorage().get(fileId);
                newFileData.setSessionid(sessionId);
                ActivityGlobalManager.getInstance().getDbFileStorage().saveFileData(newFileData);

                MessagePostman.getInstance().sendFileRequest(newFileData.getFileName(), newFileData.getFileSize(),
                        newFileData.getMode(), newFileData.getFileId(), sessionId);
            }
            if (msgId != null || fileId != null) {
                chat.confirmMessage(ChatUnit.SESSION_READY);
            }
            chat.setCurrentSessionKey(newSessionKey);
            chat.decryptIncomingMessage();
        }


        return true;
    }

    private boolean sendPreKeysPortion(Map<String, Object> responseMap) {

        String preKeysNeededString = (String) responseMap.get(Response.PreKeysResponse.PRE_KEYS_NUMBER_NEEDED);
        if (preKeysNeededString != null) {
            int preKeysCount = Integer.parseInt(preKeysNeededString);
            if (preKeysCount > 0) {
                Intent intent = new Intent(ActivityGlobalManager.getInstance(), TsmBackgroundService.class);
                intent.putExtra(ServiceParameters.BACKGROUNDACTION, ServiceParameters.BackgroundTask.NEW_KEYS);
                ActivityGlobalManager.getInstance().startService(intent);
            }
        }
        return false;
    }

    private ActivityGlobalManager getApplication() {
        return ActivityGlobalManager.getInstance();
    }

    private DummyDto parseMessage(String message) {
        DummyDto responseDto = null;
        boolean isOK;
        try {
            isOK = EdDsaSigner.getInstance().validateServerMessage(message);
        } catch (DecoderException exp) {
            UniversalHelper.logException(exp);
            isOK = true;
        }
        if (isOK) {
            Gson gson = new Gson();
            responseDto = gson.fromJson(message, DummyDto.class);
            Map<String, Object> responseDtoMap = responseDto.getParams();

            String msgId;
            msgId = responseDto.getId();
            if (msgId == null) {
                msgId = "";
            }
            Operation opCode;
            String opName;
            try {
                opName = (String) responseDtoMap.get(Param.OPERATION);
                opCode = Operation.getByName(opName);
            } catch (ClassCastException cce) {
                UniversalHelper.logException(cce);
                opCode = Operation.LIVE_MESSAGE;
            }

            if (!"".equals(msgId) && msgId.startsWith("d:")
                    && opCode != Operation.INIT_SESSION
                    && opCode != Operation.GET_TOKEN) {
                // receive confirm sent MESSAGE
                MessageQueue.getInstance().addIncomingMessageId(msgId);
            }
        }
        return responseDto;
    }

    /**
     * Class for broadcast messages constructing
     */
    public static class MessageParam {
        public static final String RESULT = "res";
        public static final String MESSAGE = "msg";

        private MessageParam() {
        }

        public static class Action {
            public static final String REFRESHCHATLIST = "refreshchatlist"; // chatFragment.REFRESHCHATLIST() will be called
            public static final String REFRESHCHAT = "refreshchat";  // chatHistoryFragment.refreshAdapter() will be called
            public static final String FULL_HISTORY = "FULL_HISTORY"; // chatHistoryFragment.setHistoryButtonState(false) will be called
            public static final String NEW_MESSAGE = "newMsg";  //showChatWindow(currentChat, true) will be called
            public static final String HISTORY_LOADED = "HISTORY_LOADED"; //chatHistoryFragment.showHistoryLoaded() will be called
            public static final String INVITATION_SENT = "INVITATION_SENT"; //showInvitationSent(invitationResult) will be called

            private Action() {
            }
        }
    }
}
