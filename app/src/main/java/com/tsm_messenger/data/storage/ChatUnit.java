package com.tsm_messenger.data.storage;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.tsm_messenger.connection.MessagePostman;
import com.tsm_messenger.protocol.transaction.Operation;
import com.tsm_messenger.protocol.transaction.Response;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.UniversalHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
public class ChatUnit extends DataObjectCommon {

    public static final String SESSION_OK = "OK";
    public static final String SESSION_READY = "ready";
    public static final String SESSION_FAIL = "fail";
    private static final String SESSION_NEED_KEY = "key";
    private final LinkedList<DbChatMessage> chatHistory = new LinkedList<>();
    private final LinkedBlockingDeque<DbChatMessage> outQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingQueue<String> confirmQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingDeque<DbChatMessage> incomingQueue = new LinkedBlockingDeque<>();
    private final TreeMap<Integer, DbChatMessage> unsentMessage = new TreeMap<>();
    private final Object tagForSync = new Object();
    private final TreeMap<String, DbChatMessage> preparedMessages = new TreeMap<>();
    private boolean isOutcast;
    private boolean hasFullHistory;
    private Integer id;
    private String unitId;
    private Long lastTime;
    private Integer lastSessionId;
    private Integer secureType;
    private DbSessionKey currentSessionKey;
    private List<DbMessengerUser> participantList;
    private DbGroupChat chatGroup;
    private int unreadMessageCnt = 0;
    private String readyForSend = "";
    private int chatCategory;
    private MessageParcerThread sendMessageThread;
    private MessageIncomingThread incomingMessageThread;

    private boolean connected = false;
    private DbChatMessage lastSortedMessage;

    /**
     * Constructor for new Chat object creating
     *
     * @param chatId       unique server chat id
     * @param ownPersIdent owner id
     * @param unitId       unique device chat id
     * @param dirtyList    list of chat participants
     * @param dbAddress    link to data address book
     * @param isGroup      tag group or private chat
     * @param secureType   security type of chat
     */
    public ChatUnit(Integer chatId,
                    String ownPersIdent,
                    String unitId,
                    List<String> dirtyList,
                    DataAddressBook dbAddress,
                    Boolean isGroup,
                    Integer secureType
    ) {
        this.id = chatId;
        this.unitId = unitId;
        this.isOutcast = false;
        this.secureType = secureType;
        try {
            chatGroup = dbAddress.getGroupChat("");

            if (chatGroup != null) {
                dbAddress.getGroupChatList().put(chatGroup.getGroupId(), chatGroup);
                Map<String, DbMessengerUser> participants = chatGroup.getMembers();
                this.participantList = new ArrayList<>();
                this.participantList.addAll(participants.values());
            } else {
                createNewChatGroup(ownPersIdent, dirtyList, dbAddress, isGroup);
            }
        } catch (NumberFormatException e) {
            UniversalHelper.logException(e);
            this.participantList = new ArrayList<>();
            for (String persId : dirtyList) {
                if (!persId.equals(ownPersIdent)) {
                    this.participantList.add(dbAddress.getMessengerDb().get(persId));
                    this.unitId = persId;
                }
            }
        }

        boolean isGroupChat = dbAddress.getGroupChat("") != null;
        isGroupChat = isGroupChat || isGroup;

        if (this.participantList.size() > 1 || isGroupChat)
            chatCategory = ChatCategoryType.GROUP;
        else
            chatCategory = ChatCategoryType.PERSON;
    }

    /**
     * Constructor for new Chat object creating
     *
     * @param chatId           unique the server chat id
     * @param unitId           unique device chat id
     * @param participantsList list of chat participants
     * @param isOutcast        tag chat is outcast or valid
     * @param group            link to local group object
     * @param secureType       security type of chat
     */
    public ChatUnit(Integer chatId, String unitId,
                    List<DbMessengerUser> participantsList,
                    int isOutcast,
                    DbGroupChat group,
                    Integer secureType) {
        this.id = chatId;
        this.unitId = unitId;
        this.secureType = secureType;
        this.participantList = participantsList;
        this.isOutcast = isOutcast == 1;
        this.chatGroup = group;

        boolean isGroupChat = ActivityGlobalManager.getInstance().getDbContact().getGroupChat(unitId) != null;

        if (isGroupChat)
            chatCategory = ChatCategoryType.GROUP;
        else
            chatCategory = ChatCategoryType.PERSON;
    }

    /**
     * Constructor for only initial chatId setting
     *
     * @param chatId chat id to set
     */
    public ChatUnit(Integer chatId) {
        this.id = chatId;
    }

    private void createNewChatGroup(String ownPersIdent, List<String> dirtyList, DataAddressBook dbAddress, Boolean isGroup) {
        this.participantList = new ArrayList<>();
        List<String> prepared = new ArrayList<>();
        chatGroup = null;
        for (String persId : dirtyList) {
            if (!persId.equals(ownPersIdent))
                prepared.add(persId);
        }

        if (prepared.size() > 1 || isGroup) {
            chatGroup = new DbGroupChat();
            chatGroup.setType(DbGroupChat.GROUPTYPE_TEMP);
            this.unitId = "";
        } else if (!prepared.isEmpty()) {
            this.unitId = prepared.get(0);
        }

        fillGroupParticipants(ownPersIdent, dbAddress, prepared, chatGroup);
        if (chatGroup != null) {
            dbAddress.saveGroupChat(chatGroup);
            dbAddress.getGroupChatList().put(chatGroup.getGroupId(), chatGroup);
            this.unitId = chatGroup.getGroupIdString();
        }
    }

