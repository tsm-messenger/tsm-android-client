package com.tsm_messenger.activities.main.contacts;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;

import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.TsmTemplateActivity;
import com.tsm_messenger.activities.control.TsmMessageDialog;
import com.tsm_messenger.activities.service.BroadcastMessages;
import com.tsm_messenger.data.access.FilterAdapterHelper;
import com.tsm_messenger.data.storage.ContactPerson;
import com.tsm_messenger.data.storage.DataAddressBook;
import com.tsm_messenger.data.storage.DbGroupChat;
import com.tsm_messenger.data.storage.DbMessengerUser;
import com.tsm_messenger.protocol.transaction.Operation;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.SearchViewListener;
import com.tsm_messenger.service.UniversalHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

public class ChooseContactsActivity extends TsmTemplateActivity {

    private static final String BUNDLE_SAVE_KEY = "checkedContacts";
    private int categoryShow;
    private RecordAdapter contactAdapter;
    private ArrayList<ContactPerson> contactList;
    private ArrayList<ContactPerson> referenceList;
    private ArrayList<String> checkedContacts;
    private DataAddressBook dbContact;
    private String mode;
    private int secureType = Integer.valueOf(Param.ChatSecureLevel.ALL_HISTORY_KEEP);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = getIntent().getStringExtra(CustomValuesStorage.IntentExtras.INTENT_KEY_MODE);

        if (savedInstanceState != null) {
            checkedContacts = savedInstanceState.getStringArrayList(BUNDLE_SAVE_KEY);
        } else {
            checkedContacts = new ArrayList<>();
        }
        setContentView(R.layout.activity_choose_contacts);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        categoryShow = CustomValuesStorage.CATEGORY_CONNECT;
        findViewById(R.id.tbGroupName).setVisibility(View.VISIBLE);

