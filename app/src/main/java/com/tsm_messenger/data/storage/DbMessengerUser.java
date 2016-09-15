package com.tsm_messenger.data.storage;

import com.tsm_messenger.service.CustomValuesStorage;

import java.util.HashSet;
import java.util.Set;

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

public class DbMessengerUser {
    private final Set<Integer> leavedchats = new HashSet<>();
    private String persId;
    private String persName;
    private String nickName;
    private Integer status;
    private Integer dbStatus;
    private String publicKey;

    private CustomValuesStorage.UserStatus statusUser;

    /**
     * Gets the current online status of a user
     *
     * @return an instance of CustomValuesStorage.UserStatus
     */
    public CustomValuesStorage.UserStatus getStatusOnline() {
        return statusUser == null ? CustomValuesStorage.UserStatus.OFFLINE : statusUser;
    }

    /**
     * Sets the current online status of a user
     *
     * @param statusOnline current online status
     */
    public void setStatusOnline(CustomValuesStorage.UserStatus statusOnline) {
        if (this.dbStatus != CustomValuesStorage.CATEGORY_CONNECT)
            this.statusUser = CustomValuesStorage.UserStatus.OFFLINE;
        else
            this.statusUser = statusOnline;
    }

    /**
     * Gets the current database status of a user: connected, not connected, unknown etc.
     *
     * @return an integer representation of a current db status
     */
    public Integer getDbStatus() {
        return dbStatus;
    }

    /**
     * Sets the current database status of a user: connected, not connected, unknown etc.
     *
     * @param dbStatus an integer representation of a current db status
     */
    public void setDbStatus(Integer dbStatus) {
        this.dbStatus = dbStatus;
        this.status = dbStatus;
        if (this.dbStatus != CustomValuesStorage.CATEGORY_CONNECT) {
            this.statusUser = CustomValuesStorage.UserStatus.OFFLINE;
        }
    }

    /**
     * Gets the current status of a user: connected, not connected, unknown etc.
     *
     * @return an integer representation of a current user status
     */
    public Integer getStatus() {
        return status == null ? Integer.valueOf(0) : status;
    }

    /**
     * Sets the current status of a user: connected, not connected, unknown etc.
     *
     * @param status an integer representation of a current user status
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Gets the current visible name of a user
     *
     * @return returns a string representing current visible name of a user
     */
    public String getPersName() {
        return persName;
    }

    /**
     * Sets the current visible name of a user
     *
     * @param persName custom name
     */
    public void setPersName(String persName) {
        if (persName == null || persName.isEmpty()) {
            this.persName = persId;
        } else {
            this.persName = persName;
        }
    }

    /**
     * Gets the current visible name of a user
     *
     * @return a string representing current visible name of a user
     */
    public String getPersLogin() {
        return nickName;
    }

    /**
     * Sets the current visible name of a user
     *
     * @param nickName custom name
     */
    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    /**
     * Gets the unique user's public name to check his signature
     *
     * @return a string representation of the user's public key
     */
    public String getPublicKey() {
        return publicKey;
    }

    /**
     * Sets the unique user's public name to check his signature
     *
     * @param publicKey a string representation of the user's public key
     */
    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    /**
     * Gets the current visible name of a user
     *
     * @return a custom user name if it is not null, or his login if custom name is null
     */
    public String getDisplayName() {
        return persName == null ? nickName : persName;
    }

    /**
     * Gets the login of current user
     *
     * @return returns a string representing current user's id
     */
    public String getPersId() {
        return persId;
    }

    /**
     * Sets the login of current user
     *
     * @param persId a string representing current user's login
     */
    public void setUserLogin(String persId) {
        this.persId = persId;
    }

    /**
     * Makes a user leaving a concrete chat
     *
     * @param groupId a group id to leave chat
     */
    public void leaveChat(Integer groupId) {
        leavedchats.add(groupId);
    }

    /**
     * Checks if current user has left a concrete chat
     *
     * @param groupId a group id to check
     * @return true if current user has left a provided chat, returns false if not
     */
    public boolean isChatLeave(Integer groupId) {
        return leavedchats.contains(groupId);
    }
}
