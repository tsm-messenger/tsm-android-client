package com.tsm_messenger.connection;

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

import android.content.res.AssetManager;
import android.os.Handler;
import android.os.HandlerThread;

import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.UniversalHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;


class ClientThread extends Thread {
    private static final long LIVE_MESSAGE_PAUSE = 4000;
    private static final long SERVER_RESPONSE_TIME = 4500L;
    private static final String LIVE_MESSAGE = "lm";
    private static SocketConnector.NetworkClient networkClient;
    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private final String host;
    private final int port;
    Socket socket = null;
    LiveMessageSendTask liveMessageSendTask;
    private BufferedReader in;
    private PrintWriter out;
    private long lastMessageTime = 5000L;
    private volatile boolean isConnectionAlive = true;
    private long acceptedDelayTime = 8600L;
    private Timer liveMessageSendTimer;

    /**
     * A constructor for a ClientThread.
     * All timers and handlers are initialized
     *
     * @param host     the host for connection
     * @param port     the port for connection
     * @param listener the implementation of connection events listener for interaction
     */
    public ClientThread(String host, int port,
                        SocketConnector.NetworkClient listener) {
        super();
        this.host = host;
        this.port = port;
        networkClient = listener;
        this.acceptedDelayTime = LIVE_MESSAGE_PAUSE + SERVER_RESPONSE_TIME;
        mHandlerThread = new HandlerThread("websocket-thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        liveMessageSendTimer = new Timer("liveMessageSendTimer", true);
    }

    /**
     * This method is needed for all timers and listeners finishing in a right way
     */
    public void shutdown() {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                isConnectionAlive = false;
                try {
                    liveMessageSendTimer.cancel();
                    if (liveMessageSendTask != null) {
                        liveMessageSendTask.cancel();
                        liveMessageSendTask = null;
                    }
                } catch (Exception e) {
                    UniversalHelper.logException(e);
                }
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (Exception e) {
                    UniversalHelper.logException(e);
                }
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (Exception e) {
                    UniversalHelper.logException(e);
                }
                try {
                    if (socket != null) {
                        socket.close();
                        socket = null;
                    }
                } catch (Exception e) {
                    UniversalHelper.logException(e);
                } finally {
                    try {
                        interrupt();
                    } catch (Exception e) {
                        UniversalHelper.logException(e);
                    }
                }
            }
        });

    }

    /**
     * Sends a text message to the output stream
     *
     * @param message a text message to send
     */
    public void send(final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (out != null) {
                        out.println(message);
                    } else {
                        networkClient.onError();
                    }

                } catch (Exception e) {
                    UniversalHelper.logException(e);
                    networkClient.onError();
                }
            }
        });
    }


    private TrustManager[] initCert() {
        AssetManager assetManager = ActivityGlobalManager.getInstance().getAssets();
        TrustManager[] tm = null;
        try {
            InputStream caInput = assetManager.open("keystore_sert156_81.crt");
            Certificate ca;

            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                ca = cf.generateCertificate(caInput);
                String keyStoreType = KeyStore.getDefaultType();
                KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                keyStore.load(null, null);
                keyStore.setCertificateEntry("ca", ca);

// Create a TrustManager that trusts the CAs in our KeyStore
                String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
                tmf.init(keyStore);
                tm = tmf.getTrustManagers();
            } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException e) {
                UniversalHelper.logException(e);
            } finally {
                caInput.close();
            }

        } catch (IOException e) {
            UniversalHelper.logException(e);
        }
        return tm;
    }

    /**
     * In this method the socket is started
     * then output and input streams are opened for writing and reading
     */
    @Override
    public void run() {
        //initialize socket
        try {
            TrustManager[] trustAllCerts = initCert();
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            SSLSocketFactory sf = sc.getSocketFactory();

            socket = sf.createSocket(host, port);

            if (socket != null) {
                in = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                isConnectionAlive = true;
                networkClient.onOpen();
            } else {
                networkClient.onError();
                return;
            }
        } catch (Exception e) {
            UniversalHelper.logException(e);
            networkClient.onError();
            return;
        }

        try {
            // Process all messages from the server, according to the protocol.
            while (this.isConnectionAlive) {
                String input = in.readLine();
                if (input == null) {
                    shutdown();
                    networkClient.onClose();
                    return;
                }
                this.lastMessageTime = System.currentTimeMillis();
                if (!input.equals(LIVE_MESSAGE)) {
                    networkClient.onMessage(input);
                }
            }
        } catch (Exception e) {
            UniversalHelper.logException(e);
            networkClient.onError();
        }
    }

    /**
     * starts a timerTask for a periodical ping
     * liveMessages are needed to monitor the connection alive state
     */
    public void startLiveMessageSending() {

        if (liveMessageSendTask == null) {
            liveMessageSendTask = new LiveMessageSendTask(this);
        }
        lastMessageTime = System.currentTimeMillis();
        try {
            liveMessageSendTimer.schedule(liveMessageSendTask, SERVER_RESPONSE_TIME, LIVE_MESSAGE_PAUSE);
        } catch (IllegalStateException st) {
            UniversalHelper.logException(st);
            liveMessageSendTask.cancel();
            liveMessageSendTimer.cancel();
            liveMessageSendTimer.purge();
            liveMessageSendTimer = new Timer("liveMessageSendTimer", true);
            liveMessageSendTask = new LiveMessageSendTask(this);
            liveMessageSendTimer.schedule(liveMessageSendTask, SERVER_RESPONSE_TIME, LIVE_MESSAGE_PAUSE);
        }
    }

    /**
     * Returns the current handler for the HandlerThread
     *
     * @return the current handler
     */
    public Handler getmHandler() {
        return mHandler;
    }

    /**
     * returns the time of the last the server message receiving in milliseconds
     *
     * @return the last message receive time in milliseconds
     */
    private long getLastMessageTime() {
        return lastMessageTime;
    }

    /**
     * Shows if the socket for current thread is alive and not null
     *
     * @return true if socket is not null
     */
    public boolean hasNotNullSocket() {
        return socket != null;
    }

    private static class LiveMessageSendTask extends TimerTask {

        private final ClientThread baseClient;

        public LiveMessageSendTask(ClientThread baseClient) {
            this.baseClient = baseClient;
        }

        @Override
        public void run() {
            if (baseClient.isConnectionAlive) {
                try {
                    if ((System.currentTimeMillis() - baseClient.getLastMessageTime()) > baseClient.acceptedDelayTime) {
                        disconnectByTimeout();
                    }
                    baseClient.send(ClientThread.LIVE_MESSAGE);
                } catch (Exception e) {
                    UniversalHelper.logException(e);
                    if (networkClient != null) {
                        networkClient.onError();
                    }
                }
            } else {
                networkClient.onClose();
            }
        }

        private void disconnectByTimeout() {
            if (networkClient != null) {
                networkClient.onClose();
            }
            baseClient.shutdown();
        }
    }

    private class CustomTrustManager implements X509TrustManager {
        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
            //No need to implement.
        }

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
            //No need to implement.
        }
    }

}