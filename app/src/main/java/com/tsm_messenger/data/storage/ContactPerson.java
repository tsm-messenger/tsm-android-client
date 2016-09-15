package com.tsm_messenger.data.storage;

import android.content.Context;

import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.UniversalHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

public class ContactPerson extends DataObjectCommon {

    public static final int MESSENGER = 1;
    public static final int GROUP = 2;
    public static final String TYPE = "type";
    public static final String CONTACT = "contact";
    public static final String NICKNAME = "nickname";
    public static final String CHECK = "check";
    private DbMessengerUser messengerUser;
    private DbGroupChat groupChat;

    /**
     * A constructor for a ContactPerson instance
     */
    public ContactPerson() {
        //do nothing
    }

    /**
     * Gets current user's online status
     *
     * @return CustomValuesStorage.UserStatus enum member
     */
    public CustomValuesStorage.UserStatus getStatusOnline() {
        if (messengerUser != null)
            return messengerUser.getStatusOnline();
        else
            return CustomValuesStorage.UserStatus.UNREACHABLE;
    }

    /**
     * Gets current contactPerson's group if possible
     *
     * @return a group object if current ContactPerson instance represents a group,
     * returns null if current ContactPerson instance represents a person
     */
    public DbGroupChat getGroupChat() {
        return groupChat;
    }

    /**
     *  Sets a group chat object for current ContactPerson instance
     *
     *  @param groupChat a group chat object to make current contactPerson a group
     */
    public void setGroupChat(DbGroupChat groupChat) {
        this.groupChat = groupChat;
    }

    /**
     *  Gets current user's online status
     *
     *  @return  CustomValuesStorage.UserStatus enum member
     */
    public int getContactType() {
        return groupChat != null ? GROUP : MESSENGER;
    }

    /**
     *  Gets current contactPerson's user if possible
     *
     *  @return a DbMessengerUser object if current ContactPerson instance represents a person,
     *  returns null if current ContactPerson instance represents a group
     */
    public DbMessengerUser getMessengerUser() {
        return messengerUser;
    }

    /**
     *  Sets a DbMessengerUser object for current ContactPerson instance
     *
     *  @param messengerUser a DbMessengerUser object to make current contactPerson a person
     */
    public void setMessengerUser(DbMessengerUser messengerUser) {
        this.messengerUser = messengerUser;
    }

    public String toString() {
        return getDisplayName();
    }

    /**
     *  Gets a first letter of a display name to show it via UI
     *
     *  @return the first letter of a display name if it exists,
     *  returns an empty string if display name is null or empty
     */
    public String getFirstLetter() {
        String displayName;
        displayName = getDisplayName();
        return displayName == null || displayName.isEmpty() ? "" : displayName.substring(0, 1);
    }

    /**
     *  Gets a messenger id of an item, contained in ContactPerson instance
     *
     *  @return user id if current instance is a person,
     *  returns group id if current instance is a group
     */
    public String getMessengerId() {
        String messengerId = null;
        if (messengerUser != null) {
            messengerId = messengerUser.getPersId();
        } else {
            if (groupChat != null) {
                messengerId = groupChat.getGroupIdString();
            }
        }
        return messengerId == null ? "" : messengerId;
    }

    /**
     *  Gets a string, representing current instance of ContactPerson
     *
     *  @return the name of group or person contained in current instance of ContactPerson,
     *  returns an empty string or "***" if display name cannot be generated
     */
    public String getDisplayName() {
        String displayName;
        if (groupChat != null)
            displayName = groupChat.getGroupName();
        else if (messengerUser != null)
            displayName = messengerUser.getPersName();
        else
            displayName = "***";
        return displayName == null ? "" : displayName;
    }

    /**
     *  Returns a type of a contact contained in current instance of ContactPerson
     *
     *  @return  CustomValuesStorage.CATEGORY_GROUP if there is the group contained in current instance,
     *  returns CustomValuesStorage.CATEGORY_MESSENGER if there is the person contained in current instance,
     *  returns 0 if current instance does not contain neither group, nor person
     */
    public Integer getStat() {
        Integer isCheck;
        if (groupChat != null) {
            isCheck = CustomValuesStorage.CATEGORY_GROUP;
        } else {
            if (messengerUser != null) {
                isCheck = messengerUser.getStatus();
            } else {
                isCheck = null;
            }
        }
        return isCheck == null ? Integer.valueOf(0) : isCheck;
    }

    /**
     *  Gets a string, representing current instance of ContactPerson
     *
     *  @param context is Context object
     *  @return the List of  DbMessengerUser objects of group or person contained in current instance of ContactPerson,
     *  returns an empty string or "***" if display name cannot be generated
     */
    public List<DbMessengerUser> getContactDetail(Context context) {
        List<DbMessengerUser> contactDetail = new ArrayList<>();


        if (groupChat != null) {
            contactDetail.addAll(getGroupChatContact());
        } else {
            contactDetail.add(messengerUser);
        }


        return contactDetail;
    }

    private List<DbMessengerUser> getGroupChatContact() {
        List<DbMessengerUser> contactDetail;

        contactDetail = new ArrayList<>();

        for (Map.Entry<String, DbMessengerUser> row : groupChat.getMembers().entrySet()) {
            if (row.getValue() != null && row.getValue().isChatLeave(groupChat.getGroupId())) {
                continue;
            }
            contactDetail.add(row.getValue());
        }
        return contactDetail;
    }

    /**
     *  Gets users that can be added to a chat
     *
     *  @param dbAddress a DataAddressBook instance containing all ContactPerson items
     *  @return a list of HashMaps containing data about users that are not in chat
     */
    public List<HashMap<String, String>> getNotInChatPersons(DataAddressBook dbAddress) {
        List<HashMap<String, String>> contactDetail = new ArrayList<>();
        HashMap<String, String> tmpContact;
        Set<String> allFriends = dbAddress.getMessengerDb().keySet();
        Set<String> inChatFriends;
        try {
            inChatFriends = groupChat.getMembers().keySet();
        } catch (NullPointerException e) {
            UniversalHelper.logException(e);
            inChatFriends = new HashSet<>();
            inChatFriends.add(messengerUser.getPersId());
        }

        DbMessengerUser tmpUser;
        for (String row : allFriends) {
            tmpUser = dbAddress.getMessengerDb().get(row);
            if (!tmpUser.getDbStatus().equals(CustomValuesStorage.CATEGORY_CONNECT) ||
                    tmpUser.getStatusOnline().equals(CustomValuesStorage.UserStatus.UNREACHABLE))
                continue;

            if (!inChatFriends.contains(row)) {
                tmpContact = new HashMap<>();
                tmpContact.put(TYPE, row);
                tmpContact.put(NICKNAME, dbAddress.getMessengerDb().get(row).getPersLogin());
                tmpContact.put(CONTACT, dbAddress.getMessengerDb().get(row).getDisplayName());
                tmpContact.put(CHECK, "0");
                contactDetail.add(tmpContact);
            }
        }
        return contactDetail;
    }

    @Override
    public boolean filterCheck(CharSequence mask) {
        boolean res = false;
        String pattern = mask.toString().toLowerCase();
        if ((getDisplayName().toLowerCase().startsWith(pattern)) ||
                (getDisplayName().toLowerCase().contains(" " + pattern))) {
            res = true;
        } else if (messengerUser != null && messengerUser.getPersId().toLowerCase().startsWith(pattern)) {
            res = true;
        }
        return res;
    }
}