    /**
     * Gets the current state of chat history
     *
     * @return true if all history from the server is loaded, returns false - if not
     */
    public boolean hasFullHistory() {
        return hasFullHistory;
    }

    /**
     * Sets new state of chat history
     *
     * @param hasFullHistory new state of chat history
     */
    public void setHasFullHistory(boolean hasFullHistory) {
        this.hasFullHistory = hasFullHistory;
    }

    /**
     * Gets current queue of outgoing messages for current chat
     *
     * @return returns current outgoing queue object
     */
    public LinkedBlockingDeque<DbChatMessage> getOutQueue() {
        return outQueue;
    }

    /**
     * re-fuels unsent messages map for current chat
     *
     * @param items the new map of unsorted messages for current chat
     */
    public void setUnsentMessage(SortedMap<Integer, DbChatMessage> items) {
        unsentMessage.clear();
        unsentMessage.putAll(items);
        boolean startOut = false;
        boolean startIn = false;
        for (Map.Entry<Integer, DbChatMessage> row : items.entrySet()) {
            row.getValue().setDbStatus(DbChatMessage.MessageDatabaseStatus.NEW);
            if (row.getValue().getType().equals(DbChatMessage.MessageType.OUT) ||
                    row.getValue().getContentType() == DbChatMessage.MSG_FILE) {
                prepareChatMessageForsend(row.getValue());
                outQueue.add(row.getValue());
                startOut = true;
            } else if (row.getValue().getType().equals(DbChatMessage.MessageType.IN) &&
                    row.getValue().getServerstate() == DbChatMessage.MessageServerStatus.ENCRYPTED) {
                incomingQueue.add(row.getValue());
                startIn = true;
            } else if (row.getValue().getType().equals(DbChatMessage.MessageType.IN) &&
                    (row.getValue().getServerstate() == DbChatMessage.MessageServerStatus.DECRYPTED) &&
                    row.getValue().getServerId() != null && !row.getValue().getServerId().isEmpty()
                    ) {
                MessagePostman.getInstance().sendReadConfirmRequest(row.getValue().getServerId());
            }
        }
        if (startOut) {
            activateOutThread();
        }
        if (startIn) {
            activateIncomingTask();
        }
    }

    /**
     * Puts the message into a map of prepared messages to send it later
     *
     * @param message message to prepare
     */
    public void prepareChatMessageForsend(DbChatMessage message) {
        preparedMessages.put(message.getMsgId().toString(), message);
    }

    /**
     * Gets a message from a map of prepared messages for send
     *
     * @param messageId unique id of a message needed
     * @return a DbChatMessage object corresponding messageId from incoming param
     */
    public DbChatMessage getPreparedMessage(String messageId) {
        DbChatMessage msg;
        msg = preparedMessages.get(messageId);
        return msg;

    }

    /**
     * Removes sent message from prepared messages map
     *
     * @param messageId message needed to mark as sent
     */
    public void preparedMessageSent(String messageId) {
        preparedMessages.remove(messageId);
    }

    /**
     * Gets the fact that current chat is private
     *
     * @return true if chat is private
     */
    public boolean isPrivateChat() {
        return chatCategory == ChatCategoryType.PERSON;
    }

    /**
     * Gets current number of participants without unreachable status
     *
     * @return the count of active participants
     */
    public int getActiveParticipantsCount() {
        int result = 0;

        if (chatGroup != null) {
            int groupId = chatGroup.getGroupId();
            for (DbMessengerUser participant : participantList) {
                if (!participant.isChatLeave(groupId))
                    result++;
            }
        } else {
            result = participantList.size();
        }
        return result;
    }

    /**
     * Gets chatGroup object from current chat unit
     *
     * @return DbGroupChat object if chat is started with a group.
     * returns null if chat is started with a person
     */
    public DbGroupChat getChatGroup() {
        return chatGroup;
    }

    /**
     * Fills existing mock chatUnit object with custom data
     *
     * @param chatId       the unique chat id for curent unit
     * @param unitId       the unique id of person or group for which the chat is started
     * @param ownPersIdent the username of current user who uses the app
     * @param dirtyList    the list of chat participants including current user's username
     * @param dbAddress    instance of DataAddressBook containing all data about users
     * @param isGroupChat  flag that indicates the type of chat: group or private
     * @param secureType   the type of security algorithm which is used for chat history storage
     */
    public void fillExistingChat(Integer chatId, String unitId,
                                 String ownPersIdent, List<String> dirtyList,
                                 DataAddressBook dbAddress,
                                 boolean isGroupChat,
                                 Integer secureType) {
        this.id = chatId;
        this.unitId = unitId;
        this.secureType = secureType;

        try {
            fillExistingGroupChat(unitId, ownPersIdent, dirtyList, dbAddress, isGroupChat);
        } catch (NumberFormatException e) {
            UniversalHelper.logException(e);
            this.participantList = new ArrayList<>();
            for (String persId : dirtyList) {
                if (!persId.equals(ownPersIdent)) {
                    this.participantList.add(dbAddress.getMessengerDb().get(persId));
                    this.unitId = persId;
                }
            }
        }
        chatCategory = isGroupChat ? ChatCategoryType.GROUP : ChatCategoryType.PERSON;
    }

