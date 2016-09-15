package com.tsm_messenger.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/*************************************************************************
 * TELESENS CONFIDENTIAL
 * __________________
 * <p>
 * [2014] Telesens International Limited
 * All Rights Reserved.
 * <p>
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

public class TsmNetworkReceiver extends BroadcastReceiver {
    private NetworkStateReceiverListener listener;

    /**
     * Sets a new instance of a listener to process events
     *
     * @param listener a new instance of a listener
     */
    public void setListener(NetworkStateReceiverListener listener) {
        this.listener = listener;
    }

    /**
     * Removes an instance of a listener to stop events processing
     */
    public void removeListener() {
        Thread.dumpStack();
        this.listener = null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null &&
                UniversalHelper.checkNotNull(2, context, intent.getExtras()) &&
                listener != null) {

            if (checkConnection(context)) {
                listener.networkAvailable();
            } else if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, Boolean.FALSE)) {
                listener.networkUnavailable();
            }
        }
    }

    /**
     * Gets the current network state
     *
     * @param context an active context of an app
     * @return true if network is active and connected, returns false if not
     */
    public boolean checkConnection(Context context) {
        if (context != null) {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = manager.getActiveNetworkInfo();
            return ni != null && ni.getState() == NetworkInfo.State.CONNECTED;
        } else {
            return false;
        }
    }

    /**
     * An interface to process events
     */
    public interface NetworkStateReceiverListener {
        void networkAvailable();

        void networkUnavailable();
    }
}
