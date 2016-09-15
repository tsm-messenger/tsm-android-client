package com.tsm_messenger.connection;

import android.util.Log;

import com.google.gson.Gson;
import com.tsm_messenger.crypto.EdDsaSigner;
import com.tsm_messenger.protocol.dto.DummyDto;
import com.tsm_messenger.protocol.transaction.Operation;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.service.UniversalHelper;

import org.bouncycastle.util.encoders.DecoderException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;


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
public class MessageQueue {

    public static final int PREINIT = 1;
    public static final int READY = 2;
    private static final long RESPONSE_TIMEOUT = 15L;
    private static final String MSG_SRV_INCOMING = "MSG_SRV_INCOMING";
    private static MessageQueue instance;
    private final PriorityBlockingQueue<OutcomingMessage> outQueueP = new PriorityBlockingQueue<>();
    private final LinkedBlockingQueue<String> incomingQueue = new LinkedBlockingQueue<>();
    private final IncomingMessageAsyncReceiver incomingReceiver = new IncomingMessageAsyncReceiver();
    private final PriorityBlockingQueue<IncomingMessage> serverIncomingQueue = new PriorityBlockingQueue<>();

    private SenderThread messageSender;
    private ReceiverThread serverReceiver;
    private int messageCounter = 0;
    private String lastMessageId = ""; // unique hash of last  sending MESSAGE
    private int sendCount = 0;
    private SocketConnector connectService;
    private boolean connected = false;
    private volatile int initSessionReady = PREINIT;

    private MessageQueue() {
    }

    /**
     * Gets current instance of MessageQueue
     *
     * @return the current instance of MessageQueue, or a new instance, if current is null
     */
    public static MessageQueue getInstance() {
        if (instance == null) {
            instance = new MessageQueue();
        }
        return instance;
    }


    /**
     * Turns current instance of MessageQueue to null
     */
    public static void destroyInstance() {
        instance = null;
    }

    /**
     * Changes status when application is ready for send message to the server
     *
     * @param status can be MessageQueue.READY or MessageQueue.PREINIT
     *               Set status MessageQueue.READY  where response INIT_SESSION operation from the server
     */
    public void setInitSessionReady(int status) {
        this.initSessionReady = status;
    }

    /**
     * Resets outcoming messages queue
     */
    public void resetQueue() {
        outQueueP.clear();
        lastMessageId = "";
        sendCount = 1;
    }

    /**
     * Gets a connect status of an app
     *
     * @return true if app has correct connection state
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Sets a status of the server connection
     *
     * @param connected new state of the server connection. true if app is connected now, false if not
     */
    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    /**
     * Starts task for messages sending from outcoming queue
     */
    public void startMessageSender() {
        if (messageSender == null) {
            messageSender = new SenderThread();
            connected = true;
            messageSender.start();
        }
    }

    /**
     * Starts task for incoming messages parsing
     */
    public void startServerQueue() {
        if (serverReceiver == null) {
            serverReceiver = new ReceiverThread();

            serverReceiver.start();
        }
    }

    private OutcomingMessage generateOutomingMessage(String extMessageId, Map<String, Object> requestMap, Integer priority) {
        OutcomingMessage msg = new OutcomingMessage();
        String messageId;

        if (extMessageId == null) {
            try {
                messageId = "d:" + ++messageCounter;
            } catch (Exception e) {
                UniversalHelper.logException(e);
                messageId = "d:" + UUID.randomUUID().toString();
            }
        } else {
            messageId = extMessageId;
        }
        msg.setMessage(generateSignedDto(requestMap));
        msg.setMessageId(messageId);
        msg.getMessage().setId(messageId);
        msg.setPriority(priority);

        return msg;
    }

    private DummyDto generateSignedDto(Map<String, Object> requestMap) {
        DummyDto requestDto = new DummyDto();
        requestDto.setParams(requestMap);
        Gson gson = new Gson();
        String tmpJson = gson.toJson(requestDto.getParams());
        String signature = EdDsaSigner.getInstance().sign(tmpJson);
        requestDto.setSignature(signature);

        return requestDto;
    }

    /**
     * Sets link to a connection service
     *
     * @param connector the current instance of SocketConnector connection service
     */
    public void setConnector(SocketConnector connector) {
        this.connectService = connector;
    }

    /**
     * Stops task which sends outcoming messages
     */
    public void stopMessageSender() {
        if (messageSender != null)
            messageSender.interrupt();
        connected = false;
        initSessionReady = PREINIT;
    }

    /**
     * Destroys queue when application is closed
     */
    public void destroyQueue() {
        stopMessageSender();
        outQueueP.clear();
    }