    private void fillExistingGroupChat(String unitId, String ownPersIdent, List<String> dirtyList, DataAddressBook dbAddress, boolean isGroupChat) {
        chatGroup = dbAddress.getGroupChat(unitId);

        if (chatGroup != null) {
            dbAddress.getGroupChatList().put(chatGroup.getGroupId(), chatGroup);
            Map<String, DbMessengerUser> participants = chatGroup.getMembers();
            this.participantList = new ArrayList<>();
            this.participantList.addAll(participants.values());
        } else {
            this.participantList = new ArrayList<>();
            ArrayList<String> prepared = new ArrayList<>();
            DbGroupChat newGroup = null;
            for (String persId : dirtyList) {
                if (!persId.equals(ownPersIdent))
                    prepared.add(persId);
            }

            if (isGroupChat) {
                newGroup = new DbGroupChat();
                newGroup.setType(DbGroupChat.GROUPTYPE_TEMP);
                this.unitId = unitId;
            } else if (!prepared.isEmpty()) {
                this.unitId = prepared.get(0);
            } else {
                this.unitId = null;
            }

            fillGroupParticipants(ownPersIdent, dbAddress, prepared, newGroup);
            if (newGroup != null) {
                dbAddress.saveGroupChat(newGroup);
                dbAddress.getGroupChatList().put(newGroup.getGroupId(), newGroup);
                chatGroup = newGroup;
                this.unitId = newGroup.getGroupIdString();
            }
        }
    }

    private void fillGroupParticipants(String ownPersIdent, DataAddressBook dbAddress,
                                       List<String> prepared, DbGroupChat groupChat) {
        for (String persId : prepared) {
            if (!persId.equals(ownPersIdent)) {
                DbMessengerUser pers = dbAddress.getMessengerDb().get(persId);
                if (pers == null) {
                    pers = new DbMessengerUser();
                    pers.setUserLogin(persId);
                    pers.setPersName(persId);
                    pers.setDbStatus(CustomValuesStorage.CATEGORY_UNKNOWN);
                    dbAddress.saveMessengerUser(pers);
                }
                this.participantList.add(pers);
                if (groupChat != null) {
                    groupChat.addMember(pers.getPersId(), pers);
                }
            }
        }
        if (groupChat != null && this.participantList.size() > 1) {
            groupChat.setGroupName("");
        }
    }

    /**
     * Adds a new participant to an existing chat
     *
     * @param participant a new participant to add
     */
    public void addParticipant(DbMessengerUser participant) {
        if (!participantList.contains(participant))
            this.participantList.add(participant);
    }

    public void putServiceMessage(Operation operation, List<String> participants,
                                  String sender, String deliveryDate, DataChatHistory dbChat) {
        DbChatMessage newMessage;
        String participantsStr = participants != null ? new Gson().toJson(participants) : sender;

        newMessage = new DbChatMessage();
        newMessage.setLogin(sender);
        newMessage.setChatId(getChatId());
        newMessage.setType(DbChatMessage.MessageType.IN);
        newMessage.setContentType(DbChatMessage.MSG_SERVICE);
        newMessage.setMessage(participantsStr);
        newMessage.setServiceOperation(operation);
        newMessage.setServerstate(DbChatMessage.FileServerStatus.RECEIVING);
        newMessage.setDbStatus(DbChatMessage.MessageDatabaseStatus.NEW);
        newMessage.setTimeStamp(deliveryDate);
        dbChat.chatSaveMessage(getChatId(), newMessage, false);

        addMessageToChatHistory(newMessage, false);
    }

    /**
     * Gets the id of last session key used for encryption
     *
     * @return an integer representation of last session id
     */
    public Integer getLastSessionId() {
        return lastSessionId;
    }

    /**
     * Changes the id of a last session key used for encryption
     *
     * @param lastSessionId id of the newest session key used for encryption
     */
    public void setLastSessionId(Integer lastSessionId) {
        this.lastSessionId = lastSessionId;
    }

    /**
     * Gets a current session key used for encryption
     *
     * @return an DbSessionKey object containing key used for encryption at the moment of method call
     */
    public DbSessionKey getCurrentSessionKey() {
        return currentSessionKey;
    }

    /**
     * Changes the session key used for encryption
     *
     * @param currentSessionKey new session key used for encryption
     */
    public void setCurrentSessionKey(DbSessionKey currentSessionKey) {
        try {
            if (this.currentSessionKey == null || (this.currentSessionKey.getSessionId() < currentSessionKey.getSessionId())) {
                this.currentSessionKey = currentSessionKey;
            }
            if (currentSessionKey.isReady()) {
                synchronized (tagForSync) {
                    readyForSend = SESSION_READY;
                    tagForSync.notifyAll();
                }
            }
        } catch (NullPointerException ne) {
            UniversalHelper.logException(ne);
            this.currentSessionKey = currentSessionKey;
            if (currentSessionKey.isReady()) {
                synchronized (tagForSync) {
                    readyForSend = SESSION_READY;
                    tagForSync.notifyAll();
                }
            }
        }
    }

    /**
     * Gets the send or receive time of the last message in chat history
     *
     * @return the last message send or receive time in milliseconds
     */
    public Long getLastTime() {
        Long lTime;
        lTime = lastTime;
        return lTime == null ? 0 : lTime;
    }

