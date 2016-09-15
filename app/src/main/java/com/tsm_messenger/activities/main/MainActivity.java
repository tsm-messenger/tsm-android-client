package com.tsm_messenger.activities.main;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.TsmTemplateActivity;
import com.tsm_messenger.activities.control.TsmMessageDialog;
import com.tsm_messenger.activities.control.TsmNotification;
import com.tsm_messenger.activities.main.chat.ChatFragment;
import com.tsm_messenger.activities.main.chat.ChatHistoryActivity;
import com.tsm_messenger.activities.main.contacts.ChooseContactsActivity;
import com.tsm_messenger.activities.main.contacts.ContactsFragment;
import com.tsm_messenger.activities.main.contacts.InfoActivity;
import com.tsm_messenger.activities.main.contacts.SearchAddUserActivity;
import com.tsm_messenger.activities.options.AppCompatPreferenceActivity;
import com.tsm_messenger.activities.options.TsmInfoActivity;
import com.tsm_messenger.activities.options.TsmPreferencesActivity;
import com.tsm_messenger.activities.service.BroadcastMessages;
import com.tsm_messenger.activities.service.OpenFileActivity;
import com.tsm_messenger.activities.service.ServiceParameters;
import com.tsm_messenger.connection.MessagePostman;
import com.tsm_messenger.connection.SocketConnector;
import com.tsm_messenger.crypto.EdDsaSigner;
import com.tsm_messenger.crypto.IKeyExportImporter;
import com.tsm_messenger.crypto.UserKeyExportImportManager;
import com.tsm_messenger.data.storage.ChatUnit;
import com.tsm_messenger.data.storage.ContactPerson;
import com.tsm_messenger.data.storage.DataAddressBook;
import com.tsm_messenger.data.storage.DataChatHistory;
import com.tsm_messenger.data.storage.DbGroupChat;
import com.tsm_messenger.data.storage.DbMessengerUser;
import com.tsm_messenger.protocol.transaction.Operation;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.protocol.transaction.Request;
import com.tsm_messenger.protocol.transaction.Response;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.BeepManager;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.SharedPreferencesAccessor;
import com.tsm_messenger.service.UniversalHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 */

