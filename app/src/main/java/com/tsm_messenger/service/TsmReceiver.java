package com.tsm_messenger.service;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import com.tsm_messenger.activities.control.TsmNotification;
import com.tsm_messenger.activities.service.BroadcastMessages;
import com.tsm_messenger.connection.MessagePostman;
import com.tsm_messenger.data.storage.DataAddressBook;
import com.tsm_messenger.data.storage.DbMessengerUser;
import com.tsm_messenger.protocol.transaction.Request;
import com.tsm_messenger.protocol.transaction.Response;

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

public class TsmReceiver extends BroadcastReceiver {
    static TsmReceiver instance;

    /**
     * A constructor of TsmReceiver recording current instance as a class member
     */
    public TsmReceiver() {
        instance = this;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (UniversalHelper.checkNotNull(2, context, intent)) {
            NotificationManager mNotificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (BroadcastMessages.NTF_REQUEST_CONTACT_SENT.equals(intent.getAction())) {
                mNotificationManager.cancel(TsmNotification.NEWREQUEST_ID);
                return;
            }
            if (BroadcastMessages.NTF_NEW_CONTACT.equals(intent.getAction())) {
                mNotificationManager.cancel(TsmNotification.NEWCONTACT_ID);
                return;
            }

            if (BroadcastMessages.NTF_REQUEST_CONTACT_ACCEPT.equals(intent.getAction()) ||
                    BroadcastMessages.NTF_REQUEST_CONTACT_DECLINE.equals(intent.getAction())) {
                String result = intent.getStringExtra(BroadcastMessages.NotificationParam.ACTION);
                String participant = intent.getStringExtra(Response.SendNotificationResponse.SENDER_ID);
                DataAddressBook dbAddr = ((ActivityGlobalManager) context.getApplicationContext()).getDbContact();
                if (UniversalHelper.checkNotNull(3, result, participant, dbAddr)) {
                    DbMessengerUser invitor = dbAddr.getMessengerDb().get(participant);
                    if (result.equals(Request.AnswerNotificationRequest.AnwerType.ACCEPT)) {
                        invitor.setDbStatus(CustomValuesStorage.CATEGORY_CONNECT);
                    } else {
                        invitor.setDbStatus(CustomValuesStorage.CATEGORY_NOTCONNECT);
                    }

                    MessagePostman.getInstance().sendAnswerNotificationMessage(participant, result);
                    dbAddr.saveMessengerUser(invitor);
                }
                mNotificationManager.cancel(TsmNotification.NEWREQUEST_ID);

                Intent wsResult = new Intent(BroadcastMessages.WS_CONTACT_STATUS);
                Bundle param = new Bundle();
                param.putString(Response.AnswerNotificationResponse.SENDER_ID, participant);
                wsResult.putExtra(BroadcastMessages.WS_PARAM, param);

                LocalBroadcastManager.getInstance(context).sendBroadcast(wsResult);
            }
            if (intent.getAction().equals(BroadcastMessages.NTF_NEW_MESSAGE)) {

                int chatId = intent.getIntExtra(Response.MessageResponse.CHAT_ID, 0);

                mNotificationManager.cancel(TsmNotification.NEWMESSAGE_ID + chatId);
            }
            if (intent.getAction().equals(BroadcastMessages.NTF_ANSWER_REQUEST_CONTACT)) {
                mNotificationManager.cancel(TsmNotification.NEWANSWER_ID);
            }
        }
    }
}