    /**
     * Sets the new last send or receive time of a chat history
     *
     * @param lastTime new send or receive time of last message in chat history
     */
    public void setLastTime(Long lastTime) {
        this.lastTime = lastTime;
    }

    /**
     * Shows if current user has left current chat
     *
     * @return true if user has left current chat
     */
    public boolean isOutcast() {
        return isOutcast;
    }

    /**
     * Sets the flag indicating that current chat is outcast by current user
     *
     * @param isOutcast new state of an outcast flag
     */
    public void setIsOutcast(boolean isOutcast) {
        this.isOutcast = isOutcast;
    }

    /**
     * Gets a list of current chat participants' ids
     *
     * @return the list of chat participants' ids
     */
    public List<String> getParticipantsList() {
        List<String> resultList = new ArrayList<>();
        if (participantList != null) {
            for (DbMessengerUser participant : participantList) {
                if (participant != null) {
                    resultList.add(participant.getPersId());
                }
            }
        }
        return resultList;
    }

    /**
     * Gets a list of current chat participants
     *
     * @return the list of objects representing chat participants
     */
    public List<DbMessengerUser> getParticipantsUserList() {

        return participantList;
    }

    /**
     * Gets current chat category
     *
     * @return an item of ChatUnit.ChatCategoryType class. Can be private or public
     */
    public int getChatCategory() {
        return chatCategory;
    }

    /**
     * Gets a secure type of chat history storage algorytm
     *
     * @return an item of Param.ChatSecureLevel interface
     */
    public Integer getSecureType() {
        return secureType;
    }

    /**
     * Gets a string representation of chat name to display it in UI
     *
     * @return the group name if group chat, person name if personal chat,
     * or *** if name cannot be defined
     */
    public String getChatName() {
        String chatName;
        try {
            if (chatGroup != null) {
                chatName = chatGroup.getGroupName();
            } else {
                if (participantList != null && !participantList.isEmpty())
                    chatName = participantList.get(0).getPersName();
                else
                    chatName = "***";
            }
        } catch (NullPointerException ne) {
            UniversalHelper.logException(ne);
            chatName = "***";
        }
        return chatName;
    }

    /**
     * Gets a text of last message to display it in UI
     *
     * @return the last message text or the last file name in the chat.
     * returns ?? if last message text cannot be defined
     */
    public String getLastMessageText() {
        String lastText = "";
        if (!chatHistory.isEmpty()) {
            DbChatMessage lastMsg = chatHistory.get(chatHistory.size() - 1);
            if (lastMsg != null) {
                if (lastMsg.getContentType() == DbChatMessage.MSG_FILE) {
                    FileData fd = lastMsg.getFileData();
                    lastText = fd != null ? fd.getFileName() : "??";
                } else {
                    lastText = lastMsg.getMessage();
                }
            } else {
                lastText = "??";
            }
        }
        return lastText;
    }

    /**
     * Gets an DbChatMessage object of a last message in current chat history
     *
     * @return an DbChatMessage object of a last message
     */
    public DbChatMessage getLastMessage() {
        DbChatMessage msg = null;
        if (!chatHistory.isEmpty()) {
            msg = chatHistory.get(chatHistory.size() - 1);
        }
        return msg;
    }

    /**
     * Gets the last message time to display it in UI
     *
     * @return a string representation of a send or receive time of last message in chat history
     */
    public String getLastMessageTime() {
        String slastTime;
        if (!chatHistory.isEmpty()) {
            DbChatMessage lastMessage = chatHistory.get(chatHistory.size() - 1);
            if (lastMessage.getContentType() == DbChatMessage.MSG_FILE) {
                slastTime = getTimeString(lastMessage.getTimeStampInt());
            } else {
                if (((Integer) DbChatMessage.MessageServerStatus.PREPARE).equals(lastMessage.getServerstate())) {
                    slastTime = "--:--";
                } else {
                    slastTime = getTimeString(lastMessage.getTimeStampInt());
                }
            }
        } else {
            slastTime = "--:--";
        }

        return slastTime;
    }

    private String getTimeString(Long time) {
        String currentDate;
        String mask;
        String result;
        if (time == null || time.equals(0L)) {
            result = "";
        } else {
            SimpleDateFormat checkFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            currentDate = checkFormat.format(new Date());
            if (currentDate.equals(checkFormat.format(time)))
                mask = "HH:mm";
            else
                mask = ActivityGlobalManager.DATE_FORMAT + " HH:mm";

            SimpleDateFormat printFormat = new SimpleDateFormat(mask, Locale.US);
            result = printFormat.format(time);
        }
        return result;
    }

    /**
     * Gets a flag if this chat authorized for access or not
     *
     * @return true if chat is authorized for access, false - if not
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Sets a flag if this chat authorized for access into a true state
     */
    public void setConnected() {
        this.connected = true;
        resetUnreadMessagesCounter();
    }

    /**
     * Sets a counter of unread messages to 0
     */
    public void resetUnreadMessagesCounter() {
        unreadMessageCnt = 0;
    }

    /**
     * Gets a messengerId of the main item of curent chat
     *
     * @return user login if current chat is personal or group id if current chat is group
     */
    public String getUnitId() {
        return unitId;
    }

