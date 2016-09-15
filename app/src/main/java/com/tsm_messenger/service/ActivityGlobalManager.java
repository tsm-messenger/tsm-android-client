package com.tsm_messenger.service;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.view.View;

import com.tsm_messenger.activities.BuildConfig;
import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.control.TsmMessageDialog;
import com.tsm_messenger.activities.control.TsmNotification;
import com.tsm_messenger.activities.main.chat.ChatHistoryActivity;
import com.tsm_messenger.activities.service.BroadcastMessages;
import com.tsm_messenger.activities.service.ServiceParameters;
import com.tsm_messenger.activities.service.TsmBackgroundService;
import com.tsm_messenger.activities.service.TsmDatabaseService;
import com.tsm_messenger.connection.MessageQueue;
import com.tsm_messenger.connection.SocketConnector;
import com.tsm_messenger.crypto.EdDsaSigner;
import com.tsm_messenger.data.storage.DataAddressBook;
import com.tsm_messenger.data.storage.DataChatHistory;
import com.tsm_messenger.data.storage.DataFileStorage;
import com.tsm_messenger.data.storage.TsmDatabaseHelper;
import com.tsm_messenger.protocol.transaction.Operation;
import com.tsm_messenger.protocol.transaction.Response;
import com.tsm_messenger.server.TigerHelper;

import net.sqlcipher.database.SQLiteDatabase;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
 */

public class ActivityGlobalManager extends Application {

    public static final String DATE_FORMAT = "dd.MM.yy";
    public static final int NOTIFICATION_ID = 112299;
    private static String TRANSACTION_URL = null;
    private static int TRANSACTION_PORT = 0;
    private static ActivityGlobalManager instance;
    private final Set<Integer> chatsForInvite = new HashSet<>();
    private final Map<String, Intent> NOT_ANSWERED_FILES = new HashMap<>();
    private int phantomMessageCounter;
    private String pin;
    private FileProgressListener fileProgressListener;
    private Activity currentActivity;
    private TsmDatabaseService dbService;
    private SocketConnector socketConnector;
    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {

            SocketConnector.LocalBinder binder = (SocketConnector.LocalBinder) service;
            socketConnector = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {

            socketConnector = null;
        }
    };
    private String ownLogin;
    private boolean isOnline = false;
    private BroadcastReceiver broadcastReceiver;
    private Set<Integer> chatIdSet;
    private TsmDatabaseHelper db;

    private int unreadRequestsCount = 0;
    private Set<String> transferringFilesSet = new HashSet<>();
    private ChatHistoryActivity.RecordAdapter currentAdapter;
    private Integer currentChatId;

    /**
     * Gets the current instance of an application, managing all activities
     *
     * @return the current active ActivityGlobalManager instance
     */
    public static ActivityGlobalManager getInstance() {
        return instance;
    }

    /**
     * Gets a string representing a presented Date
     *
     * @param date a date to present as string
     * @return a string representing a presented Date
     */
    public static String getTimeString(Date date) {
        Date timeStamp = date;
        if (timeStamp == null) {
            timeStamp = new Date();
        }
        String currentDate;
        String mask;
        SimpleDateFormat checkFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        currentDate = checkFormat.format(new Date());
        if (currentDate.equals(checkFormat.format(timeStamp)))
            mask = "HH:mm:ss";
        else
            mask = ActivityGlobalManager.DATE_FORMAT + " HH:mm:ss";

        SimpleDateFormat printFormat = new SimpleDateFormat(mask, Locale.US);
        return printFormat.format(timeStamp.getTime());
    }

    private static synchronized void updateInstance(ActivityGlobalManager newInstance) {
        instance = newInstance;
    }

    /**
     * Sets a new URL of the transaction server
     *
     * @param url  a new connection URL
     * @param port a new connection port
     */
    public static void setTransactURL(String url, int port) {
        TRANSACTION_URL = url;
        TRANSACTION_PORT = port;
    }

    /**
     * Gets the current url of the transaction server
     *
     * @return the string containing current connection URL
     */
    public static String getTransactionUrl() {
        return TRANSACTION_URL;
    }

