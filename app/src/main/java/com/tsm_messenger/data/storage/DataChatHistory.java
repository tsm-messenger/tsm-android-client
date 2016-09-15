package com.tsm_messenger.data.storage;

import android.util.SparseArray;

import com.tsm_messenger.service.ActivityGlobalManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import com.tsm_messenger.protocol.transaction.Param;

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
 *
 */
public class DataChatHistory {

    private final SparseArray<ChatUnit> chatList;
    private final TsmDatabaseHelper db;
    private final ArrayList<ChatUnit> adapterList = new ArrayList<>();

    /**
     * A constructor that initiates inner members of class instance
     *
     * @param db the TsmDatabaseHelper object with current instance of a database helper
     */
    public DataChatHistory(TsmDatabaseHelper db) {

        this.db = db;
        chatList = new SparseArray<>();
        SparseArray<ChatUnit> loadedChats = db.selectChatList(ActivityGlobalManager.getInstance().getDbContact().getMessengerDb(),
                ActivityGlobalManager.getInstance().getDbContact().getGroupChatList());
        for (int i = 0; i < loadedChats.size(); i++) {
            ChatUnit chat = loadedChats.valueAt(i);
            chat.setChatHistory(db.selectChatHistoryById(chat));
            chat.setUnsentMessage(db.getUnsendedMessage());
            chatList.put(loadedChats.keyAt(i), chat);
        }
    }

    /**
     * Updates information about requested chat
     *
     * @param chat a ChatUnit object  to update
     */
    public void saveChat(ChatUnit chat) {
        db.updateChatUnit(chat);
    }

    /**
     * Adds or updates an instance of a message in chat
     *
     * @param chatId    an id of a chat containing a provided message
     * @param msg       a message instance
     * @param addToList a flag defining if message adding to chat history is needed
     */
    public void chatSaveMessage(Integer chatId, DbChatMessage msg, boolean addToList) {

        List<DbChatMessage> history;
        ChatUnit unit;
        unit = chatList.get(chatId);
        Integer secureType;
        if (unit != null) {
            secureType = unit.getSecureType();
        } else {
            secureType = Integer.valueOf(Param.ChatSecureLevel.ALL_HISTORY_KEEP);
        }
        db.updateChatMessage(msg, secureType);

        if (addToList) {
            history = unit != null ? unit.getChatHistory() : new LinkedList<DbChatMessage>();
            history.add(msg);
            if (unit != null) {
                unit.notifyChatHistory();
            }
            sortList(adapterList);
        }
    }

    /**
     * Gets a message instance by the server ID
     *
     * @param id server id of a message needed
     * @return a DbChatMessage instance with needed server ID
     */
    public DbChatMessage getMessageByServerId(String id) {
        return db.selectMessageByServerId(id);
    }

    /**
     * Gets a concrete chat instance
     *
     * @param chatid an id of a requested chat
     * @return a chat instance matching provided id
     */
    public ChatUnit getChat(Integer chatid) {
        return chatList.get(chatid);
    }

    /**
     * gets a concrete chat instance
     *
     * @param persId user login or group id that identify the main unit of chat
     * @return returns a chat instance containing the main unit that matches provided id
     */
    public ChatUnit getChatByPersId(String persId) {
        ChatUnit chat = null;
        if (persId != null) {
            for (int i = 0; i < chatList.size(); i++) {
                ChatUnit cl = chatList.valueAt(i);
                if (persId.equals(cl.getUnitId())) {
                    chat = cl;
                    break;
                } else if (isGroupChatWithOnePerson(persId, cl)) {
                    chat = cl;
                }
            }
        }
        return chat;
    }

    private boolean isGroupChatWithOnePerson(String persId, ChatUnit cl) {
        boolean result = false;
        if (cl != null) {
            String unitId = cl.getUnitId();
            List<String> participantsList = cl.getParticipantsList();
            if (unitId != null && participantsList != null) {
                result = !unitId.startsWith("%GRP") && participantsList.contains(persId) && participantsList.size() == 1;
            }
        }
        return result;
    }

    /**
     * Saves a new chat in memory and database
     *
     * @param newChat chat to save
     */
    public void addChat(ChatUnit newChat) {
        chatList.put(newChat.getChatId(), newChat);
        adapterList.add(newChat);
        sortList(adapterList);
        db.updateChatUnit(newChat);
    }

    /**
     * Gets a source list of chats for an adapter
     *
     * @return a list of all chats
     */
    public List<ChatUnit> getAdapterList() {
        adapterList.clear();
        for (int i = 0; i < chatList.size(); i++) {
            adapterList.add(chatList.valueAt(i));
        }
        sortList(adapterList);
        return adapterList;
    }

    /**
     * Gets a chat history of a requested chat from database into memory
     *
     * @param chat a chat to fill chat history
     */
    public void getChatHistory(ChatUnit chat) {

        List<DbChatMessage> list = chat.getChatHistory();
        if (list == null || list.isEmpty()) {
            list = db.selectChatHistoryById(chat);
            chat.setChatHistory(list);
        }
    }

    /**
     * Sorts a provided list of chats
     *
     * @param adapterList a source list
     */
    public void sortList(List<ChatUnit> adapterList) {
        List<ChatUnit> forSort;
        if (adapterList == null)
            forSort = this.adapterList;
        else
            forSort = adapterList;

        Collections.sort(forSort, new Comparator<ChatUnit>() {
            @Override
            public int compare(ChatUnit o1, ChatUnit o2) {
                int ret;
                ret = o2.getLastTime().compareTo(o1.getLastTime());
                return ret;
            }
        });
    }

    /**
     * Removes a chat from memory and database
     *
     * @param chatId the ID of a chat to remove
     */
    public void dropChat(int chatId) {

        adapterList.remove(getChat(chatId));
        chatList.remove(chatId);
        db.dropChatById(chatId);
    }
}
