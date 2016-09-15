package com.tsm_messenger.activities.main.contacts;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import android.widget.Toast;

import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.TsmTemplateActivity;
import com.tsm_messenger.activities.service.BroadcastMessages;
import com.tsm_messenger.connection.MessagePostman;
import com.tsm_messenger.data.storage.DbMessengerUser;
import com.tsm_messenger.protocol.transaction.Operation;
import com.tsm_messenger.protocol.transaction.Request;
import com.tsm_messenger.protocol.transaction.Response;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.SharedPreferencesAccessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

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

public class SearchAddUserActivity extends TsmTemplateActivity {

    private static final String BUNDLE_SAVE_KEY_ARRAY = "checkedContacts";
    private static final String BUNDLE_SAVE_KEY_MASK = "checkedContactsmask";
    private final List<String> filteredItems = new ArrayList<>();
    private String myPersIdent;
    private String myUserName;
    private RecordAdapter contactAdapter;
    private String activeMask;
    private List<String> contactList = new ArrayList<>();
    private AutoCompleteTextView etContactMask;
    private EditText etUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        myPersIdent = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE).
                getString(SharedPreferencesAccessor.USER_ID, "");
        myUserName = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE).
                getString(SharedPreferencesAccessor.USER_NICKNAME, "");

        if (savedInstanceState != null) {
            contactList = savedInstanceState.getStringArrayList(BUNDLE_SAVE_KEY_ARRAY);
            activeMask = savedInstanceState.getString(BUNDLE_SAVE_KEY_MASK);
        } else {
            contactList = new ArrayList<>();
        }

        setContentView(R.layout.search_add_user_activity);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        etContactMask = (AutoCompleteTextView) findViewById(R.id.tbLogin);
        etUserName = (EditText) findViewById(R.id.tbName);
        etContactMask.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return false;
            }
        });
        etContactMask.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String value = (String) parent.getAdapter().getItem(position);
                if (value.equals(getString(R.string.info_no_users_found))) {
                    contactList.clear();
                    filteredItems.clear();
                    contactAdapter.notifyDataSetInvalidated();
                    etContactMask.setText("");
                }
            }
        });
        findViewById(R.id.btnBack_toolbar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        contactAdapter = new RecordAdapter(this, contactList);
        etContactMask.setAdapter(contactAdapter);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search_add_user, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_search_request:
                sendSearchUserRequest();
                return true;
            case R.id.action_add_contact:
                btnOkPress();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void sendSearchUserRequest() {
        String mask = etContactMask.getText().toString();
        contactNeedsRequest(mask);
        MessagePostman.getInstance().sendSearchContactRequest(mask);
    }

    private boolean contactNeedsRequest(String mask) {
        boolean result = false;
        if (!mask.isEmpty()) {
            if (mask.length() < 3) {
                Toast.makeText(this, R.string.error_mask_too_short, Toast.LENGTH_LONG).show();
            } else if (mask.equals(myPersIdent)) {
                etContactMask.setError(getString(R.string.error_login_owner));
            } else {
                if (userIsInContactList(mask)) {
                    etContactMask.setError(getString(R.string.error_user_already_in_contactbook));
                }
                activeMask = mask;
                result = true;
            }
        } else {
            etContactMask.setError(getString(R.string.error_empty_name_field));
        }
        return result;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putStringArrayList(BUNDLE_SAVE_KEY_ARRAY, (ArrayList<String>) contactList);
        outState.putString(BUNDLE_SAVE_KEY_MASK, activeMask);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onSubscribeBroadcastMessage() {
        super.onSubscribeBroadcastMessage();
        IntentFilter intentFilter = new IntentFilter(BroadcastMessages.getBroadcastOperation(Operation.GET_LIKE_NAMES));
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected boolean onReceiveBroadcast(Intent intent) {
        Bundle bundle = intent.getBundleExtra(BroadcastMessages.WS_PARAM);

        if (intent.getAction().equals(BroadcastMessages.getBroadcastOperation(Operation.GET_LIKE_NAMES))) {
            List<String> list = bundle.getStringArrayList(Response.GetLikeNamesResponse.LIKE_NAMES);
            TreeSet<String> result = new TreeSet<>();

            if (list != null) {
                refillFoundUsersList(list, result);
            }
        }

        return super.onReceiveBroadcast(intent);
    }

    private void refillFoundUsersList(List<String> list, TreeSet<String> result) {
        contactList.clear();
        filteredItems.clear();
        if (list.isEmpty()) {
            contactList.add(getString(R.string.info_no_users_found));
            filteredItems.addAll(contactList);
        } else {
            for (String user : list) {
                if (!(user.equals(myUserName) || userIsInContactList(user))) {
                    result.add(user);
                }
            }
            if (result.isEmpty()) {
                result.add(getString(R.string.info_no_users_found));
            }
            contactList.addAll(result);
            filteredItems.addAll(result);
        }

        contactAdapter.notifyDataSetChanged();
        etContactMask.showDropDown();
    }

    private boolean userIsInContactList(String userName) {
        DbMessengerUser user = dbService.getTsmContact().getMessengerDb().get(userName);
        for (Map.Entry<String, DbMessengerUser> row : dbService.getTsmContact().getMessengerDb().entrySet()) {
            if (userName.equals(row.getValue().getPersLogin())) {
                user = row.getValue();
                break;
            }
        }
        return user != null && user.getDbStatus() != CustomValuesStorage.CATEGORY_UNKNOWN;
    }

    private void btnOkPress() {
        String mask = etContactMask.getText().toString();
        if (!mask.isEmpty()) {
            HashMap<String, Object> requestData = new HashMap<>();
            requestData.put(Request.NewContactRequest.ValuesType.PERS_IDENT, mask);
            String name = etUserName.getText().toString();
            if (name.isEmpty()) {
                name = mask;
            }
            SharedPreferences settings = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE);
            String ownLogin = settings.getString(SharedPreferencesAccessor.USER_NICKNAME, "");

            if (mask.equals(ownLogin)) {
                etContactMask.setError(getString(R.string.error_login_owner));
            } else if (contactNeedsRequest(mask)) {
                requestData.put(Request.NewContactRequest.ValuesType.PERS_NAME, name);
                MessagePostman.getInstance().sendCreateContactRequest(requestData);
                this.finish();
            }
        } else {
            etContactMask.setError(getString(R.string.error_empty_name_field));
        }
    }

    /**
     * a custom adapter to make informative list items for the found users list
     */
    public class RecordAdapter extends ArrayAdapter<String> implements Filterable {
        public final List<String> listItems;

        final Context parent;
        private FoundUsersFilter foundUsersFilter;

        public RecordAdapter(Context context, List<String> items) {
            super(context, R.layout.simple_list_item, filteredItems);
            this.listItems = items;
            foundUsersFilter = new FoundUsersFilter();
            parent = context;
        }

        @Override
        public Filter getFilter() {
            return foundUsersFilter;
        }

        private class FoundUsersFilter extends Filter {

            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<String> filteredArrList = new ArrayList<>();

                if (activeMask != null && !constraint.toString().contains(activeMask)) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            etContactMask.setError(getString(R.string.message_mask_was_changed));
                        }
                    });
                    activeMask = null;

                } else {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            etContactMask.setError(null);
                        }
                    });

                    for (String item : listItems) {
                        if (item.contains(constraint)) {
                            filteredArrList.add(item);
                        }
                    }
                }
                results.count = filteredArrList.size();
                results.values = filteredArrList;

                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results.count > 0) {
                    filteredItems.clear();
                    filteredItems.addAll((ArrayList<String>) results.values);
                    contactAdapter.notifyDataSetChanged();
                } else {
                    contactAdapter.notifyDataSetInvalidated();
                }
            }
        }

    }

}
