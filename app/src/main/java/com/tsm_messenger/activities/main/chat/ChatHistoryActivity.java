package com.tsm_messenger.activities.main.chat;

import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.TsmTemplateActivity;
import com.tsm_messenger.activities.control.TsmMessageDialog;
import com.tsm_messenger.activities.control.TsmNotification;
import com.tsm_messenger.activities.main.contacts.InfoActivity;
import com.tsm_messenger.activities.service.BroadcastMessages;
import com.tsm_messenger.activities.service.OpenFileActivity;
import com.tsm_messenger.connection.MessagePostman;
import com.tsm_messenger.data.storage.ChatUnit;
import com.tsm_messenger.data.storage.DbChatMessage;
import com.tsm_messenger.data.storage.DbMessengerUser;
import com.tsm_messenger.data.storage.FileData;
import com.tsm_messenger.protocol.transaction.Operation;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.protocol.transaction.Response;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.FileProgressListener;
import com.tsm_messenger.service.SharedPreferencesAccessor;
import com.tsm_messenger.service.UniversalHelper;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * **********************************************************************
 * <p>
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

public class ChatHistoryActivity extends TsmTemplateActivity
        implements FileProgressListener {

    public static final int SEND_OFFLINE = 1001;
    public static final int SEND_ONLINE = 1002;
    private List<DbChatMessage> chatHistory;
    private RecordAdapter chatHistoryAdapter;
    private int incomePhraseRoot1;
    private int incomePhrase1;
    private int incomePhraseRoot2;
    private int incomePhrase2;
    private int ownPhraseRoot;
    private int ownPhrase;
    private ChatUnit currentChat;
    private EditText tbMessage;
    private ListView lvChatHistory;
    private TextView lblNewMessages;
    private TextView tvOnlineState;
    private ImageButton btnSend;
    private ProgressDialog mProgress;
    private Toolbar mToolbar;
    private boolean fileSendState;
    private int imageWidth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_chat_history);
            mToolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(mToolbar);
            seekForResources();

            findViewById(R.id.btnBack_toolbar).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setResult(RESULT_CANCELED);
                    finish();
                }
            });

            btnSend = (ImageButton) findViewById(R.id.btnSendMessage);
            lblNewMessages = (TextView) findViewById(R.id.lbl_new_msgs);
            lblNewMessages.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    lblNewMessages.setVisibility(View.GONE);
                    //post scroll delayed to workaround the visibility changing for label
                    new Handler(Looper.myLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            refreshAdapter(true);
                        }
                    }, 30);
                }
            });
            lvChatHistory = (ListView) findViewById(R.id.lvChatHistory);
            lvChatHistory.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    DbChatMessage message = chatHistoryAdapter.getItem(position);
                    if (message.getContentType() == DbChatMessage.MSG_FILE) {
                        final FileData fileData = message.getFileData();
                        UniversalHelper.showFileOptionsPopup(view, fileData, ChatHistoryActivity.this);
                    }
                    return true;
                }
            });
            lvChatHistory.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    if (isBottomReached() && lblNewMessages.getVisibility() == View.VISIBLE) {
                        lblNewMessages.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    //do nothing
                }
            });
            tbMessage = (EditText) findViewById(R.id.tbMessage);
            tbMessage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //change is delayed to wait for soft-input keyboard to appear
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            lvChatHistory.setSelection(chatHistory.size() - 1);
                        }
                    }, 300);
                }
            });
            tbMessage.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus && lvChatHistory != null && chatHistory != null) {
                        //change is delayed to wait for soft-input keyboard to appear
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                lvChatHistory.setSelection(chatHistory.size() - 1);
                            }
                        }, 500);
                    }
                }
            });

            tvOnlineState = (TextView) findViewById(R.id.tv_online_state);

            mProgress = ProgressDialog.show(this, "", getString(R.string.lbl_progress_wait));
            mProgress.dismiss();

            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            imageWidth = (int) Math.max((Math.min(size.x, size.y)) / 3,
                    getResources().getDimension(R.dimen.btn_std_width));
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    refreshAdapter(true);
                }
            }, 500);
        } catch (Exception e) {
            UniversalHelper.logException(e);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_history, menu);
        if (currentChat != null) {
            if (!currentChat.getSecureType().equals(Integer.valueOf(Param.ChatSecureLevel.ALL_HISTORY_KEEP))) {
                menu.findItem(R.id.action_load_history).setVisible(false);
            } else {
                menu.findItem(R.id.action_load_history).setVisible(!currentChat.hasFullHistory());
            }
            menu.findItem(R.id.action_send_file).setVisible(fileSendState);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_chat_info:
                Intent intent = new Intent(this, InfoActivity.class);
                String unitId = currentChat.getUnitId();
                intent.putExtra(CustomValuesStorage.IntentExtras.INTENT_MESSENGER_ID, unitId);
                intent.putExtra(InfoActivity.INFO_MODE_EXTRA, InfoActivity.EXTRA_CHAT_MODE);
                startActivity(intent);
                break;
            case R.id.action_load_history:
                loadHistory();
                break;
            case R.id.action_send_file:
                callSendFileMenu();
                break;
            default: //do nothing
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadHistory() {
        Toast.makeText(this, R.string.info_history_will_load, Toast.LENGTH_LONG).show();

        String earliestDateString = null;

        if (!chatHistory.isEmpty()) {
            Date earliestDate = chatHistory.get(0).getTimeStamp();
            if (earliestDate == null) {
                earliestDate = new Date();
            }
            DateFormat df = new SimpleDateFormat(Param.DATE_FORMAT, Locale.US);
            earliestDateString = df.format(earliestDate);
        }

        MessagePostman.getInstance().sendHistoryRequest(
                currentChat.getChatId().toString(),
                currentChat.getFirstServerId(),
                earliestDateString
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        ActivityGlobalManager globalManager = (ActivityGlobalManager) getApplication();

        globalManager.setFileProgressListener(this);
        if (currentChat != null) {
            globalManager.setCurrentChatId(currentChat.getChatId());
            if (chatHistoryAdapter != null) {
                chatHistoryAdapter.notifyDataSetChanged();
                globalManager.setCurrentAdapter(chatHistoryAdapter);
            }
        }


    }

    @Override
    protected void onPause() {
        ActivityGlobalManager globalManager = (ActivityGlobalManager) getApplication();
        globalManager.setFileProgressListener(null);
        globalManager.setCurrentChatId(null);
        globalManager.setCurrentAdapter(null);
        if (currentChat != null) {
            currentChat.resetUnreadMessagesCounter();
            globalManager.setChatRead(currentChat.getChatId());
        }
        super.onPause();
    }

    @Override
    protected boolean onReceiveBroadcast(final Intent intent) {
        Bundle bundle = intent.getBundleExtra(BroadcastMessages.WS_PARAM);
        if (intent.getAction().equals(BroadcastMessages.WS_NEWMESSAGE) ||
                intent.getAction().equals(BroadcastMessages.WS_FILEACTION) ||
                intent.getAction().equals(BroadcastMessages.WS_NEWMESSAGE_HISTORY) ||
                intent.getAction().equals(BroadcastMessages.WS_FILERECEIVE)) {
            Integer chatId = bundle.getInt(Response.MessageResponse.CHAT_ID);

            if (chatId.equals(currentChat.getChatId())) {
                new Handler(Looper.myLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refreshAdapter(needsSkipToLastMessage(intent));
                    }
                }, 300);
                if (intent.getAction().equals(BroadcastMessages.WS_NEWMESSAGE_HISTORY)) {
                    showHistoryLoaded();
                } else if (!needsSkipToLastMessage(intent) &&
                        intent.getAction().equals(BroadcastMessages.WS_NEWMESSAGE) ||
                        intent.getAction().equals(BroadcastMessages.WS_FILERECEIVE)) {
                    lblNewMessages.setVisibility(View.VISIBLE);
                }
                return true;
            }
        }
        if (intent.getAction().equals(BroadcastMessages.WS_CONTACT_STATUS) ||
                intent.getAction().equals(BroadcastMessages.WS_DELETE_RELATION)) {
            refreshOnlineStatus();
        }
        if (intent.getAction().equals(BroadcastMessages.getBroadcastOperation(Operation.INVITE)) ||
                intent.getAction().equals(BroadcastMessages.getBroadcastOperation(Operation.LEAVE))) {
            int chatId = intent.getIntExtra(Response.AuthorizeResponse.CHAT_ID, -1);
            fullRefreshOnlineMarker(chatId);
        }
        if (intent.getAction().equals(BroadcastMessages.WS_NET_STATE)) {
            int newState = bundle.getInt(BroadcastMessages.WS_NET_STATE_VAL);
            if (newState == BroadcastMessages.ConnectionState.OFFLINE) {
                fullRefreshOnlineMarker(currentChat.getChatId());
            }
        }
        return super.onReceiveBroadcast(intent);
    }

    private void fullRefreshOnlineMarker(int chatId) {
        if (chatId == currentChat.getChatId()) {
            if (currentChat.getActiveParticipantsCount() == 1) {
                tvOnlineState.setVisibility(View.VISIBLE);
                refreshOnlineStatus();
            } else {
                tvOnlineState.setVisibility(View.GONE);
            }
        }
    }

    private void refreshOnlineStatus() {
        DbMessengerUser participant = currentChat.getParticipantsUserList().get(0);
        if ((!ActivityGlobalManager.getInstance().isOnline()) &&
                (participant.getStatusOnline() != CustomValuesStorage.UserStatus.UNREACHABLE)) {
            participant.setStatusOnline(CustomValuesStorage.UserStatus.OFFLINE);
        }
        UniversalHelper.refreshOnlineStatus(tvOnlineState, participant, true);
    }

    private boolean needsSkipToLastMessage(Intent intent) {

        boolean ownMessage;
        if (chatHistory == null || chatHistory.isEmpty()) {
            ownMessage = false;
        } else {
            ownMessage = chatHistory.get(chatHistory.size() - 1).getType() == DbChatMessage.MessageType.OUT;
        }
        return (!intent.getAction().equals(BroadcastMessages.WS_FILEACTION) &&
                isBottomReached()) || ownMessage;
    }

    private boolean isBottomReached() {
        boolean retVal;
        try {
            retVal = (lvChatHistory.getLastVisiblePosition() == lvChatHistory.getAdapter().getCount() - 1) &&
                    lvChatHistory.getChildAt(lvChatHistory.getChildCount() - 1).getTop() <= lvChatHistory.getHeight();
        } catch (NullPointerException npe) {
            UniversalHelper.logException(npe);
            retVal = true;
        }
        return retVal;
    }

    @Override
    protected void onSubscribeBroadcastMessage() {
        super.onSubscribeBroadcastMessage();
        IntentFilter bcFilter = new IntentFilter(BroadcastMessages.WS_NEWMESSAGE_HISTORY);
        bcFilter.addAction(BroadcastMessages.WS_FILEACTION);
        bcFilter.addAction(BroadcastMessages.WS_FILERECEIVE);
        bcFilter.addAction(BroadcastMessages.WS_CONTACT_STATUS);
        bcFilter.addAction(BroadcastMessages.WS_DELETE_RELATION);
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.LEAVE));
        bcFilter.addAction(BroadcastMessages.getBroadcastOperation(Operation.INVITE));
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver,
                bcFilter);

    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {

        super.onServiceConnected(name, service);
        Intent intent = getIntent();
        String currentCheckedChatGroup = intent.getStringExtra(CustomValuesStorage.IntentExtras.INTENT_UNIT_ID);
        if (currentCheckedChatGroup != null) {
            currentChat = dbService.getLocalChatHistory().getChatByPersId(currentCheckedChatGroup);
            if (currentChat != null) {
                fullRefreshOnlineMarker(currentChat.getChatId());
            } else {
                finish();
            }

        } else {
            Integer chatId = intent.getIntExtra(Response.MessageResponse.CHAT_ID, 0);
            currentChat = dbService.getLocalChatHistory().getChat(chatId);
            if (currentChat != null) {
                fullRefreshOnlineMarker(currentChat.getChatId());
            } else {
                finish();
            }

        }
        if (currentChat == null) {
            finish();
            return;
        }

        ActivityGlobalManager globalManager = (ActivityGlobalManager) getApplication();
        globalManager.setChatRead(currentChat.getChatId());
        globalManager.setCurrentChatId(currentChat.getChatId());

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(TsmNotification.NEWMESSAGE_ID + currentChat.getChatId());

        chatHistory = currentChat.getChatHistory();
        setTitle(currentChat.getChatName());

        chatHistoryAdapter = new RecordAdapter(this, chatHistory);
        globalManager.setCurrentAdapter(chatHistoryAdapter);
        ((ListView) findViewById(R.id.lvChatHistory)).setAdapter(chatHistoryAdapter);

        LinearLayout inputMessageLayout = (LinearLayout) findViewById(R.id.lvInputMessageBlock);

        if (currentChat.isOutcast()) {
            inputMessageLayout.setVisibility(View.GONE);
        } else {
            inputMessageLayout.setVisibility(View.VISIBLE);
        }

        currentChat.resetUnreadMessagesCounter();

        boolean isPrivateChat = currentChat.isPrivateChat();
        if (!isPrivateChat && currentChat.getActiveParticipantsCount() <= 0) {
            showChatEmpty();
        } else {
            if (currentChat.ifChatUnreachable())
                showChatUnreachable(isPrivateChat);
            else
                showSendEnabled();
        }
        refreshAdapter(true);

    }

    private void showProgress() {
        if (mProgress != null && !mProgress.isShowing()) {
            mProgress.show();
        }
    }

    private void hideProgress() {
        if (mProgress != null && mProgress.isShowing()) {
            mProgress.dismiss();
        }
    }

    private void seekForResources() {
        ownPhraseRoot = R.drawable.phrase_light_gray;
        ownPhrase = R.drawable.rounded_rectangle_light_gray;

        incomePhraseRoot1 = R.drawable.phrase;
        incomePhrase1 = R.drawable.rounded_rectangle_gray;

        incomePhraseRoot2 = R.drawable.phrase_blue;
        incomePhrase2 = R.drawable.rounded_rectangle_blue;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == SEND_OFFLINE ||
                requestCode == SEND_ONLINE) && resultCode == RESULT_OK
                ) {

            List<Uri> retVal = new ArrayList<>();
            if (data.getData() != null) {
                retVal.add(data.getData());
            } else {
                ClipData retData = data.getClipData();
                for (int i = 0; i < retData.getItemCount(); i++) {
                    retVal.add(retData.getItemAt(i).getUri());
                }
            }
            for (Uri selectedImage : retVal) {
                String sendMode = requestCode == SEND_ONLINE ? Param.FileTransferMode.ONLINE : Param.FileTransferMode.OFFLINE;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    String filePath = UniversalHelper.getPath(this, selectedImage);

                    sendFile(filePath, sendMode);
                } else {
                    String[] filePathColumn = {MediaStore.Images.Media.DATA};

                    Cursor cursor = getContentResolver().query(selectedImage,
                            filePathColumn, null, null, null);
                    if (cursor == null) {
                        continue;
                    }
                    cursor.moveToFirst();
                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    String fileName = cursor.getString(columnIndex);

                    cursor.close();

                    sendFile(fileName, sendMode);
                }
            }
        } else if (resultCode == RESULT_OK && null != data) {
            String fileName = data.getExtras().getString(OpenFileActivity.EXTRA_URI, "");
            String sendMode = data.getExtras().getString(OpenFileActivity.EXTRA_SEND_MODE, "");
            sendFile(fileName, sendMode);
        } else {
            if (data != null) {
                Integer intExtra = data.getIntExtra(OpenFileActivity.EXTRA_URI, RESULT_OK);
                if (intExtra == RESULT_CANCELED) {
                    Toast.makeText(this, R.string.file_select_canceled, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * Performs a text message preparing and sending
     *
     * @param view a button that called this method by its OnClick event
     */
    public void btnSendMessage_onClick(View view) {
        String message = tbMessage.getText().toString().trim();
        if ("".equals(message)) {
            //do not send empty messages
            return;
        }
        tbMessage.setError(null);
        currentChat.sendMessage(message, dbService.getLocalChatHistory(),
                getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE).getString(SharedPreferencesAccessor.USER_ID, ""));

        tbMessage.setText("");
        if (chatHistoryAdapter != null) {
            chatHistoryAdapter.notifyDataSetChanged();
            lvChatHistory.setSelection(chatHistory.size() - 1);
        }
    }

    private void sendFile(String fileName, String sendMode) {
        File selectedFile = new File(fileName);
        long fileSize = selectedFile.length();
        if (fileSize == 0) {
            Toast.makeText(ChatHistoryActivity.this,
                    R.string.error_file_transfer_empty_file
                    , Toast.LENGTH_LONG).show();
            return;
        }
        Date lastModified = new Date(selectedFile.lastModified());
        String sessionId = "";
        if (currentChat.getCurrentSessionKey() != null)
            sessionId = currentChat.getCurrentSessionKey().getSessionId().toString();
        FileData newFileData = new FileData(selectedFile.getName(), lastModified,
                fileSize, fileName, sessionId, currentChat.getChatId().toString(), sendMode);
        newFileData.setIsPending(false);
        dbService.getDbFileStorage().saveFileData(newFileData);
        MessagePostman.getInstance().sendFileRequest(selectedFile.getName(), fileSize,
                sendMode, newFileData.getFileId(), sessionId);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                refreshAdapter(true);
            }
        });

    }

    private void showChatEmpty() {
        showSendState(false);
        tbMessage.setHint(R.string.hint_no_active_participants);
    }

    private void showChatUnreachable(boolean isPrivate) {
        showSendState(false);
        if (isPrivate) {
            tbMessage.setHint(R.string.error_user_unreachable);
        } else {
            tbMessage.setHint(R.string.error_chat_unreachable);
        }
    }

    private void showSendEnabled() {
        showSendState(true);
        tbMessage.setHint(R.string.hint_write_message);
    }

    private void showSendState(boolean state) {
        boolean realState = (currentChat != null) && (!currentChat.isOutcast()) && state;

        setButtonOnState(btnSend, realState);
        tbMessage.setEnabled(realState);

        fileSendState = realState;
        invalidateOptionsMenu();
    }

    private void setButtonOnState(ImageButton btn, Boolean state) {
        if (state == null) {
            btn.setVisibility(View.GONE);
            btn.setEnabled(false);
        } else {
            btn.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
            btn.setEnabled(state);
        }
    }

    private void refreshAdapter(Boolean skipToLastMessage) {
        if (chatHistoryAdapter != null) {
            tbMessage.setError(null);
            chatHistoryAdapter.notifyDataSetChanged();
            if (skipToLastMessage) {
                lvChatHistory.setSelection(chatHistory.size() - 1);
            }
        }
    }

    @Override
    public void fileProgressEvent(Integer chatId) {
        if (currentChat != null && currentChat.getChatId().equals(chatId)) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    refreshAdapter(false);
                }
            });

        }
    }

    private void callSendFileMenu() {

        final PopupMenu fileSendModePopupMenu = new PopupMenu(this, mToolbar);
        fileSendModePopupMenu.getMenuInflater()
                .inflate(R.menu.file_send_mode, fileSendModePopupMenu.getMenu());
        fileSendModePopupMenu
                .setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        boolean bRet = true;
                        switch (item.getItemId()) {

                            case R.id.menu_send_file_online:
                                selectFileForSend( Param.FileTransferMode.ONLINE);
                                break;
                            case R.id.menu_send_file_offline:
                                selectFileForSend( Param.FileTransferMode.OFFLINE);
                                break;
                            default:
                                bRet = false;
                        }
                        return bRet;
                    }
                });
        if (!currentChat.getSecureType().equals(Integer.valueOf(Param.ChatSecureLevel.ALL_HISTORY_KEEP))) {
            fileSendModePopupMenu.getMenu().findItem(R.id.menu_send_file_offline).setVisible(false);
        }
        fileSendModePopupMenu.show();
    }

    private void selectFileForSend( final String sendMode) {
        if (currentChat != null) {
            ActivityGlobalManager manager = (ActivityGlobalManager) getApplication();
            if (currentChat.getCurrentSessionKey() == null) {
                if (!manager.isOnline()) {
                    Toast.makeText(this,
                            R.string.error_file_transfer_offline_session_not_init
                            , Toast.LENGTH_LONG).show();
                } else {
                    askSessionKeyForFile( sendMode);
                }
                return;
            }
            showSelectFileDialog( sendMode);
        }
    }

    private void askSessionKeyForFile( final String sendMode) {
        showProgress();
        MessagePostman.getInstance().sendSessionKeyAskRequest(currentChat.getChatId().toString(), null);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                hideProgress();
                if (currentChat.getCurrentSessionKey() == null) {
                    Toast.makeText(ChatHistoryActivity.this,
                            R.string.error_file_transfer_session_init_error
                            , Toast.LENGTH_LONG).show();
                } else {
                    showSelectFileDialog( sendMode);
                }
            }
        }, 2000);
    }

    private void showSelectFileDialog( final String sendMode) {
        Intent intent;
        int resultCode;

        resultCode = sendMode.equals(Param.FileTransferMode.ONLINE) ? SEND_ONLINE : SEND_OFFLINE;

        Intent getIntent = new Intent(Intent.ACTION_GET_CONTENT);
        getIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        getIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        getIntent.setType("*/*");
        intent = Intent.createChooser(getIntent, getString(R.string.action_send_file));

        startActivityForResult(intent, resultCode);
    }

    private void showHistoryLoaded() {
        chatHistoryAdapter.notifyDataSetChanged();
        lvChatHistory.smoothScrollToPosition(0);
        Toast.makeText(this, R.string.info_history_loaded, Toast.LENGTH_LONG).show();
        invalidateOptionsMenu();
    }

    private void showFileCancelDialog(final FileData fileData) {
        final TsmMessageDialog dlg = new TsmMessageDialog(this);
        dlg.setTitle(R.string.title_file_transfer);
        dlg.setMessage(R.string.file_cancel_transfer);
        dlg.setPositiveButton(R.string.btn_yes, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MessagePostman.getInstance().sendFileCancel(fileData.getFileId());
                dlg.dismiss();
            }
        });
        dlg.setNegativeButton(R.string.btn_no, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
            }
        });
        dlg.show();
    }

    /**
     * A custom adapter to make informative list items for the messages list
     */
    public class RecordAdapter extends ArrayAdapter<DbChatMessage> {

        private String lastIncomePersId;
        private int lastPhraseColour = 1;

        public RecordAdapter(Context context, List<DbChatMessage> listitems) {
            super(context, R.layout.phrase_list_item, listitems);
        }

        private void setHistory(DataHolder holder) {
            switchLayout(holder.historyLayout, holder);

        }

        private void setText(DbChatMessage messageItem, DataHolder holder, View v) {
            switchLayout(holder.textLayout, holder);

            if (messageItem.getServerstate() == DbChatMessage.MessageServerStatus.PREPARE) {
                holder.lblSendTime.setText("--:--");
            } else {
                holder.lblSendTime.setText(messageItem.getTimeString());
            }
            if (messageItem.getServerstate() == DbChatMessage.MessageServerStatus.ENCRYPTED) {
                holder.lblMessageText.setText("*****");
            } else {
                holder.lblMessageText.setText(messageItem.getMessage());
            }

            AbsListView.LayoutParams curParams = new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            v.setLayoutParams(curParams);

            if (messageItem.getType() == DbChatMessage.MessageType.IN) {
                holder.lblMessageHeader.setVisibility(View.VISIBLE);
                DbMessengerUser participant = ((ActivityGlobalManager) getApplication()).getDbContact().getMessengerDb().get(messageItem.getLogin());
                String userName;
                if (participant != null) {
                    userName = participant.getPersName();
                } else {
                    userName = messageItem.getLogin();
                }
                holder.lblMessageHeader.setText(userName);
                holder.phraseLayout.setGravity(Gravity.LEFT);
                holder.phraseRootIn.setVisibility(View.VISIBLE);
                holder.phraseRootOut.setVisibility(View.INVISIBLE);
                changeIncomeMessageColour(holder.phraseRootIn, holder.llCloud, messageItem);
                holder.lblState.setVisibility(View.INVISIBLE);
            } else {
                if (messageItem.getServerstate() == DbChatMessage.MessageServerStatus.PREPARE) {
                    holder.lblState.setBackgroundResource(R.drawable.msg_prepared);
                    holder.lblState.setVisibility(View.VISIBLE);
                } else if (messageItem.getServerstate() == DbChatMessage.MessageServerStatus.SEND) {
                    holder.lblState.setBackgroundResource(R.drawable.msg_sent);
                    holder.lblState.setVisibility(View.VISIBLE);
                } else if ((messageItem.getServerstate() & DbChatMessage.MessageServerStatus.DELIVERED) != 0) {
                    holder.lblState.setBackgroundResource(R.drawable.msg_read);
                    holder.lblState.setVisibility(View.VISIBLE);
                } else {
                    holder.lblState.setVisibility(View.INVISIBLE);
                }

                holder.lblMessageHeader.setVisibility(View.GONE);
                holder.phraseLayout.setGravity(Gravity.RIGHT);
                holder.llCloud.setBackgroundResource(ownPhrase);
                holder.phraseRootOut.setVisibility(View.VISIBLE);
                holder.phraseRootOut.setBackgroundResource(ownPhraseRoot);
                holder.phraseRootIn.setVisibility(View.INVISIBLE);
            }
        }

        private void setService(DbChatMessage messageItem, DataHolder holder) {
            switchLayout(holder.serviceLayout, holder);
            if (messageItem != null) {
                Operation curOperation = messageItem.getServiceOperation();
                DbMessengerUser participant = ((ActivityGlobalManager) getApplication()).getDbContact().getMessengerDb().get(messageItem.getLogin());
                String sender;
                if (participant != null) {
                    sender = participant.getPersName();
                } else {
                    sender = messageItem.getLogin();
                }
                String message;

                switch (curOperation){
                    case INVITE:
                        String participants = messageItem.getMessage();
                        message = String.format(getString(R.string.service_invite), sender, participants);
                        break;
                    case LEAVE:
                        message = String.format(getString(R.string.service_leave), sender);
                        break;
                    default:
                        message = messageItem.getMessage();
                }

                holder.tvServiceInfo.setText(message);
            }
        }

        private void setFile(final DbChatMessage messageItem, final DataHolder holder) {
            switchLayout(holder.fileLayout, holder);

            final FileData fileItem = messageItem.getFileData();
            if (fileItem != null) {
                String message = fileItem.getFileName();
                message += String.valueOf(" " + fileItem.getPercentcomplite() + "%");
                holder.lblFileName.setText(message);

                boolean fileIsOut = messageItem.getType().equals(DbChatMessage.MessageType.OUT);
                boolean fileNotCanceled = !(messageItem.getServerstate()
                        == DbChatMessage.FileServerStatus.ERROR);

                if (fileItem.isPending()) {
                    holder.fileAcceptBtn.setVisibility(View.VISIBLE);
                    holder.fileAcceptBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            UniversalHelper.sendFileAcceptRequest(fileItem, fileItem.getFileSize(), currentChat, messageItem);
                            refreshAdapter(false);
                        }
                    });
                    holder.fileCancelBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            UniversalHelper.sendFileDeclineRequest(fileItem, fileItem.getFileSize());
                            refreshAdapter(false);
                        }
                    });
                } else {
                    holder.fileCancelBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showFileCancelDialog(fileItem);
                        }
                    });
                    holder.fileAcceptBtn.setVisibility(View.GONE);
                }
                if (currentChat.getChatGroup() != null && fileIsOut && fileNotCanceled) {
                    holder.llFileStatusOne.setVisibility(View.GONE);
                    holder.llFileStatusGroup.setVisibility(View.VISIBLE);
                    if (fileItem.getPercentcomplite() < 100) {
                        holder.fileCancelBtn.setVisibility(View.VISIBLE);
                    } else {
                        holder.fileCancelBtn.setVisibility(View.GONE);
                    }
                    holder.tvCancelCount.setText(String.valueOf(messageItem.getFileCancelCount()));
                    holder.tvReceiveCount.setText(String.valueOf(messageItem.getReceiveCount()));

                } else {
                    //file in a private chat or is received in group chat
                    holder.llFileStatusOne.setVisibility(View.VISIBLE);
                    holder.llFileStatusGroup.setVisibility(View.GONE);

                    if (fileItem.getPercentcomplite() < 100) {
                        showPrivateFileProgress(messageItem, holder);
                    } else {
                        showPrivateFileResult(messageItem, holder);
                    }
                }
                boolean showImage = UniversalHelper.hasImageExtension(fileItem.getFileName()) &&
                        (fileItem.getPercentcomplite() == 100 || fileIsOut) &&
                        new File(fileItem.getFilePath()).exists();
                if (showImage) {
                    if (fileItem.getPercentcomplite() == 100 || !fileNotCanceled) {
                        holder.lblFileName.setVisibility(View.GONE);
                    } else {
                        holder.lblFileName.setText(fileItem.getPercentcomplite() + "%");
                        holder.lblFileName.setVisibility(View.VISIBLE);
                    }
                    holder.fileImage.setVisibility(View.VISIBLE);
                    holder.fileImage.getLayoutParams().width = imageWidth;
                    holder.fileImage.getLayoutParams().height = imageWidth;
                    holder.fileImage.requestLayout();

                    if (fileItem.getThumbnail() == null) {
                        try {
                            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                            bmOptions.inSampleSize = 8;
                            Bitmap img = BitmapFactory.decodeFile(fileItem.getFilePath(), bmOptions);
                            fileItem.setThumbnail(ThumbnailUtils.extractThumbnail(img, imageWidth, imageWidth));
                        } catch (OutOfMemoryError ooe) {
                            UniversalHelper.logException(ooe);
                        }
                    }
                    holder.fileImage.setImageBitmap(fileItem.getThumbnail());
                } else {
                    holder.lblFileName.setVisibility(View.VISIBLE);
                    holder.fileImage.setVisibility(View.GONE);
                }
            }
            holder.lblState.setVisibility(View.INVISIBLE);
            if (messageItem.getType() == DbChatMessage.MessageType.IN) {
                holder.lblFileHeader.setVisibility(View.VISIBLE);
                DbMessengerUser participant = ((ActivityGlobalManager) getApplication()).getDbContact().getMessengerDb().get(messageItem.getLogin());
                String userName;
                if (participant != null) {
                    userName = participant.getPersName();
                } else {
                    userName = messageItem.getLogin();
                }
                holder.lblFileHeader.setText(userName);
                holder.fileLayout.setGravity(Gravity.LEFT);
                holder.fileRootIn.setVisibility(View.VISIBLE);
                holder.fileRootOut.setVisibility(View.INVISIBLE);
                changeIncomeMessageColour(holder.fileRootIn, holder.fileBody, messageItem);
            } else {
                holder.lblFileHeader.setVisibility(View.GONE);
                holder.fileLayout.setGravity(Gravity.RIGHT);
                holder.fileBody.setBackgroundResource(ownPhrase);
                holder.fileRootOut.setVisibility(View.VISIBLE);
                holder.fileRootOut.setBackgroundResource(ownPhraseRoot);
                holder.fileRootIn.setVisibility(View.INVISIBLE);
            }
            holder.lblFileTime.setText(messageItem.getTimeString());

        }

        private void switchLayout(LinearLayout layout, DataHolder holder) {
            int layoutId = layout.getId();
            boolean isHistory = holder.historyLayout.getId() == layoutId;
            boolean isFile = holder.fileLayout.getId() == layoutId;
            boolean isMessage = holder.textLayout.getId() == layoutId;
            boolean isService = holder.serviceLayout.getId() == layoutId;

            holder.historyLayout.setVisibility(isHistory ? View.VISIBLE : View.GONE);
            holder.fileLayout.setVisibility(isFile ? View.VISIBLE : View.GONE);
            holder.textLayout.setVisibility(isMessage ? View.VISIBLE : View.GONE);
            holder.serviceLayout.setVisibility(isService ? View.VISIBLE : View.GONE);
        }

        private void showPrivateFileResult(DbChatMessage messageItem, DataHolder holder) {
            holder.fileCancelBtn.setVisibility(View.GONE);
            if (messageItem.getServerstate() == DbChatMessage.FileServerStatus.ERROR) {
                holder.lblFileIcon.setBackgroundResource(R.drawable.file_error);
            } else {
                holder.lblFileIcon.setBackgroundResource(R.drawable.file_sent);
            }
        }

        private void showPrivateFileProgress(DbChatMessage messageItem, DataHolder holder) {
            if (messageItem.getServerstate() == DbChatMessage.FileServerStatus.ERROR) {
                holder.lblFileIcon.setBackgroundResource(R.drawable.file_error);
                holder.fileCancelBtn.setVisibility(View.GONE);
            } else {
                holder.fileCancelBtn.setVisibility(View.VISIBLE);
                if (messageItem.getType().equals(DbChatMessage.MessageType.IN)) {
                    holder.lblFileIcon.setBackgroundResource(R.drawable.file_receiving);
                } else {
                    holder.lblFileIcon.setBackgroundResource(R.drawable.file_sending);
                }
            }
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            DataHolder holder;
            DbChatMessage messageItem = getItem(position);
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.phrase_list_item, parent, false);
                holder = new DataHolder(v);
                v.setTag(holder);
            } else {
                holder = (DataHolder) v.getTag();
            }
            if (messageItem != null) {
                switch (messageItem.getContentType()) {
                    case DbChatMessage.MSG_HISTORY:
                        setHistory(holder);
                        break;
                    case DbChatMessage.MSG_TEXT:
                        setText(messageItem, holder, v);
                        break;
                    case DbChatMessage.MSG_FILE:
                        setFile(messageItem, holder);
                        break;
                    case DbChatMessage.MSG_SERVICE:
                        setService(messageItem, holder);
                        break;
                    default: //do nothing
                }
            }
            return v;
        }

        private void changeIncomeMessageColour(TextView phraseRootOut, LinearLayout llCloud, DbChatMessage messageItem) {
            String persIdent = messageItem.getLogin();
            if (!persIdent.equals(lastIncomePersId)) {
                lastIncomePersId = persIdent;
                lastPhraseColour = -lastPhraseColour;
            }
            if (lastPhraseColour == 1) {
                phraseRootOut.setBackgroundResource(incomePhraseRoot1);
                llCloud.setBackgroundResource(incomePhrase1);
            } else {
                phraseRootOut.setBackgroundResource(incomePhraseRoot2);
                llCloud.setBackgroundResource(incomePhrase2);
            }
        }

        class DataHolder {
            public final TextView lblMessageHeader, lblMessageText, lblSendTime,
                    phraseRootOut, phraseRootIn, lblState;
            public final TextView lblFileHeader, tvCancelCount, tvReceiveCount, lblFileName,
                    lblFileTime, lblFileIcon, fileRootIn, fileRootOut, tvServiceInfo;
            public final LinearLayout phraseLayout, llCloud, historyLayout,
                    textLayout, fileLayout, llFileStatusOne, llFileStatusGroup, fileBody, serviceLayout;
            public final ImageView fileCancelBtn, fileAcceptBtn, fileImage;

            DataHolder(View v) {

                lblMessageHeader = (TextView) v.findViewById(R.id.lblMessageHeader);
                lblMessageText = (TextView) v.findViewById(R.id.lblMessageText);
                lblSendTime = (TextView) v.findViewById(R.id.lblSendTime);
                phraseLayout = (LinearLayout) v.findViewById(R.id.row_layout);
                phraseRootIn = (TextView) v.findViewById(R.id.phraseRoot_in);
                phraseRootOut = (TextView) v.findViewById(R.id.phraseRoot_out);
                lblState = (TextView) v.findViewById(R.id.messagestat);
                llCloud = (LinearLayout) v.findViewById(R.id.llCloud);
                historyLayout = (LinearLayout) v.findViewById(R.id.message_history);
                textLayout = (LinearLayout) v.findViewById(R.id.message_text);

                tvServiceInfo = (TextView) v.findViewById(R.id.lbl_info);
                serviceLayout = (LinearLayout) v.findViewById(R.id.service_layout);

                fileLayout = (LinearLayout) v.findViewById(R.id.file_layout);
                fileBody = (LinearLayout) v.findViewById(R.id.message_file);
                fileRootIn = (TextView) v.findViewById(R.id.phraseRoot_in_file);
                fileRootOut = (TextView) v.findViewById(R.id.phraseRoot_out_file);
                lblFileHeader = (TextView) v.findViewById(R.id.lblFileHeader);
                lblFileName = (TextView) v.findViewById(R.id.chat_filename);
                fileImage = (ImageView) v.findViewById(R.id.chat_fileImage);
                lblFileTime = (TextView) v.findViewById(R.id.chat_filetime);
                lblFileIcon = (TextView) v.findViewById(R.id.tvChat_file);
                fileCancelBtn = (ImageView) v.findViewById(R.id.btn_cancel_file);
                fileAcceptBtn = (ImageView) v.findViewById(R.id.btn_accept_file);

                llFileStatusOne = (LinearLayout) v.findViewById(R.id.lvFileStatus_single);
                llFileStatusGroup = (LinearLayout) v.findViewById(R.id.lvFileStatus_group);
                tvCancelCount = (TextView) v.findViewById(R.id.tvFileCancel_count);
                tvReceiveCount = (TextView) v.findViewById(R.id.tvFileOk_count);
            }
        }
    }
}
