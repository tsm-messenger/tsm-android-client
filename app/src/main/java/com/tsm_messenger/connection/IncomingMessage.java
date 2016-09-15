package com.tsm_messenger.connection;

import android.support.annotation.NonNull;

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
class IncomingMessage implements Comparable<IncomingMessage> {
    public static final int PRIORITY_LOW = 3;
    public static final int PRIORITY_NORMAL = 2;
    public static final int PRIORITY_HIGH = 1;
    public static final int PRIORITY_IMMEDIATE = 0;
    private String message;
    private Integer priority;
    private String messageId;

    @Override
    public int compareTo(@NonNull IncomingMessage another) {
        int ret;
        ret = this.priority.compareTo(another.priority);
        if (ret == 0 && getId() != null && another.getId() != null) {
            ret = getId().compareTo(another.getId());
        } else {
            ret = getId() == null ? 1 : -1;
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
        IncomingMessage msg = (IncomingMessage) o;
        ret = this.priority.equals(msg.priority);
        if (ret && msg.getId() != null && getId() != null) {
            ret = getId().equals(msg.getId());
        }
        return ret;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * Returns an ID for current message
     *
     * @return a string representation of current message ID
     */
    private String getId() {
        return messageId;
    }

    /**
     * Returns the text of current message
     *
     * @return a string representation of current message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the new message text for current item
     *
     * @param message a new message text
     */
    public void setMessage(String message) {
        this.message = message;
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
     * Sets a new id for current message
     *
     * @param messageId a new message ID
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}