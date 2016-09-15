package com.tsm_messenger.connection;

import android.support.annotation.NonNull;

import com.tsm_messenger.protocol.dto.DummyDto;
import com.tsm_messenger.service.UniversalHelper;

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
class OutcomingMessage implements Comparable<OutcomingMessage> {
    public static final int PRIORITY_LOW = 3;
    public static final int PRIORITY_NORMAL = 2;
    public static final int PRIORITY_HIGH = 1;
    public static final int PRIORITY_IMMEDIATE = 0;
    private DummyDto message;
    private Integer priority;
    private String messageId;


    @Override
    public int compareTo(@NonNull OutcomingMessage another) {
        int ret;
        ret = this.priority.compareTo(another.priority);
        if (ret == 0 && getId() != null) {

            ret = getId().compareTo(another.getId());
        }
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (this.getClass() != o.getClass()) {
            return false;
        }

        boolean ret;
        OutcomingMessage msg = (OutcomingMessage) o;
        ret = this.priority.equals(msg.priority);
        if (ret && msg.getId() != null) {
            ret = getId().equals(msg.getId());
        }
        return ret;
    }

    @Override
    public int hashCode() {
        return getId() != null ? getId() : super.hashCode();
    }

    private Integer getId() {
        Integer id = 0;
        try {
            if (messageId != null) {
                id = Integer.getInteger(messageId.substring(2));
            }
        } catch (Exception e) {
            UniversalHelper.logException(e);
            id = 0;
        }
        return id;
    }

    /**
     * Returns body of the current message
     *
     * @return message's body as DummyDto object
     */
    public DummyDto getMessage() {
        return message;
    }

    /**
     * Sets current message
     *
     * @param message the Map object with message
     */
    public void setMessage(DummyDto message) {
        this.message = message;
    }

    /**
     * Returns the priority of current message
     *
     * @return integer number which shows the priority of current item
     */
    public Integer getPriority() {
        return priority;
    }

    /**
     * Sets a new priority of current item
     *
     * @param priority a new priority of the item
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Shows current message ID
     *
     * @return string representation of message ID
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * Sets a new ID for the current message
     *
     * @param messageId a new message ID
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}