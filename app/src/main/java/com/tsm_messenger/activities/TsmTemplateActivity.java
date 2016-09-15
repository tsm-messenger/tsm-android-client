package com.tsm_messenger.activities;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.view.View;
import android.view.WindowManager;
import android.widget.RemoteViews;

import com.tsm_messenger.activities.control.TsmMessageDialog;
import com.tsm_messenger.activities.control.TsmNotification;
import com.tsm_messenger.activities.main.MainActivity;
import com.tsm_messenger.activities.main.chat.ChatHistoryActivity;
import com.tsm_messenger.activities.registration.TsmSignInActivity;
import com.tsm_messenger.activities.service.BroadcastMessages;
import com.tsm_messenger.activities.service.TsmDatabaseService;
import com.tsm_messenger.connection.IncomingMessageAsyncReceiver;
import com.tsm_messenger.data.storage.ChatUnit;
import com.tsm_messenger.data.storage.DataChatHistory;
import com.tsm_messenger.data.storage.DataFileStorage;
import com.tsm_messenger.data.storage.DbChatMessage;
import com.tsm_messenger.data.storage.DbMessengerUser;
import com.tsm_messenger.data.storage.FileData;
import com.tsm_messenger.protocol.transaction.Operation;
import com.tsm_messenger.protocol.transaction.Request;
import com.tsm_messenger.protocol.transaction.Response;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.BeepManager;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.TsmReceiver;
import com.tsm_messenger.service.UniversalHelper;

import java.util.ArrayList;

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

public class TsmTemplateActivity extends AppCompatActivity implements ServiceConnection {

    protected BroadcastReceiver broadcastReceiver;
    protected TsmDatabaseService dbService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (((ActivityGlobalManager) getApplication()).getEncryptKey() == null) {
            Intent loginActivity = new Intent(this, TsmSignInActivity.class);
            this.finish();
            startActivity(loginActivity);
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        unbindService(this);
        clearReferences();
    }

