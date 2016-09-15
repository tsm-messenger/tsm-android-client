package com.tsm_messenger.activities.main.chat;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.main.MainActivity;
import com.tsm_messenger.activities.main.contacts.InfoActivity;
import com.tsm_messenger.data.access.FilterAdapterHelper;
import com.tsm_messenger.data.storage.ChatUnit;
import com.tsm_messenger.data.storage.DataAddressBook;
import com.tsm_messenger.data.storage.DataChatHistory;
import com.tsm_messenger.data.storage.DbChatMessage;
import com.tsm_messenger.service.UniversalHelper;

import java.util.ArrayList;
import java.util.List;

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

public class ChatFragment extends Fragment implements SearchView.OnQueryTextListener {

    private DataChatHistory dbChatHistory;
    private RecordAdapter listAdapter;
    private ArrayList<ChatUnit> chatList;
    private ArrayList<ChatUnit> referenceList;
    private DataAddressBook dbAddress;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        ((MainActivity) getActivity()).setChatFragment(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View currentView = inflater.inflate(R.layout.fragment_main_chat, container, false);

        dbAddress = ((MainActivity) getActivity()).getDbAddress();

        ListView lvChats = (ListView) currentView.findViewById(R.id.lvChats);

        chatList = new ArrayList<>();
        referenceList = new ArrayList<>();
        listAdapter = new RecordAdapter(getActivity(), chatList);

        lvChats.setAdapter(listAdapter);
        lvChats.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                lvChatOnItemClick(i);
            }
        });
        refreshChatList();

        return currentView;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    private void lvChatOnItemClick(int i) {
        ChatUnit currentChat = listAdapter.getItem(i);
        if (currentChat.getChatHistory().size() == 0) {
            dbChatHistory.getChatHistory(currentChat);
        }
        ((MainActivity) getActivity()).openChat(currentChat);
    }

    /**
     * Applies the last changes in chat list to the UI
     */
    public void refreshChatList() {
        try {
            if (dbChatHistory == null) {
                dbChatHistory = ((MainActivity) getActivity()).getDbChatHistory();
            }

            chatList.clear();
            referenceList.clear();
            referenceList.addAll(dbChatHistory.getAdapterList());
            chatList.addAll(referenceList);
            listAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            UniversalHelper.logException(e);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        String mask = query.trim();
        listAdapter.getFilter().filter(mask);
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        String mask = newText.trim();
        listAdapter.getFilter().filter(mask);
        return true;
    }

    /**
     * A custom adapter to make informative list items for chat list
     */
    public class RecordAdapter extends ArrayAdapter<ChatUnit> implements Filterable {

        private final FilterAdapterHelper<ChatUnit> chatFilter;

        public RecordAdapter(Context context, List<ChatUnit> items) {
            super(context, R.layout.chat_preview_list_item, items);
            this.chatFilter = new FilterAdapterHelper<>(referenceList, chatList, RecordAdapter.this);
        }

        @Override
        public Filter getFilter() {
            return chatFilter;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            DataHolder holder;
            final ChatUnit clItem = getItem(position);
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.chat_preview_list_item, parent, false);
                holder = new DataHolder(v);
                v.setTag(holder);
            } else {
                holder = (DataHolder) v.getTag();
            }
            if (clItem != null) {
                setAvatar(holder, clItem);

                holder.tvInfo.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        if (clItem.getUnitId() == null) {
                            Toast.makeText(getActivity(),
                                    R.string.error_chat_not_recognized_yet,
                                    Toast.LENGTH_LONG).show();
                        } else {
                            ((MainActivity) getActivity()).showContactInfo(
                                    dbAddress.getContactPerson(clItem.getUnitId()),
                                    InfoActivity.EXTRA_CHAT_MODE);
                        }
                    }
                });

                String chatName = clItem.getChatName();
                holder.tvTitle.setText(chatName);

                setLastMessageText(holder, clItem);

                if (clItem.getUnreadMessageCount() == 0) {
                    holder.tvUnread.setVisibility(View.GONE);
                    holder.tvTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.black));
                } else {
                    holder.tvUnread.setVisibility(View.VISIBLE);
                    holder.tvUnread.setText(clItem.getUnreadMessagesString());
                    holder.tvTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
                    holder.tvLastMsg.setText(getResources().getString(R.string.lbl_unread_messages));
                }

                holder.tvTime.setText(clItem.getLastMessageTime());
            }
            return v;

        }

        private void setAvatar(DataHolder holder, ChatUnit clItem) {
            if (clItem.getChatCategory() == ChatUnit.ChatCategoryType.GROUP) {
                holder.tvAvatar.setBackgroundResource(UniversalHelper.getGroupBackgroundId(clItem));
                holder.tvAvatar.setText("");
            } else {
                holder.tvAvatar.setBackgroundResource(R.drawable.ava_background_yellow);
                String s = clItem.getChatName();
                if (s.length() == 0) {
                    s = "?";
                }
                holder.tvAvatar.setText(s.substring(0, 1));
            }
        }

        private void setLastMessageText(DataHolder holder, ChatUnit clItem) {
            try {
                if (clItem.getChatHistory().size() == 0) {
                    holder.tvLastMsg.setText("");
                } else if (clItem.getLastMessage().getServerstate() == DbChatMessage.MessageServerStatus.ENCRYPTED) {
                    holder.tvLastMsg.setText("***");
                } else {
                    holder.tvLastMsg.setText(clItem.getLastMessageText());
                }
            } catch (Exception e) {
                UniversalHelper.logException(e);
                holder.tvLastMsg.setText("***");
            }
        }

        class DataHolder {
            public final TextView tvTitle;
            public final TextView tvUnread;
            public final TextView tvAvatar;
            public final TextView tvLastMsg;
            public final TextView tvTime;
            public final TextView tvInfo;

            DataHolder(View v) {
                tvInfo = (TextView) v.findViewById(R.id.btnInfo_chat);
                tvTitle = (TextView) v.findViewById(R.id.lblChatName);
                tvAvatar = (TextView) v.findViewById(R.id.lblChatAva);
                tvLastMsg = (TextView) v.findViewById(R.id.lblLastMessage);
                tvTime = (TextView) v.findViewById(R.id.lblTime);
                tvUnread = (TextView) v.findViewById(R.id.tbUnreadCount);
            }
        }
    }
}