    /**
     * Gets the current connection port of a transaction the server
     *
     * @return the integer number of a port
     */
    public static int getTransactionPort() {
        return TRANSACTION_PORT;
    }

    /**
     * Returns an incremented value of phantom (not-saved) messages incoming
     *
     * @return a number of incoming phantom messages
     */
    public synchronized int getPhantomMessageCounter() {
        return ++phantomMessageCounter;
    }

    /**
     * Sets a new active instance of database service
     *
     * @param dbService an instance of database service
     */
    public void setDbService(TsmDatabaseService dbService) {
        this.dbService = dbService;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        chatIdSet = new HashSet<>();
        updateInstance(this);
        pin = null;

        SharedPreferences settings = getSharedPreferences(
                SharedPreferencesAccessor.PREFS_NAME,
                SharedPreferencesAccessor.PREFS_MODE);
        String lastBalancerUrl = settings.getString(SharedPreferencesAccessor.LAST_BALANCER_URL, "");
        if (lastBalancerUrl == null || lastBalancerUrl.isEmpty()) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(SharedPreferencesAccessor.LAST_BALANCER_URL,
                    BuildConfig.BALANCER_URL);
            editor.putInt(SharedPreferencesAccessor.FAILED_CONNECT, 0);
            editor.apply();
        }

        SQLiteDatabase.loadLibs(this);

        final Thread.UncaughtExceptionHandler oldHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
                        UniversalHelper.logException(paramThrowable);