public class MainActivity extends TsmTemplateActivity
        implements
        Handler.Callback,
        IKeyExportImporter {

    public static final String EXTRA_EXPORT_FOLDER = "export_folder";
    public static final String EXTRA_PERFORM_EXPORT = "do_not_export";
    private static final String CURRENT_PAGE = "CUR_PAGE";
    private final HashMap<String, TsmMessageDialog> shownDialogs = new HashMap<>();
    private ContactsFragment contactsFragment = new ContactsFragment();
    private ChatFragment chatFragment = new ChatFragment();
    private ActivityGlobalManager activityManager;
    private SharedPreferences settings;
    private ViewPager pager;
    private MyPagerAdapter pagerAdapter;
    private SocketConnector connectService;
    private String currentCheckedChatGroup;
    private ChatUnit currentViewedChat;
    private String startmode;
    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            connectService = ((SocketConnector.LocalBinder) service).getService();

            if ("first".equals(startmode)) {
                connectService.setFirstStart();
                startmode = "";
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            connectService = null;
        }
    };
    private int currentPage;
    private InputMethodManager inputMethodManager;
    private Menu currentOptionsMenu;
    private TextView tvUnreadChatsCount;
    private TextView tvUnreadRequestsCount;
    private boolean regMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            startmode = "first";
            currentPage = -1;
            regMode = ServiceParameters.REGISTRATION.equals(getIntent().getStringExtra(ServiceParameters.MODE));

        } else {
            startmode = "turn";
            currentPage = savedInstanceState.getInt(CURRENT_PAGE);
            regMode = false;
        }

        initializeUI();

        activityManager = (ActivityGlobalManager) getApplication();
        activityManager.readLogin();


        inputMethodManager =
                (InputMethodManager) getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);

        settings = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE);

        ArrayList<Fragment> tabList = new ArrayList<>();
        tabList.add(contactsFragment);
        tabList.add(chatFragment);
        pagerAdapter = new MyPagerAdapter(getSupportFragmentManager(), tabList);
        pager = (ViewPager) findViewById(R.id.container);
        pager.setAdapter(pagerAdapter);
        pager.addOnPageChangeListener(pagerAdapter);
        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(pager);

        BeepManager.getInstance(this).setSettings(settings);
    }

    private void initializeUI() {
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tvUnreadChatsCount = (TextView) findViewById(R.id.unread_chats_indicator);
        tvUnreadRequestsCount = (TextView) findViewById(R.id.unread_contacts_indicator);
    }

    @Override
    protected void onPause() {
        unbindService(mConnection);
        super.onPause();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        super.onServiceConnected(name, service);

        if (regMode) {
            dbService.saveKeys();
            boolean performExport = getIntent().getBooleanExtra(EXTRA_PERFORM_EXPORT, true);
            if (performExport) {
                showExportInfo();
            }
            regMode = false;
        }


        if (contactsFragment.getActivity() != null) {
            contactsFragment.refreshContactsList();
        }

        if (chatFragment.getActivity() != null) {
            chatFragment.refreshChatList();
        }

        if (currentPage == -1 && !activityManager.getDbChatHistory().getAdapterList().isEmpty()) {
            pager.setCurrentItem(1);
        }

        if (pagerAdapter != null) {
            pagerAdapter.onPageSelected(pager.getCurrentItem());
        }
    }

    private void changePage(int number) {
        pager.setCurrentItem(number);
        if (pagerAdapter != null) {
            pagerAdapter.onPageSelected(pager.getCurrentItem());
        }
    }

    /**
     * skips to a needed page when pressing an unread state counter
     *
     * @param view an unread state counter that called this method by onClick method
     */
    public void switchPage(View view) {
        if (view.getId() == R.id.unread_chats_indicator) {
            changePage(1);
        } else if (view.getId() == R.id.unread_contacts_indicator) {
            changePage(0);
            contactsFragment.setCategoryShow(R.id.menu_category_confirm);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent(this, SocketConnector.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        BeepManager.getInstance(this).updatePrefs();

        changeOnlineState(activityManager.isOnline());
        updateUnreadChatsCount();
        updateUnreadRequestsCount();
    }

    /**
     * Sets a new active ContactsFragment object for an activity
     *
     * @param contactsFragment a fragment to save
     */
    public void setContactsFragment(ContactsFragment contactsFragment) {
        this.contactsFragment = contactsFragment;
    }

    /**
     * Sets a new active ChatFragment object for an activity
     *
     * @param chatFragment a fragment to save
     */
    public void setChatFragment(ChatFragment chatFragment) {
        this.chatFragment = chatFragment;
    }

    private void updateUnreadChatsCount() {
        int unreadChatsCount = activityManager.getUnreadChatsCount();
        if (unreadChatsCount == 0) {
            tvUnreadChatsCount.setVisibility(View.GONE);
        } else {
            tvUnreadChatsCount.setVisibility(View.VISIBLE);
            tvUnreadChatsCount.setText(activityManager.getUnreadChatsString());
        }
    }

    /**
     * Updates the counter of unread incoming friendship requests and displays it via UI
     */
    public void updateUnreadRequestsCount() {
        int unreadRequestsCount = activityManager.getUnreadRequestsCount();
        if (unreadRequestsCount == 0) {
            tvUnreadRequestsCount.setVisibility(View.GONE);
        } else {
            tvUnreadRequestsCount.setVisibility(View.VISIBLE);
            tvUnreadRequestsCount.setText(activityManager.getUnreadRequestsString());
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.currentOptionsMenu = menu;
        if (currentOptionsMenu != null) {
            currentOptionsMenu.clear();
            getMenuInflater().inflate(R.menu.main, currentOptionsMenu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean result = true;
        // Handle ACTION bar item clicks here. The ACTION bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        Intent intent;
        switch (id) {
            case R.id.action_settings:
                showPreferencesActivity();
                break;
            case R.id.action_help:
                showInfoActivity();
                break;
            case R.id.action_export_user_key:
                exportPrivateUserKey(true);
                break;
            case R.id.action_downloads:
                intent = new Intent(this, DownloadsActivity.class);
                startActivity(intent);
                break;
            case R.id.action_add_contact:
                startActivityForResult(new Intent(this, SearchAddUserActivity.class), 0);
                break;
            case R.id.action_create_group_chat:
                intent = new Intent(this, ChooseContactsActivity.class);
                intent.putExtra(CustomValuesStorage.IntentExtras.INTENT_KEY_MODE, CustomValuesStorage.IntentExtras.INTENT_CREATE_CHAT);
                startActivityForResult(intent, 0);
                break;
            case R.id.action_deleteaccount:
                askForAccountDeletion();
                break;
            default:
                result = super.onOptionsItemSelected(item);
        }
        return result;
    }

    private void showInfoActivity() {
        Intent intent;
        intent = new Intent(this, TsmInfoActivity.class);
        intent.putExtra(AppCompatPreferenceActivity.EXTRA_SHOW_FRAGMENT,
                TsmInfoActivity.GeneralPreferenceFragment.class.getName());
        intent.putExtra(AppCompatPreferenceActivity.EXTRA_NO_HEADERS, true);
        startActivity(intent);
    }

    private void showPreferencesActivity() {
        Intent intent;
        intent = new Intent(this, TsmPreferencesActivity.class);
        intent.putExtra(AppCompatPreferenceActivity.EXTRA_SHOW_FRAGMENT,
                TsmPreferencesActivity.GeneralPreferenceFragment.class.getName());
        intent.putExtra(AppCompatPreferenceActivity.EXTRA_NO_HEADERS, true);
        startActivity(intent);
    }

    private void askForAccountDeletion() {
        final TsmMessageDialog deleteAccount = new TsmMessageDialog(this);
        String message = getString(R.string.info_delete_account);
        if (Build.VERSION_CODES.KITKAT > Build.VERSION.SDK_INT) {
            message += getString(R.string.info_delete_app_manually);
        }
        deleteAccount.setMessage(message);

        deleteAccount.setPositiveButton(R.string.btn_yes, new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                MessagePostman.getInstance().sendDeleteAccountRequest();
                deleteAccount.dismiss();


            }
        });
        deleteAccount.setNegativeButton(R.string.btn_no, new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                deleteAccount.dismiss();
            }
        });
        deleteAccount.show();
    }

    @Override
    protected void onDestroy() {
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(activityManager.NOTIFICATION_ID);
        mNotificationManager.cancelAll();
        super.onDestroy();
    }

    @Override
    protected void onSubscribeBroadcastMessage() {
        super.onSubscribeBroadcastMessage();
        IntentFilter intentFilter = new IntentFilter(BroadcastMessages.WS_CONTACT_STATUS);
        intentFilter.addAction(BroadcastMessages.WS_SEND_ADDRESS_BOOK );
        intentFilter.addAction(BroadcastMessages.WS_DELETE_RELATION);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);

        activityManager.setCurrentActivity(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("mode", "turn");
        outState.putInt(CURRENT_PAGE, currentPage);
    }

    @Override
    protected boolean onReceiveBroadcast(Intent intent) {
        boolean retVal = true;
        Bundle bundle = intent.getBundleExtra(BroadcastMessages.WS_PARAM);
        String action = intent.getAction();

        if (action.equals(BroadcastMessages.WS_NET_STATE)) {
            int newState = bundle.getInt(BroadcastMessages.WS_NET_STATE_VAL);
            changeOnlineState(newState == BroadcastMessages.ConnectionState.ONLINE);
        }
        if (action.equals(BroadcastMessages.WS_NEWMESSAGE) ||
                action.equals(BroadcastMessages.getBroadcastOperation(Operation.INVITE) +
                        BroadcastMessages.UI_BROADCAST)) {
            updateUnreadChatsCount();
            chatFragment.refreshChatList();
        }
        if (action.equals(BroadcastMessages.getBroadcastOperation(Operation.AUTHORIZE) + BroadcastMessages.UI_BROADCAST)) {
            String groupId = bundle.getString(Response.AuthorizeResponse.GROUP_ID);
            showChatWindow(groupId);
        }
        if (action.equals(BroadcastMessages.WS_DELETE_RELATION)) {
            String sender = bundle.getString(Response.UsersRelationsDeleteResponse.SENDER);
            dismissDialog(sender);
            contactsFragment.refreshContactsList();
        }
        if (processContactsChangeBroadcasts(bundle, action)) {
            retVal = super.onReceiveBroadcast(intent);
        }

        return retVal;
    }

    private boolean processContactsChangeBroadcasts(Bundle bundle, String action) {
        boolean runParent = true;
        if (action.equals(BroadcastMessages.getBroadcastOperation(Operation.SEND_NOTIFICATION))) {
            runParent = processSendNotificationBroadcast(bundle);
        }
        if (action.equals(BroadcastMessages.getBroadcastOperation(Operation.ANSWER_NOTIFICATION))) {
            runParent = processAnswerNotificationBroadcast(bundle);
        }
        boolean contactBroadcast = action.equals(BroadcastMessages.WS_CONTACT_STATUS) ||
                action.equals(BroadcastMessages.getBroadcastOperation(Operation.ANSWER_NOTIFICATION)) ||
                action.equals(BroadcastMessages.getBroadcastOperation(Operation.SEND_NOTIFICATION));
        if ((contactBroadcast ||
                action.equals(
                        BroadcastMessages.WS_SEND_ADDRESS_BOOK) ||
                action.equals(
                        BroadcastMessages.getBroadcastOperation(Operation.NEW_CONTACT) +
                                BroadcastMessages.UI_BROADCAST)) &&
                (contactsFragment != null)) {
            contactsFragment.refreshContactsList();
            if (action.equals(BroadcastMessages.getBroadcastOperation(Operation.SEND_NOTIFICATION))) {
                String senderId = bundle.getString(Response.AnswerNotificationResponse.SENDER);
                String ownId = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE)
                        .getString(SharedPreferencesAccessor.USER_ID, "");
                if (ownId.equals(senderId)) {
                    ArrayList<String> incorrect = bundle.getStringArrayList(
                            Response.SendNotificationResponse.ACCEPTORS_NOT_SEND);
                    if (incorrect != null && !incorrect.isEmpty()) {
                        DbMessengerUser uninstalled = getDbAddress().getMessengerDb().get(incorrect.get(0));
                        String message = String.format(
                                getString(R.string.info_request_not_sent_concrete),
                                uninstalled.getDisplayName());
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
        return runParent;
    }

    private boolean processAnswerNotificationBroadcast(Bundle bundle) {
        boolean runParent;
        if (bundle.getInt(Response.SendNotificationResponse.TYPE, 0) == CustomValuesStorage.CATEGORY_CONFIRM_OUT) {
            String userName = bundle.getString(Response.AnswerNotificationResponse.SENDER);
            TsmMessageDialog currentShownDialog = shownDialogs.get(userName);
            if (currentShownDialog != null) {
                currentShownDialog.dismiss();
                shownDialogs.remove(userName);
            }

        }

        runParent = false;
        return runParent;
    }

    private boolean processSendNotificationBroadcast(Bundle bundle) {
        int type = bundle.getInt(Response.SendNotificationResponse.TYPE);
        boolean showDlg = bundle.getBoolean(CustomValuesStorage.IntentExtras.INTENT_SHOW_DIALOG);
        if (type == CustomValuesStorage.CATEGORY_CONFIRM_IN && showDlg) {
                String senderPersId = bundle.getString(Response.SendNotificationResponse.SENDER_ID);
                String nickName = bundle.getString(Response.SendNotificationResponse.SENDER);
                if (nickName == null || nickName.isEmpty())
                    nickName = senderPersId;
                showInvitation(senderPersId, nickName);
        }
        return false;
    }

    private void changeOnlineState(boolean isOnline) {
        findViewById(R.id.lblOffline).setVisibility(isOnline ? View.GONE : View.VISIBLE);
        if (contactsFragment != null && !isOnline) {
            contactsFragment.setAllOffline();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (resultCode) {
            case CustomValuesStorage.ActivityResult.RESULT_GRP_OK:
                contactsFragment.refreshContactsList();
                break;
            case CustomValuesStorage.ActivityResult.RESULT_CHT_OK:
                int groupId = data.getIntExtra(CustomValuesStorage.IntentExtras.INTENT_KEY_GRP_ID, -1);
                if (groupId >= 0) {
                    createNewChat(groupId);
                }
                break;
            case Activity.RESULT_OK:
                String uri = data.getStringExtra(OpenFileActivity.EXTRA_URI);
                if (uri != null) {
                    writeUserKeys(true, uri);
                }
                break;
            case Activity.RESULT_CANCELED:
            default:
        }
    }

    private void writeUserKeys(boolean showResult, String uri) {
        int ret = EdDsaSigner.getInstance().writeKeysToTextFile(uri,
                settings.getString(SharedPreferencesAccessor.USER_NICKNAME, ""));
        if (showResult) {
            showResults(ret);
        }
    }

    private void createNewChat(int groupId) {
        DbGroupChat newGroup = ((ActivityGlobalManager) getApplication()).getDbContact()
                .getGroupChat(String.valueOf(groupId));
        ContactPerson newChat = new ContactPerson();
        newChat.setGroupChat(newGroup);
        startChat(newChat);
    }

    @Override
    public void onBackPressed() {
        if (activityManager.getTransferringFilesCount() > 0) {
            final TsmMessageDialog reallyExit = new TsmMessageDialog(this);
            reallyExit.setTitle(R.string.title_exit);
            reallyExit.setMessage(R.string.info_exit_file_transfer);
            reallyExit.setPositiveButton(R.string.btn_yes, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    reallyExit.dismiss();
                    finish();
                    activityManager.finishApp();
                    getParent().onBackPressed();
                }
            });
            reallyExit.setNegativeButton(R.string.btn_no, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    reallyExit.dismiss();
                }
            });
            reallyExit.show();
        } else {
            UniversalHelper.pressBackButton(this);
        }
    }

    private void showInvitation(final String senderPersId, final String nickName) {
        final TsmMessageDialog invitationMessage = new TsmMessageDialog(this);
        final DbMessengerUser invitor = dbService.getTsmContact().getMessengerDb().get(senderPersId);
        invitationMessage.setMessage(
                getString(R.string.info_user_capital) + " " +
                        nickName + " " +
                        getString(R.string.info_asks_share_contacts));

        invitationMessage.setPositiveButton(R.string.btn_share, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);
                NotificationManager mNotificationManager =
                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.cancel(TsmNotification.NEWREQUEST_ID);

                if (invitor.getDbStatus() != CustomValuesStorage.CATEGORY_CONNECT) {
                    MessagePostman.getInstance().sendAnswerNotificationMessage(
                            senderPersId, Request.AnswerNotificationRequest.AnwerType.ACCEPT);
                    invitor.setNickName(nickName);
                    invitor.setDbStatus(CustomValuesStorage.CATEGORY_CONNECT);
                    dbService.getTsmContact().saveMessengerUser(invitor);
                    dismissDialog(senderPersId, invitationMessage);
                    contactsFragment.refreshContactsList();
                } else {
                    dismissDialog(senderPersId, invitationMessage);
                    Toast.makeText(MainActivity.this, R.string.contact_already_connected, Toast.LENGTH_SHORT).show();
                }
            }
        });
        invitationMessage.setNegativeButton(R.string.btn_not_to_share, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);
                NotificationManager mNotificationManager =
                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.cancel(TsmNotification.NEWREQUEST_ID);

                if (invitor.getDbStatus() != CustomValuesStorage.CATEGORY_CONNECT) {
                    MessagePostman.getInstance().sendAnswerNotificationMessage(senderPersId,
                            Request.AnswerNotificationRequest.AnwerType.DECLINE);
                    invitor.setDbStatus(CustomValuesStorage.CATEGORY_NOTCONNECT);
                    dbService.getTsmContact().saveMessengerUser(invitor);
                    dismissDialog(senderPersId, invitationMessage);
                    contactsFragment.refreshContactsList();
                } else {
                    dismissDialog(senderPersId, invitationMessage);
                    Toast.makeText(MainActivity.this, R.string.contact_already_connected, Toast.LENGTH_SHORT).show();
                }
            }
        });
        showDialog(senderPersId, invitationMessage);
    }

    private void showChatWindow(String unitId) {
        currentCheckedChatGroup = unitId;
        if (dbService.getLocalChatHistory().getChatByPersId(unitId) != null) {
            Intent intent = new Intent(this, ChatHistoryActivity.class);
            intent.putExtra(CustomValuesStorage.IntentExtras.INTENT_UNIT_ID, currentCheckedChatGroup);
            startActivity(intent);

        } else {
            sendAuthorizeForChat();
        }
    }

    private void sendAuthorizeForChat() {
        String chatId = "";
        String chatName = null;
        String sequrityType = Param.ChatSecureLevel.ALL_HISTORY_KEEP;
        List<String> participantsList = new ArrayList<>();
        ArrayList<String> requestKey = new ArrayList<>();

        if (currentViewedChat != null) {
            chatId = currentViewedChat.getChatId().toString();
            if (dbService.getLocalChatHistory().getChat(currentViewedChat.getChatId()).getChatCategory() == ChatUnit.ChatCategoryType.GROUP) {
                String conIdent = settings.getString(com.tsm_messenger.protocol.transaction.Request.BaseRequest.CON_IDENT, "");
                chatName = currentCheckedChatGroup + "_" + conIdent;
                sequrityType = currentViewedChat.getSecureType().toString();
            }
            participantsList = currentViewedChat.getParticipantsList();
        } else {
            if (currentCheckedChatGroup != null) {
                String conIdent = settings.getString(com.tsm_messenger.protocol.transaction.Request.BaseRequest.CON_IDENT, "");
                chatName = currentCheckedChatGroup + "_" + conIdent;
                if (!currentCheckedChatGroup.startsWith(CustomValuesStorage.GROUP_DESCRIPTIOR)) {
                    participantsList.add(currentCheckedChatGroup);
                    sequrityType = Param.ChatSecureLevel.ALL_HISTORY_KEEP;
                } else {
                    DbGroupChat group = ((ActivityGlobalManager) getApplication()).getDbContact().getGroupChat(currentCheckedChatGroup);
                    sequrityType = group.getSequreType().toString();

                    Set<String> participants = group.getMembers().keySet();
                    participantsList.addAll(participants);
                }
            } else {
                TsmMessageDialog smthWrongMessage = new TsmMessageDialog(MainActivity.this);
                smthWrongMessage.show(R.string.title_error, R.string.error_smth_wrong);
            }
        }

        for (String userId : participantsList) {

            DbMessengerUser user = dbService.getTsmContact().getMessengerDb().get(userId);
            if (user.getPublicKey() == null || user.getPublicKey().isEmpty()) {
                requestKey.add(userId);
            }
        }
        if (!requestKey.isEmpty()) {
            activityManager.requestParticipantsPublicKey(requestKey);
            UniversalHelper.showSnackBar(getWindow().getDecorView().getRootView(),
                    this, getResources().getString(R.string.error_chat_create_verify_key));

            return;
        }
        participantsList.add(settings.getString(SharedPreferencesAccessor.USER_ID, ""));


        if ("".equals(chatId)) {
            ChatUnit chat = dbService.getLocalChatHistory().getChatByPersId(currentCheckedChatGroup);
            if (chat == null) {
                //we don't have such chat
                if (connectService.hasCorrectConnection()) {
                    MessagePostman.getInstance().sendAuthorizeMessage(chatId, chatName,
                            currentCheckedChatGroup, participantsList, sequrityType);
                } else {
                    UniversalHelper.showSnackBar(
                            getWindow().getDecorView().getRootView(),
                            this, getResources().getString(R.string.error_chat_create_offline));
                }
            }
        }
    }

    /**
     * Changes a user status to a connected and sends a positive friendship answer to the server
     *
     * @param login a user to accept friendship request
     */
    public void acceptNotification(String login) {
        DbMessengerUser user = ((ActivityGlobalManager) getApplication()).getDbContact().getMessengerDb().get(login);
        user.setDbStatus(CustomValuesStorage.CATEGORY_CONNECT);
        ((ActivityGlobalManager) getApplication()).getDbContact().saveMessengerUser(user);

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(TsmNotification.NEWREQUEST_ID);

        MessagePostman.getInstance().sendAnswerNotificationMessage(
                login,
                Request.AnswerNotificationRequest.AnwerType.ACCEPT);
    }

    /**
     * Changes a user status to a not-connected, deletes pending notification
     * and sends a negative friendship answer to the server
     *
     * @param login a user to decline friendship request
     */
    public void rejectNotification(String login) {
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(TsmNotification.NEWREQUEST_ID);

        MessagePostman.getInstance().sendAnswerNotificationMessage(
                login,
                Request.AnswerNotificationRequest.AnwerType.DECLINE);
        DataAddressBook dbContact = ((ActivityGlobalManager) getApplication())
                .getDbContact();
        DbMessengerUser messengerUser = dbContact.getMessengerDb().get(login);
        dbContact.getMessengerDb().get(login).setDbStatus(CustomValuesStorage.CATEGORY_NOTCONNECT);
        dbContact.saveMessengerUser(messengerUser);
    }

    /**
     * Sends a friendship request to a the server
     *
     * @param pers login of a user to request friendship
     */
    public void requestContact(ContactPerson pers) {
        UniversalHelper.requestContact(pers.getMessengerId());
        contactsFragment.refreshContactsList();
    }

    /**
     * Gets the current active instance of a DataAddressBook
     *
     * @return an instance of the DataAddressBook class
     */
    public DataAddressBook getDbAddress() {
        return ((ActivityGlobalManager) getApplication()).getDbContact();
    }

    /**
     * Performs a chat initialization and opens a chat window for a selected contact
     *
     * @param item a person or a group to start chat
     */
    public void startChat(ContactPerson item) {
        boolean isOutcast = false;
        if (item.getContactType() == ContactPerson.GROUP) {
            currentCheckedChatGroup = item.getGroupChat().getGroupIdString();
        } else {
            currentCheckedChatGroup = item.getMessengerUser().getPersId();
            if (item.getMessengerUser().getStatusOnline() == CustomValuesStorage.UserStatus.UNREACHABLE)
                isOutcast = true;
        }
        currentViewedChat = dbService.getLocalChatHistory().getChatByPersId(currentCheckedChatGroup);


        if (currentViewedChat != null && currentViewedChat.isConnected()) {
            currentViewedChat.resetUnreadMessagesCounter();
            if (item.getContactType() != ContactPerson.GROUP) {
                currentViewedChat.setIsOutcast(isOutcast);
            }
        }

        showChatWindow(currentCheckedChatGroup);
    }

    /**
     * Marks a selected chat as current viewed chat and opens a chat history
     *
     * @param chat a chat to open
     */
    public void openChat(ChatUnit chat) {
        if (chat.getChatCategory() == ChatUnit.ChatCategoryType.PERSON) {
            currentCheckedChatGroup = chat.getUnitId();
        }
        currentViewedChat = chat;
        if (currentViewedChat.isConnected()) {
            currentViewedChat.resetUnreadMessagesCounter();
        }
        showChatWindow(currentViewedChat.getUnitId());
    }

    private void showDialog(String sender, TsmMessageDialog newDialog) {
        TsmMessageDialog currentShownDialog = shownDialogs.get(sender);
        if (currentShownDialog != null) {
            currentShownDialog.dismiss();
            shownDialogs.remove(sender);
        }
        newDialog.show();
        shownDialogs.put(sender, newDialog);
    }

    private void dismissDialog(String sender, TsmMessageDialog dialog) {
        dialog.dismiss();
        shownDialogs.remove(sender);
    }

    private void dismissDialog(String sender) {
        TsmMessageDialog dialog = shownDialogs.get(sender);
        if (dialog != null) {
            dismissDialog(sender, dialog);
        }
    }

    /**
     * Opens an info activity for a selected contact
     *
     * @param pers a person to view info
     */
    public void showContactInfo(final ContactPerson pers, String mode) {
        hideKeyboard();
        Intent intent = new Intent(this, InfoActivity.class);
        intent.putExtra(CustomValuesStorage.IntentExtras.INTENT_MESSENGER_ID, pers.getMessengerId());
        intent.putExtra(InfoActivity.INFO_MODE_EXTRA, mode);
        startActivity(intent);
    }

    private void hideKeyboard() {
        inputMethodManager.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
    }

    /**
     * Gets the current active instance of a DataChatHistory
     *
     * @return returns an instance of a DataChatHistory class
     */
    public DataChatHistory getDbChatHistory() {
        return dbService.getLocalChatHistory();
    }

    @Override
    public void exportPrivateUserKey(boolean showChooseExportMode) {
        try {
            if (showChooseExportMode) {
                UserKeyExportImportManager.getInstance().exportKey(this);
            } else {
                String uri = getIntent().getStringExtra(EXTRA_EXPORT_FOLDER);
                if (uri == null || "".equals(uri)) {
                    uri = CustomValuesStorage.KEYS_DIRECTORY;
                }
                writeUserKeys(false, uri);
            }
        } catch (NullPointerException npe) {
            UniversalHelper.logException(npe);
        }
    }

    @Override
    public void importPrivateUSerKey() {
        // no need to implement in MainActivity
    }

    @Override
    public void showResults(int result) {
        Toast.makeText(this, R.string.info_operation_done_success, Toast.LENGTH_LONG).show();
        getIntent().removeExtra(ServiceParameters.MODE);
        getIntent().putExtra(ServiceParameters.MODE, ServiceParameters.SIGNIN);
    }

    /**
     * Shows an information dialog that private key export was performed
     */
    public void showExportInfo() {
        if (!EdDsaSigner.getInstance().isKeyImported()) {
            exportPrivateUserKey(false);

            getIntent().removeExtra(ServiceParameters.MODE);
            getIntent().putExtra(ServiceParameters.MODE, ServiceParameters.SIGNIN);
        }
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void scanQrCodeForImport() {
        // no need to implement in MainActivity
    }

    @Override
    public void chooseFile() {
        Intent intent = new Intent(this, OpenFileActivity.class);
        intent.putExtra(OpenFileActivity.EXTRA_OPEN_FOR_WRITE, true);
        intent.putExtra(OpenFileActivity.EXTRA_VIEW_MODE, OpenFileActivity.MODE_DIRECTORIES_ONLY);
        intent.putExtra(OpenFileActivity.EXTRA_HOME_DIRECTORY, CustomValuesStorage.KEYS_DIRECTORY);
        startActivityForResult(intent, 0);
    }

    /**
     * Applies the last changes in the chat list to the UI
     */
    public void refreshChatList() {
        chatFragment.refreshChatList();
    }

    private class MyPagerAdapter extends FragmentPagerAdapter implements ViewPager.OnPageChangeListener {
        private final List<Fragment> fragmentList;

        public MyPagerAdapter(FragmentManager fm, List<Fragment> fragmentList) {
            super(fm);
            this.fragmentList = fragmentList;
        }

        @Override
        public Fragment getItem(int pos) {
            return fragmentList.get(pos);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (fragmentList.get(position).getClass() == ChatFragment.class) {
                return getString(R.string.btn_chats);
            } else {
                return getString(R.string.btn_contacts);
            }
        }

        @Override
        public int getCount() {
            return fragmentList.size();
        }

        @Override
        public void onPageScrolled(int i, float v, int i2) {
            // no need to implement in MainActivity
        }

        @Override
        public void onPageSelected(int i) {
            currentPage = i;
            if (currentOptionsMenu != null) {
                MenuItem searchItem = currentOptionsMenu.findItem(R.id.action_search);
                SearchView searchView = (SearchView) searchItem.getActionView();
                if (i == 0) {
                    searchView.setOnQueryTextListener(contactsFragment.getSearchViewListener());
                    contactsFragment.getSearchViewListener()
                            .onQueryTextSubmit(searchView.getQuery().toString());
                    activityManager.resetUnreadRequestsCount();
                    updateUnreadRequestsCount();
                } else {
                    searchView.setOnQueryTextListener(chatFragment);
                    chatFragment.onQueryTextSubmit(searchView.getQuery().toString());
                }
            }
        }

        @Override
        public void onPageScrollStateChanged(int i) {
            // no need to implement in MainActivity
        }
    }
}