    /**
     * Returns the server ID if of a first message of chat history.
     * Used for requesting chat history from the server
     *
     * @return a string representation of a server id for the first message in the chat history
     */
    public String getFirstServerId() {

        String val = null;
        if (chatHistory.size() > 2) {
            val = chatHistory.get(1).getServerId();
        }
        return val;
    }

    /**
     * Gets a current unread messages count
     *
     * @return the value of an integer unread messages counter
     */
    public int getUnreadMessageCount() {
        return unreadMessageCnt;
    }

    /**
     * Sets a flag if this chat authorized for access into a true state
     */
    public String getUnreadMessagesString() {
        return unreadMessageCnt < 100 ? String.valueOf(unreadMessageCnt)
                : "99+";
    }

    /**
     * Sets a flag that current chat is authorized for access into a true state
     */
    public List<DbChatMessage> getChatHistory() {
        return chatHistory;
    }

    /**
     * Fills current chat history with a new list of messages
     *
     * @param chatHistory a new list of messages
     */
    public void setChatHistory(List<DbChatMessage> chatHistory) {
        this.chatHistory.addAll(chatHistory);
        notifyChatHistory();
        lastSortedMessage = null;
        sortChatHistory();
    }

    /**
     * sorts an unsorted part of chat history
     */
    public void sortChatHistory() {
        List<DbChatMessage> listToSort;

        if (!chatHistory.isEmpty()) {
            if (lastSortedMessage == null || chatHistory.indexOf(lastSortedMessage) == -1) {
                listToSort = chatHistory;
            } else {
                try {
                    listToSort = chatHistory.subList(
                            chatHistory.indexOf(lastSortedMessage),
                            chatHistory.size());
                } catch (IndexOutOfBoundsException ibo) {
                    UniversalHelper.logException(ibo);
                    listToSort = chatHistory;
                }
            }
            try {
                Collections.sort(listToSort, new Comparator<DbChatMessage>() {
                    @Override
                    public int compare(DbChatMessage m1, DbChatMessage m2) {
                        if (m1 == null && m2 == null)
                            return 0;
                        if (m1 == null)
                            return -1;
                        if (m2 == null)
                            return 1;
                        return m1.compareTo(m2);
                    }
                });
            } catch (IllegalArgumentException ile) {
                UniversalHelper.logException(ile);
            }
            notifyChatHistory();
            Iterator<DbChatMessage> iterator = chatHistory.descendingIterator();
            while (iterator.hasNext()) {
                lastSortedMessage = iterator.next();
                if (lastSortedMessage.isDelivered()) {
                    break;
                }
            }
            setLastTime(chatHistory.getLast().getTimeStampInt());
        }
    }