    /**
     * Adds  message to the out queue with default priority marker (PRIORITY_NORMAL)
     *
     * @param messageId an unique message id
     * @param outParam  the message body
     */
    public void sendMessage(String messageId, Map<String, Object> outParam) {
        sendMessage(messageId, outParam, OutcomingMessage.PRIORITY_NORMAL);
    }

    /**
     * Adds message to the out queue with a specific priority marker
     *
     * @param extMessageId an unique message ID
     * @param outParam     the message body
     * @param msgPriority  the priority marker
     * @return true if message is added to queue
     */
    public boolean sendMessage(String extMessageId, Map<String, Object> outParam, int msgPriority) {
        String messageId;
        DummyDto requestDto = null;
        OutcomingMessage outMsg = null;
        int cnt = 0;
        do {
            try {
                outMsg = generateOutomingMessage(extMessageId, outParam, msgPriority);
                requestDto = outMsg.getMessage();
            } catch (OutOfMemoryError ome) {
                cnt++;
                UniversalHelper.debugLog(Log.ERROR, "MSG_SRV", "!!!!!!!!!!!!!! OUT OF MEMORY !!!!  trying " + String.valueOf(cnt));
                UniversalHelper.logException(ome);

                if (cnt == 2)
                    return false;

            } catch (DecoderException de) {
                UniversalHelper.debugLog(Log.ERROR, "MSG_SRV", "can't encrypt MESSAGE");
                UniversalHelper.logException(de);
                return false;
            }
        } while (cnt > 1);

        if (outMsg == null || requestDto == null) {
            return false;
        }
        Gson gson = new Gson();
        messageId = requestDto.getId();
        if (messageId.isEmpty()
                || messageId.startsWith("d:000;")
                || messageId.startsWith("s:")) {
            sendImmediate(gson.toJson(requestDto));
            return true;
        } else {
            synchronized (this) {
                outQueueP.add(outMsg);
            }
        }
        return true;
    }

    private void sendImmediate(String msg) {
        if (connected && (connectService != null)) {
            UniversalHelper.debugLog(Log.DEBUG, "MSG_SRV PREP", "Send MESSAGE :" + msg);
            connectService.sendMessage(msg);
        } else {
            UniversalHelper.debugLog(Log.ERROR, "MSG_SRV PREP", "connected is " + (connected ? "true " : "false ") + "service = " + (connectService == null ? " NULL" : "OK"));
            if (connectService != null) {
                connectService.reconnect();
            }
        }
    }

    /**
     * Adds message ID to incoming queue, when the server returns a response for message from device
     *
     * @param messageId an unique message ID
     */
    public void addIncomingMessageId(String messageId) {
        incomingQueue.add(messageId);
        UniversalHelper.debugLog(Log.WARN, MSG_SRV_INCOMING, "add from PARSE addIncomingMessageId   MESSAGE MESSAGE with  " + messageId + "  total in q = " + incomingQueue.size());
    }

    /**
     * Parses incoming message from incoming message queue
     *
     * @param message a string representation of incoming message
     */
    public void receiveMessage(String message) {
        if (EdDsaSigner.getInstance().validateServerMessage(message)) {
            Gson gson = new Gson();
            UniversalHelper.debugLog(Log.WARN, "MSG_SRV RECEIVEMQ", "RECEIVE MESSAGE : " + message);
            DummyDto responseDto = gson.fromJson(message, DummyDto.class);
            Map<String, Object> responseMap = responseDto.getParams();

            Operation opCode;
            String opName;
            try {
                opName = (String) responseMap.get(Param.OPERATION);
                opCode = Operation.getByName(opName);
            } catch (ClassCastException cce) {
                UniversalHelper.logException(cce);
                opCode = Operation.LIVE_MESSAGE;
            }

            if (opCode != null) {
                if (serverReceiver == null) {
                    startServerQueue();
                }
                IncomingMessage msg = new IncomingMessage();
                msg.setMessage(message);
                if (responseDto.getId() != null && responseDto.getId().startsWith("d:")) {
                    msg.setPriority(IncomingMessage.PRIORITY_HIGH);
                } else {
                    msg.setPriority(IncomingMessage.PRIORITY_NORMAL);
                }
                msg.setMessageId(responseDto.getId() == null ? "e:" : responseDto.getId());
                serverIncomingQueue.put(msg);
            }
        } else {
            UniversalHelper.debugLog(Log.ERROR, "MSG_SRV Err", "Invalid signature! " + message);
        }
    }

    /**
     * A task for messages sending from the out queue
     */
    private class SenderThread extends Thread {
        int waitCounter;

        @Override
        public void run() {
            UniversalHelper.debugLog(Log.DEBUG, "MSG_THR", "start SenderThread");
            incomingQueue.clear();
            waitCounter = 0;
            while (!this.isInterrupted()) {
                if (!messageSentCorrectly()) {
                    break;
                }
            }
        }

