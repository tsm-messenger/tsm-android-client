package com.tsm_messenger.activities.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tsm_messenger.activities.R;
import com.tsm_messenger.connection.MessagePostman;
import com.tsm_messenger.crypto.EdDsaSigner;
import com.tsm_messenger.data.storage.ChatUnit;
import com.tsm_messenger.data.storage.DataAddressBook;
import com.tsm_messenger.data.storage.DataChatHistory;
import com.tsm_messenger.data.storage.DataFileStorage;
import com.tsm_messenger.data.storage.DbChatMessage;
import com.tsm_messenger.data.storage.DbGroupChat;
import com.tsm_messenger.data.storage.DbMessengerUser;
import com.tsm_messenger.data.storage.FileData;
import com.tsm_messenger.data.storage.TsmDatabaseHelper;
import com.tsm_messenger.protocol.registration.UserPublicKeysResponse;
import com.tsm_messenger.protocol.transaction.Operation;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.protocol.transaction.Response;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.SharedPreferencesAccessor;
import com.tsm_messenger.service.UniversalHelper;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class TsmDatabaseService extends Service {
    private final IBinder mBinder = new LocalBinder();
    private final HashMap<String, String> unreportedFilesMap = new HashMap<>();
    TsmDatabaseHelper dbHelper;
    private BroadcastReceiver broadcastReceiver;
    private DataAddressBook tsmContact;
    private DataChatHistory localChatHistory;
    private DataFileStorage dbFileStorage;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String contactString = intent.getStringExtra(CustomValuesStorage.IntentExtras.INTENT_CONTACT_LIST);
            ActivityGlobalManager.getInstance().setDbService(this);
            dbHelper = TsmDatabaseHelper.getInstance(getApplicationContext());
            UniversalHelper.debugLog(Log.DEBUG, "SRVST", "onStartCommand TsmDatabaseService");
            String keys = dbHelper.loadKeys();
            // Load keys from database
            if (keys != null) {
                UniversalHelper.debugLog(Log.DEBUG, "SRVST", "onStartCommand keys is  " + keys);
                EdDsaSigner.getInstance().loadKey(keys);
            } else {
                UniversalHelper.debugLog(Log.DEBUG, "SRVST", "onStartCommand keys is null ");
            }

            tsmContact = new DataAddressBook(this);
            tsmContact.loadLocalAddressBook();

            if (contactString != null) {
                Gson gson = new Gson();
                ArrayList<Map<String, Object>> contactList = gson.fromJson(contactString, new TypeToken<ArrayList<Map<String, Object>>>() {
                }.getType());
                tsmContact.responseMessengerUser(contactList);
                tsmContact.saveMessengerUser();
            }
            ArrayList<String> unconfirm = new ArrayList<>();
            for (Map.Entry<String, DbMessengerUser> user : tsmContact.getMessengerDb().entrySet()) {
                if (user.getValue().getDbStatus().equals(CustomValuesStorage.CATEGORY_CONNECT)
                        && (user.getValue().getPublicKey() == null || user.getValue().getPublicKey().isEmpty())
                        ) {
                    unconfirm.add(user.getKey());
                }
            }
            if (!unconfirm.isEmpty()) {
                ActivityGlobalManager.getInstance().requestParticipantsPublicKey(unconfirm);
            }

            String chatString = intent.getStringExtra(CustomValuesStorage.IntentExtras.INTENT_CHAT_LIST);

            Gson gson = new GsonBuilder()
                    .setDateFormat(Param.DATE_FORMAT)
                    .create();

            Type listType = new TypeToken<List<String>>() {
            }.getType();
            List<String> chatList = gson.fromJson(chatString, listType);

            localChatHistory = new DataChatHistory(dbHelper);
            Set<Integer> chatForInvite = ((ActivityGlobalManager) getApplication()).getChatsForInvite();
            if (chatList != null) {
                for (String chatId : chatList) {
                    ChatUnit newChat = new ChatUnit(Integer.valueOf(chatId));
                    localChatHistory.addChat(newChat);
                    localChatHistory.saveChat(newChat);
                    chatForInvite.add(Integer.valueOf(chatId));
                }
            }

            dbFileStorage = new DataFileStorage(dbHelper);

            broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    onReceiveBroadcast(intent);
                }
            };


        }
        IntentFilter bcFilter = new IntentFilter(BroadcastMessages.getBroadcastOperation(Operation.INVITE));
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.NEW_CONTACT));
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.AUTHORIZE));
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.LEAVE));
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.DELETE_USER_ACCOUNT));
        bcFilter.addAction(BroadcastMessages.WS_FILERECEIVE);
        bcFilter.addAction(ServiceParameters.TSM_USER_PUBLIC_LIST);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver,
                bcFilter);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        TsmDatabaseHelper.destroyInstance();
        super.onDestroy();
    }

    private void onReceiveBroadcast(Intent intent) {
        if (intent.getAction().equals(ServiceParameters.TSM_USER_PUBLIC_LIST)) {
            receiveUserPublicKeyResponse(intent);
        }
        if (intent.getAction().equals(BroadcastMessages.getBroadcastOperation(Operation.INVITE))) {
            receiveInviteResponse(intent);
        }
        if (intent.getAction().equals(BroadcastMessages.WS_FILERECEIVE)) {
            receiveFileReceiveResponse(intent);
            return;
        }
        if (intent.getAction().equals(BroadcastMessages.getBroadcastOperation(Operation.LEAVE))) {
            receiveLeaveChatResponse(intent);
            return;
        }
        if (intent.getAction().equals(BroadcastMessages.getBroadcastOperation(Operation.AUTHORIZE))) {
            receiveAuthorizeResult(intent);
            return;
        }

        if (intent.getAction().equals(BroadcastMessages.getBroadcastOperation(Operation.NEW_CONTACT))) {
            receiveNewContactResponse(intent);
        }
        if (intent.getAction().equals(BroadcastMessages.getBroadcastOperation(Operation.DELETE_USER_ACCOUNT))) {
            deleteUser(intent);
        }
    }

    /**
     * gets the current instance of DataAddressBook
     *
     * @return the current DataAddressBook instance
     */
    public DataAddressBook getTsmContact() {
        return tsmContact;
    }

    /**
     * gets the current instance of DataChatHistory
     *
     * @return the current DataChatHistory instance
     */
    public DataChatHistory getLocalChatHistory() {
        return localChatHistory;
    }

    /**
     * gets the current instance of DataFileStorage
     *
     * @return the current DataFileStorage instance
     */
    public DataFileStorage getDbFileStorage() {
        return dbFileStorage;
    }

    /**
     * writes the keys of an app to an encrypted database
     */
    public void saveKeys() {
        UniversalHelper.debugLog(Log.DEBUG, "SRVST", "saveKeys");
        dbHelper.saveKeys();
    }

    private void receiveInviteResponse(Intent intent) {
        Bundle bundle = intent.getBundleExtra(BroadcastMessages.WS_PARAM);

        String currentChatId = bundle.getString(Response.AuthorizeResponse.CHAT_ID);
        ChatUnit currentChat = getLocalChatHistory().getChat(Integer.valueOf(currentChatId));
        List<String> participantsList = bundle.getStringArrayList(Response.InvitationResponse.PARTICIPANTS);
        SharedPreferences settings = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE);
        if (participantsList == null) {
            return;
        }

        String ownId = settings.getString(SharedPreferencesAccessor.USER_ID, "");
        participantsList.remove(ownId);
        String chatName = bundle.getString(Response.InvitationResponse.CHAT_NAME);
        Integer secureLevel = bundle.getInt(Response.InvitationResponse.SECURITY_LEVEL);
        if (currentChat == null) {
            boolean isGroupchat = isGroupchat(participantsList, chatName);
            currentChat = new ChatUnit(
                    Integer.valueOf(currentChatId),
                    ownId,
                    chatName,
                    participantsList, tsmContact, isGroupchat, secureLevel);

            getLocalChatHistory().addChat(currentChat);

        } else {
            String unitId = currentChat.getUnitId();
            if (unitId == null || unitId.isEmpty()) {
                boolean isGroupchat = isGroupchat(participantsList, chatName);
                currentChat.fillExistingChat(Integer.valueOf(currentChatId),
                        !isGroupchat ? bundle.getString(Response.InvitationResponse.INVITORS_PERS_IDENT) : "",
                        ownId,
                        participantsList, tsmContact, isGroupchat, secureLevel);

                unitId = currentChat.getUnitId();
            }else{
                String sender = bundle.getString(Response.InvitationResponse.INVITORS_PERS_IDENT, "");
                String date = bundle.getString(Response.InvitationResponse.DATE, "");
                participantsList.removeAll(currentChat.getParticipantsList());
                currentChat.putServiceMessage(Operation.INVITE, participantsList, sender, date, getLocalChatHistory());
            }
            DbGroupChat groupToAddUser = tsmContact.getGroupChat(unitId);
            if (groupToAddUser != null) {
                addUsersToExistingGroup(currentChat, participantsList,
                        settings, tsmContact, groupToAddUser);
            }

            getLocalChatHistory().saveChat(currentChat);
        }
        ArrayList<String> keyRequest = new ArrayList<>();
        for (String userId : participantsList) {
            DbMessengerUser user = tsmContact.getMessengerDb().get(userId);
            if (user.getPublicKey() == null || user.getPublicKey().isEmpty()) {
                keyRequest.add(userId);
            }
        }
        if (!keyRequest.isEmpty()) {
            ((ActivityGlobalManager) getApplication()).requestParticipantsPublicKey(keyRequest);
        }

        Intent wsResult = new Intent(BroadcastMessages.getBroadcastOperation(Operation.INVITE) + BroadcastMessages.UI_BROADCAST);
        LocalBroadcastManager.getInstance(this).sendBroadcast(wsResult);
    }

    private boolean isGroupchat(List<String> participantsList, String chatName) {
        return (participantsList.size() > 1) || (chatName != null && chatName.startsWith(CustomValuesStorage.GROUP_DESCRIPTIOR));
    }

    private void addUsersToExistingGroup(ChatUnit currentChat, List<String> participantsList, SharedPreferences settings, DataAddressBook dbAddress, DbGroupChat groupToAddUser) {
        Set<String> currentParticipants = groupToAddUser.getMembers().keySet();
        participantsList.remove(settings.getString(SharedPreferencesAccessor.USER_ID, ""));
        for (String persIdent : participantsList) {
            if (!currentParticipants.contains(persIdent)) {
                DbMessengerUser pers = dbAddress.getMessengerDb().get(persIdent);
                if (pers == null) {
                    pers = new DbMessengerUser();
                    pers.setUserLogin(persIdent);
                    pers.setPersName(persIdent);
                    pers.setDbStatus(CustomValuesStorage.CATEGORY_UNKNOWN);
                    tsmContact.saveMessengerUser(pers);
                }
                groupToAddUser.addMember(persIdent, pers);
                currentChat.addParticipant(pers);
            }
        }
        dbAddress.saveGroupChat(groupToAddUser);
    }

    private void receiveUserPublicKeyResponse(Intent intent) {
        String stat = intent.getStringExtra(ServiceParameters.STATE);
        String ownerKey = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE)
                .getString(SharedPreferencesAccessor.USER_ID, "");
        if (ServiceParameters.OK.equals(stat)) {
            String json = intent.getStringExtra(ServiceParameters.PARAM);
            UserPublicKeysResponse response = UserPublicKeysResponse.createFromJson(json);
            String ownerPK = response.getUsersKeys().get(Long.valueOf(ownerKey));
            if (!EdDsaSigner.getInstance().validateOwnerPublicKey(ownerPK)) {
                ((ActivityGlobalManager) getApplication()).alarmOwnerKey(null);
                return;
            }

            for (Map.Entry<Long, String> entry : response.getUsersKeys().entrySet()) {
                DbMessengerUser user = tsmContact.getMessengerDb().get(entry.getKey().toString());
                if (user != null) {
                    user.setPublicKey(entry.getValue());
                    tsmContact.saveMessengerUser(user);
                }
            }
            Intent wsResult = new Intent(BroadcastMessages.WS_CONTACT_STATUS);
            LocalBroadcastManager.getInstance(this).sendBroadcast(wsResult);
        }
    }

    private void receiveNewContactResponse(Intent intent) {
        Bundle bundle = intent.getBundleExtra(BroadcastMessages.WS_PARAM);


        String result = bundle.getString(Response.NewContactResponse.RESULT);

        String persId = bundle.getString(Response.NewContactResponse.ValuesType.PERS_ID);
        String nickName = bundle.getString(Response.NewContactResponse.ValuesType.PERS_IDENT);
        String userName = bundle.getString(Response.NewContactResponse.ValuesType.PERS_NAME);


        if (result != null && result.equals(Response.BaseResponse.Result.OK)) {

            if (persId == null) {
                bundle.putString(CustomValuesStorage.IntentExtras.INTENT_ERROR_TEXT, getString(R.string.error_user_not_registered_notary));
                bundle.putString(CustomValuesStorage.IntentExtras.INTENT_RESULT, Response.BaseResponse.Result.FAILED);
            } else {
                saveUserToContactList(bundle, persId, nickName, userName);
            }
        }
        Intent wsResult = new Intent(BroadcastMessages.getBroadcastOperation(Operation.NEW_CONTACT) + BroadcastMessages.UI_BROADCAST);
        wsResult.putExtra(BroadcastMessages.WS_PARAM, bundle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(wsResult);
    }

    private void saveUserToContactList(Bundle bundle, String persId, String nickName, String userName) {
        DbMessengerUser user;
        boolean sendRequest = false;
        user = getTsmContact().getMessengerDb().get(persId);
        if (user != null && !user.getStatus().equals(CustomValuesStorage.CATEGORY_UNKNOWN)) {
            bundle.putString(CustomValuesStorage.IntentExtras.INTENT_ERROR_TEXT, getString(R.string.error_login_already_exists));
            bundle.putString(CustomValuesStorage.IntentExtras.INTENT_RESULT, Response.BaseResponse.Result.FAILED);
        } else {
            bundle.putString(CustomValuesStorage.IntentExtras.INTENT_RESULT, Response.BaseResponse.Result.OK);
            if (user == null) {
                user = new DbMessengerUser();
                user.setUserLogin(persId);
                user.setNickName(nickName);
                user.setPersName(userName);
                sendRequest = true;
            } else {
                if (user.getStatus().equals(CustomValuesStorage.CATEGORY_UNKNOWN)) {
                    sendRequest = true;
                }
                user.setNickName(nickName);
                if (user.getPersName().isEmpty() || user.getPersName().equals(user.getPersId())) {
                    user.setPersName(nickName);
                }
            }
            user.setDbStatus(CustomValuesStorage.CATEGORY_NOTCONNECT);
            getTsmContact().saveMessengerUser(user);
            if (sendRequest) {
                UniversalHelper.requestContact(persId);
            }
        }

    }

    private void receiveFileReceiveResponse(Intent intent) {
        Bundle bundle = intent.getBundleExtra(BroadcastMessages.WS_PARAM);

        String sender = bundle.getString(Response.FileReceiveResponse.SENDER);
        String sessionId = bundle.getString(Response.FileReceiveResponse.SESSION_ID);

        final String fileId = bundle.getString(Response.FileReceiveResponse.FILE_ID);
        final Integer chatId = bundle.getInt(Response.FileReceiveResponse.CHAT_ID);
        final String sendMode = bundle.getString(Response.FileReceiveResponse.MODE);
        final Integer fileSize = bundle.getInt(Response.FileReceiveResponse.FILE_SIZE);
        final String srcfileName = bundle.getString(Response.FileReceiveResponse.FILE_NAME);
        final String time = bundle.getString(Response.BaseResponse.DATE);

        ChatUnit chat = getLocalChatHistory().getChat(chatId);

        final String fileName = FileData.findFreeFileName(srcfileName);

        Date lastModified = new Date();

        final FileData dFile = dbFileStorage.get(fileId);
        if (dFile != null) {
            return;
        }
        if (chat == null) {
            chat = new ChatUnit(chatId);
            localChatHistory.addChat(chat);
            localChatHistory.saveChat(chat);
            ArrayList<String> participantsList = new ArrayList<>();
            MessagePostman.getInstance().sendChatInvitationRequest(chatId.toString(), participantsList);

        }

        DbMessengerUser user = tsmContact.getMessengerDb().get(sender);
        if (user == null) {
            user = new DbMessengerUser();
            user.setUserLogin(sender);
            user.setNickName(sender);
            user.setPersName(sender);
            user.setDbStatus(CustomValuesStorage.CATEGORY_UNKNOWN);
            tsmContact.saveMessengerUser(user);
        }
        final FileData newFileToReceive = new FileData(fileId, fileName, lastModified, fileSize, null, sessionId, chat.getChatId().toString(), sendMode);
        DbChatMessage fileMessage = chat.putFile(newFileToReceive, sender, sessionId,
                getLocalChatHistory(), DbChatMessage.MessageType.IN);
        getDbFileStorage().saveFileData(newFileToReceive);
        fileMessage.setTimeStamp(time);

        getLocalChatHistory().saveChat(chat);
        getLocalChatHistory().sortList(null);

        bundle.putInt(Response.MessageResponse.MESSAGE_ID, fileMessage.getMsgId());

        Intent wsResult = new Intent(BroadcastMessages.WS_FILERECEIVE + BroadcastMessages.UI_BROADCAST);
        wsResult.putExtra(BroadcastMessages.WS_PARAM, bundle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(wsResult);

    }

    private void receiveAuthorizeResult(Intent intent) {
        Bundle bundle = intent.getBundleExtra(BroadcastMessages.WS_PARAM);
        Integer chatId = bundle.getInt(Response.AuthorizeResponse.CHAT_ID);
        String groupId = bundle.getString(Response.AuthorizeResponse.GROUP_ID);
        String mode = bundle.getString(Response.AuthorizeResponse.MODE);
        Integer secureMode = bundle.getInt(Response.InvitationResponse.SECURITY_LEVEL);
        ChatUnit currentChat = getLocalChatHistory().getChat(chatId);

        ArrayList<String> participantsList = new ArrayList<>();
        boolean isPrivateChat;

        SharedPreferences settings = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE);
        isPrivateChat = groupId != null && !groupId.startsWith(CustomValuesStorage.GROUP_DESCRIPTIOR);
        if (currentChat == null) {
            DbGroupChat groupChat = createChatTemplate(groupId, participantsList, isPrivateChat);
            if (isPrivateChat) {
                currentChat = new ChatUnit(
                        chatId,
                        settings.getString(SharedPreferencesAccessor.USER_ID, ""),
                        groupId,
                        participantsList, getTsmContact(), false, secureMode);
            } else {
                ArrayList<DbMessengerUser> users = new ArrayList<>();
                if (groupChat == null) {
                    return;
                }
                users.addAll(groupChat.getMembers().values());
                currentChat = new ChatUnit(chatId, groupId, users, 0, groupChat, secureMode);

            }

            currentChat.setHasFullHistory(Response.AuthorizeResponse.AuthorizeMode.NEW.equals(mode));

            getLocalChatHistory().addChat(currentChat);
            currentChat.setConnected();
            Intent wsResult = new Intent(BroadcastMessages.getBroadcastOperation(Operation.INVITE) + BroadcastMessages.UI_BROADCAST);
            LocalBroadcastManager.getInstance(this).sendBroadcast(wsResult);


        } else {
            groupId = fillParticipantsListFromExistingChat(
                    groupId, currentChat, participantsList, isPrivateChat);
        }
        currentChat.setConnected();
        addSelfToParticipantsList(participantsList, settings);
        getLocalChatHistory().saveChat(currentChat);

        Intent wsResult = new Intent(BroadcastMessages.getBroadcastOperation(Operation.AUTHORIZE)
                + BroadcastMessages.UI_BROADCAST);
        wsResult.putExtra(BroadcastMessages.WS_PARAM, bundle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(wsResult);

        Map<String, String> unreportedFilesClone = (Map<String, String>) unreportedFilesMap.clone();
        if (unreportedFilesClone.containsValue(groupId)) {
            ArrayList<String> filesToDelete = new ArrayList<>();
            FileData fileData;

            for (Map.Entry<String, String> entry : unreportedFilesClone.entrySet()) {
                String fileId = entry.getKey();
                if (unreportedFilesClone.get(fileId).equals(groupId)) {
                    fileData = dbFileStorage.get(fileId);

                    DbChatMessage fileMessage = currentChat.putFile(fileData, currentChat.getParticipantsList().get(0),
                            fileData.getSessionid(), getLocalChatHistory(), DbChatMessage.MessageType.IN);

                    getLocalChatHistory().saveChat(currentChat);
                    getLocalChatHistory().sortList(null);
                    filesToDelete.add(fileId);

                    Intent fileMsg = new Intent(BroadcastMessages.WS_FILERECEIVE + BroadcastMessages.UI_BROADCAST);
                    Bundle fileBundle = new Bundle();
                    fileBundle.putInt(Response.FileReceiveResponse.FILE_ID, fileMessage.getMsgId());
                    fileBundle.putInt(Response.FileReceiveResponse.CHAT_ID, currentChat.getChatId());

                    fileMsg.putExtra(BroadcastMessages.WS_PARAM, fileBundle);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(fileMsg);

                }
                for (String lfileId : filesToDelete) {
                    unreportedFilesMap.remove(lfileId);
                }
            }
        }
    }

    private DbGroupChat createChatTemplate(String groupId, ArrayList<String> participantsList, boolean isPrivateChat) {
        DbGroupChat groupChat = null;
        if (isPrivateChat) {
            participantsList.add(groupId);
        } else {
            groupChat = createGroupChat(groupId, participantsList);
        }
        return groupChat;
    }

    private void addSelfToParticipantsList(ArrayList<String> participantsList, SharedPreferences settings) {
        if (!participantsList.contains(settings.getString(SharedPreferencesAccessor.USER_ID, ""))) {
            participantsList.add(settings.getString(SharedPreferencesAccessor.USER_ID, ""));
        }
    }

    private String fillParticipantsListFromExistingChat(String groupId, ChatUnit currentChat, List<String> participantsList, boolean isPrivateChat) {
        String resGroupId = groupId;
        List<String> participants;
        if (isPrivateChat) {
            participants = new ArrayList<>(currentChat.getParticipantsList());
        } else {
            resGroupId = currentChat.getUnitId();
            participants = currentChat.getParticipantsList();
        }
        for (String participant : participants) {
            participantsList.add(participant);
        }
        return resGroupId;
    }

    @Nullable
    private DbGroupChat createGroupChat(String groupId, ArrayList<String> participantsList) {
        DbGroupChat groupChat;
        groupChat = tsmContact.getGroupChat(groupId);
        if (groupChat != null) {
            Set<String> participants = groupChat.getMembers().keySet();
            for (String participant : participants) {
                participantsList.add(participant);
            }
        }
        return groupChat;
    }

    private void receiveLeaveChatResponse(Intent intent) {
        Bundle bundle = intent.getBundleExtra(BroadcastMessages.WS_PARAM);
        String leaver = bundle.getString(Response.LeaveChatResponse.LEAVE_PERS_IDENT);
        Integer chatId = bundle.getInt(Response.LeaveChatResponse.CHAT_ID);
        String ownPersIdent = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE).
                getString(SharedPreferencesAccessor.USER_ID, "");
        try {
            ChatUnit curChat;
            if ((leaver == null) || (leaver.equals(ownPersIdent))) {
                curChat = getLocalChatHistory().getChat(chatId);
                curChat.setIsOutcast(true);
                getLocalChatHistory().saveChat(curChat);
            } else {
                curChat = getLocalChatHistory().getChat(chatId);
                String chatName = getTsmContact().getGroupChat(curChat.getUnitId()).getGroupName();
                String date = bundle.getString(Response.InvitationResponse.DATE, "");
                curChat.putServiceMessage(Operation.LEAVE, null, leaver, date, getLocalChatHistory());

                Toast.makeText(this, leaver + " " + getString(R.string.info_user_left) + " " + chatName, Toast.LENGTH_LONG).show();
            }
            if (leaver != null) {
                DbGroupChat group = getTsmContact().getGroupChat(curChat.getUnitId());
                getTsmContact().getMessengerDb().get(leaver).leaveChat(group.getGroupId());
                getTsmContact().saveGroupChat(group);
            }
        } catch (NullPointerException e) {
            UniversalHelper.logException(e);
        }
    }


    private void deleteUser(Intent intent) {
        Bundle bundle = intent.getBundleExtra(BroadcastMessages.WS_PARAM);
        String oldPersIdent = bundle.getString(Response.DeleteUserAccountResponse.SENDER);
        DbMessengerUser deletedUser = getTsmContact().getMessengerDb().get(oldPersIdent);
        if ( deletedUser == null ) {
            deletedUser = new DbMessengerUser();
            deletedUser.setUserLogin(oldPersIdent);
            String userName = bundle.getString(Response.DeleteUserAccountResponse.SENDER_NAME) ;
            deletedUser.setNickName(userName == null ? oldPersIdent: userName );
            deletedUser.setDbStatus(CustomValuesStorage.CATEGORY_NOTCONNECT);
        }
        deletedUser.setDbStatus(CustomValuesStorage.CATEGORY_DELETE);
        deletedUser.setStatusOnline(CustomValuesStorage.UserStatus.UNREACHABLE);

        getTsmContact().saveMessengerUser(deletedUser);
        Intent wsResult = new Intent(BroadcastMessages.WS_CONTACT_STATUS);
        LocalBroadcastManager.getInstance(this).sendBroadcast(wsResult);


    }

    /**
     * a class to bind a service to an activity or an app instance
     */
    public class LocalBinder extends Binder {
        public TsmDatabaseService getService() {
            return TsmDatabaseService.this;
        }
    }

}