    /**
     * Notifies an adapter of chat history in UI if the current chat is viewed at the moment
     */
    public void notifyChatHistory() {
        final ActivityGlobalManager globalManager = ActivityGlobalManager.getInstance();
        boolean chatIsShown = id.equals(globalManager.getCurrentChatId());
        if (chatIsShown) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (globalManager.getCurrentAdapter() != null) {
                        globalManager.getCurrentAdapter().notifyDataSetChanged();
                    }
                }
            });
        }
    }

    /**
     * Gets an id of current chat
     *
     * @return a unique id of current chat
     */
    public Integer getChatId() {
        return id;
    }

    /**
     * Adds a new file into a chat history
     *
     * @param fileData    an object containing all information about a new file
     * @param persIdent   user login of a file sender
     * @param sessionId   an id of a session key used to encrypt the file
     * @param dbChat      an instance of a DataChatHistory containing information about all chats
     * @param messageType a type of a message. can be IN or OUT
     * @return a message instance containing the added file
     */
    public DbChatMessage putFile(FileData fileData,
                                 String persIdent,
                                 String sessionId,
                                 DataChatHistory dbChat,
                                 int messageType) {

        DbChatMessage newMessage;

        newMessage = new DbChatMessage();
        newMessage.setLogin(persIdent);
        newMessage.setSessionId(Integer.valueOf(sessionId));
        newMessage.setChatId(getChatId());
        newMessage.setType(messageType);
        newMessage.setContentType(DbChatMessage.MSG_FILE);
        newMessage.setMessage(fileData.fileId);
        newMessage.setFileData(fileData);
        newMessage.setServerstate(DbChatMessage.FileServerStatus.RECEIVING);
        newMessage.setServerId(fileData.fileId);
        newMessage.setDbStatus(DbChatMessage.MessageDatabaseStatus.NEW);
        newMessage.setTimeStamp(new Date().getTime());
        dbChat.chatSaveMessage(getChatId(), newMessage, false);

        addMessageToChatHistory(newMessage, false);
        return newMessage;
    }

    /**
     * Adds a new message into a chat history
     *
     * @param responseMap      JSON response from the server containing a new message
     * @param type             a type of a message. can be IN or OUT
     * @param currentChat      a flag showing if current chat is viewed by user (true) or is hidden (false)
     * @param isHistoryMessage a flag showing if current message is received in real-time (false)
     *                         or by a chat history request (true)
     * @param dbChat           an instance of a DataChatHistory containing information about all chats
     */
    public void putMessage(Map<String, Object> responseMap,
                           int type,
                           boolean currentChat,
                           boolean isHistoryMessage,
                           DataChatHistory dbChat
    ) {
        String receivedMessage = (String) responseMap.get(Response.MessageResponse.MESSAGE);

        String sessionId = (String) responseMap.get(Response.MessageResponse.SESSION_ID);
        DbSessionKey sessionKey = ActivityGlobalManager.getInstance().getDb().sessionKeySelect(Integer.valueOf(sessionId));

        String serverMessageId = (String) responseMap.get(Response.MessageResponse.SERVER_MESSAGE_ID);
        String messageIsRead = (String) responseMap.get(Response.MessageResponse.MESSAGE_HAS_BEEN_READ);
        ArrayList<String> needKeys = new ArrayList<>();

        String persIdent = (String) responseMap.get(Response.MessageResponse.PERS_IDENT);
        if (sessionKey == null) {
            needKeys.add(sessionId);
        }
        DbChatMessage newMessage = null;

        if (serverMessageId != null) {
            newMessage = findMessageByServerId(serverMessageId);
        }
        boolean isNewMessage = false;

        if (newMessage == null) {
            newMessage = new DbChatMessage();
            newMessage.setLogin(persIdent);
            newMessage.setServerId(serverMessageId);
            newMessage.setSessionId(Integer.valueOf(sessionId));
            newMessage.setChatId(Integer.valueOf((String) responseMap.get(Response.MessageResponse.CHAT_ID)));
            newMessage.setType(type);
            newMessage.setDbStatus(DbChatMessage.MessageDatabaseStatus.NEW);
            isNewMessage = true;
        }
        String deliveryDate = (String) responseMap.get(Response.MessageResponse.DATE);
        newMessage.setTimeStamp(deliveryDate);

        if (type == DbChatMessage.MessageType.IN) {
            if (messageIsRead != null) {
                newMessage.setServerstate(DbChatMessage.MessageServerStatus.ENCRYPTED |
                        DbChatMessage.MessageServerStatus.HISTORY_IN);
            } else {
                newMessage.setServerstate(DbChatMessage.MessageServerStatus.ENCRYPTED);
            }
        } else {
            if (messageIsRead != null) {
                newMessage.setServerstate(DbChatMessage.MessageServerStatus.ENCRYPTED |
                        DbChatMessage.MessageServerStatus.DELIVERED);
            } else {
                newMessage.setServerstate(DbChatMessage.MessageServerStatus.ENCRYPTED |
                        DbChatMessage.MessageServerStatus.SEND);
            }
        }
        if (isNewMessage) {
            incomingQueue.add(newMessage);
            newMessage.setMessage(receivedMessage);
            dbChat.chatSaveMessage(getChatId(), newMessage, false);

            if (!currentChat)
                unreadMessageCnt++;

            if (!needKeys.isEmpty()) {
                preparedMessages.put(newMessage.getMsgId().toString(), newMessage);
            }
            addMessageToChatHistory(newMessage, isHistoryMessage);
        }
        activateIncomingTask();
    }

    /**
     * Decrypts new incoming messages
     */
    public synchronized void decryptIncomingMessage() {

        if (incomingMessageThread == null ||
                incomingMessageThread.isInterrupted() ||
                !incomingMessageThread.isRun) {
            incomingMessageThread = new MessageIncomingThread();
            incomingMessageThread.start();
        } else {
            synchronized (incomingMessageThread.lock) {
                incomingMessageThread.lock.notifyAll();
            }
        }
    }

    /**
     * Prepares the message for send and puts it into an outgoing messages queue
     *
     * @param message   the message text
     * @param dbChat    an instance of a DataChatHistory containing information about all chats
     * @param persIdent the sender's user login
     */
    public synchronized void sendMessage(String message, DataChatHistory dbChat, String persIdent) {

        DbChatMessage newMessage = new DbChatMessage();
        newMessage.setLogin(persIdent);
        newMessage.setChatId(getChatId());
        newMessage.setMessage(message);
        newMessage.setType(DbChatMessage.MessageType.OUT);
        newMessage.setDbStatus(DbChatMessage.MessageDatabaseStatus.NEW);
        newMessage.setServerstate(DbChatMessage.MessageServerStatus.PREPARE);
        Date msgTime = new Date();
        newMessage.setTimeStamp(msgTime.getTime());
        setLastTime(msgTime.getTime());
        dbChat.chatSaveMessage(getChatId(), newMessage, true);
        prepareChatMessageForsend(newMessage);

        outQueue.add(newMessage);
        activateOutThread();
    }

    /**
     * Makes an attempt to start messages sending or to request the actual session key
     */
    public void activateOutThread() {

        if (!isOutcast) {
            if (currentSessionKey == null || !currentSessionKey.isReady()) {
                readyForSend = SESSION_NEED_KEY;
                String sessionId = null;
                if (currentSessionKey != null && currentSessionKey.getSessionId() != null) {
                    sessionId = currentSessionKey.getSessionId().toString();
                }
                MessagePostman.getInstance().sendSessionKeyAskRequest(getChatId().toString(),
                        sessionId);
            } else {
                if (sendMessageThread != null) {
                    synchronized (tagForSync) {
                        readyForSend = SESSION_READY;
                        tagForSync.notifyAll();
                    }
                }
            }
            if (sendMessageThread == null || sendMessageThread.isInterrupted() || !sendMessageThread.isRun()) {
                sendMessageThread = new MessageParcerThread();
                sendMessageThread.start();
            }
        }
    }

    private void activateIncomingTask() {
        if (incomingMessageThread == null ||
                incomingMessageThread.isInterrupted() ||
                !incomingMessageThread.isRun()) {

            incomingMessageThread = new MessageIncomingThread();
            incomingMessageThread.start();
        }
    }

    /**
     * Sends a confirm for a message to the server
     *
     * @param stat the confirmation state of a message
     */
    public void confirmMessage(String stat) {
        try {
            confirmQueue.put(stat);
        } catch (InterruptedException e) {
            UniversalHelper.logException(e);
        }
    }

    private void addMessageToChatHistory(DbChatMessage newMessage, boolean isHistoryMessage) {
        if (isHistoryMessage) {
            chatHistory.addFirst(newMessage);
            notifyChatHistory();
        } else {
            chatHistory.addLast(newMessage);
            notifyChatHistory();
            sortChatHistory();
        }
    }

    /**
     * Gets the message using unique server id
     *
     * @param id the server id of a message needed
     * @return the DbChatMessage object if such message is found, or null if not
     */
    public DbChatMessage findMessageByServerId(String id) {
        DbChatMessage msg = null;
        if (chatHistory != null) {
            for (DbChatMessage m : chatHistory) {
                if (m.getServerId().equals(id)) {
                    msg = m;
                    break;
                }
            }
        }
        return msg;
    }

    /**
     * Gets the message using it's local id
     *
     * @param id the local id of a message needed
     * @return the DbChatMessage object if such message is found, or null if not
     */
    public DbChatMessage findMessageById(Integer id) {
        DbChatMessage msg = null;
        if (chatHistory != null) {
            for (DbChatMessage m : chatHistory) {
                if (m.getMsgId().equals(id)) {
                    msg = m;
                    break;
                }
            }
        }
        return msg;
    }


    @Override
    public boolean filterCheck(CharSequence mask) {
        boolean result = false;
        String pattern = mask.toString().toLowerCase();
        boolean isSavedChat;
        try {
            isSavedChat = chatGroup.getType().equals(DbGroupChat.GROUPTYPE_SAVE);
        } catch (Exception e) {
            UniversalHelper.logException(e);
            isSavedChat = false;
        }
        if (isSavedChat) {
            if ((getChatName().toLowerCase().startsWith(pattern)) || (getChatName().toLowerCase().contains(" " + pattern))) {
                result = true;
            }
        } else {
            for (DbMessengerUser curUser : participantList) {
                if ((curUser.getPersName().toLowerCase().startsWith(pattern)) ||
                        (curUser.getPersName().toLowerCase().contains(" " + pattern))) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Gets the fact that all participants of chat are unreachable
     *
     * @return true if all chat participants are unreachable,
     * returns false if there is at least one user online or offline in chat
     */
    public boolean ifChatUnreachable() {
        boolean result = true;
        if (chatGroup != null) {
            int groupId = chatGroup.getGroupId();
            for (DbMessengerUser user : participantList) {
                if (!user.isChatLeave(groupId) && userAvailableForChat(user)) {
                    result = false;
                    break;
                }
            }
        } else {
            for (DbMessengerUser user : participantList) {
                if (user == null ||
                        (userAvailableForChat(user)
                                && user.getDbStatus() != CustomValuesStorage.CATEGORY_UNKNOWN)) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    private boolean userAvailableForChat(DbMessengerUser user) {
        return user.getStatusOnline() != CustomValuesStorage.UserStatus.UNREACHABLE
                && user.getDbStatus() != CustomValuesStorage.CATEGORY_DELETE
                && user.getDbStatus() != CustomValuesStorage.CATEGORY_NOTCONNECT;
    }

    private int saveMessage(DataChatHistory dbChat, DbChatMessage msg, DbSessionKey sessionKey) {
        int result = 0;
        try {
            msg.setMessage(sessionKey.decryptMessage(msg.getMessage()));
        } catch (Exception e) {
            UniversalHelper.logException(e);
            msg.addDecryptCount();
            if (msg.getDecryptCount() < 4) {
                MessagePostman.getInstance().sendSessionKeyAskRequest(msg.getChatId().toString(), msg.getSessionId().toString());
                return 3;
            }
        }
        if (msg.getType().equals(DbChatMessage.MessageType.OUT)) {
            msg.setServerstate(msg.getServerstate() ^ DbChatMessage.MessageServerStatus.ENCRYPTED);
        } else {

            if (msg.getServerId() != null && !msg.getServerId().isEmpty() &&
                    ((msg.getServerstate() & DbChatMessage.MessageServerStatus.HISTORY_IN) == 0)
                    ) {
                MessagePostman.getInstance().sendReadConfirmRequest(msg.getServerId());
            }
            msg.setServerstate(DbChatMessage.MessageServerStatus.DECRYPTED);
        }
        msg.setDbStatus(DbChatMessage.MessageDatabaseStatus.NEW);
        if (dbChat != null) {
            dbChat.chatSaveMessage(getChatId(), msg, false);
        }

        return result;
    }

    /**
     * The class containing chat type markers: personal chat and group chat marker
     */
    public static class ChatCategoryType {
        public static final int PERSON = 1;

        public static final int GROUP = 2;

        private ChatCategoryType() {
        }
    }

    /**
     * A thread for outgoing messages processing and sending
     */
    public class MessageParcerThread extends Thread {
        private volatile boolean isRun;

        public boolean isRun() {
            return isRun;
        }

        @Override
        public void run() {

            while (!this.isInterrupted()) {
                try {
                    isRun = true;
                    DbChatMessage outMessage;

                    synchronized (tagForSync) {
                        if (!readyForSend.equals(SESSION_READY)) {
                            tagForSync.wait(10000);
                        }
                    }
                    outMessage = outQueue.poll(15L, TimeUnit.SECONDS);

                    if (outMessage != null) {
                        processOutgoingMessage(outMessage);
                    } else {
                        this.interrupt();
                        isRun = false;
                    }
                } catch (InterruptedException ex) {
                    UniversalHelper.logException(ex);
                    this.interrupt();
                    isRun = false;
                    break;
                }
            }
        }

        private void processOutgoingMessage(DbChatMessage outMessage) throws InterruptedException {
            String encryptedMessage;
            if (outMessage.getContentType() == DbChatMessage.MSG_FILE) {
                DbSessionKey fileKey =
                        ActivityGlobalManager.getInstance().getDb().sessionKeySelect(
                                Integer.valueOf(outMessage.getFileData().getSessionid())
                        );
                DataFileStorage dfStore = ActivityGlobalManager.getInstance().getDbFileStorage();

                if (fileKey != null && fileKey.isReady()) {
                    if (outMessage.getType().equals(DbChatMessage.MessageType.OUT)) {
                        dfStore.readFileForSend(outMessage.getFileData().getFileId(),
                                (int) outMessage.getFileData().getCurrentChunk() - 1, null);
                    } else {
                        MessagePostman.getInstance().sendGetChunkRequest(String.valueOf(outMessage.getFileData().getCurrentChunk()),
                                outMessage.getFileData().getFileId());
                    }

                } else {
                    outQueue.add(outMessage);
                    readyForSend = SESSION_FAIL;
                    MessagePostman.getInstance().sendSessionKeyAskRequest(getChatId().toString(),
                            outMessage.getFileData().getSessionid());

                }
            } else {
                if (currentSessionKey != null && currentSessionKey.isReady()) {
                    outMessage.setSessionId(currentSessionKey.getSessionId());
                    encryptedMessage = currentSessionKey.encryptMessage(outMessage.getMessage());
                    MessagePostman.getInstance().sendChatMessageRequest(
                            outMessage.getChatId().toString(),
                            outMessage.getMsgId().toString(),
                            currentSessionKey.getSessionId().toString(),
                            encryptedMessage
                    );
                    String state = confirmQueue.take();
                    if (state == null || !state.equals(SESSION_OK)) {
                        outQueue.addFirst(outMessage);
                    }
                } else {
                    outQueue.addFirst(outMessage);
                    readyForSend = SESSION_FAIL;
                }
            }
        }
    }

    /**
     * A thread for incoming messages processing and saving
     */
    public class MessageIncomingThread extends Thread {
        public final Object lock = new Object();
        private volatile boolean isRun;

        @Override
        public void run() {
            isRun = true;
            TsmDatabaseHelper db;
            DataChatHistory dbChat;
            db = ActivityGlobalManager.getInstance().getDb();
            dbChat = ActivityGlobalManager.getInstance().getDbChatHistory();

            while (!this.isInterrupted()) {
                if (dbChat == null) {
                    dbChat = ActivityGlobalManager.getInstance().getDbChatHistory();
                }
                int result = processIncomingMessage(db, dbChat);
                boolean toBreak = result == 1;
                if (result == 2 || result == 3) {
                    synchronized (lock) {
                        try {
                            lock.wait(5000);
                        } catch (InterruptedException e) {
                            UniversalHelper.logException(e);
                            this.interrupt();
                            isRun = false;
                            if (result == 3) {
                                incomingMessageThread = null;
                            }
                            toBreak = true;
                        }
                    }
                }
                if (toBreak) {
                    break;
                }
            }
            isRun = false;
        }

        private int processIncomingMessage(TsmDatabaseHelper db, DataChatHistory dbChat) {
            int result;
            DbChatMessage msg;
            DbSessionKey sessionKey;
            msg = incomingQueue.poll();
            if (msg == null) {
                this.interrupt();
                isRun = false;
                incomingMessageThread = null;
                sortChatHistory();
                result = 1;
            } else {
                sessionKey = db.sessionKeySelect(msg.getSessionId());

                if (sessionKey == null ||
                        sessionKey.getKeyStatus() == DbSessionKey.ST_WAIT ||
                        (!sessionKey.isReady())) {
                    incomingQueue.addFirst(msg);

                    if (sessionKey == null) {
                        DbSessionKey newSessionKey = new DbSessionKey();
                        newSessionKey.setSessionId(msg.getSessionId());
                        ActivityGlobalManager.getInstance().getDb().sessionKeySave(newSessionKey);
                        MessagePostman.getInstance().sendSessionKeyAskRequest(msg.getChatId().toString(), msg.getSessionId().toString());
                    }
                    result = 2;
                } else {
                    if (!sessionKey.isReady()) {
                        incomingQueue.addFirst(msg);
                        result = 2;
                    } else {
                        result = saveMessage(dbChat, msg, sessionKey);
                    }
                }
            }
            return result;
        }

        public boolean isRun() {
            return isRun;
        }
    }
}