    @Override
    protected void onStart() {
        super.onStart();
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onReceiveBroadcast(intent);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent(this, TsmDatabaseService.class);
        bindService(intent, this, Context.BIND_AUTO_CREATE);
        UniversalHelper.setAppbarColor(((ActivityGlobalManager) getApplication()).isOnline(), this);
        onSubscribeBroadcastMessage();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {

        if (service instanceof TsmDatabaseService.LocalBinder) {
            dbService = ((TsmDatabaseService.LocalBinder) service).getService();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        dbService = null;
    }


    /**
     * Subscribes an activity for custom broadcast messages
     */
    protected void onSubscribeBroadcastMessage() {
        IntentFilter bcFilter = new IntentFilter(BroadcastMessages.WS_NET_STATE);
        bcFilter.addAction(BroadcastMessages.WS_FILERECEIVE + BroadcastMessages.UI_BROADCAST);
        bcFilter.addAction(BroadcastMessages.WS_FILEANSWER);
        bcFilter.addAction(BroadcastMessages.WS_NEWMESSAGE);
        bcFilter.addAction(BroadcastMessages.WS_ERROR);
        bcFilter.addAction(BroadcastMessages.WS_SHOW_LOGIN_TIME);
        bcFilter.addAction(BroadcastMessages.WS_DELETE_RELATION);
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.DELETE_USER_ACCOUNT));
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.SEND_NOTIFICATION));
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.ANSWER_NOTIFICATION));
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.INVITE) + BroadcastMessages.UI_BROADCAST);
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.AUTHORIZE) + BroadcastMessages.UI_BROADCAST);
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.NEW_CONTACT) + BroadcastMessages.UI_BROADCAST);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver,
                bcFilter);

        ((ActivityGlobalManager) getApplication()).setCurrentActivity(this);
    }

    private void clearReferences() {
        Activity currActivity = ((ActivityGlobalManager) getApplication()).getCurrentActivity();
        if (this.equals(currActivity))
            ((ActivityGlobalManager) getApplication()).setCurrentActivity(null);
    }

    /**
     * Processes the received broadcast
     *
     * @param intent an intent received by broadcast
     * @return returns true if broadcast was processed successfully
     */
    protected boolean onReceiveBroadcast(Intent intent) {
        boolean result = true;
        Bundle bundle = intent.getBundleExtra(BroadcastMessages.WS_PARAM);

        switch (intent.getAction()) {
            case BroadcastMessages.WS_SHOW_LOGIN_TIME:
                showLastLogin(bundle);
                break;
            case BroadcastMessages.WS_ERROR:
                showErrorMessage(bundle);
                break;
            case BroadcastMessages.WS_NEWMESSAGE:
                showNewMessageNotification(bundle);
                break;
            case BroadcastMessages.WS_NET_STATE:
                int newState = bundle.getInt(BroadcastMessages.WS_NET_STATE_VAL);
                UniversalHelper.setAppbarColor(newState == BroadcastMessages.ConnectionState.ONLINE, this);
                break;
            case BroadcastMessages.WS_FILERECEIVE + BroadcastMessages.UI_BROADCAST:
                showIncomingFile(bundle);
                break;
            case BroadcastMessages.WS_FILEANSWER:
                showIncomingFile(bundle);
                break;
            case BroadcastMessages.WS_DELETE_RELATION:
                removeRequestNotification();
                break;
            default:
                result = processBroadcastOperation(intent.getAction(), bundle);
        }

        return result;
    }

    private void removeRequestNotification() {
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(TsmNotification.NEWREQUEST_ID);
    }

    private boolean processBroadcastOperation(String action, Bundle bundle) {

        if (action.equals(BroadcastMessages.getBroadcastOperation(Operation.ANSWER_NOTIFICATION)
                + BroadcastMessages.UI_BROADCAST)) {

            showNewContactResult(bundle);
            return true;

        } else if (action.equals(BroadcastMessages.getBroadcastOperation(Operation.ANSWER_NOTIFICATION))) {

            showNotificationAnswerRequest(bundle);
            return true;

        } else if (action.equals(BroadcastMessages.getBroadcastOperation(Operation.SEND_NOTIFICATION))) {

            showSendNotificationResult(bundle);
            return true;

        } else {
            return false;
        }
    }

    private void showIncomingFile(Bundle bundle) {
        String fileId = bundle.getString(Response.FileReceiveResponse.FILE_ID);
        Integer chatId = bundle.getInt(Response.FileReceiveResponse.CHAT_ID);
        ActivityGlobalManager app = (ActivityGlobalManager) getApplicationContext();
        ChatUnit chat = app.getDbChatHistory().getChat(chatId);
        FileData file = app.getDbFileStorage().get(fileId);
        if (file != null) {
            DbChatMessage message = file.getMessage();

            DbMessengerUser user = app.getDbContact().getMessengerDb().get(message.getLogin());
            String sender = user.getDisplayName();
            if (user.getDbStatus() == CustomValuesStorage.CATEGORY_UNKNOWN) {
                sender += " (" + getString(R.string.lbl_chat) + " " + chat.getChatName() + ")";
            }
            UniversalHelper.showFileIncomingDialog(sender, file, chat, message, this);
        }
    }

    private void showSendNotificationResult(Bundle bundle) {
        if (bundle.getInt(Response.SendNotificationResponse.TYPE)
                == CustomValuesStorage.CATEGORY_CONFIRM_OUT) {
            showNotificationRequestResult(bundle);
        } else {
            showNotificationRequest(bundle);
        }
    }

    private void showNewMessageNotification(Bundle bundle) {
        boolean notificationShow = bundle.getBoolean(BroadcastMessages.MessagesParam.SHOW_NOTIFICATION);
        if (notificationShow) {
            showNotificationMessage(bundle);

            BeepManager.getInstance(this).vibrate();
        }
    }

    private void showLastLogin(Bundle bundle) {
        String lastLoginTime = bundle.getString(Response.InitSessionResponse.LAST_LOGIN_TIME);
        Boolean currentDevice = bundle.getBoolean(Response.InitSessionResponse.CURRENT_DEVICE);

        String message;
        if (currentDevice) {
            message = String.format(getString(R.string.info_last_login_own_device), lastLoginTime);
        } else {
            message = String.format(getString(R.string.info_last_login_other_device), lastLoginTime);
        }
        UniversalHelper.showSnackBar(getWindow().getDecorView().getRootView(), this, message);
        bundle.putString(IncomingMessageAsyncReceiver.MessageParam.MESSAGE,
                BroadcastMessages.WS_CONTACT_STATUS);
        Intent wsResult = new Intent(BroadcastMessages.WS_CONTACT_STATUS);
        wsResult.putExtra(BroadcastMessages.WS_PARAM, bundle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(wsResult);
    }

    private void showErrorMessage(Bundle bundle) {
        Response.BaseResponse.ErrorCode errCode;
        errCode = getErrorCode(bundle);

        final TsmMessageDialog errorMessage = new TsmMessageDialog(this);
        errorMessage.setTitle(R.string.title_error);
        View.OnClickListener btnOkListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                errorMessage.dismiss();
            }
        };
        if (errCode == null) {
            errorMessage.setMessage(R.string.error_smth_wrong);
        } else {

            switch (errCode) {
                case USER_IS_NOT_CHAT_PARTICIPANT:
                    deleteInvalidChat(bundle);
                    return;
                case LOW_VERSION:
                    errorMessage.setMessage(R.string.error_low_version);
                    Intent message = new Intent(BroadcastMessages.CONNECT_PROHIBITED);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(message);
                    break;
                case USER_ALREADY_ONLINE:
                    errorMessage.setMessage(R.string.error_user_already_online);
                    break;
                case INCORRECT_USER_KEY:
                    btnOkListener = prepareIncorrectUserKeyMessage(errorMessage);
                    break;
                case USER_NOT_FOUND:
                    errorMessage.setMessage(R.string.error_login_outdated);
                    break;
                case NOTIFICATION_REQUEST_NOT_FOUND:
                    errorMessage.setMessage(R.string.error_request_not_found);
                    break;
                default:
                    processRareErrors(errCode, errorMessage);
            }
        }
        errorMessage.setNeutralButton(R.string.btn_ok, btnOkListener);
        errorMessage.show();
    }

    private void deleteInvalidChat(Bundle bundle) {
        Integer chatId = bundle.getInt(Response.InvitationResponse.CHAT_ID);
        ActivityGlobalManager activityManager = (ActivityGlobalManager) getApplication();
        DataChatHistory dbChatHistory = activityManager.getDbChatHistory();
        DataFileStorage dbFileStorage = activityManager.getDbFileStorage();
        for (FileData file : dbFileStorage.getAdapterList()) {
            if (Integer.valueOf(file.getChatId()).equals(chatId)) {
                UniversalHelper.sendFileDeclineRequest(file, file.getFileSize());
            }
        }
        ChatUnit chatToLeave = dbChatHistory.getChat(chatId);
        if (chatToLeave != null) {
            String unitId = chatToLeave.getUnitId();
            if (unitId != null) {
                activityManager.getDbContact().deleteGroupChat(unitId);
            }
        }
        dbChatHistory.dropChat(chatId);
        if (activityManager.getCurrentActivity() instanceof MainActivity) {
            ((MainActivity) activityManager.getCurrentActivity()).refreshChatList();
        }
    }

    private Response.BaseResponse.ErrorCode getErrorCode(Bundle bundle) {
        Response.BaseResponse.ErrorCode errCode;
        try {
            String errorCode = bundle.getString(Response.BaseResponse.ERROR_CODE);
            errCode = Response.BaseResponse.ErrorCode.valueOf(errorCode);
        } catch (Exception e) {
            errCode = Response.BaseResponse.ErrorCode.SERVER_ERROR;
            UniversalHelper.logException(e);
        }
        return errCode;
    }

    private void processRareErrors(Response.BaseResponse.ErrorCode errCode,
                                   TsmMessageDialog errorMessage) {
        switch (errCode) {
            case FILE_TOO_LARGE:
                errorMessage.setMessage(R.string.error_file_too_large);
                break;
            case FILE_ACCEPTOR_ONLINE_ERROR:
                errorMessage.setMessage(R.string.error_participant_offline);
                break;
            case CHUNK_READ_ERROR:
            case CHUNK_TIMEOUT_ERROR:
            case FILE_SENDER_ONLINE_ERROR:
            case FILE_TIMEOUT_ERROR:
            case CHUNK_SIZE_ERROR:
            case CHUNK_WRITE_ERROR:
            case CHUNK_MODE_ERROR:
            case CHUNK_NUMBER_ERROR:
                errorMessage.setMessage(R.string.error_file_download);
                break;
            case TOO_MANY_PARTICIPANTS:
                errorMessage.setMessage(R.string.error_too_many_participants);
                break;
            case TOO_FEW_PARTICIPANTS:
                errorMessage.setMessage(R.string.error_too_few_parameter);
                break;
            default:
                processServerErrors(errCode, errorMessage);
        }
    }

    private void processServerErrors(Response.BaseResponse.ErrorCode errCode, TsmMessageDialog errorMessage) {
        switch (errCode) {
            case TOO_FEW_PARAMETERS:
                errorMessage.setMessage(R.string.error_too_few_parameter);
                break;
            case SERVER_ERROR:
                errorMessage.setMessage(R.string.error_transact_processing);
                break;
            case CHAT_HAVE_NOT_HISTORY:
                errorMessage.setMessage(R.string.error_chat_has_not_have_history);
                break;
            default:
                errorMessage.setMessage(R.string.error_smth_wrong);
        }
    }

    @NonNull
    private View.OnClickListener prepareIncorrectUserKeyMessage(final TsmMessageDialog errorMessage) {
        View.OnClickListener btnOkListener;
        errorMessage.setMessage(R.string.error_user_private_key_invalid);
        btnOkListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                errorMessage.dismiss();
                TsmTemplateActivity.this.finish();
            }
        };
        return btnOkListener;
    }

    private void showNotificationRequest(Bundle bundle) {
        Intent yesReceive = new Intent(this, TsmReceiver.class);
        yesReceive.setAction(BroadcastMessages.NTF_REQUEST_CONTACT_ACCEPT);
        yesReceive.putExtra(BroadcastMessages.NotificationParam.ACTION, Request.AnswerNotificationRequest.AnwerType.ACCEPT);
        yesReceive.putExtra(Response.SendNotificationResponse.SENDER,
                bundle.getString(Response.SendNotificationResponse.SENDER));
        yesReceive.putExtra(Response.SendNotificationResponse.SENDER_ID,
                bundle.getString(Response.SendNotificationResponse.SENDER_ID));

        PendingIntent pendingIntentYes = PendingIntent.getBroadcast(this, TsmNotification.NEWREQUEST_ID, yesReceive, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent noReceive = new Intent(this, TsmReceiver.class);
        noReceive.setAction(BroadcastMessages.NTF_REQUEST_CONTACT_DECLINE);

        noReceive.putExtra(BroadcastMessages.NotificationParam.ACTION, Request.AnswerNotificationRequest.AnwerType.DECLINE);
        noReceive.putExtra(Response.SendNotificationResponse.SENDER,
                bundle.getString(Response.SendNotificationResponse.SENDER));
        noReceive.putExtra(Response.SendNotificationResponse.SENDER_ID,
                bundle.getString(Response.SendNotificationResponse.SENDER_ID));

        PendingIntent pendingIntentNo = PendingIntent.getBroadcast(this, TsmNotification.NEWREQUEST_ID, noReceive, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder hh = new NotificationCompat.Builder(this);
        hh.setContentTitle(getString(R.string.title_contact_request_pending));
        hh.setContentText(getString(R.string.info_contact_request_pending));
        hh.setSmallIcon(R.drawable.ic_info_small);
        hh.setAutoCancel(true);
        Notification note1 = hh.build();
        RemoteViews rv = new RemoteViews(this.getPackageName(),
                R.layout.notification_requestcontact);

        rv.setTextViewText(R.id.notification_pers, getString(R.string.lbl_request_from) + bundle.getString(Response.SendNotificationResponse.SENDER));
        rv.setOnClickPendingIntent(R.id.notification_accept, pendingIntentYes);
        rv.setOnClickPendingIntent(R.id.notification_decline, pendingIntentNo);

        note1.contentView = rv;
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(TsmNotification.NEWREQUEST_ID, note1);
    }

    private void showNotificationRequestResult(Bundle bundle) {
        NotificationCompat.Builder hh = new NotificationCompat.Builder(this);

        ArrayList<String> requestOk = bundle.getStringArrayList(Response.SendNotificationResponse.ACCEPTORS);
        ArrayList<String> requestErr = bundle.getStringArrayList(Response.SendNotificationResponse.ACCEPTORS_NOT_SEND);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);


        if ((requestOk != null) && (!requestOk.isEmpty())) {
            if (requestOk.size() == 1) {
                hh.setContentTitle(getString(R.string.title_request_sent));
                hh.setContentText(String.format(getString(R.string.info_request_sent_concrete),
                        requestOk.get(0)));
            } else {
                hh.setContentTitle(getString(R.string.title_requests_sent));
                String requestOkStr = requestOk.get(0);
                for (int i = 1; i < requestOk.size(); i++) {
                    requestOkStr += ", " + requestOk.get(i);
                }
                hh.setContentText(String.format(getString(R.string.info_request_sent_concrete),
                        requestOkStr));
            }
            hh.setSmallIcon(R.drawable.ic_info_small);
            hh.setAutoCancel(true);
        }

        if ((requestErr != null) && (!requestErr.isEmpty())) {
            hh.setContentTitle(getString(R.string.title_request_send_error));
            if (requestErr.size() == 1) {
                hh.setContentText(String.format(getString(R.string.info_request_not_sent_concrete),
                        requestErr.get(0)));
            } else {
                String requestOkStr = requestErr.get(0);
                for (int i = 1; i < requestErr.size(); i++) {
                    requestOkStr += ", " + requestErr.get(i);
                }
                hh.setContentText(String.format(getString(R.string.info_request_not_sent_concrete),
                        requestOkStr));
            }
            hh.setSmallIcon(R.drawable.ic_info_small);
            hh.setAutoCancel(true);
        }
        Intent msgReceive = new Intent(this, TsmReceiver.class);
        msgReceive.setAction(BroadcastMessages.NTF_REQUEST_CONTACT_SENT);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, TsmNotification.NEWINFORM_ID, msgReceive, PendingIntent.FLAG_UPDATE_CURRENT);
        hh.setContentIntent(pendingIntent);


        Notification note1 = hh.build();
        mNotificationManager.notify(TsmNotification.NEWINFORM_ID, note1);
    }

    private void showNotificationMessage(Bundle bundle) {

        Intent msgReceive = new Intent(this, ChatHistoryActivity.class);
        msgReceive.setAction(BroadcastMessages.NTF_NEW_MESSAGE);
        Integer chatId = bundle.getInt(Response.MessageResponse.CHAT_ID, 0);
        msgReceive.putExtra(Response.MessageResponse.CHAT_ID, chatId);

        PendingIntent pendingIntentMsg = PendingIntent.getActivity(this, TsmNotification.NEWMESSAGE_ID, msgReceive, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder hh = new NotificationCompat.Builder(this);

        int unreadMsg = bundle.getInt(BroadcastMessages.MessagesParam.UNREAD);
        String chatName = bundle.getString(BroadcastMessages.MessagesParam.CHAT_NAME);

        String responseType = bundle.getString(Response.MessageResponse.RESPONSETYPE);
        String detail;
        if (responseType == null) {
            return;
        }
        if (responseType.equals(Response.MessageResponse.ResponseType.CONFIRM)) {
            hh.setContentTitle(getString(R.string.notification_messages_sent));
            detail = getString(R.string.notification_new_messages_has_been_sent) + "  " + chatName;
        } else {
            hh.setContentTitle(getString(R.string.notification_new_messages));
            detail = String.format(getString(R.string.notification_new_messages_in_chat), unreadMsg) + " " + chatName;
        }
        hh.setContentText(detail);
        hh.setSmallIcon(R.drawable.ic_info_small);
        hh.setAutoCancel(true);
        hh.setContentIntent(pendingIntentMsg);
        Notification note1 = hh.build();

        NotificationManager mNotificationManager =
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(TsmNotification.NEWMESSAGE_ID + chatId, note1);


    }

    private void showNotificationAnswerRequest(Bundle bundle) {
        String userName = bundle.getString(Response.AnswerNotificationResponse.SENDER);
        String answer = bundle.getString(Response.AnswerNotificationResponse.ANSWER);
        if (bundle.getInt(Response.SendNotificationResponse.TYPE, 0) == CustomValuesStorage.CATEGORY_CONFIRM_OUT) {
            NotificationManager mNotificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.cancel(TsmNotification.NEWREQUEST_ID);
            return;
        }
        Intent msgReceive = new Intent(this, TsmReceiver.class);
        msgReceive.setAction(BroadcastMessages.NTF_ANSWER_REQUEST_CONTACT);

        PendingIntent pendingIntentMsg = PendingIntent.getBroadcast(this, TsmNotification.NEWANSWER_ID, msgReceive, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder hh = new NotificationCompat.Builder(this);
        hh.setContentTitle(getString(R.string.notification_new_answer_title));

        if (answer == null) {
            return;
        }
        if (answer.equals(Response.AnswerNotificationResponse.AnwerType.ACCEPT)) {
            hh.setContentText(String.format(getString(R.string.notification_new_answer_accept), userName));
        } else {
            hh.setContentText(String.format(getString(R.string.notification_new_answer_decline), userName));
        }

        hh.setSmallIcon(R.drawable.ic_info_small);
        hh.setAutoCancel(true);
        hh.setContentIntent(pendingIntentMsg);

        Notification note1 = hh.build();

        NotificationManager mNotificationManager =
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(TsmNotification.NEWANSWER_ID, note1);


    }

    private void showNewContactResult(Bundle bundle) {
        NotificationCompat.Builder hh = new NotificationCompat.Builder(this);

        String result = bundle.getString(CustomValuesStorage.IntentExtras.INTENT_RESULT);
        String detail = bundle.getString(CustomValuesStorage.IntentExtras.INTENT_ERROR_TEXT);
        String user = bundle.getString(Response.NewContactResponse.ValuesType.PERS_IDENT);
        if (user == null) {
            user = " ?? ";
        }
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (result == null || result.equals(Response.BaseResponse.Result.OK)) {
            return;
        }
        hh.setContentTitle(getString(R.string.lbl_new_contact) + user);
        hh.setContentText(detail);
        hh.setSmallIcon(R.drawable.ic_info_small);
        hh.setAutoCancel(true);

        Intent msgReceive = new Intent(this, TsmReceiver.class);
        msgReceive.setAction(BroadcastMessages.NTF_NEW_CONTACT);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, TsmNotification.NEWCONTACT_ID, msgReceive, PendingIntent.FLAG_UPDATE_CURRENT);
        hh.setContentIntent(pendingIntent);

        Notification note1 = hh.build();
        mNotificationManager.notify(TsmNotification.NEWCONTACT_ID, note1);

    }
}
