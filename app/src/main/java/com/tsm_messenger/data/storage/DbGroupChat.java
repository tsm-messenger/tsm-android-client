package com.tsm_messenger.data.storage;

import com.tsm_messenger.activities.R;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.CustomValuesStorage;

import java.util.HashMap;
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

public class DbGroupChat {
    public static final String GROUPTYPE_TEMP = "temp";
    public static final String GROUPTYPE_SAVE = "save";
    public static final int MAX_GROUP_COUNT = 30;
    private final Map<String, DbMessengerUser> members = new HashMap<>();
    private String groupName;
    private Integer groupId;
    private String type;
    private Integer sequreType;

    /**
     * Gets a string representation of a current group name
     *
     * @return a list of participants in a string, or a custom group name
     */
    public String getGroupName() {
        String result;
        if (groupName == null || groupName.isEmpty()) {
            int i = 0;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("");
            for (DbMessengerUser user : members.values()) {
                i++;
                stringBuilder.append(stringBuilder.length() == 0 ? "" : ", ").append(user.getPersName());
                if (i > 1 && members.size() > 2) {
                    stringBuilder.append("...");
                    break;
                }
            }
            if (members.size() == 0) {
                stringBuilder.append(ActivityGlobalManager.getInstance().getCurrentActivity().getString(R.string.lbl_empty_chat));
            }
            result = stringBuilder.toString();
        } else {
            result = groupName;
        }
        return result;
    }

    /**
     * Sets a custom name for a group
     *
     * @param groupName a string to set a custom group name or null to remove it
     */
    public void setGroupName(String groupName) {
        this.groupName = groupName == null ? "" : groupName;
    }

    /**
     * Gets a type of an algorytm used to store messages for current group chat
     *
     * @return an integer representation of a sequre-storage algorytm
     */
    public Integer getSequreType() {
        return sequreType == null ? Integer.valueOf(Param.ChatSecureLevel.ALL_HISTORY_KEEP) : sequreType;
    }

    /**
     * Sets a type of an algorytm used to store messages for current group chat
     *
     * @param sequreType an integer representation of a sequre-storage algorytm
     */
    public void setSequreType(Integer sequreType) {
        this.sequreType = sequreType;
    }

    /**
     * Gets the unique id of current group
     *
     * @return an integer representation of current group id
     */
    public Integer getGroupId() {
        return groupId;
    }

    /**
     * Sets the unique id of current group
     *
     * @param groupId an integer representation current group id
     */
    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    /**
     * Gets the type of a group: saved in a contact list or temporary
     *
     * @return a string representation of current group type
     */
    public String getType() {
        return type == null ? GROUPTYPE_SAVE : type;
    }

    /**
     * Sets the type of a group: saved in a contact list or temporary
     *
     * @param type a string representation of current group type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets a string representation of the unique id of current group
     *
     * @return an string representation of current group id
     */
    public String getGroupIdString() {
        return CustomValuesStorage.GROUP_DESCRIPTIOR + groupId.toString();
    }

    /**
     * Gets the map of current group participants identified by their user logins
     *
     * @return a map of current group participants
     */
    public Map<String, DbMessengerUser> getMembers() {
        return members;
    }

    /**
     * Adds the provided user to current group
     *
     * @param key       new member's user login
     * @param newMember a new group participant to add
     */
    public void addMember(String key, DbMessengerUser newMember) {
        members.put(key, newMember);
    }
}