                        if (oldHandler != null)
                            oldHandler.uncaughtException(
                                    paramThread,
                                    paramThrowable
                            );
                    }
                });
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onReceiveBroadcast(intent);
            }
        };
        onSubscribeBroadcastMessage();
    }

    /**
     * Loads a user login from sharedPreferences into memory
     */
    public void readLogin() {
        ownLogin = getSharedPreferences(
                SharedPreferencesAccessor.PREFS_NAME,
                SharedPreferencesAccessor.PREFS_MODE)
                .getString(SharedPreferencesAccessor.USER_ID, "");
    }

    /**
     * Gets the login loaded from shared preferences
     *
     * @return the login of an app owner
     */
    public String getOwnLogin() {
        return ownLogin;
    }

    private void onSubscribeBroadcastMessage() {
        IntentFilter bcFilter = new IntentFilter(BroadcastMessages.WS_NET_STATE);
        bcFilter.addAction(BroadcastMessages.WS_FILERECEIVE + BroadcastMessages.UI_BROADCAST);
        bcFilter.addAction(BroadcastMessages.WS_FILEANSWER);
        bcFilter.addAction(BroadcastMessages.WS_NEWMESSAGE);
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.SEND_NOTIFICATION));
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.ANSWER_NOTIFICATION));
        bcFilter.addAction(BroadcastMessages.WS_DELETE_ACCOUNT);
        bcFilter.addAction(BroadcastMessages.WS_DELETE_RELATION);
        bcFilter.addAction(BroadcastMessages.ALARM_OWNER_KEY);
        bcFilter.addAction(BroadcastMessages.ALARM_PARTICIPANT_KEY);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver,
                bcFilter);

    }

    private void onReceiveBroadcast(Intent intent) {
        if (intent == null) {
            return;
        }
        Bundle bundle = intent.getBundleExtra(BroadcastMessages.WS_PARAM);
        String action = intent.getAction();
        if (BroadcastMessages.ALARM_OWNER_KEY.equals(action)) {
            alarmOwnerKey(bundle);
            return;
        }
        if (BroadcastMessages.ALARM_PARTICIPANT_KEY.equals(action)) {
            alarmParticipantKey(bundle);
            return;
        }
        if (BroadcastMessages.WS_NEWMESSAGE.equals(action)) {
            processNewMessageBroadcast(bundle);
        }
        if (BroadcastMessages.getBroadcastOperation(Operation.SEND_NOTIFICATION).equals(action) &&
                bundle != null) {
            int type = bundle.getInt(Response.SendNotificationResponse.TYPE);
            boolean showDlg = bundle.getBoolean(CustomValuesStorage.IntentExtras.INTENT_SHOW_DIALOG);
            if (type == CustomValuesStorage.CATEGORY_CONFIRM_IN && showDlg) {
                addUnreadRequest();
            }
        }
        if (BroadcastMessages.WS_DELETE_ACCOUNT.equals(action)) {
            deleteUserAccount();
            return;
        }
        if (BroadcastMessages.WS_DELETE_RELATION.equals(action) && bundle != null) {

            Intent msgReceive = new Intent(this, ChatHistoryActivity.class);
            msgReceive.setAction(BroadcastMessages.NTF_NEW_MESSAGE);
            String persIdent = bundle.getString(Response.DeleteUserAccountResponse.SENDER);


            PendingIntent pendingIntentYes = PendingIntent.getBroadcast(this, TsmNotification.NEWREQUEST_ID, msgReceive, PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder hh = new NotificationCompat.Builder(this);
            hh.setContentTitle(getString(R.string.notification_deleterelation_title));
            hh.setContentText(String.format(getString(R.string.notification_deleterelation_detail), persIdent));
            hh.setSmallIcon(R.drawable.ic_info_small);
            hh.setAutoCancel(true);

            hh.setContentIntent(pendingIntentYes);

            Notification note1 = hh.build();
            NotificationManager mNotificationManager =
                    (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

            mNotificationManager.notify(TsmNotification.RELATIONBROKE_ID, note1);

            Intent wsResult = new Intent(BroadcastMessages.WS_CONTACT_STATUS);
            wsResult.putExtra(BroadcastMessages.WS_PARAM, bundle);
            LocalBroadcastManager.getInstance(this).sendBroadcast(wsResult);

            return;
        }
        if (action.equals(BroadcastMessages.WS_NET_STATE) && bundle != null) {
            int newState = bundle.getInt(BroadcastMessages.WS_NET_STATE_VAL);
            if (newState == BroadcastMessages.ConnectionState.ONLINE) {
                showToggleOnline();
            } else {
                showToggleOffline();
            }
        }
        if (action.equals(BroadcastMessages.WS_FILERECEIVE + BroadcastMessages.UI_BROADCAST) ||
                action.equals(BroadcastMessages.WS_FILEANSWER)) {
            rememberIncomingFile(intent, bundle);
        }
    }

    /**
     * Shows an alarm that the server is untrusted by the last response
     *
     * @param bundle a bundle containing a corrupted key
     */
    public void alarmOwnerKey(Bundle bundle) {
        if (getCurrentActivity() != null) {
            final TsmMessageDialog errorMessage = new TsmMessageDialog(getCurrentActivity());
            errorMessage.setTitle(R.string.alarm_title);
            errorMessage.setMessage(R.string.alarm_owner_key);
            errorMessage.setPositiveButton(R.string.btn_ok, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    errorMessage.dismiss();
                }
            });
            errorMessage.show();
        }
    }

    /**
     * Shows an alarm that the chat participant is untrusted by the last response
     *
     * @param bundle a bundle containing a corrupted key
     */
    public void alarmParticipantKey(Bundle bundle) {
        if (getCurrentActivity() != null) {
            final TsmMessageDialog errorMessage = new TsmMessageDialog(getCurrentActivity());
            errorMessage.setTitle(R.string.alarm_title);
            errorMessage.setMessage(R.string.alarm_participant_key);
            errorMessage.setPositiveButton(R.string.btn_ok, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    errorMessage.dismiss();
                }
            });

            errorMessage.show();
        }
    }

    private void rememberIncomingFile(Intent intent, Bundle bundle) {
        if (currentActivity == null && bundle != null && intent != null) {
            String fileId = bundle.getString(Response.FileReceiveResponse.FILE_ID);
            NOT_ANSWERED_FILES.put(fileId, intent);
        }
    }

    private void processNewMessageBroadcast(Bundle bundle) {
        if (bundle != null) {
            boolean notificationShow = bundle.getBoolean(BroadcastMessages.MessagesParam.SHOW_NOTIFICATION);
            if (notificationShow) {
                int chatId = bundle.getInt(Response.MessageResponse.CHAT_ID);
                if (chatId != -1) {
                    addUnreadMessage(chatId);
                }
            }
            if (currentActivity == null) {
                showNotificationMessage(bundle);
            }
        }
    }

    private void showNotificationMessage(Bundle bundle) {
        if (bundle != null) {
            Intent msgReceive = new Intent(this, ChatHistoryActivity.class);
            msgReceive.setAction(Intent.ACTION_VIEW);
            msgReceive.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            Integer chatId = bundle.getInt(Response.MessageResponse.CHAT_ID, 0);
            msgReceive.putExtra(Response.MessageResponse.CHAT_ID,
                    chatId);

            PendingIntent pendingIntentMsg = PendingIntent.getActivity(this, 0, msgReceive, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Builder hh = new NotificationCompat.Builder(this);
            hh.setContentTitle(getString(R.string.notification_new_messages));
            int unreadMsg = bundle.getInt(BroadcastMessages.MessagesParam.UNREAD);
            String chatName = bundle.getString(BroadcastMessages.MessagesParam.CHAT_NAME);
            if (chatName == null) {
                chatName = "";
            }
            String detail = String.format(getString(R.string.notification_new_messages_in_chat), unreadMsg) + " " + chatName;
            hh.setContentText(detail);
            hh.setSmallIcon(R.drawable.ic_info_small);
            hh.setAutoCancel(true);
            hh.setContentIntent(pendingIntentMsg);

            Notification note1 = hh.build();
            note1.flags |= Notification.FLAG_ONGOING_EVENT;

            NotificationManager mNotificationManager =
                    (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

            mNotificationManager.notify(TsmNotification.NEWMESSAGE_ID + chatId, note1);
        }
    }

    /**
     * Adds a new file id to the list of current transferring files
     *
     * @param fileId a fileId to add
     */
    public void addTransferringFile(String fileId) {
        if (fileId != null && transferringFilesSet != null) {
            transferringFilesSet.add(fileId);
        }
    }

    /**
     * Removes a fileId from the list of current transferring files
     *
     * @param fileId a fileId to remove
     */
    public void removeTranstferringFile(String fileId) {
        if (fileId != null && transferringFilesSet != null) {
            transferringFilesSet.remove(fileId);
        }
    }

    /**
     * Gets the count of files that are transferring at the moment
     *
     * @return the number of files
     */
    public int getTransferringFilesCount() {
        if (transferringFilesSet != null) {
            return transferringFilesSet.size();
        } else {
            return 0;
        }
    }

    private void addUnreadMessage(Integer chatId) {
        if (chatId != null && chatIdSet != null) {
            chatIdSet.add(chatId);
        }
    }

    private void addUnreadRequest() {
        unreadRequestsCount++;
    }

    /**
     * Gets the count of unread incoming contact requests (friendship requests)
     *
     * @return an integer number of requests
     */
    public int getUnreadRequestsCount() {
        return unreadRequestsCount;
    }

    /**
     * Sets an unread requests counter to zero
     */
    public void resetUnreadRequestsCount() {
        unreadRequestsCount = 0;
    }

    /**
     * Decrements an unread requests counter
     */
    public void minusOneUnreadRequest() {
        unreadRequestsCount--;
        if (unreadRequestsCount < 0) unreadRequestsCount = 0;
    }

    /**
     * Removes a provided chat id from unread chats lists
     *
     * @param chatId an id of a chat to set as read
     */
    public void setChatRead(Integer chatId) {
        if (chatId != null && chatIdSet != null) {
            chatIdSet.remove(chatId);
        }
    }

    /**
     * Gets the count of chats having unread messages
     *
     * @return an integer number of unread chats
     */
    public int getUnreadChatsCount() {
        if (chatIdSet != null) {
            return chatIdSet.size();
        } else {
            return 0;
        }
    }

    /**
     * Gets the string to show the unread chats count via UI
     *
     * @return a string containing the unread chats count
     */
    public String getUnreadChatsString() {
        int size = getUnreadChatsCount();
        return size < 100 ? String.valueOf(size) : "99+";
    }

    /**
     * Gets the string to show the unread requests count via UI
     *
     * @return a string containing the unread requests count
     */
    public String getUnreadRequestsString() {
        return unreadRequestsCount < 100
                ? String.valueOf(unreadRequestsCount)
                : "99+";
    }

    /**
     * Gets the instance of a current active acivity
     *
     * @return an active Activity object
     */
    public Activity getCurrentActivity() {
        return currentActivity;
    }

    /**
     * Sets the new instance of an active activity
     *
     * @param currentActivity a new active Activity object
     */
    public void setCurrentActivity(Activity currentActivity) {
        this.currentActivity = currentActivity;
        if (currentActivity != null) {
            startUnsentFilesShowing();
        }
    }

    private void startUnsentFilesShowing() {
        new Handler(Looper.myLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent fileIntent;
                List<String> filesToRemove = new ArrayList<>();
                synchronized (NOT_ANSWERED_FILES) {
                    for (Map.Entry<String, Intent> entry : NOT_ANSWERED_FILES.entrySet()) {
                        fileIntent = entry.getValue();
                        filesToRemove.add(entry.getKey());
                        LocalBroadcastManager.getInstance(ActivityGlobalManager.this).sendBroadcast(fileIntent);
                    }
                    for (String fileId : filesToRemove) {
                        NOT_ANSWERED_FILES.remove(fileId);
                    }
                }
            }
        }, 500);
    }

    /**
     * Gets the reference to a service managing net connections
     *
     * @return an active instance of a SocketConnector
     */
    public SocketConnector getSocketConnector() {
        return socketConnector;
    }

    /**
     * Initializes the net-managing service binding
     */
    public void bindServices() {
        Intent intent = new Intent(this, SocketConnector.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

    }

    /**
     * Returns a current active instance of DataAddressBook
     *
     * @return an active DataAddressBook instance
     */
    public DataAddressBook getDbContact() {
        return dbService == null ? null : dbService.getTsmContact();
    }

    /**
     * Returns an active instance of a database helper
     *
     * @return an active TsmDatabaseHelper instance
     */
    public TsmDatabaseHelper getDb() {
        if (db == null) {
            db = TsmDatabaseHelper.getInstance(getApplicationContext());
        }
        return db;
    }

    /**
     * Returns an active instance of a chat history storage
     *
     * @return an active DataChatHistory object
     */
    public DataChatHistory getDbChatHistory() {
        return dbService == null ? null : dbService.getLocalChatHistory();
    }

    /**
     * Returns an active instance of a file storage
     *
     * @return an active instance of a DataFileStorage object
     */
    public DataFileStorage getDbFileStorage() {
        return dbService == null ? null : dbService.getDbFileStorage();
    }

    /**
     * Returns an active instance of shared preferences for current app
     *
     * @return an instance of SharedPreferences
     */
    public SharedPreferences getSettings() {
        return getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE);
    }

    /**
     * Generates a hardwareId of a device
     *
     * @return a string containing a hardwareId of current device
     */
    public String getHardwareId() {
        String hardwareId;
        String hardwareString = Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);

        try {
            hardwareId = TigerHelper.getInstance().getHexString(TigerHelper.getInstance().create(hardwareString));
        } catch (Exception e) {
            UniversalHelper.logException(e);
            hardwareId = hardwareString;
        }

        SharedPreferences.Editor editor = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE).edit();
        editor.putString(CustomValuesStorage.HARDWARE_STRING, hardwareString);
        editor.putString(CustomValuesStorage.HARDWARE_ID, hardwareId);
        editor.apply();

        return hardwareId;
    }

    /**
     * Validates the provided login
     *
     * @param resource a login to check
     * @return true if login matches all validation rules, returns false if not
     */
    public boolean loginIsCorrect(String resource) {
        boolean retVal = true;
        String login = resource;
        if (login == null) {
            login = "";
        }
        String result = login.trim();
        if (result.matches("^[a-zA-Z0-9-_\\.\\s]+$")) {
            if (result.startsWith(CustomValuesStorage.GROUP_DESCRIPTIOR)) {
                retVal = false;
            } else {
                if ((result.length() < 3) || (result.length() > 30)) {
                    retVal = false;
                }
            }
        } else {
            retVal = false;
        }
        return retVal;
    }

    /**
     * Sets the pin entered by user at startup
     *
     * @param pin a pin entered by user
     */
    public void setPin(String pin) {
        this.pin = pin;
    }

    private void deleteUserAccount() {
        if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
            clearApp();
        }
        finishApp();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void clearApp() {
        ((android.app.ActivityManager) this.getSystemService(ACTIVITY_SERVICE))
                .clearApplicationUserData();
    }

    /**
     * Returns the key used for encryption
     *
     * @return the current encryption key
     */
    public String getEncryptKey() {
        return pin;
    }

    /**
     * Shows an alert dialog when app needs to be locked because of login failure
     *
     * @param activity an active activity to show dialog
     */
    public void showAuthEpicFailDialog(final Activity activity) {
        if (activity != null) {
            final TsmMessageDialog errorMessage = new TsmMessageDialog(activity);
            errorMessage.setMessage(R.string.error_app_becomes_locked);
            errorMessage.setNeutralButton(R.string.btn_ok, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    errorMessage.dismiss();
                }
            });
            errorMessage.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    activity.finish();
                }
            });

            errorMessage.show();
        }
    }

    /**
     * Correctly finishes all processes and services connected to the app
     */
    public void finishApp() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        if (currentActivity != null) {
            currentActivity.finish();
        }
        stopService(new Intent(getApplicationContext(), SocketConnector.class));
        stopService(new Intent(getApplicationContext(), TsmDatabaseService.class));

        EdDsaSigner.getInstance().clearUserKeys();
        MessageQueue.getInstance().resetQueue();
        MessageQueue.getInstance().stopMessageSender();
        TsmDatabaseHelper.destroyInstance();
        EdDsaSigner.destroyInstance();

        MessageQueue.destroyInstance();
        pin = null;
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    /**
     * Returns an active object implementing the FileProgressListener interface
     *
     * @return an object if current FileProgressListener implementation is active,
     * returns null if not
     */
    public FileProgressListener getFileProgressListener() {
        return fileProgressListener;
    }

    /**
     * Sets a new active object implementing the FileProgressListener interface
     *
     * @param fileProgressListener an instance to set
     */
    public void setFileProgressListener(FileProgressListener fileProgressListener) {
        this.fileProgressListener = fileProgressListener;
    }

    /**
     * Sets the global app online indicator to an offline state
     */
    public void showToggleOffline() {
        this.isOnline = false;
    }

    /**
     * Sets the global app online indicator to an online state
     */
    public void showToggleOnline() {
        this.isOnline = true;
    }

    /**
     * Gets the online state of an app
     *
     * @return true if app is online, returns false if not
     */
    public boolean isOnline() {
        return isOnline;
    }

    /**
     * Sends a get-participant-key request to the server
     *
     * @param participants a list of participants whose keys are needed to receive
     */
    public void requestParticipantsPublicKey(List<String> participants) {

        participants.add(getSettings().getString(SharedPreferencesAccessor.USER_ID, ""));

        Intent bgSrv = new Intent(this, TsmBackgroundService.class);
        bgSrv.putExtra(ServiceParameters.BACKGROUNDACTION, ServiceParameters.BackgroundTask.USERKEY);
        bgSrv.putExtra(ServiceParameters.USER_LIST, (ArrayList<String>) participants);
        ActivityGlobalManager.getInstance().startService(bgSrv);

    }

    /**
     * Gets the set of chats waiting for an INVITE response
     *
     * @return a set of chats for invite
     */
    public Set<Integer> getChatsForInvite() {
        return chatsForInvite;
    }

    /**
     * Gets the id of a current active chat
     *
     * @return an integer id of an active chat
     */
    public Integer getCurrentChatId() {
        return currentChatId;
    }

    /**
     * Sets the id of a current active chat
     *
     * @param currentChatId the new active chat ID
     */
    public void setCurrentChatId(Integer currentChatId) {
        this.currentChatId = currentChatId;
    }

    /**
     * Gets an adapter containing chat history of an active chat
     *
     * @return a current active instance of a chat history adapter
     */
    public ChatHistoryActivity.RecordAdapter getCurrentAdapter() {
        return currentAdapter;
    }

    /**
     * Sets an adapter containing chat history of an active chat
     *
     * @param currentAdapter a new active chat history adapter
     */
    public void setCurrentAdapter(ChatHistoryActivity.RecordAdapter currentAdapter) {
        this.currentAdapter = currentAdapter;
    }
}