        findViewById(R.id.btnBack_toolbar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        ListView lvContacts = (ListView) findViewById(R.id.lvContacts);
        contactList = new ArrayList<>();
        referenceList = new ArrayList<>();
        contactAdapter = new RecordAdapter(this, contactList);
        lvContacts.setAdapter(contactAdapter);
        lvContacts.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                contactAdapter.onCheckValue(view, position);
            }
        });

        dbContact = ((ActivityGlobalManager) getApplication()).getDbContact();
        refreshContactsList();
        refreshSecureTypeLabel();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_choose_contacts, menu);
        SearchView search = (SearchView) menu.findItem(R.id.action_search).getActionView();
        search.setOnQueryTextListener(new SearchViewListener(contactAdapter));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search || id == R.id.action_done) {
            if (id == R.id.action_done) {
                btnOkPress();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putStringArrayList(BUNDLE_SAVE_KEY, checkedContacts);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ActivityGlobalManager manager = (ActivityGlobalManager) getApplication();
        if (!manager.isOnline()) {
            setAllOffline();
        } else {
            refreshContactsList();
        }
    }

    private void setAllOffline() {
        if (contactAdapter != null) {
            for (int i = 0; i < contactAdapter.getCount(); i++) {
                ContactPerson item = contactAdapter.getItem(i);
                if ((item.getStatusOnline() == CustomValuesStorage.UserStatus.ONLINE) && item.getMessengerUser() != null)
                    item.getMessengerUser().setStatusOnline(CustomValuesStorage.UserStatus.OFFLINE);
            }
            contactAdapter.notifyDataSetChanged();
            refreshContactsList();
        }
    }

    @Override
    protected void onSubscribeBroadcastMessage() {
        super.onSubscribeBroadcastMessage();
        IntentFilter intentFilter = new IntentFilter(BroadcastMessages.WS_CONTACT_STATUS);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected boolean onReceiveBroadcast(Intent intent) {
        Bundle bundle = intent.getBundleExtra(BroadcastMessages.WS_PARAM);

        if (intent.getAction().equals(BroadcastMessages.WS_NET_STATE)) {
            int newState = bundle.getInt(BroadcastMessages.WS_NET_STATE_VAL);
            if (newState == BroadcastMessages.ConnectionState.ONLINE) {
                refreshContactsList();
            } else {
                setAllOffline();
            }
        }
        if (intent.getAction().equals(BroadcastMessages.WS_CONTACT_STATUS) ||
                intent.getAction().equals(BroadcastMessages.WS_SEND_ADDRESS_BOOK) ||
                intent.getAction().equals(BroadcastMessages.getBroadcastOperation(Operation.ANSWER_NOTIFICATION))) {
            refreshContactsList();
        }
        return super.onReceiveBroadcast(intent);
    }

    private void refreshContactsList() {
        if (dbContact != null) {
            contactList.clear();
            referenceList.clear();
            contactList.addAll(dbContact.getAdapterList(categoryShow, true));
            referenceList.addAll(contactList);
        }
    }

    private void btnOkPress() {
        saveGroup();
    }

    private void saveGroup() {
        if (checkedContacts.isEmpty()) {
            TsmMessageDialog msgErr = new TsmMessageDialog(this);
            msgErr.show(R.string.title_error, R.string.error_chat_member_count);
            return;
        }
        DbGroupChat newGroupChat = new DbGroupChat();
        Map<String, DbMessengerUser> messengerDb = dbContact.getMessengerDb();
        for (String login : checkedContacts) {
            newGroupChat.addMember(login, messengerDb.get(login));
        }
        //add Groupname and save it
        String groupChatName;
        EditText grpName = (EditText) findViewById(R.id.tbGroupName);

        groupChatName = grpName.getText().toString().trim().replace("\n", " ");
        if (!"".equals(groupChatName)) {
            newGroupChat.setType(DbGroupChat.GROUPTYPE_SAVE);
        } else {
            newGroupChat.setType(DbGroupChat.GROUPTYPE_TEMP);
        }
        newGroupChat.setGroupName(groupChatName);
        newGroupChat.setSequreType(secureType);

        dbContact.saveGroupChat(newGroupChat);
        //Tell MainActivity that group is created
        if (mode.equals(CustomValuesStorage.IntentExtras.INTENT_CREATE_CHAT)) {
            Intent intent = new Intent();
            intent.putExtra(CustomValuesStorage.IntentExtras.INTENT_KEY_GRP_ID, newGroupChat.getGroupId());
            setResult(CustomValuesStorage.ActivityResult.RESULT_CHT_OK, intent);
        }
        finish();
    }

    /**
     * creates a dialog to choose a chat secure type
     *
     * @param view a view that called this method with its OnClick event
     */
    public void showChatSettings(View view) {
        final ChatSettingsDialog settingsDialog = new ChatSettingsDialog(this, secureType) {

            @Override
            protected void refreshSecureType() {
                secureType = getIntSecureType();
                refreshSecureTypeLabel();
            }
        };
        settingsDialog.show();
    }

    private void refreshSecureTypeLabel() {
        TextView tvChatSecureType = (TextView) findViewById(R.id.lblChatSecureType);
        UniversalHelper.setLblSecureType(this, tvChatSecureType, secureType);
    }

    /**
     * shows the chat secure type details in the dialog
     *
     * @param view a view that called this method with its OnClick event
     */
    public void btnSecuretypeDetails_onClick(View view) {
        int stringId = UniversalHelper.getSecureTypeDetailsString(secureType);
        String message;
        if (stringId == R.string.lbl_securetype_keep_until_lifetime_details) {
            message = String.format(getString(stringId), UniversalHelper.getLifeTimeGenitive(this, secureType));
        } else {
            message = getString(stringId);
        }
        new TsmMessageDialog(this).show(getString(R.string.title_info), message);
    }

    /**
     * a custom adapter to make informative list items for the found users list
     */
    public class RecordAdapter extends ArrayAdapter<ContactPerson> implements Filterable {

        final Context parent;
        private final FilterAdapterHelper<ContactPerson> contactFilter;

        public RecordAdapter(Context context, List<ContactPerson> items) {
            super(context, R.layout.messenger_main_list_item, items);
            this.contactFilter = new FilterAdapterHelper<>(referenceList, contactList, RecordAdapter.this);
            parent = context;
        }

        @Override
        public Filter getFilter() {
            return contactFilter;
        }

        public int memberCount() {
            int cnt = 0;
            for (int i = 0; i < getCount(); i++) {
                ContactPerson pers = getItem(i);
                if (isChecked(pers)) {
                    cnt++;
                }
            }
            return cnt;
        }

        public void onCheckValue(View view, int position) {
            ContactPerson clItem = getItem(position);
            DataHolder dh;
            dh = (DataHolder) view.getTag();
            if (clItem.getStatusOnline() == CustomValuesStorage.UserStatus.UNREACHABLE) {
                switchCheckedStatus(clItem, false);
                dh.cbSelected.setChecked(false);
                return;
            }
            if (!dh.cbSelected.isChecked()) {
                if (memberCount() < DbGroupChat.MAX_GROUP_COUNT) {
                    switchCheckedStatus(clItem, true);
                    dh.cbSelected.setChecked(true);
                } else {
                    UniversalHelper.showGroupOverflowError(ChooseContactsActivity.this);
                }
            } else {
                dh.cbSelected.setChecked(false);
                switchCheckedStatus(clItem, false);
            }
        }

        private void switchCheckedStatus(ContactPerson clItem, boolean status) {
            if (status) {
                checkedContacts.add(clItem.getMessengerId());
            } else {
                checkedContacts.remove(clItem.getMessengerId());
            }
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            DataHolder holder;
            ContactPerson clItem = getItem(position);
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) ChooseContactsActivity.this
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.addressbook_list_item, parent, false);
                holder = new DataHolder(v);
                v.setTag(holder);
            } else {
                holder = (DataHolder) v.getTag();
            }
            if (clItem != null) {
                holder.tvAvatar.setText(clItem.getFirstLetter());
                holder.tvName.setText(clItem.getDisplayName());
                if (isChecked(clItem)) {
                    holder.cbSelected.setChecked(true);
                } else
                    holder.cbSelected.setChecked(false);

                switch (clItem.getStatusOnline()) {
                    case ONLINE:
                        holder.tvOnline.setBackgroundResource(R.drawable.status_online);
                        holder.cbSelected.setVisibility(View.VISIBLE);
                        break;
                    case OFFLINE:
                        holder.tvOnline.setBackgroundResource(R.drawable.status_offline);
                        holder.cbSelected.setVisibility(View.VISIBLE);
                        break;
                    default:
                        holder.tvOnline.setBackgroundResource(R.drawable.status_unreachable);
                        holder.cbSelected.setVisibility(View.INVISIBLE);
                        break;
                }
            }
            return v;
        }

        private boolean isChecked(ContactPerson clItem) {
            return checkedContacts.contains(clItem.getMessengerId());
        }

        class DataHolder {
            public final TextView tvName;
            public final TextView tvAvatar;
            public final TextView tvPhone;
            public final CheckBox cbSelected;
            public final TextView tvOnline;

            DataHolder(View v) {
                tvName = (TextView) v.findViewById(R.id.tv_item_addressBook_Name);
                tvAvatar = (TextView) v.findViewById(R.id.tvAvatar);
                tvPhone = (TextView) v.findViewById(R.id.tv_item_addressBook_Phone);
                cbSelected = (CheckBox) v.findViewById(R.id.tv_item_selected);
                tvOnline = (TextView) v.findViewById(R.id.tv_item_online);
            }
        }
    }
}