        private boolean messageSentCorrectly() {
            boolean success = true;

            DummyDto requestDto;
            OutcomingMessage newMsg;
            try {
                newMsg = outQueueP.take();
                requestDto = newMsg.getMessage();
                if (newMsg.getPriority() != OutcomingMessage.PRIORITY_IMMEDIATE && initSessionReady != READY) {
                    newMsg.setPriority(OutcomingMessage.PRIORITY_HIGH);
                    outQueueP.add(newMsg);
                    UniversalHelper.debugLog(Log.DEBUG, "MSG_SRV WAIT", "Wait for init session response " + String.valueOf(waitCounter));
                    UniversalHelper.debugLog(Log.DEBUG, "MSG_SRV WAIT", newMsg.getMessageId());
                    if (waitCounter > 50) {
                        reconnectIfGetToken(newMsg);
                        UniversalHelper.debugLog(Log.DEBUG, "MSG_SRV WAIT", "Reconnect  ");
                    }
                    waitCounter++;

                    sleep(1000);
                } else {
                    waitCounter = 0;
                    if (requestDto != null) {
                        lastMessageId = requestDto.getId();
                        Gson gson = new Gson();
                        String requestJSon = gson.toJson(requestDto);
                        UniversalHelper.debugLog(Log.WARN, "MSG_DBG", "send Immediate " + requestJSon);
                        sendImmediate(requestJSon);

                        success = processIncomingQueue(requestDto, newMsg);
                    }
                }
            } catch (InterruptedException ex) {
                UniversalHelper.logException(ex);
                UniversalHelper.debugLog(Log.WARN, "MSG_DBG", "InterruptedException  " + ex.getMessage());

                Thread.currentThread().interrupt();
                messageSender = null;
                success = false;
            }
            return success;
        }

        private boolean processIncomingQueue(DummyDto requestDto, OutcomingMessage newMsg) {
            boolean success = true;
            String incomingMessage;
            try {
                incomingMessage = incomingQueue.poll(RESPONSE_TIMEOUT, TimeUnit.SECONDS);

                UniversalHelper.debugLog(Log.DEBUG, "MSG_SRV IN", String.valueOf(" First receive MESSAGE with  id =  " + incomingMessage + " items left = " + incomingQueue.size()));

                if (incomingMessage == null) {
                    UniversalHelper.debugLog(Log.DEBUG, "MSG_SRV SEND RE", "MESSAGE  " + lastMessageId);

                    newMsg.setPriority(newMsg.getPriority() != OutcomingMessage.PRIORITY_IMMEDIATE
                            ? OutcomingMessage.PRIORITY_HIGH
                            : newMsg.getPriority());
                    UniversalHelper.debugLog(Log.DEBUG, "MSG_SRV IN", " Server timeout ");
                    reconnectIfGetToken(newMsg);

                    outQueueP.add(newMsg);

                } else if (incomingMessage.equals(requestDto.getId())) {
                    lastMessageId = "";
                    sendCount = 0;
                }
                if (sendCount >= 3) {
                    UniversalHelper.debugLog(Log.ERROR, "QUEUE ERROR", "Message delete after 3 requests to server ");
                    lastMessageId = "";
                    sendCount = 0;
                }
            } catch (InterruptedException ex) {
                UniversalHelper.logException(ex);
                Thread.currentThread().interrupt();
                UniversalHelper.debugLog(Log.DEBUG, "MSG_SRV IN", " Exception ");
                messageSender = null;
                if (newMsg.getPriority() != OutcomingMessage.PRIORITY_IMMEDIATE) {
                    newMsg.setPriority(OutcomingMessage.PRIORITY_HIGH);
                }
                outQueueP.add(newMsg);
                success = false;
            }
            return success;
        }

        private void reconnectIfGetToken(OutcomingMessage newMsg) {
            if (newMsg.getMessageId().startsWith("d:000;GT")) {
                UniversalHelper.debugLog(Log.ERROR, "RECONNECT", "message queue reconnect if getTokn");
                connectService.startRestoreConnection();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    UniversalHelper.logException(e);
                }
            }
        }

    }

    /**
     * A task for incoming messages parsing
     */
    private class ReceiverThread extends Thread {
        @Override
        public void run() {
            IncomingMessage msgDto;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    msgDto = serverIncomingQueue.take();
                    if (msgDto == null) {
                        break;
                    }
                    incomingReceiver.processIncomingMessages(connectService, msgDto.getMessage());

                } catch (InterruptedException e) {
                    UniversalHelper.logException(e);
                    serverReceiver = null;
                }
            }
            this.interrupt();
            serverReceiver = null;
        }
    }
}
