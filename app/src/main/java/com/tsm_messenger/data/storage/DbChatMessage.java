package com.tsm_messenger.data.storage;

import android.support.annotation.NonNull;

import com.google.gson.Gson;
import com.tsm_messenger.protocol.transaction.Operation;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.UniversalHelper;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*************************************************************************
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

public class DbChatMessage implements Comparable<DbChatMessage> {
    public static final int MSG_HISTORY = 0;
    public static final int MSG_TEXT = 1;
    public static final int MSG_FILE = 2;
    public static final int MSG_SERVICE = 3;
    private Integer id;
    private Integer chatId;
    private String login;
    private Integer type;
    private Date timeStamp;
    private String message;
    private Integer serverstate;
    private Integer sessionId;
    private int contentType = MSG_TEXT;
    private FileData fileData;
    private int receiveCount = 0;
    private int cancelCount = 0;
    private String serverId;
    private int dbStatus = MessageDatabaseStatus.NEW;
    private int decryptCount = 0;
    private Operation serviceOperation;

    /**
     * Increments the count of users who have canceled receiving of the file contained in the message
     */
    public void incFileCancel() {
        cancelCount++;
    }

    /**
     * Gets a count of the users who have canceled receiving of the file contained in the message
     *
     * @return the number of times current file was canceled
     */
    public int getFileCancelCount() {
        return cancelCount;
    }

    /**
     * Increments the count of users who have successfully received the file contained in the message
     */
    public void incReceiveCount() {
        receiveCount++;
    }

    /**
     * Gets a count of the users who have successfully received the file contained in the message
     *
     * @return the number of times current file was received
     */
    public int getReceiveCount() {
        return receiveCount;
    }

    /**
     * Sets the count of users who have successfully received the file contained in the message
     *
     * @param receiveCount number of times current file was received
     */
    public void setReceiveCount(int receiveCount) {
        this.receiveCount = receiveCount;
    }

    /**
     * Sets the count of users who have canceled receiving of the file contained in the message
     *
     * @param cancelCount number of times current file was canceled
     */
    public void setCancelCount(int cancelCount) {
        this.cancelCount = cancelCount;
    }

    @Override
    public int compareTo(@NonNull DbChatMessage another) {
        int compareresult;
        boolean thisDeliv, anotherDeliv;

        compareresult = Integer.valueOf(getContentType()).compareTo(another.getContentType());

        if (compareresult != 0) {
            compareresult = getTimeStampInt().compareTo(another.getTimeStampInt());
        } else {
            thisDeliv = this.isDelivered();
            anotherDeliv = another.isDelivered();

            if (thisDeliv == anotherDeliv) {
                compareresult = getTimeStampInt().compareTo(another.getTimeStampInt());
            } else {
                if (anotherDeliv) {
                    compareresult = 1;
                } else {
                    compareresult = -1;
                }
            }
        }
        return compareresult;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || this.getClass() == o.getClass())
            return false;

