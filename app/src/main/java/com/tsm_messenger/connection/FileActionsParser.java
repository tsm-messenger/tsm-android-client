package com.tsm_messenger.connection;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.control.TsmMessageDialog;
import com.tsm_messenger.activities.options.AppCompatPreferenceActivity;
import com.tsm_messenger.activities.options.TsmPreferencesActivity;
import com.tsm_messenger.activities.service.BroadcastMessages;
import com.tsm_messenger.data.storage.ChatUnit;
import com.tsm_messenger.data.storage.DataChatHistory;
import com.tsm_messenger.data.storage.DataFileStorage;
import com.tsm_messenger.data.storage.DbChatMessage;
import com.tsm_messenger.data.storage.DbSessionKey;
import com.tsm_messenger.data.storage.FileData;
import com.tsm_messenger.data.storage.TsmDatabaseHelper;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.protocol.transaction.Response;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.FileProgressListener;
import com.tsm_messenger.service.SharedPreferencesAccessor;
import com.tsm_messenger.service.UniversalHelper;

import org.bouncycastle.util.encoders.Hex;

import java.security.GeneralSecurityException;
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
public class FileActionsParser {
    private FileActionsParser() {
    }

    /**
     * Processes the new received file chunk from the server.
     *
     * @param responseMap the incoming JSON from the server presented as a map.
     *                    must have fileId, chunkId,
     *                    the string representing the bytes of received chunk
     * @return true if the confirm from the server is needed
     */
    public static boolean receiveChunkReceiveResponse(Map<String, Object> responseMap) {
        String fileId = (String) responseMap.get(Response.ChunkReceiveResponse.FILE_ID);
        String chunkId = (String) responseMap.get(Response.ChunkReceiveResponse.CHUNK_ID);
        String chunkString = (String) responseMap.get(Response.ChunkReceiveResponse.CHUNK);
        ActivityGlobalManager activityGlobalManager = ActivityGlobalManager.getInstance();
        DataFileStorage dfStore = activityGlobalManager.getDbFileStorage();
        final FileData dFile = dfStore.get(fileId);
        if (dFile == null) {
            return false;
        }
        DbSessionKey sessionKey = (TsmDatabaseHelper.getInstance(null)).
                sessionKeySelect(Integer.valueOf(dFile.getSessionid()));
        final ChatUnit chat;

        if (dFile.getChatId() != null) {
            chat = activityGlobalManager.getDbChatHistory()
                    .getChat(Integer.valueOf(dFile.getChatId()));
        } else {
            chat = null;
        }

        byte[] chunk;
        if (sessionKey.isReady()) {
            try {
                chunkString = sessionKey.decryptMessage(chunkString);
            } catch (GeneralSecurityException e) {
                UniversalHelper.logException(e);
            }
        }
        try {
            chunk = Hex.decode(chunkString);
        } catch (org.bouncycastle.util.encoders.DecoderException de) {
            UniversalHelper.logException(de);
            return false;
        }

        int result = activityGlobalManager.getDbFileStorage().writeChunk(chunk, fileId, chunkId);

        showChunkWriteResult(fileId, chunkId, dFile, chat, result);

        activityGlobalManager.getDbFileStorage().saveFileData(dFile);

        if (result == DataFileStorage.RESULT_EOF) {
            activityGlobalManager.removeTranstferringFile(fileId);
        } else {
            activityGlobalManager.addTransferringFile(fileId);
        }

        FileProgressListener fileProgressListener = activityGlobalManager.getFileProgressListener();
        if (chat != null && fileProgressListener != null) {
            fileProgressListener.fileProgressEvent(chat.getChatId());
        }
        return false;
    }

    private static void showChunkWriteResult(String fileId, String chunkId, FileData dFile, ChatUnit chat, int result) {
        switch (result) {
            case DataFileStorage.RESULT_OK:
                Integer newChunkId = Integer.valueOf(chunkId) + 1;
                dFile.setCurrentChunk(Integer.valueOf(chunkId).longValue());
                MessagePostman.getInstance().sendGetChunkRequest(newChunkId.toString(), fileId);
                break;
            case DataFileStorage.RESULT_EOF:
                dFile.setCurrentChunk(Integer.valueOf(chunkId).longValue() + 1);
                MessagePostman.getInstance().sendFileFinish(fileId);
                showFileDownloaded(dFile, chat);
                break;
            case DataFileStorage.ERR_CHUNK_EXIST:
                break;
            case DataFileStorage.ERR_FILE_WRITE:
                dFile.getMessage().setServerstate(DbChatMessage.FileServerStatus.ERROR);
                showDownloadFolderChangeDialog();
                break;
            case DataFileStorage.ERR_FILE_MOVED_OR_DELETED:
                dFile.getMessage().setServerstate(DbChatMessage.FileServerStatus.ERROR);
                break;
            default:
                MessagePostman.getInstance().sendGetChunkRequest(chunkId, fileId);
        }
    }

