package com.tsm_messenger.data.storage;


import android.content.Context;
import android.content.SharedPreferences;
import android.util.SparseArray;

import com.tsm_messenger.connection.MessagePostman;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.SharedPreferencesAccessor;
import com.tsm_messenger.service.UniversalHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tsm_messenger.protocol.transaction.Request;
import com.tsm_messenger.protocol.transaction.Response;

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
 */
public class DataAddressBook {

    private final TsmDatabaseHelper dbHelper;

    private final List<ContactPerson> registeredList;
    private Map<String, DbMessengerUser> messengerDb;
    private SparseArray<DbGroupChat> groupChat;

    private String ownerPersId;

    /**
     * A constructor initializing all collections and references in current DataAddressBook instance
     *
     * @param context a context to get current instances of SharedPreferences and TsmDatabaseHelper
     */
    public DataAddressBook(Context context) {

        messengerDb = new HashMap<>();
        groupChat = new SparseArray<>();

        registeredList = new ArrayList<>();
        SharedPreferences settings = context.getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME,
                SharedPreferencesAccessor.PREFS_MODE);
        ownerPersId = settings.getString(SharedPreferencesAccessor.USER_ID, "");
        dbHelper = TsmDatabaseHelper.getInstance(context);
    }

    /**
     * creates new users or refreshes info about existing users in users list
     *
     * @param response a list of data about users got from the server
     */
    public void responseMessengerUser(List response) {
        String persId;
        String nickName;
        Integer status;
        DbMessengerUser messengerUser;

        for (int i = 0; i < response.size(); i++) {
            Map<String, Object> contact = (Map<String, Object>) response.get(i);
            persId = (String) contact.get(Response.SendAddressBookResponse.NumbersParamsResponse.ID);
            nickName = (String) contact.get(Response.SendAddressBookResponse.NumbersParamsResponse.PERS_IDENT);
            if (nickName == null || nickName.isEmpty())
                nickName = persId;
            if (persId.equals(ownerPersId))
                continue;
            try {
                if (contact.get(Response.SendAddressBookResponse.NumbersParamsResponse.STATUS).getClass() == Double.class) {
                    status = ((Double) contact.get(Response.SendAddressBookResponse.NumbersParamsResponse.STATUS)).intValue();
                } else {
                    status = Integer.valueOf((String) contact.get(Response.SendAddressBookResponse.NumbersParamsResponse.STATUS));
                }
            } catch (Exception e) {
                UniversalHelper.logException(e);
                status = CustomValuesStorage.CATEGORY_NOTCONNECT;

            }

            messengerUser = messengerDb.get(persId);

            if (messengerUser == null) {
                messengerUser = new DbMessengerUser();
                messengerUser.setUserLogin(persId);
                messengerUser.setNickName(nickName);
                messengerUser.setDbStatus(status);
                messengerUser.setPersName(nickName);
            }
            messengerDb.put(messengerUser.getPersId(), messengerUser);
        }

    }

    /**
     * Returns a list of TSM users contained in DataAddressBook
     *
     * @return a map of TSM users and their usernames
     */
    public Map<String, DbMessengerUser> getMessengerDb() {
        return messengerDb;
    }

    /**
     * Saves or refreshes data about a TSM user
     *
     * @param user is a DbMessengerUser object to update or save
     */
    public synchronized void saveMessengerUser(DbMessengerUser user) {
        dbHelper.updateMessengerUser(user);
        if (messengerDb.get(user.getPersId()) == null) {
            messengerDb.put(user.getPersId(), user);
        }
    }

    /**
     * Refreshes data about all users in messengerDb map
     */
    public synchronized void saveMessengerUser() {
        for (Map.Entry<String, DbMessengerUser> rows : messengerDb.entrySet()) {
            dbHelper.updateMessengerUser(rows.getValue());
        }
    }

    /**
     * Saves or refreshes data about a group
     *
     * @param newGroup is a DbGroupChat object to save or refresh
     */
    public void saveGroupChat(DbGroupChat newGroup) {
        dbHelper.updateGroupChat(newGroup);
        groupChat.put(newGroup.getGroupId(), newGroup);
    }

    /**
     * Finds a group by it's id
     *
     * @param id id of a group needed to find
     */
    public DbGroupChat getGroupChat(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            Integer idGrp = null;
            DbGroupChat currentGroup;
            if (id.startsWith(CustomValuesStorage.GROUP_DESCRIPTIOR)) {
                idGrp = Integer.valueOf(id.substring(5));
            } else if (android.text.TextUtils.isDigitsOnly(id)) {
                idGrp = Integer.valueOf(id);
            }

            currentGroup = idGrp != null ? groupChat.get(idGrp) : null;
            return currentGroup;
        } catch (Exception e) {
            UniversalHelper.logException(e);
            return null;
        }

    }

    /**
     * Gets the whole list of groups
     *
     * @return an SparseArray object containing the whole list of group objects
     */
    public SparseArray<DbGroupChat> getGroupChatList() {
        return groupChat;
    }

    /**
     * Loads all data about users and groups from database into memory
     */
    public void loadLocalAddressBook() {
        messengerDb = dbHelper.selectMessengerUser();
        groupChat = dbHelper.selectGroupChat(messengerDb);
    }

    private void requestOutContact(String responseId) {

        DbMessengerUser messengerUser = messengerDb.get(responseId);

        if (messengerUser == null) {
            messengerUser = new DbMessengerUser();
            messengerUser.setUserLogin(responseId);
            messengerUser.setPersName(responseId);
            messengerUser.setDbStatus(CustomValuesStorage.CATEGORY_NOTCONNECT);
        }
        if ((messengerUser.getStatus() != CustomValuesStorage.CATEGORY_CONFIRM_IN) &&
                (messengerUser.getStatus() != CustomValuesStorage.CATEGORY_CONNECT)) {
            messengerUser.setDbStatus(CustomValuesStorage.CATEGORY_CONFIRM_OUT);
            saveMessengerUser(messengerUser);
        }

    }

    /**
     * Saves changes made for users that were requested to be friends
     *
     * @param notificationType a type of a friendship request
     * @param acceptors  the list of users who can receive a friendship request
     * @param uninstalled the list of users who can not receive a friendship request at the moment
     */
    public int receiveNotification(String notificationType,
                                   List acceptors,
                                   List<String> uninstalled) {

        int retVal = 0;
        if (!notificationType.equals(Request.SendNotificationRequest.NotificationType.NOTIFICATION) &&
                acceptors != null
                && !acceptors.isEmpty()) {
            retVal = 1;
            for (Object acceptorPersId : acceptors) {
                requestOutContact((String) acceptorPersId);
                    }
            }
            String sUninstalledAcceptors = "";
        if (uninstalled != null && !sUninstalledAcceptors.isEmpty()) {
            retVal += 2;
                for (String persId : uninstalled) {
                    final DbMessengerUser messengerUser = messengerDb.get(persId);
                    if (messengerUser != null) {
                        sUninstalledAcceptors += sUninstalledAcceptors.isEmpty() ? "" : ";" + persId;
                        messengerUser.setDbStatus(CustomValuesStorage.CATEGORY_NOTCONNECT);
                        saveMessengerUser(messengerUser);
                    }
                }
            }
        return retVal;
    }

    /**
     * Shows an incoming invitation from a contact via UI
     *
     * @param senderPersId the user login of the person sending invitation
     * @param senderNickname a visible name of a user, displayed in an address book
     * @return true if invitation is shown,
     * returns false if user is already connected with an app owner
     */
    public boolean showContactInvitation(final String senderPersId, final String senderNickname) {
        DbMessengerUser user;
        user = messengerDb.get(senderPersId);
        if (user == null) {
            user = new DbMessengerUser();
            user.setUserLogin(senderPersId);
            user.setPersName(senderNickname);
        }
        user.setNickName(senderNickname);
        if (user.getStatus() == CustomValuesStorage.CATEGORY_CONNECT) {
            MessagePostman.getInstance().sendAnswerNotificationMessage(
                    user.getPersId(),
                    Request.AnswerNotificationRequest.AnwerType.ACCEPT);
            return false;
        } else {
            user.setDbStatus(CustomValuesStorage.CATEGORY_CONFIRM_IN);
            saveMessengerUser(user);
            return true;
        }
    }

    /**
     * Returns a source list with contactPerson items for an adapter
     *
     * @param type a type of contact persons needed to show in adapter
     * @param showByCategory a flag showing if users must be filtered by category or not
     * @param filter a filter string which is used to make a filtered list for adapter
     * @return a list of users and groups requested by the given parameters
     */
    public List<ContactPerson> getAdapterList(int type, boolean showByCategory, String... filter) {
        List<ContactPerson> adapterList = new ArrayList<>();
        if ((type & CustomValuesStorage.CATEGORY_GROUP) != 0) {
            adapterList.addAll(prepareGroupContact());
        }
        if ((type & CustomValuesStorage.CATEGORY_MESSENGER) != 0) {
            adapterList.addAll(prepareRegisteredContact(type, showByCategory));
        }

        if (filter.length > 0) {
            adapterList = getFilteredList(adapterList, filter[0]);
        }

        return adapterList;
    }

    private List<ContactPerson> getFilteredList(List<ContactPerson> referenceList, String extMask) {
        String mask = extMask.toLowerCase();
        List<ContactPerson> resultList = new ArrayList<>();
        if (mask.length() == 0) {
            resultList.addAll(referenceList);
        } else {
            for (ContactPerson item : referenceList) {
                if ((item.getDisplayName().toLowerCase().contains(mask)) ||
                        (item.getMessengerId().contains(mask))) {
                    resultList.add(item);
                }
            }
        }
        return resultList;
    }

    private List<ContactPerson> prepareRegisteredContact(final int mode, final boolean sortByCategory) {
        fillList(mode);
        Collections.sort(registeredList, new Comparator<ContactPerson>() {
            @Override
            public int compare(ContactPerson o1, ContactPerson o2) {
                int ret;
                ret = o1.getStatusOnline().compareTo(o2.getStatusOnline());
                if (ret == 0) {
                    if (sortByCategory) {
                        if (mode == CustomValuesStorage.CATEGORY_CONFIRM) {
                            ret = o1.getStat().compareTo(o2.getStat());
                        } else {
                            ret = Integer.valueOf(o1.getStat() == CustomValuesStorage.CATEGORY_CONNECT ? 0 : 1)
                                    .compareTo(o2.getStat() == CustomValuesStorage.CATEGORY_CONNECT ? 0 : 1);
                        }
                    } else ret = 0;
                }
                if (ret == 0)
                    ret = o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
                return ret;
            }
        });

        return registeredList;
    }

    private void fillList(final int mode) {
        registeredList.clear();
        for (Map.Entry<String, DbMessengerUser> rows : messengerDb.entrySet()) {
            DbMessengerUser pers = rows.getValue();
            int contactMode = pers.getStatus();
            if (((contactMode & mode) != 0) || pers.getStatus().equals(0)) {
                ContactPerson row = new ContactPerson();
                row.setMessengerUser(pers);
                registeredList.add(row);
            }
        }
    }

    private List<ContactPerson> prepareGroupContact() {
        List<ContactPerson> groupsList = new ArrayList<>();
        groupsList.clear();
        for (int i = 0; i < groupChat.size(); i++) {
            DbGroupChat group = groupChat.valueAt(i);
            if (group.getType().equals(DbGroupChat.GROUPTYPE_SAVE)) {
                ContactPerson row = new ContactPerson();
                row.setGroupChat(group);
                groupsList.add(row);
            }
        }
        Collections.sort(groupsList, new Comparator<ContactPerson>() {
            @Override
            public int compare(ContactPerson o1, ContactPerson o2) {
                return o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
            }
        });
        return groupsList;
    }

    /**
     * Gets the concrete ContactPerson item
     *
     * @param unitId id of a group or TSM user needed to get
     * @return a ContactPerson item if there was a group or user having the given unitId
     * returns an empty instance of ContactPerson if no items matching the given id were found
     */
    public ContactPerson getContactPerson(String unitId) {
        ContactPerson contactPerson = new ContactPerson();

        DbMessengerUser user = messengerDb.get(unitId);
        if (user != null) {
            contactPerson.setMessengerUser(user);
        } else {
            contactPerson.setGroupChat(getGroupChat(unitId));
        }

        return contactPerson;
    }

    /**
     * Removes a group chat from ContactPerson lists
     *
     * @param unitId the String object with an id of a group to delete
     */
    public void deleteGroupChat(String unitId) {
        int indexToDelete = 0;
        int groupId = 0;
        for (int i = 0; i < groupChat.size(); i++) {
            if (groupChat.get(groupChat.keyAt(i)).getGroupIdString().equals(unitId)) {
                indexToDelete = groupChat.keyAt(i);
                groupId = groupChat.get(groupChat.keyAt(i)).getGroupId();
                break;
            }
        }
        groupChat.delete(indexToDelete);
        dbHelper.deleteGroupChat(groupId);
    }
}
