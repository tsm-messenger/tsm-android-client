package com.tsm_messenger.activities.main.contacts;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.TsmTemplateActivity;
import com.tsm_messenger.activities.control.TsmMessageDialog;
import com.tsm_messenger.activities.control.TsmNotification;
import com.tsm_messenger.activities.service.BroadcastMessages;
import com.tsm_messenger.connection.MessagePostman;
import com.tsm_messenger.data.storage.ChatUnit;
import com.tsm_messenger.data.storage.ContactPerson;
import com.tsm_messenger.data.storage.DataAddressBook;
import com.tsm_messenger.data.storage.DataChatHistory;
import com.tsm_messenger.data.storage.DbGroupChat;
import com.tsm_messenger.data.storage.DbMessengerUser;
import com.tsm_messenger.protocol.transaction.Operation;
import com.tsm_messenger.protocol.transaction.Request;
import com.tsm_messenger.protocol.transaction.Response;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.SharedPreferencesAccessor;
import com.tsm_messenger.service.UniversalHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
 */

public class InfoActivity extends TsmTemplateActivity {

    public static final String INFO_MODE_EXTRA = "info_mode";
    public static final String EXTRA_CONTACT_MODE = "contact_mode";
    public static final String EXTRA_CHAT_MODE = "chat_mode";
    private static final String CHAT_PARTICIPANT_VIEW_EXTRA = "chat_participant_view";
    private String messengerId;
    private ContactPerson pers;
    private int contactType;
    private DataAddressBook dbAddress;
    private DataChatHistory dbChatHistory;
    private ActivityGlobalManager activityManager;
    private boolean hasNoChat;
    private boolean chatMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        findViewById(R.id.btnBack_toolbar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        Intent intent = getIntent();
        messengerId = intent.getStringExtra(CustomValuesStorage.IntentExtras.INTENT_MESSENGER_ID);

        String mode = intent.getStringExtra(INFO_MODE_EXTRA);
        chatMode = EXTRA_CHAT_MODE.equals(mode);
        if (chatMode) {
            setTitle(R.string.title_activity_chat_info);
        } else {
            setTitle(R.string.title_activity_contact_info);
        }

        activityManager = (ActivityGlobalManager) getApplication();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        super.onServiceConnected(name, service);
        dbAddress = activityManager.getDbContact();
        dbChatHistory = activityManager.getDbChatHistory();
        ChatUnit chat = dbChatHistory.getChatByPersId(messengerId);
        hasNoChat = chat == null || chat.isOutcast();
        pers = dbAddress.getContactPerson(messengerId);
        contactType = pers.getContactType();
        showInfo();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean participantsLimitFull;
        if (pers == null)
            return true;
        if (contactType == ContactPerson.MESSENGER) {
            int category = pers.getStat();
            if (category == CustomValuesStorage.CATEGORY_CONNECT) {
                getMenuInflater().inflate(R.menu.contact_connected, menu);
            } else {
                getMenuInflater().inflate(R.menu.contact_notconnect, menu);
                if (category == CustomValuesStorage.CATEGORY_CONFIRM_IN) {
                    menu.findItem(R.id.action_request_contact).setVisible(false);
                } else {
                    menu.findItem(R.id.action_accept).setVisible(false);
                    menu.findItem(R.id.action_decline).setVisible(false);
                    boolean showRequestPossible = category != CustomValuesStorage.CATEGORY_CONFIRM_OUT &&
                            category != CustomValuesStorage.CATEGORY_DELETE;
                    menu.findItem(R.id.action_request_contact)
                            .setVisible(showRequestPossible);
                }
            }
        } else {
            DbGroupChat group = pers.getGroupChat();
            String groupType = group.getType();
            participantsLimitFull = group.getMembers().size() > DbGroupChat.MAX_GROUP_COUNT;

            getMenuInflater().inflate(R.menu.group_menu, menu);
            if (groupType.equals(DbGroupChat.GROUPTYPE_SAVE)) {
                menu.findItem(R.id.action_add_to_contacts).setVisible(false);
            } else {
                menu.findItem(R.id.remove_from_contacts).setVisible(false);
            }
            menu.findItem(R.id.action_add_participants)
                    .setVisible(!(participantsLimitFull) && !hasNoChat);
            menu.findItem(R.id.action_leave_chat).setVisible(!hasNoChat);
            menu.findItem(R.id.action_delete_chat).setVisible(dbChatHistory.getChatByPersId(messengerId) != null);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_rename:
                renameContact();
                break;
            case R.id.action_request_contact:
                requestContact(pers.getMessengerUser());
                break;
            case R.id.action_accept:
                acceptRequest();
                break;
            case R.id.action_decline:
                rejectRequest();
                break;
            case R.id.action_add_participants:
                addParticipants();
                break;
            case R.id.action_add_to_contacts:
                addToContacts();
                break;
            case R.id.remove_from_contacts:
                removeFromContacts();
                break;
            default:
                performSelfDeleteActions(id);
        }
        return super.onOptionsItemSelected(item);
    }