    private static void showDownloadFolderChangeDialog() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                final Activity curActitivy = ActivityGlobalManager.getInstance().getCurrentActivity();
                TsmMessageDialog msg = new TsmMessageDialog(curActitivy);
                msg.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {

                        Intent intent = new Intent(curActitivy, TsmPreferencesActivity.class);
                        intent.putExtra(AppCompatPreferenceActivity.EXTRA_SHOW_FRAGMENT,
                                TsmPreferencesActivity.GeneralPreferenceFragment.class.getName());
                        intent.putExtra(AppCompatPreferenceActivity.EXTRA_NO_HEADERS, true);
                        curActitivy.startActivity(intent);
                    }
                });
                msg.show(R.string.title_file_write_err, R.string.err_change_download_folder);
            }
        });
    }

    private static void showFileDownloaded(final FileData dFile, final ChatUnit chat) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                String userName = "";
                if (chat != null) {
                    userName = chat.getParticipantsUserList().get(0).getDisplayName();
                }
                String fileIncomingMessage = String.format(ActivityGlobalManager.getInstance().getString(R.string.info_file_received),
                        dFile.getFileName(),
                        userName);

                Toast.makeText(ActivityGlobalManager.getInstance().getBaseContext(),
                        fileIncomingMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Sends a new file chunk by the server request
     *
     * @param responseMap incoming JSON from the server presented as a map.
     *                    must have fileId and chunkId
     * @param sendMode    an element of Param.FileTransferMode. can be online or offline
     * @return true if chunk send operation was done correctly
     */
    public static boolean receiveChunkSendResponse(Map<String, Object> responseMap, String sendMode) {
        String fileId = (String) responseMap.get(Response.ChunkSendResponse.FILE_ID);
        Integer chunkId = Integer.valueOf((String) responseMap.get(Response.ChunkSendResponse.CHUNK_ID));
        int increment;
        if (sendMode.equals(Param.FileTransferMode.OFFLINE)) {
            increment = 1;
        } else
            increment = 0;

        ActivityGlobalManager activityGlobalManager = ActivityGlobalManager.getInstance();
        activityGlobalManager.getDbFileStorage().readFileForSend(fileId, chunkId + increment, null);

        return true;
    }

    /**
     * Processes an answer for a file send operation.
     * The file transfer is started if file is accepted and is canceled if file is declined
     *
     * @param responseMap          incoming JSON from the server presented as a map.
     *                             must have fileId and answer
     * @param fileProgressListener an interaction element to show file send state changes
     * @return true when method is finished
     */
    public static boolean receiveFileAnswer(Map<String, Object> responseMap, FileProgressListener fileProgressListener) {
        String answer = (String) responseMap.get(Response.FileAnswerResponse.ANSWER);
        if (answer != null && Response.FileAnswerResponse.AnswerType.ACCEPT.equals(answer)) {
            return true;
        }
        String fileId = (String) responseMap.get(Response.FileAnswerResponse.FILE_ID);
        DataFileStorage dfStore = ActivityGlobalManager.getInstance().getDbFileStorage();
        final FileData dFile = dfStore.get(fileId);
        if (dFile != null && dFile.getMessage() != null && dFile.getChatId() != null) {
            ChatUnit chat = ActivityGlobalManager.getInstance().getDbChatHistory()
                    .getChat(Integer.valueOf(dFile.getChatId()));
            Integer secureType;
            if (chat != null) {
                secureType = chat.getSecureType();
            } else {
                secureType = Integer.valueOf(Param.ChatSecureLevel.ALL_HISTORY_KEEP);
            }
            if (chat != null && chat.getChatGroup() != null) {
                //it's only a cancel from one participant
                dFile.getMessage().incFileCancel();
                if (chat.getParticipantsList().size() == dFile.getMessage().getFileCancelCount()) {
                    dFile.getMessage().setServerstate(DbChatMessage.FileServerStatus.ERROR);
                    dFile.getMessage().setDbStatus(DbChatMessage.MessageDatabaseStatus.NEW);
                }
            } else {
                //it's a private chat, where is one receiver
                dFile.getMessage().setServerstate(DbChatMessage.FileServerStatus.ERROR);
                dFile.getMessage().setDbStatus(DbChatMessage.MessageDatabaseStatus.NEW);
            }
            if (ActivityGlobalManager.getInstance() != null) {
                ActivityGlobalManager.getInstance().getDb().updateChatMessage(dFile.getMessage(), secureType);
            }

            if (fileProgressListener != null) {
                fileProgressListener.fileProgressEvent(Integer.valueOf(dFile.getChatId()));
            }
        }
        return true;
    }

    /**
     * Starts or cancels file sending depending on an answer
     *
     * @param responseMap incoming JSON from the server presented as a map.
     *                    must have fileId and time. Answer can be null
     * @return true when method is finished
     */
    public static boolean receiveFileSendResponse(Map<String, Object> responseMap, Bundle retVal) {
        String fileId = (String) responseMap.get(Response.FileSendResponse.FILE_ID);
        String answer = (String) responseMap.get(Response.FileSendResponse.ANSWER);
        String time = (String) responseMap.get(Response.BaseResponse.DATE);
        ActivityGlobalManager activityGlobalManager = ActivityGlobalManager.getInstance();
        DataFileStorage dbFileStorage = activityGlobalManager.getDbFileStorage();
        FileData file = dbFileStorage.get(fileId);
        DataChatHistory dbChatHistory = ActivityGlobalManager.getInstance().getDbChatHistory();
        ChatUnit chat = dbChatHistory.getChat(Integer.valueOf(file.getChatId()));
        String ownPersIdent = ActivityGlobalManager.getInstance().getSharedPreferences(
                SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE)
                .getString(SharedPreferencesAccessor.USER_ID, "");
        String sessionId = chat.getCurrentSessionKey().getSessionId().toString();
        DbChatMessage msg = chat.putFile(file, ownPersIdent, sessionId, dbChatHistory, DbChatMessage.MessageType.OUT);
        file.setMessage(msg);
        retVal.putString(IncomingMessageAsyncReceiver.MessageParam.MESSAGE, BroadcastMessages.WS_NEWMESSAGE);
        retVal.putInt(Response.MessageResponse.CHAT_ID, chat.getChatId());

        if (answer == null) {
            // online mode. Set time in MESSAGE only
            dbFileStorage.setOnlineFileTime(fileId, time);
            activityGlobalManager.addTransferringFile(fileId);
        } else if (Response.FileSendResponse.AnswerType.ACCEPT.equals(answer)) {
            dbFileStorage.readFileForSend(fileId, 0, time);
            activityGlobalManager.addTransferringFile(fileId);
        } else {
            dbFileStorage.removeFileData(fileId);
        }
        return true;
    }

    /**
     * Processes a fileCancel operation received from the server
     *
     * @param responseMap incoming JSON from the server presented as a map.
     *                    must contain fileId
     * @return true when method is finished
     */
    public static boolean receiveFileCancel(Map<String, Object> responseMap, Bundle bundle) {

        String fileId = (String) responseMap.get(Response.ChunkReceiveResponse.FILE_ID);
        ActivityGlobalManager activityGlobalManager = ActivityGlobalManager.getInstance();
        DataFileStorage dfStore = activityGlobalManager.getDbFileStorage();
        activityGlobalManager.removeTranstferringFile(fileId);

        final FileData dFile = dfStore.get(fileId);
        if (dFile != null) {
            final ChatUnit chat;
            if (dFile.getChatId() != null) {
                chat = activityGlobalManager.getDbChatHistory().getChat(Integer.valueOf(dFile.getChatId()));
            } else {
                chat = null;
            }

            if (chat != null) {
                notifyChatAboutFileCancel(responseMap, dFile, chat);
                bundle.putInt(Response.MessageResponse.CHAT_ID, chat.getChatId());
                bundle.putString(IncomingMessageAsyncReceiver.MessageParam.MESSAGE, BroadcastMessages.WS_FILEACTION);
            }
        }

        return true;
    }

    private static void notifyChatAboutFileCancel(Map<String, Object> responseMap, FileData dFile, ChatUnit chat) {
        boolean notOwnCancel = responseMap.get(Response.FileIsCanceledResponse.REFUSED_PERS_IDENT) != null;
        boolean isGroupChat = chat.getChatGroup() != null;
        try {
            if (dFile.getMessage() != null) {
                if (notOwnCancel && isGroupChat) {
                    dFile.getMessage().incFileCancel();
                } else {
                    dFile.getMessage().setServerstate(DbChatMessage.FileServerStatus.ERROR);
                    dFile.getMessage().setDbStatus(DbChatMessage.MessageDatabaseStatus.NEW);
                    dFile.setIsPending(false);
                }
                if (!chat.getSecureType().equals(Param.ChatSecureLevel.NOTHING_KEEP)) {
                    ActivityGlobalManager.getInstance().getDbChatHistory().chatSaveMessage(dFile.getMessage().getChatId(), dFile.getMessage(), false);
                }
            }
            if (!chat.getSecureType().equals(Param.ChatSecureLevel.NOTHING_KEEP)) {
                ActivityGlobalManager.getInstance().getDbChatHistory().chatSaveMessage(chat.getChatId(), dFile.getMessage(), false);
            }
            FileProgressListener fileProgressListener = ActivityGlobalManager.getInstance().getFileProgressListener();
            if (fileProgressListener != null) {
                fileProgressListener.fileProgressEvent(chat.getChatId());
            }
        } catch (NullPointerException npe) {
            UniversalHelper.logException(npe);
        }
    }

    /**
     * Processes a message that file transfer is finished
     *
     * @param responseMap incoming JSON from the server presented as a map.
     *                    must contain fileId
     * @return true when method is finished
     */
    public static boolean receiveFileDelivered(Map<String, Object> responseMap) {
        String fileId = (String) responseMap.get(Response.FileAnswerResponse.FILE_ID);
        DataFileStorage dfStore = ActivityGlobalManager.getInstance().getDbFileStorage();
        final FileData dFile = dfStore.get(fileId);
        if (dFile != null && dFile.getMessage() != null && dFile.getChatId() != null) {
            ChatUnit chat = ActivityGlobalManager.getInstance().getDbChatHistory()
                    .getChat(Integer.valueOf(dFile.getChatId()));
            if (chat.getChatGroup() != null) {
                //it's only a cancel from one participant
                dFile.getMessage().incReceiveCount();
            }
            if (ActivityGlobalManager.getInstance() != null) {
                ActivityGlobalManager.getInstance().getDb().updateChatMessage(dFile.getMessage(), chat.getSecureType());
                ActivityGlobalManager.getInstance().removeTranstferringFile(fileId);
            }

            FileProgressListener fileProgressListener = ActivityGlobalManager.getInstance().getFileProgressListener();
            if (fileProgressListener != null) {
                fileProgressListener.fileProgressEvent(Integer.valueOf(dFile.getChatId()));
            }
        }
        return true;
    }

    /**
     * Processes a message from the server that new file can be received
     *
     * @param responseMap incoming JSON from the server presented as a map.
     *                    must contain sender, session id, fileId, chat id,
     *                    file send mode, file size, file name and send date
     * @param bundle      a bundle for a broadcast message to notify user about new file
     */
    public static void receiveFileReceiveResponse(Map<String, Object> responseMap, Bundle bundle) {
        String sender = (String) responseMap.get(Response.FileReceiveResponse.SENDER);
        String sessionId = (String) responseMap.get(Response.FileReceiveResponse.SESSION_ID);

        final String fileId = (String) responseMap.get(Response.FileReceiveResponse.FILE_ID);
        final String chatId = (String) responseMap.get(Response.FileReceiveResponse.CHAT_ID);
        final String sendMode = (String) responseMap.get(Response.FileReceiveResponse.MODE);
        final Integer fileSize = Integer.valueOf((String) responseMap.get(Response.FileReceiveResponse.FILE_SIZE));
        final String srcfileName = (String) responseMap.get(Response.FileReceiveResponse.FILE_NAME);
        final String time = (String) responseMap.get(Response.BaseResponse.DATE);

        bundle.putString(Response.FileReceiveResponse.FILE_ID, fileId);
        bundle.putInt(Response.FileReceiveResponse.CHAT_ID, Integer.valueOf(chatId));
        bundle.putString(Response.FileReceiveResponse.SENDER, sender);
        bundle.putString(Response.FileReceiveResponse.SESSION_ID, sessionId);
        bundle.putString(Response.FileReceiveResponse.MODE, sendMode);
        bundle.putInt(Response.FileReceiveResponse.FILE_SIZE, fileSize);
        bundle.putString(Response.FileReceiveResponse.FILE_NAME, srcfileName);
        bundle.putString(Response.BaseResponse.DATE, time);
        bundle.putString(IncomingMessageAsyncReceiver.MessageParam.MESSAGE, BroadcastMessages.WS_FILERECEIVE);
    }
}
