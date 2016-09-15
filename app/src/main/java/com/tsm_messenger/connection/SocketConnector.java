package com.tsm_messenger.connection;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;

import com.tsm_messenger.activities.service.BroadcastMessages;
import com.tsm_messenger.activities.service.ServiceParameters;
import com.tsm_messenger.activities.service.TsmBackgroundService;
import com.tsm_messenger.activities.service.TsmDatabaseService;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.TsmNetworkReceiver;
import com.tsm_messenger.service.UniversalHelper;

import java.util.Timer;
import java.util.TimerTask;

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

public class SocketConnector extends Service
        implements ServiceConnection, TsmNetworkReceiver.NetworkStateReceiverListener {


    private static final int SLEEP_TIME = 12000;
    private static final long RECONNECT_TIME = 1500L;
    private static final int KEY_DECRYPT_TIME = 300;
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private Timer disconnectTimer;
    private TimerTask currentDisconnectTask;
    private ClientThread client;
    private int currentConnectState;
    //Service implementation
    private NetworkClient networkClient;
    private boolean firstStart = false;
    private ReconnectTask reconnectRun;
    private boolean screenOff = false;
    private boolean networkAvailable = false;
    private TsmNetworkReceiver networkReceiver = new TsmNetworkReceiver();
    private BroadcastReceiver screenToggleReceiver;
    private BroadcastReceiver transactAddressReceiver;
    private boolean connectProhibited = true;
    private BroadcastReceiver connectStatusReceiver;

    public SocketConnector() {
        //do nothing
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        screenToggleReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                screenOff = intent.getAction().equals(Intent.ACTION_SCREEN_OFF)
                        || !intent.getAction().equals(Intent.ACTION_SCREEN_ON);

                if (screenOff) {
                    currentDisconnectTask = new DisconnectTimerTask();
                    disconnectTimer.schedule(currentDisconnectTask, SLEEP_TIME);

                } else {
                    if (currentDisconnectTask != null)
                        currentDisconnectTask.cancel();
                    if (disconnectTimer != null)
                        disconnectTimer.purge();
                    reconnectRun.setKill(false);
                    reconnect();
                }
            }
        };
        registerReceiver(screenToggleReceiver, filter);
        final IntentFilter networkFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, networkFilter);
        networkReceiver.setListener(this);
        IntentFilter connectStatusFilter = new IntentFilter(BroadcastMessages.CONNECT_PROHIBITED);
        connectStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                connectProhibited = true;
                reconnectRun.setKill(true);
                currentDisconnectTask = new DisconnectTimerTask();
                disconnectTimer.schedule(currentDisconnectTask, 0);
            }
        };
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(connectStatusReceiver, connectStatusFilter);
        disconnectTimer = new Timer("disconnectTimer");

        IntentFilter transactAddressFilter = new IntentFilter(ServiceParameters.TSM_BROADCAST_REGISTRATION);
        transactAddressReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String param = intent.getStringExtra(ServiceParameters.PARAM);
                try {
                    ActivityGlobalManager.setTransactURL(param.substring(0, param.indexOf(":")),
                            Integer.valueOf(param.substring(param.indexOf(":") + 1)));
                    initClient();
                } catch (Exception e) {
                    UniversalHelper.logException(e);
                    networkClient.onError();
                }
            }
        };
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(transactAddressReceiver, transactAddressFilter);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(screenToggleReceiver);
        networkReceiver.removeListener();
        unregisterReceiver(networkReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(connectStatusReceiver);
        unbindService(this);
        finishConnectionProcesses();
        MessageQueue.getInstance().destroyQueue();
        super.onDestroy();
    }


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        reconnectRun = new ReconnectTask();
        bindService(new Intent(this, TsmDatabaseService.class), this, BIND_AUTO_CREATE);
        currentConnectState = BroadcastMessages.ConnectionState.OFFLINE;
        networkClient = new NetworkClient();
        new Handler(Looper.myLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                connectProhibited = false;
                initClient();
            }
        }, KEY_DECRYPT_TIME);

        MessageQueue.getInstance().setConnector(this);
        return super.onStartCommand(intent, flags, startId);
    }

    private void initClient() {
        if (ActivityGlobalManager.getTransactionUrl() == null) {
            Intent bgSrv = new Intent(this, TsmBackgroundService.class);
            bgSrv.putExtra(ServiceParameters.BACKGROUNDACTION, ServiceParameters.BackgroundTask.TRANSACT_URL);
            startService(bgSrv);
            return;

        }

        client = new ClientThread(ActivityGlobalManager.getTransactionUrl(), ActivityGlobalManager.getTransactionPort(), networkClient);
        client.setDaemon(true);

        if (!client.isAlive() && client.getState() != Thread.State.RUNNABLE) {
            client.start();
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        //do nothing
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        //nothing to do

    }

    /**
     * Sets first start flag to true when application connects service the first time
     */
    public void setFirstStart() {
        this.firstStart = true;
    }

    /**
     * Sends message to the server
     *
     * @param message the string representation of current message
     */
    public void sendMessage(String message) {
        try {
            client.send(message);
        } catch (Exception e) {
            UniversalHelper.logException(e);
            networkClient.onError();
        }
    }

    /**
     * Sends connection status changes via broadcast manager
     *
     * @param newState the current connection status
     */
    private void switchState(int newState) {
        if (currentConnectState != newState) {
            currentConnectState = newState;
            Bundle param = new Bundle();
            Intent message = new Intent(BroadcastMessages.WS_NET_STATE);
            param.putInt(BroadcastMessages.WS_NET_STATE_VAL, newState);
            message.putExtra(BroadcastMessages.WS_PARAM, param);
            LocalBroadcastManager.getInstance(this).sendBroadcast(message);
        }
    }

    /**
     * Opens new connection to the server
     */
    public void reconnect() {
        if (currentConnectState == BroadcastMessages.ConnectionState.OFFLINE && !connectProhibited) {
            stopClient();
            initClient();
        }
    }

    /**
     * Closes the server connection
     */
    private void stopClient() {
        if (client != null) {
            client.shutdown();
            client = null;
        }
    }

    /**
     * Gets current status of socket connection
     *
     * @return true if the socket connection is not null
     */
    public boolean hasCorrectConnection() {
        return client != null && client.hasNotNullSocket();
    }

    /**
     * Starts disconnection process
     */
    public void finishConnectionProcesses() {
        stopClient();
    }

    /**
     * Sends an INIT_SESSION operation to the server
     *
     * @param token the temporary token, received from the server
     */
    public void sendInitSession(String token) {
        MessagePostman.getInstance().sendInitSessionRequest(token, firstStart);
        firstStart = false;
    }

    /**
     * Starts new online session
     */
    public void startSession() {
        switchState(BroadcastMessages.ConnectionState.ONLINE);
        if (client != null) {
            client.startLiveMessageSending();
        }
    }

    @Override
    public void networkAvailable() {
        if (!networkAvailable) {
            networkAvailable = true;
            if (!connectProhibited) {
                if (client == null) {
                    initClient();
                } else if (ActivityGlobalManager.getTransactionUrl() == null) {
                    reconnect();
                } else {
                    startRestoreConnection();
                }
            }
        }
    }

    @Override
    public void networkUnavailable() {
        if (networkAvailable) {
            networkAvailable = false;
            stopClient();
        }
    }

    /**
     * Restores connection if there are no conditions for prohibiting connection
     */
    public void startRestoreConnection() {
        if (screenOff || connectProhibited) return;
        try {
            reconnectRun.setKill(false);
            client.getmHandler().postDelayed(reconnectRun, RECONNECT_TIME);
        } catch (Exception e) {
            UniversalHelper.logException(e);
            reconnect();
        }
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public SocketConnector getService() {
            // Return this instance of LocalService so clients can call public methods
            return SocketConnector.this;
        }
    }

    /**
     * Class for the socket connection callback processing
     */
    public class NetworkClient {

        public void onOpen() {
            MessageQueue.getInstance().setConnected(true);
            MessagePostman.getInstance().sendGetTokenRequest();
            MessageQueue.getInstance().startMessageSender();
        }

        public void onMessage(final String message) {
            MessageQueue.getInstance().receiveMessage(message);
        }

        public void onClose() {
            switchState(BroadcastMessages.ConnectionState.OFFLINE);
            startRestoreConnection();
        }

        public void onError() {
            switchState(BroadcastMessages.ConnectionState.OFFLINE);
            startRestoreConnection();
        }

    }

    /**
     * Timer-task for disconnect when the server connection is broken
     */
    private class DisconnectTimerTask extends TimerTask {

        @Override
        public void run() {
            finishConnectionProcesses();
            switchState(BroadcastMessages.ConnectionState.OFFLINE);
        }
    }

    /**
     * Task for reconnect to the server
     */
    private class ReconnectTask implements Runnable {
        private final Object lock = new Object();
        private boolean isKill = false;

        public void setKill(boolean isKill) {
            synchronized (lock) {
                this.isKill = isKill;
            }
        }

        @Override
        public void run() {
            if (isKill) return;

            if (networkReceiver.checkConnection(SocketConnector.this)) {
                try {
                    synchronized (lock) {
                        isKill = true;
                        if (!screenOff) {
                            reconnect();
                        }
                        if (client != null) {
                            client.getmHandler().removeCallbacks(reconnectRun);
                        }
                    }
                }catch (OutOfMemoryError ou){
                    UniversalHelper.logException(ou);
                    runReconnect();
                }
            } else {
                runReconnect();
            }
        }

        private void runReconnect() {
            if (client != null) {
                client.getmHandler().postDelayed(reconnectRun, RECONNECT_TIME);
            }
        }
    }
}