    private void performSelfDeleteActions(int id) {
        switch (id) {
            case R.id.action_leave_chat:
                askForChatLeaving();
                break;
            case R.id.action_delete_chat:
                deleteGroupChat();
                break;
            case R.id.action_remove:
                askForRemoval();
                break;
            default: //do nothing
        }
    }

    private void askForChatLeaving() {
        final TsmMessageDialog chtLeaveDialog = new TsmMessageDialog(this);
        chtLeaveDialog.setTitle(R.string.btn_leave_chat);
        chtLeaveDialog.setMessage(R.string.info_leave_chat);
        chtLeaveDialog.setPositiveButton(R.string.btn_yes, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chtLeaveDialog.dismiss();
                leaveChat();
            }
        });
        chtLeaveDialog.setNegativeButton(R.string.btn_no, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chtLeaveDialog.dismiss();
            }
        });
        chtLeaveDialog.show();
    }

    private void deleteGroupChat() {
        DbGroupChat grpToLeave = pers.getGroupChat();
        final ChatUnit chatToLeave = dbChatHistory.getChatByPersId(grpToLeave.getGroupIdString());

        if (!((ActivityGlobalManager) getApplication()).isOnline() && !chatToLeave.isOutcast()) {
            Toast.makeText(InfoActivity.this, R.string.error_leave_chat_offline, Toast.LENGTH_LONG).show();
            return;
        }

        final TsmMessageDialog deleteChatDialog = new TsmMessageDialog(this);
        deleteChatDialog.setTitle(R.string.title_delete_chat);
        if (chatToLeave.isOutcast()) {
            deleteChatDialog.setMessage(R.string.info_delete_chat);
        } else {
            deleteChatDialog.setMessage(R.string.info_delete_chat_leave);
        }
        deleteChatDialog.setNegativeButton(R.string.btn_no, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteChatDialog.dismiss();
            }
        });
        deleteChatDialog.setPositiveButton(R.string.btn_yes, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteChatDialog.dismiss();
                if (!chatToLeave.isOutcast()) {
                    leaveChat();
                }
                dbAddress.deleteGroupChat(chatToLeave.getUnitId());
                dbChatHistory.dropChat(chatToLeave.getChatId());
                finish();
            }
        });
        deleteChatDialog.show();
    }

    private void askForRemoval() {
        final TsmMessageDialog removeDialog = new TsmMessageDialog(this);
        removeDialog.setTitle(R.string.title_remove_from_contacts);
        removeDialog.setMessage(R.string.info_remove_from_contacts);
        removeDialog.setPositiveButton(R.string.btn_yes, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                destroyRelations();
                DbMessengerUser user = pers.getMessengerUser();
                user.setDbStatus(CustomValuesStorage.CATEGORY_UNKNOWN);
                dbAddress.saveMessengerUser(user);
                ChatUnit chatByPersId = dbChatHistory.getChatByPersId(user.getPersId());
                if (chatByPersId != null) {
                    dbChatHistory.dropChat(chatByPersId.getChatId());
                }
                removeDialog.dismiss();
                InfoActivity.this.finish();
            }
        });
        removeDialog.setNegativeButton(R.string.btn_no, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeDialog.dismiss();
            }
        });
        removeDialog.show();
    }

    private void destroyRelations() {
        if (contactType == ContactPerson.MESSENGER) {
            switch (pers.getMessengerUser().getDbStatus()) {
                case CustomValuesStorage.CATEGORY_CONNECT:
                    MessagePostman.getInstance().sendDeleteRelationRequest(messengerId);
                    break;
                case CustomValuesStorage.CATEGORY_CONFIRM_IN:
                    rejectRequest();
                    break;
                case CustomValuesStorage.CATEGORY_CONFIRM_OUT:
                    cancelRequest();
                    break;
                default:
                    //do nothing
            }
        }
    }

    private void cancelRequest() {
        DbMessengerUser messengerUser = pers.getMessengerUser();
        MessagePostman.getInstance().sendDeleteRelationRequest(messengerId);
        messengerUser.setDbStatus(CustomValuesStorage.CATEGORY_NOTCONNECT);
        dbAddress.saveMessengerUser(messengerUser);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        showInfo();
    }

    private void acceptRequest() {
        changeContactStatus();
        String login = pers.getMessengerId();
        DbMessengerUser user = pers.getMessengerUser();
        user.setDbStatus(CustomValuesStorage.CATEGORY_CONNECT);
        dbAddress.saveMessengerUser(user);

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(TsmNotification.NEWREQUEST_ID);

        MessagePostman.getInstance().sendAnswerNotificationMessage(
                login,
                Request.AnswerNotificationRequest.AnwerType.ACCEPT);
    }

    private void rejectRequest() {
        changeContactStatus();
        String login = pers.getMessengerId();
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(TsmNotification.NEWREQUEST_ID);

        MessagePostman.getInstance().sendAnswerNotificationMessage(
                login,
                Request.AnswerNotificationRequest.AnwerType.DECLINE);
        DbMessengerUser messengerUser = pers.getMessengerUser();
        messengerUser.setDbStatus(CustomValuesStorage.CATEGORY_NOTCONNECT);
        dbAddress.saveMessengerUser(messengerUser);
    }

    @Override
    protected void onSubscribeBroadcastMessage() {
        super.onSubscribeBroadcastMessage();
        IntentFilter intentFilter = new IntentFilter(BroadcastMessages.WS_CONTACT_STATUS);
        intentFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.INVITE));
        intentFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.LEAVE));
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected boolean onReceiveBroadcast(Intent intent) {
        boolean runParent = true;

        Bundle bundle = intent.getBundleExtra(BroadcastMessages.WS_PARAM);
        String action = intent.getAction();
        String persNickName = "";
        if (contactType == ContactPerson.MESSENGER) {
            persNickName = pers.getMessengerUser().getPersLogin();
        }

        if ((action.equals(BroadcastMessages.WS_CONTACT_STATUS) ||
                action.equals(BroadcastMessages.getBroadcastOperation(Operation.SEND_NOTIFICATION)) ||
                action.equals(BroadcastMessages.getBroadcastOperation(Operation.ANSWER_NOTIFICATION))
        ) && (bundle != null)) {
            String sender = bundle.getString(Response.AnswerNotificationResponse.SENDER);
            if (persNickName.equals(sender) || messengerId.equals(sender)) {
                pers = dbAddress.getContactPerson(messengerId);
                changeContactStatus();
                return true;
            } else if (getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE)
                    .getString(SharedPreferencesAccessor.USER_ID, "").equals(sender)) {
                ArrayList<String> incorrect = bundle.getStringArrayList(
                        Response.SendNotificationResponse.ACCEPTORS_NOT_SEND);
                if (incorrect != null && incorrect.contains(messengerId)) {
                    TsmMessageDialog notSentDialog = new TsmMessageDialog(this);
                    notSentDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            changeContactStatus();
                        }
                    });
                    notSentDialog.show(
                            getString(R.string.title_error),
                            String.format(getString(R.string.info_request_not_sent_concrete), pers.getDisplayName()));
                    runParent = false;
                }
            }
            if (contactType == ContactPerson.GROUP) {
                ListView contactDetails = (ListView) findViewById(R.id.lvContactNumbers_details);
                if (contactDetails != null) {
                    BaseAdapter adapter = (BaseAdapter) contactDetails.getAdapter();
                    adapter.notifyDataSetChanged();
                }
            }
        }
        if (contactType == ContactPerson.GROUP &&
                (action.equals(BroadcastMessages.getBroadcastOperation(Operation.INVITE)) ||
                        action.equals(BroadcastMessages.getBroadcastOperation(Operation.LEAVE)))) {
            showInfo();
        }

        return !runParent || super.onReceiveBroadcast(intent);
    }

    private void leaveChat() {
        hasNoChat = true;
        changeContactStatus();
        DbGroupChat grpToLeave = pers.getGroupChat();
        if (!((ActivityGlobalManager) getApplication()).isOnline()) {
            Toast.makeText(this, R.string.error_leave_chat_offline, Toast.LENGTH_LONG).show();
            return;
        }
        ChatUnit chatToLeave = dbChatHistory.getChatByPersId(grpToLeave.getGroupIdString());
        Integer chatId = chatToLeave.getChatId();

        MessagePostman.getInstance().sendLeaveChatRequest(chatId.toString());
    }

    private void addParticipants() {
        showAddParticipantsDialog(pers);
    }

    private void showAddParticipantsDialog(final ContactPerson pers) {
        ChatUnit currentChat = dbChatHistory.getChatByPersId(pers.getMessengerId());
        List<HashMap<String, String>> detailAdapterList = pers.getNotInChatPersons(dbAddress);

        int allowedParticipantsCount = getAllowedParticipantsCount(pers, currentChat);

        if (allowedParticipantsCount == 0 || detailAdapterList.isEmpty()) {
            Toast.makeText(InfoActivity.this, R.string.error_add_no_participants, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        final NotInChatFriendsAdapter stdCommandsAdapter = new NotInChatFriendsAdapter(detailAdapterList);

        stdCommandsAdapter.setAllowCount(allowedParticipantsCount);

        final TsmMessageDialog participantsDialog = new TsmMessageDialog(this);

        participantsDialog.setTitle(pers.getDisplayName());
        participantsDialog.setList(stdCommandsAdapter, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                stdCommandsAdapter.onCheckValue(view, i);
            }
        });

        participantsDialog.setNeutralButton(R.string.btn_ok, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ChatUnit currentChat = dbChatHistory.getChatByPersId(pers.getMessengerId());

                if (currentChat == null) {
                    //we cannot find our chat, let's search it manually
                    currentChat = searchForChatManually(pers);
                }
                if (currentChat == null) {
                    //it's a plain group without a chat. Only participants adding is needed
                    addParticipantsToChatlessGroup(stdCommandsAdapter, pers, participantsDialog);
                    return;
                }
                if (!activityManager.loginIsCorrect(pers.getMessengerId())) {
                    //we have found a group chat
                    addParticipantsToGroupChat(currentChat, stdCommandsAdapter, participantsDialog);
                }
            }
        });
        participantsDialog.show();
    }

    private int getAllowedParticipantsCount(ContactPerson pers, ChatUnit currentChat) {
        int allowToAdd;
        if (currentChat != null) {
            allowToAdd = DbGroupChat.MAX_GROUP_COUNT - currentChat.getParticipantsList().size();
        } else {
            allowToAdd = DbGroupChat.MAX_GROUP_COUNT - pers.getGroupChat().getMembers().size();
        }
        return allowToAdd;
    }

    private void addParticipantsToGroupChat(ChatUnit currentChat, NotInChatFriendsAdapter stdCommandsAdapter, TsmMessageDialog participantsDialog) {
        String chatId = currentChat.getChatId().toString();

        try {
            ArrayList<String> participantsList = (ArrayList<String>) stdCommandsAdapter.getParticipantsToInviteList();
            MessagePostman.getInstance().sendChatInvitationRequest(chatId, participantsList);
        } catch (NullPointerException e) {
            UniversalHelper.logException(e);
        } finally {
            participantsDialog.dismiss();
        }
    }

    private void addParticipantsToChatlessGroup(NotInChatFriendsAdapter stdCommandsAdapter, ContactPerson pers, TsmMessageDialog participantsDialog) {
        ArrayList<String> participantsList = (ArrayList<String>) stdCommandsAdapter.getParticipantsToInviteList();
        DbMessengerUser userToAdd;
        DbGroupChat groupChat = pers.getGroupChat();
        for (String login : participantsList) {
            userToAdd = dbAddress.getMessengerDb().get(login);
            groupChat.addMember(login, userToAdd);
        }
        dbAddress.saveGroupChat(groupChat);
        invalidateOptionsMenu();
        showInfo();
        participantsDialog.dismiss();
    }

    private ChatUnit searchForChatManually(ContactPerson pers) {
        ChatUnit currentChat = null;
        for (ChatUnit chat : dbChatHistory.getAdapterList()) {
            if (chat.getUnitId().equals(pers.getGroupChat().getGroupIdString())) {
                currentChat = chat;
                break;
            }
        }
        return currentChat;
    }

    private void requestContact(DbMessengerUser pers) {
        ArrayList<String> notificationAcceptors = new ArrayList<>(1);

        notificationAcceptors.add(pers.getPersId());

        MessagePostman.getInstance().sendNotificationRequest(notificationAcceptors);

        //change contact status immediately
        pers.setStatus(CustomValuesStorage.CATEGORY_CONFIRM_OUT);
        //quit activity to refresh contact status
        changeContactStatus();
    }

    private void removeFromContacts() {
        DbGroupChat grpChat = pers.getGroupChat();
        grpChat.setType(DbGroupChat.GROUPTYPE_TEMP);
        dbAddress.saveGroupChat(grpChat);
        changeContactStatus();
    }

    private void addToContacts() {
        DbGroupChat grpChat = pers.getGroupChat();
        grpChat.setType(DbGroupChat.GROUPTYPE_SAVE);
        dbAddress.saveGroupChat(grpChat);
        changeContactStatus();
    }

    private void renameContact() {
        final TextView lblName = (TextView) findViewById(R.id.lblContactName_details);
        final TextView lblAvatar = (TextView) findViewById(R.id.imgAvatar_details);
        final TsmMessageDialog changeNameMessage = new TsmMessageDialog(this);

        changeNameMessage.setTitle(R.string.title_rename);
        changeNameMessage.setTextBox(pers.getDisplayName());
        final DbMessengerUser userToRename = pers.getMessengerUser();
        final DbGroupChat groupChatToRename = pers.getGroupChat();
        changeNameMessage.setPositiveButton(R.string.btn_ok, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = changeNameMessage.getTextFromTextBox().replace("\n", " ").trim();
                if (userToRename != null && !newName.equals(userToRename.getPersName())) {
                    userToRename.setPersName(newName);
                    dbAddress.saveMessengerUser(userToRename);
                    lblName.setText(userToRename.getDisplayName());
                    lblAvatar.setText(userToRename.getDisplayName().substring(0, 1));
                } else if (groupChatToRename != null
                        && !newName.equals(groupChatToRename.getGroupName())) {
                    groupChatToRename.setGroupName(newName);
                    dbAddress.saveGroupChat(groupChatToRename);
                    lblName.setText(groupChatToRename.getGroupName());
                }
                changeNameMessage.dismiss();
            }
        });
        changeNameMessage.setNegativeButton(R.string.btn_cancel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeNameMessage.dismiss();
            }
        });
        changeNameMessage.show();
    }

    private void showInfo() {
        TextView infoName = (TextView) findViewById(R.id.lblContactName_details);
        TextView infoAvatar = (TextView) findViewById(R.id.imgAvatar_details);
        ListView infoList = (ListView) findViewById(R.id.lvContactNumbers_details);

        String displayName = pers.getDisplayName();
        infoName.setText(displayName);
        if (contactType == ContactPerson.MESSENGER) {
            infoAvatar.setText(String.valueOf(displayName.charAt(0)));
            findViewById(R.id.lbl_chat_participants).setVisibility(View.GONE);
        } else {
            findViewById(R.id.lbl_chat_participants).setVisibility(View.VISIBLE);
            ChatUnit currentChat = ActivityGlobalManager.getInstance().getDbChatHistory().getChatByPersId(messengerId);
            infoAvatar.setBackgroundResource(UniversalHelper.getGroupBackgroundId(currentChat));
        }

        List<DbMessengerUser> detailAdapterList;

        detailAdapterList = pers.getContactDetail(this);
        BaseAdapter infoAdapter;
        infoAdapter = new GroupParticipantsAdapter(detailAdapterList);
        infoList.setAdapter(infoAdapter);
        changeContactStatus();
    }

    private void changeContactStatus() {
        invalidateOptionsMenu();
        TextView tvContactStatus = (TextView) findViewById(R.id.lblContactStatus);
        ImageButton btnDetails = (ImageButton) findViewById(R.id.btn_securetype_info);
        tvContactStatus.setVisibility(View.GONE);
        btnDetails.setVisibility(View.GONE);
        if (contactType == ContactPerson.MESSENGER) {
            int status = pers.getStat();
            if (!(status == CustomValuesStorage.CATEGORY_CONNECT && pers.getStatusOnline() != CustomValuesStorage.UserStatus.UNREACHABLE)) {
                if (!chatMode) {
                    tvContactStatus.setVisibility(View.VISIBLE);
                }
                btnDetails.setVisibility(View.GONE);
                switch (status) {
                    case CustomValuesStorage.CATEGORY_NOTCONNECT:
                        tvContactStatus.setText(R.string.info_contact_not_connected);
                        break;
                    case CustomValuesStorage.CATEGORY_CONFIRM_OUT:
                        tvContactStatus.setText(R.string.info_contact_requested);
                        break;
                    case CustomValuesStorage.CATEGORY_CONFIRM_IN:
                        tvContactStatus.setText(R.string.info_contact_pending);
                        break;
                    case CustomValuesStorage.CATEGORY_CONNECT:
                        tvContactStatus.setText(R.string.info_contact_unreachable);
                        break;
                    case CustomValuesStorage.CATEGORY_DELETE:
                        tvContactStatus.setText(R.string.info_contact_self_deleted);
                        break;
                    default://do nothing
                }
            } else if (getIntent().getBooleanExtra(CHAT_PARTICIPANT_VIEW_EXTRA, false)) {
                tvContactStatus.setVisibility(View.GONE);
                btnDetails.setVisibility(View.GONE);
            } else {
                if (chatMode) {
                    tvContactStatus.setVisibility(View.VISIBLE);
                    btnDetails.setVisibility(View.VISIBLE);
                    tvContactStatus.setText(R.string.lbl_securetype_all_history_keep);
                }
            }
        } else {
            if (chatMode) {
                tvContactStatus.setVisibility(View.VISIBLE);
                btnDetails.setVisibility(View.VISIBLE);
            }
            try {
                String groupId = pers.getMessengerId();
                Integer secureType = dbChatHistory.getChatByPersId(groupId).getSecureType();
                UniversalHelper.setLblSecureType(this, tvContactStatus, secureType);
            } catch (NullPointerException npe) {
                UniversalHelper.logException(npe);
            }
        }
    }

    /**
     * shows the details for the secure type of a chat for a current viewed group or person
     *
     * @param view a button that called this method with its OnClick event
     */
    public void btnSecuretypeDetails_onClick(View view) {
        ChatUnit currentChat = ActivityGlobalManager.getInstance().getDbChatHistory().getChatByPersId(messengerId);
        int stringId = UniversalHelper.getChatSecureTypeDetails(currentChat);
        String message;
        if (stringId == R.string.lbl_securetype_keep_until_lifetime_details) {
            message = String.format(getString(stringId),
                    UniversalHelper.getLifeTimeGenitive(this, currentChat.getSecureType()));
        } else {
            message = getString(stringId);
        }
        new TsmMessageDialog(this).show(getString(R.string.title_info), message);
    }

    private class NotInChatFriendsAdapter extends ArrayAdapter<HashMap<String, String>> {

        private final ArrayList<String> participantsToInviteList;
        private int allowCount;

        public NotInChatFriendsAdapter(List<HashMap<String, String>> detailAdapterList) {
            super(InfoActivity.this, R.layout.group_participant_item, detailAdapterList);
            participantsToInviteList = new ArrayList<>();
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            final DataHolder holder;
            HashMap<String, String> participant = getItem(position);
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.not_in_chat_friend_item, parent, false);
                holder = new DataHolder(v);
                v.setTag(holder);
            } else {
                holder = (DataHolder) v.getTag();
            }
            if (participant != null) {
                final String persIdent, name, checked, nickname;
                persIdent = participant.get(ContactPerson.TYPE);
                nickname = participant.get(ContactPerson.NICKNAME);
                name = participant.get(ContactPerson.CONTACT);
                checked = participant.get(ContactPerson.CHECK);

                holder.tvPersIdent.setText(nickname);
                holder.tvName.setText(name);
                if ("0".equals(checked)) {
                    holder.chbAddToChat.setChecked(false);
                } else {
                    holder.chbAddToChat.setChecked(true);
                }

                holder.chbAddToChat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                        if (b) {
                            participantsToInviteList.add(persIdent);
                        } else {
                            participantsToInviteList.remove(persIdent);
                        }
                    }
                });
            }
            return v;
        }

        public List<String> getParticipantsToInviteList() {
            return participantsToInviteList;
        }

        public void onCheckValue(View view, int position) {

            DataHolder dh;
            dh = (DataHolder) view.getTag();
            HashMap<String, String> participant = getItem(position);

            if (!dh.chbAddToChat.isChecked()) {
                if (participantsToInviteList.size() < allowCount) {
                    dh.chbAddToChat.setChecked(true);
                    participant.put("chacked", "1");
                } else {
                    UniversalHelper.showGroupOverflowError(InfoActivity.this);
                }
            } else {
                dh.chbAddToChat.setChecked(false);
                participant.put("chacked", "0");

            }
        }

        public void setAllowCount(int allowCount) {
            this.allowCount = allowCount;
        }

        class DataHolder {
            public final TextView tvName;
            public final TextView tvPersIdent;
            public final CheckBox chbAddToChat;

            DataHolder(View v) {
                tvName = (TextView) v.findViewById(R.id.contact_detail_item_head);
                tvPersIdent = (TextView) v.findViewById(R.id.contact_detail_item_row);
                chbAddToChat = (CheckBox) v.findViewById(R.id.chbAddToChat);
            }
        }

    }

    private class GroupParticipantsAdapter extends ArrayAdapter<DbMessengerUser> {

        public GroupParticipantsAdapter(List<DbMessengerUser> detailAdapterList) {
            super(InfoActivity.this, R.layout.group_participant_item, detailAdapterList);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            final DataHolder holder;
            DbMessengerUser participant = getItem(position);
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.group_participant_item, parent, false);
                holder = new DataHolder(v);
                v.setTag(holder);
            } else {
                holder = (DataHolder) v.getTag();
            }
            if (participant != null) {
                String userId;
                String login;
                String name;
                userId = participant.getPersId();
                name = participant.getPersName();
                login = participant.getPersLogin();
                if (pers.getContactType() == ContactPerson.MESSENGER) {
                    holder.tvPersIdent.setText(R.string.lbl_login);
                    if (login == null) {
                        holder.tvName.setText(userId);
                    } else {
                        holder.tvName.setText(login);
                    }
                    holder.tvOnlineStatus.setVisibility(View.GONE);
                    holder.btnEditName.setVisibility(View.GONE);
                } else {
                    if (login == null) {
                        holder.tvPersIdent.setText(userId);
                    } else {
                        holder.tvPersIdent.setText(login);
                    }
                    holder.tvName.setText(name);
                    DbMessengerUser participantUser = dbAddress.getMessengerDb().get(userId);

                    if (participantUser != null) {
                        if (CustomValuesStorage.CATEGORY_UNKNOWN != participantUser.getDbStatus()) {
                            holder.changeBtNameMode(DataHolder.MODE_EDIT, participantUser);
                        } else {
                            holder.changeBtNameMode(DataHolder.MODE_CREATE, participantUser);
                        }

                    } else {
                        participantUser = new DbMessengerUser();
                        participantUser.setUserLogin(userId);
                        participantUser.setNickName(userId);
                        participantUser.setPersName(name);
                        holder.changeBtNameMode(DataHolder.MODE_CREATE, participantUser);
                    }
                    holder.tvOnlineStatus.setVisibility(View.VISIBLE);
                    UniversalHelper.refreshOnlineStatus(holder.tvOnlineStatus, participantUser);
                }
            }
            return v;
        }

        class DataHolder {
            public static final int MODE_EDIT = 1;
            public static final int MODE_CREATE = 0;
            public final TextView tvName;
            public final TextView tvPersIdent;
            public final ImageButton btnEditName;
            public final TextView tvOnlineStatus;

            DataHolder(View v) {
                tvPersIdent = (TextView) v.findViewById(R.id.contact_detail_item_head);
                tvName = (TextView) v.findViewById(R.id.contact_detail_item_row);
                btnEditName = (ImageButton) v.findViewById(R.id.contactInfo_btn_editName);
                tvOnlineStatus = (TextView) v.findViewById(R.id.contactInfo_tv_onlineStatus);
            }

            public void changeBtNameMode(final int mode, final DbMessengerUser userToRename) {
                int icon;
                if (mode == MODE_CREATE) {
                    icon = R.drawable.ic_add_circle_outline_black_24dp;
                } else {
                    icon = R.drawable.ic_info_outline_black_24dp;
                }
                if (pers.getContactType() == ContactPerson.MESSENGER) {
                    btnEditName.setVisibility(View.INVISIBLE);
                } else {
                    btnEditName.setImageResource(icon);
                    btnEditName.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (mode == MODE_CREATE) {
                                userToRename.setDbStatus(CustomValuesStorage.CATEGORY_NOTCONNECT);
                                dbAddress.saveMessengerUser(userToRename);
                                changeBtNameMode(MODE_EDIT, userToRename);
                                requestContact(userToRename);
                                Intent wsResult = new Intent(BroadcastMessages.WS_CONTACT_STATUS);
                                LocalBroadcastManager.getInstance(InfoActivity.this).sendBroadcast(wsResult);
                            } else {
                                Intent newIntent = new Intent(InfoActivity.this, InfoActivity.class);
                                String persId = userToRename.getPersId();
                                newIntent.putExtra(CustomValuesStorage.IntentExtras.INTENT_MESSENGER_ID, persId);
                                newIntent.putExtra(CHAT_PARTICIPANT_VIEW_EXTRA, true);
                                startActivityForResult(newIntent, 0);
                            }
                        }
                    });
                }
            }
        }
    }
}
