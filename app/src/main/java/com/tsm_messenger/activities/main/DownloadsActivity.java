package com.tsm_messenger.activities.main;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.TsmTemplateActivity;
import com.tsm_messenger.activities.main.chat.ChatHistoryActivity;
import com.tsm_messenger.activities.service.BroadcastMessages;
import com.tsm_messenger.connection.MessagePostman;
import com.tsm_messenger.data.storage.ChatUnit;
import com.tsm_messenger.data.storage.DataChatHistory;
import com.tsm_messenger.data.storage.DbChatMessage;
import com.tsm_messenger.data.storage.DbMessengerUser;
import com.tsm_messenger.data.storage.FileData;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.FileProgressListener;
import com.tsm_messenger.service.UniversalHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DownloadsActivity extends TsmTemplateActivity implements FileProgressListener {

    private ArrayAdapter<FileData> downloadsAdapter;
    private ActivityGlobalManager aManager;
    private List<FileData> filesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        aManager = (ActivityGlobalManager) getApplication();
        setContentView(R.layout.activity_downloads);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        findViewById(R.id.btnBack_toolbar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        ListView lvFiles = (ListView) findViewById(R.id.lvDownloads);
        try {
            filesList = ((ActivityGlobalManager) getApplication()).getDbFileStorage().getAdapterList();
        } catch (Exception e) {
            UniversalHelper.logException(e);
            filesList = new ArrayList<>();
        }
        downloadsAdapter = new RecordAdapter(this, filesList);
        lvFiles.setAdapter(downloadsAdapter);
        lvFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                fileClick(position);
            }
        });
        lvFiles.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final FileData fileData = downloadsAdapter.getItem(position);
                UniversalHelper.showFileOptionsPopup(view, fileData, DownloadsActivity.this);
                return true;
            }
        });
    }

    private void fileClick(int position) {
        final FileData fileData = downloadsAdapter.getItem(position);
        if (fileData.getMessage().getServerstate() == DbChatMessage.FileServerStatus.ERROR &&
                fileData.getPercentcomplite() >= 100
                ) {
            Intent intent = new Intent(this, ChatHistoryActivity.class);
            String unitId = aManager.getDbChatHistory().getChat(Integer.valueOf(fileData.getChatId())).getUnitId();
            intent.putExtra(CustomValuesStorage.IntentExtras.INTENT_UNIT_ID, unitId);
            startActivity(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        aManager.setFileProgressListener(this);
        aManager.setCurrentActivity(this);
        refreshList(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        aManager.setFileProgressListener(null);
        aManager.setCurrentActivity(null);
    }

    @Override
    protected void onSubscribeBroadcastMessage() {
        super.onSubscribeBroadcastMessage();
        IntentFilter bcFilter = new IntentFilter(BroadcastMessages.WS_NEWMESSAGE_HISTORY);
        bcFilter.addAction(BroadcastMessages.WS_FILEACTION);
        bcFilter.addAction(BroadcastMessages.WS_FILERECEIVE);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver,
                bcFilter);
    }

    @Override
    protected boolean onReceiveBroadcast(Intent intent) {
        if (intent.getAction().equals(BroadcastMessages.WS_FILEACTION)) {
            refreshList(false);
        }
        if (intent.getAction().equals(BroadcastMessages.WS_NEWMESSAGE) ||
                intent.getAction().equals(BroadcastMessages.WS_FILERECEIVE)) {
            refreshList(true);
        }
        return super.onReceiveBroadcast(intent);
    }

    @Override
    public void fileProgressEvent(Integer chatId) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                refreshList(false);
            }
        });
    }

    private void refreshList(boolean rebuildList) {
        if (downloadsAdapter != null) {
            if (rebuildList) {
                filesList = ((ActivityGlobalManager) getApplication()).getDbFileStorage().getAdapterList();
            }
            downloadsAdapter.notifyDataSetChanged();
        }
    }

    private class RecordAdapter extends ArrayAdapter<FileData> {

        public RecordAdapter(Context context, List<FileData> listitems) {
            super(context, R.layout.file_list_item, listitems);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            DataHolder holder;
            final FileData fileItem = getItem(position);
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.file_list_item, parent, false);
                holder = new DataHolder(v);
                v.setTag(holder);
            } else {
                holder = (DataHolder) v.getTag();
            }
            if (fileItem != null && fileItem.getMessage() != null) {
                String fileName = fileItem.getFileName();
                final DbChatMessage fileMessage = fileItem.getMessage();
                Map<String, DbMessengerUser> dbAddress = aManager.getDbContact().getMessengerDb();
                DbMessengerUser sender = dbAddress.get(fileMessage.getLogin());
                String fileSender = sender != null ? sender.getDisplayName() : fileMessage.getLogin();
                DataChatHistory dbChatMessage = aManager.getDbChatHistory();
                final ChatUnit chat = dbChatMessage.getChat(fileMessage.getChatId());
                String fileChat = chat != null ? chat.getChatName() : "***";
                String fileSize = UniversalHelper.getVisibleFileSize(DownloadsActivity.this, fileItem.getFileSize());
                String filePercents = fileItem.getPercentcomplite() + "%";
                String fileTime = fileMessage.getTimeString();

                holder.lbl_file_name.setText(fileName);
                holder.lbl_chat_name.setText(fileChat);
                holder.lbl_file_size.setText(fileSize);
                holder.lbl_user_login.setText(getString(R.string.lbl_sender) + " " + fileSender);
                holder.lbl_percents.setText(filePercents);
                holder.lblFileTime.setText(fileTime);

                holder.fileAcceptBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        UniversalHelper.sendFileAcceptRequest(fileItem, fileItem.getFileSize(), chat, fileMessage);
                        refreshList(false);
                    }
                });

                if (fileItem.isPending()) {
                    holder.fileCancelBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            UniversalHelper.sendFileDeclineRequest(fileItem, fileItem.getFileSize());
                            refreshList(false);
                        }
                    });
                } else {
                    holder.fileCancelBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            MessagePostman.getInstance().sendFileCancel(fileItem.getFileId());
                            refreshList(false);
                        }
                    });
                }

                boolean fileIsOut = fileMessage.getType().equals(DbChatMessage.MessageType.OUT);
                boolean fileNotCanceled = !(fileMessage.getServerstate()
                        == DbChatMessage.FileServerStatus.ERROR);

                if (chat != null && chat.getChatGroup() != null && fileIsOut && fileNotCanceled) {
                    holder.lblFileIcon.setVisibility(View.GONE);
                    holder.llFileStatusGroup.setVisibility(View.VISIBLE);
                    if (fileMessage.getFileData().getPercentcomplite() < 100) {
                        holder.fileCancelBtn.setVisibility(View.VISIBLE);
                        if (fileItem.isPending()) {
                            holder.fileAcceptBtn.setVisibility(View.VISIBLE);
                        } else {
                            holder.fileAcceptBtn.setVisibility(View.GONE);
                        }
                    } else {
                        holder.fileCancelBtn.setVisibility(View.GONE);
                        holder.fileAcceptBtn.setVisibility(View.GONE);
                    }
                    holder.tvCancelCount.setText(String.valueOf(fileMessage.getFileCancelCount()));
                    holder.tvReceiveCount.setText(String.valueOf(fileMessage.getReceiveCount()));

                } else {
                    holder.lblFileIcon.setVisibility(View.VISIBLE);
                    holder.llFileStatusGroup.setVisibility(View.GONE);

                    if (fileMessage.getFileData().getPercentcomplite() < 100) {
                        showPrivateFileProgress(fileMessage, holder);
                    } else {
                        showPrivateFileResult(fileMessage, holder);
                    }
                }
            }
            return v;
        }

        private void showPrivateFileResult(DbChatMessage messageItem, DataHolder holder) {
            holder.fileCancelBtn.setVisibility(View.GONE);
            holder.fileAcceptBtn.setVisibility(View.GONE);
            if (messageItem.getServerstate() == DbChatMessage.FileServerStatus.ERROR) {
                holder.lblFileIcon.setBackgroundResource(R.drawable.file_error);
            } else {
                holder.lblFileIcon.setBackgroundResource(R.drawable.file_sent);
            }
        }

        private void showPrivateFileProgress(DbChatMessage messageItem, DataHolder holder) {
            if (messageItem.getFileData().isPending()) {
                holder.fileAcceptBtn.setVisibility(View.VISIBLE);
            } else {
                holder.fileAcceptBtn.setVisibility(View.GONE);
            }
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

        private class DataHolder {

            private TextView lbl_file_name, lbl_chat_name, lbl_user_login, lbl_file_size,
                    lblFileIcon, tvReceiveCount, tvCancelCount, lbl_percents, lblFileTime;
            private LinearLayout llFileStatusGroup;
            private ImageView fileCancelBtn, fileAcceptBtn;

            public DataHolder(View v) {
                lbl_file_name = (TextView) v.findViewById(R.id.lbl_file_name);
                lbl_chat_name = (TextView) v.findViewById(R.id.lbl_chat_name);
                lbl_user_login = (TextView) v.findViewById(R.id.lbl_user_login);
                lbl_file_size = (TextView) v.findViewById(R.id.lbl_file_size);
                lblFileIcon = (TextView) v.findViewById(R.id.tvChat_file);
                tvReceiveCount = (TextView) v.findViewById(R.id.tvFileOk_count);
                tvCancelCount = (TextView) v.findViewById(R.id.tvFileCancel_count);
                lbl_percents = (TextView) v.findViewById(R.id.lbl_percents);
                llFileStatusGroup = (LinearLayout) v.findViewById(R.id.lvFileStatus_group);
                fileCancelBtn = (ImageView) v.findViewById(R.id.btn_cancel_file);
                fileAcceptBtn = (ImageView) v.findViewById(R.id.btn_accept_file);
                lblFileTime = (TextView) v.findViewById(R.id.lbl_file_time);
            }
        }
    }
}