        boolean ret;
        DbChatMessage localMessage = (DbChatMessage) o;
        ret = getContentType() == localMessage.getContentType();
        ret &= getTimeStampInt().equals(localMessage.getTimeStampInt());
        ret &= isDelivered() == localMessage.isDelivered();
        return ret;
    }

    @Override
    public int hashCode() {
        return getMsgId();
    }

    /**
     * Defines if current message has been already delivered to a receiver
     *
     * @return true if current message is marked as received, returns false if not
     */
    public boolean isDelivered() {
        boolean result;
        switch (contentType) {
            case MSG_TEXT:
                result = (type == MessageType.IN) ||
                        (serverstate == MessageServerStatus.DELIVERED) ||
                        (serverstate == MessageServerStatus.SEND);
                break;
            case MSG_FILE:
                result = true;
                break;
            default:
                result = true;
                break;
        }
        return result;
    }

    /**
     * Gets the number of attempts to decrypt current message
     *
     * @return an integer number of decryption attempts
     */
    public int getDecryptCount() {
        return decryptCount;
    }

    /**
     * Increments the number of attempts to decrypt current message
     */
    public void addDecryptCount() {
        this.decryptCount++;
    }

    /**
     * Gets the file contained in current message
     *
     * @return an instance of FileData class if there is a file contained in the message.
     * returns null if there is no files in current message
     */
    public FileData getFileData() {
        if (fileData == null) {
            fileData = ActivityGlobalManager.getInstance().getDbFileStorage().get(message);
        }
        return fileData;
    }

    /**
     * Puts the file object into a message
     *
     * @param fileData a file to put into a message
     */
    public void setFileData(FileData fileData) {
        this.fileData = fileData;
        this.fileData.setMessage(this);
    }

    /**
     * Returns an integer representation of current message type
     *
     * @return an integer code of message type
     */
    public int getContentType() {
        return contentType;
    }

    /**
     * Sets a new message type
     *
     * @param contentType an integer code of message type
     */
    public void setContentType(int contentType) {
        this.contentType = contentType;
    }

    /**
     * Gets a server id of a current message
     *
     * @return gets a string representation of a unique server id of a message
     */
    public String getServerId() {
        return serverId == null ? "" : serverId;
    }

    /**
     * Sets a server id of a current message
     *
     * @param serverId a string representation of a unique server id of a message
     */
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    /**
     * Gets an id of a session key used for current message encryption
     *
     * @return a sessionId for current message
     */
    public Integer getSessionId() {
        return sessionId;
    }

    /**
     * Sets an id of a session key used for current message encryption
     *
     * @param sessionId a sessionId for current message
     */
    public void setSessionId(Integer sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Gets a local id of current message
     *
     * @return an integer local id of current message
     */
    public Integer getMsgId() {
        return id;
    }

    /**
     * Sets a local ID of current message
     *
     * @param id an integer local ID of current message
     */
    public void setMsgId(Integer id) {
        this.id = id;
    }

    /**
     * Gets an id of a chat which current message belongs to
     *
     * @return an id of a chat for current message
     */
    public Integer getChatId() {
        return chatId;
    }

    /**
     * Sets an ID of a chat which current message belongs to
     *
     * @param chatId an id of a chat for current message
     */
    public void setChatId(Integer chatId) {
        this.chatId = chatId;
    }

    /**
     * Gets a user login of a message author
     *
     * @return a user login of a message author
     */
    public String getLogin() {
        return login;
    }

    /**
     * Sets a user login of a message author
     *
     * @param login a user login of a message author
     */
    public void setLogin(String login) {
        this.login = login;
    }

    /**
     * Gets the type of a message: incoming (in) or outgoing (out)
     *
     * @return an integer code of a message type
     */
    public Integer getType() {
        return type;
    }

    /**
     * Sets the type of a message: incoming (in) or outgoing (out)
     *
     * @param type an integer code of a message type
     */
    public void setType(Integer type) {
        this.type = type;
    }

    /**
     * Gets the state of a message: if it is delivered to the server or to receiver
     * or is not delivered at all
     *
     * @return an integer code of a message state
     */
    public int getServerstate() {
        return serverstate == null ? MessageServerStatus.DELIVERED : serverstate;
    }

    /**
     * Sets the state of a message: if it is delivered to the server or to receiver
     * or is not delivered at all
     *
     * @param serverstate an integer code of a message state
     */
    public void setServerstate(Integer serverstate) {
        this.serverstate = serverstate == null ? Integer.valueOf(MessageServerStatus.PREPARE) : serverstate;
    }

    /**
     * Gets the timestamp when the message was sent to the server or confirmed by server
     *
     * @return returns a message timestamp as a Date object
     */
    public Date getTimeStamp() {
        return timeStamp;
    }

    /**
     * Sets the timestamp when the message was sent to the server or confirmed by server
     *
     * @param intTime a message timestamp in a milliseconds format
     */
    public void setTimeStamp(Long intTime) {
        this.timeStamp = new Date(intTime);
    }

    /**
     * Sets the timestamp when the message was sent to the server or confirmed by the server
     *
     * @param dateMask a message timestamp in a string format yyyy-MM-dd HH:mm ss:SSSz
     */
    public void setTimeStamp(String dateMask) {
        DateFormat df = new SimpleDateFormat(Param.DATE_FORMAT, Locale.US);
        Date receiveDate;
        try {
            receiveDate = df.parse(dateMask);
        } catch (ParseException pe) {
            UniversalHelper.logException(pe);
            receiveDate = new Date();
        }
        setTimeStamp(receiveDate.getTime());
    }

    /**
     * Gets the timestamp when the message was sent to the server or confirmed by the server
     *
     * @return a message timestamp in a milliseconds format
     */
    public Long getTimeStampInt() {
        Long lDate;
        lDate = timeStamp == null ? 0 : timeStamp.getTime();
        return lDate;

    }

    /**
     * Gets the timestamp when the message was sent to the server or confirmed by the server
     *
     * @return a timestamp string to display it via UI
     */
    public String getTimeString() {

        return timeStamp == null ? "--:--" : ActivityGlobalManager.getTimeString(timeStamp);
    }

    /**
     * Gets the message text of current message instance
     *
     * @return the current message text
     */
    public String getMessage() {
        String msgToShow;
        if (contentType == MSG_SERVICE) {
            msgToShow = getParticipantsString(message);
        } else {
            msgToShow = message;
        }
        return msgToShow;
    }

    /**
     * Sets the message text for current message instance
     *
     * @param message current message text
     */
    public void setMessage(String message) {
        this.message = message;
    }

    private String getParticipantsString(String message) {
        String result;
        Map<String, DbMessengerUser> dbAddress = ActivityGlobalManager.getInstance().getDbContact().getMessengerDb();
        try {
            List<String> participants = Arrays.asList(new Gson().fromJson(message, String[].class));
            StringBuilder participantsBuilder = new StringBuilder();
            DbMessengerUser tmpUser;

            int counter = 0;
            for (String participantId : participants) {
                tmpUser = dbAddress.get(participantId);
                if (tmpUser != null) {
                    participantsBuilder.append(tmpUser.getDisplayName());
                } else {
                    participantsBuilder.append(participantId);
                }
                if (++counter != participants.size()) {
                    participantsBuilder.append(", ");
                }
            }
            result = participantsBuilder.toString();
        } catch (Exception e) {
            UniversalHelper.logException(e);
            result = message;
        }
        return result;
    }

    /**
     * Gets the status of message if it is saved or still not
     *
     * @return the integer code of message database status
     */
    public int getDbStatus() {
        return dbStatus;
    }

    /**
     * Sets the status of message if it is saved or still not
     *
     * @param dbStatus the integer code of message database status
     */
    public void setDbStatus(int dbStatus) {
        this.dbStatus = dbStatus;
    }

    public Operation getServiceOperation() {
        return serviceOperation;
    }

    public void setServiceOperation(Operation serviceOperation) {
        this.serviceOperation = serviceOperation;
    }

    public int getServiceOperationCode() {
        return serviceOperation != null ? serviceOperation.getValue() : -1;
    }

    public void setServiceOperationByCode(int operationCode) {
        try {
            serviceOperation = Operation.getByValue(operationCode);
        } catch (Exception e) {
            UniversalHelper.logException(e);
            serviceOperation = null;
        }
    }

    /**
     * A class to represent a type of a message: incoming (in) or outgoing (out)
     */
    public abstract static class MessageType {
        public static final int IN = 1;
        public static final int OUT = 0;

        private MessageType() {
        }
    }

    /**
     * A class to represent a database status of a message: saved or not
     */
    public abstract static class MessageDatabaseStatus {
        public static final int NEW = 0;
        public static final int SAVED = 1;

        private MessageDatabaseStatus() {
        }
    }

    /**
     * A class to represent the state of message sending process
     */
    public abstract static class MessageServerStatus {
        public static final int PREPARE = 1;
        public static final int SEND = 2;
        public static final int DELIVERED = 4;
        public static final int ENCRYPTED = 8;
        public static final int DECRYPTED = 16;
        public static final int HISTORY_IN = 32;

        private MessageServerStatus() {
        }
    }

    /**
     * A class to represent a state of a file sending process for current message
     */
    public abstract static class FileServerStatus {
        public static final int RECEIVING = 1;
        public static final int ERROR = 4;

        private FileServerStatus() {
        }
    }
}
