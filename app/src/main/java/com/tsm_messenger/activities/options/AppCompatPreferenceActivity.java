package com.tsm_messenger.activities.options;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatDelegate;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import com.tsm_messenger.activities.service.BroadcastMessages;
import com.tsm_messenger.data.storage.ChatUnit;
import com.tsm_messenger.data.storage.DbChatMessage;
import com.tsm_messenger.data.storage.FileData;
import com.tsm_messenger.protocol.transaction.Operation;
import com.tsm_messenger.protocol.transaction.Response;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.UniversalHelper;

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

public abstract class AppCompatPreferenceActivity extends PreferenceActivity {

    private BroadcastReceiver broadcastReceiver;
    private AppCompatDelegate mDelegate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getDelegate().installViewFactory();
        getDelegate().onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        clearReferences();
        super.onPause();
    }

    @Override
    protected void onResume() {

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onReceiveBroadcast(intent);
            }
        };
        onSubscribeBroadcastMessage();
        super.onResume();
    }

    /**
     * Subscribes an activity for custom broadcast messages
     */
    void onSubscribeBroadcastMessage() {
        IntentFilter bcFilter = new IntentFilter(BroadcastMessages.WS_NET_STATE);
        bcFilter.addAction(BroadcastMessages.WS_FILERECEIVE);
        bcFilter.addAction(BroadcastMessages.WS_FILEANSWER);
        bcFilter.addAction(BroadcastMessages.WS_NEWMESSAGE);
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.SEND_NOTIFICATION));
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.ANSWER_NOTIFICATION));
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
     */
    void onReceiveBroadcast(Intent intent) {
        Bundle bundle = intent.getBundleExtra(BroadcastMessages.WS_PARAM);

        if (intent.getAction().equals(BroadcastMessages.WS_NET_STATE)) {
            int newState = bundle.getInt(BroadcastMessages.WS_NET_STATE_VAL);
            if (newState == BroadcastMessages.ConnectionState.ONLINE) {
                ((ActivityGlobalManager) getApplicationContext()).showToggleOnline();
            } else {
                ((ActivityGlobalManager) getApplicationContext()).showToggleOffline();
            }
            return;
        }
        if (intent.getAction().equals(BroadcastMessages.WS_FILERECEIVE)) {

            String fileId = bundle.getString(Response.FileReceiveResponse.FILE_ID);
            Integer chatId = bundle.getInt(Response.FileReceiveResponse.CHAT_ID);
            ActivityGlobalManager app = (ActivityGlobalManager) getApplicationContext();
            ChatUnit chat = app.getDbChatHistory().getChat(chatId);
            FileData file = app.getDbFileStorage().get(fileId);
            DbChatMessage message = file.getMessage();

            String sender = app.getDbContact().getMessengerDb().get(message.getLogin()).getDisplayName();
            UniversalHelper.showFileIncomingDialog(sender, file, chat, message, this);
            return;
        }
        if (intent.getAction().equals(BroadcastMessages.WS_FILEANSWER)) {

            String fileId = bundle.getString(Response.FileReceiveResponse.FILE_ID);
            Integer chatId = bundle.getInt(Response.FileReceiveResponse.CHAT_ID);
            ActivityGlobalManager app = (ActivityGlobalManager) getApplicationContext();
            ChatUnit chat = app.getDbChatHistory().getChat(chatId);
            FileData file = app.getDbFileStorage().get(fileId);
            DbChatMessage message = file.getMessage();

            String sender = app.getDbContact().getMessengerDb().get(message.getLogin()).getDisplayName();
            UniversalHelper.showFileIncomingDialog(sender, file, chat, message, this);
        }

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getDelegate().onPostCreate(savedInstanceState);
    }

    @Override
    @NonNull
    public MenuInflater getMenuInflater() {
        return getDelegate().getMenuInflater();
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        getDelegate().setContentView(layoutResID);
    }

    @Override
    public void setContentView(View view) {
        getDelegate().setContentView(view);
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        getDelegate().setContentView(view, params);
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        getDelegate().addContentView(view, params);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        getDelegate().onPostResume();
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        getDelegate().setTitle(title);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getDelegate().onConfigurationChanged(newConfig);
    }

    @Override
    protected void onStop() {
        super.onStop();
        getDelegate().onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getDelegate().onDestroy();
    }

    /**
     * calls an invalidateOptionsMenu for currently existing AppCompatDelegate object
     */
    public void invalidateOptionsMenu() {
        getDelegate().invalidateOptionsMenu();
    }

    private AppCompatDelegate getDelegate() {
        if (mDelegate == null) {
            mDelegate = AppCompatDelegate.create(this, null);
        }
        return mDelegate;
    }
}
