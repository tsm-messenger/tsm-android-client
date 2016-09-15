package com.tsm_messenger.activities.main.contacts;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Space;
import android.widget.TextView;

import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.main.MainActivity;
import com.tsm_messenger.data.access.FilterAdapterHelper;
import com.tsm_messenger.data.storage.ChatUnit;
import com.tsm_messenger.data.storage.ContactPerson;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.SearchViewListener;
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
public class ContactsFragment extends Fragment {

    private int categoryShow = CustomValuesStorage.CATEGORY_ALL | CustomValuesStorage.CATEGORY_GROUP;
    private View currentView;
    private MainActivity currentActivity;
    private ListView lvRegistered;
    private Button btnCategory;
    private ArrayList<ContactPerson> contactList;
    private ArrayList<ContactPerson> referenceList;
    private RecordAdapter contactAdapter;
    private PopupMenu popupMenu;
    private SearchViewListener searchViewListener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        ((MainActivity) getActivity()).setContactsFragment(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        currentView = inflater.inflate(R.layout.fragment_main_contacts, container, false);
        currentActivity = (MainActivity) getActivity();


        findViews();
        setAdapters();
        refreshContactsList();

        return currentView;
    }

    /**
     * changes the category of users shown in the list
     *
     * @param category a category to show
     * @return returns true if operation was performed correctly
     */
    public boolean setCategoryShow(int category) {
        boolean bRet = true;
        switch (category) {

            case R.id.menu_category_all:
                btnCategory.setText(R.string.btn_category_all);
                categoryShow = CustomValuesStorage.CATEGORY_ALL | CustomValuesStorage.CATEGORY_GROUP;
                break;
            case R.id.menu_category_messenger:
                btnCategory.setText(R.string.btn_category_messenger);
                categoryShow = CustomValuesStorage.CATEGORY_CONNECT | CustomValuesStorage.CATEGORY_GROUP;
                break;
            case R.id.menu_category_confirm:
                btnCategory.setText(R.string.btn_category_confirm);
                categoryShow = CustomValuesStorage.CATEGORY_CONFIRM;
                break;
            default:
                bRet = false;
        }
        if (bRet)
            refreshContactsList();
        return bRet;

    }

    /**
     * applies all changes in the contacts list to the UI
     */
    public void refreshContactsList() {
        try {
            if (((ActivityGlobalManager) getActivity().getApplication()).getDbContact() != null) {
                contactList.clear();
                referenceList.clear();
                contactList.addAll(((ActivityGlobalManager) getActivity().getApplication()).getDbContact().getAdapterList(categoryShow, true));
                referenceList.addAll(contactList);
                contactAdapter.notifyDataSetChanged();
            }
        } catch (NullPointerException ne) {
            UniversalHelper.logException(ne);
        }
    }

    private void findViews() {
        lvRegistered = (ListView) currentView.findViewById(R.id.listRegistered);
        lvRegistered.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                                @Override
                                                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                                                    onClickListener(i);
                                                }
                                            }
        );

        btnCategory = (Button) currentView.findViewById(R.id.btnCategory);
        btnCategory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changeCategory();
            }
        });

        popupMenu = new PopupMenu(currentActivity, btnCategory);
        popupMenu.getMenuInflater().inflate(R.menu.contact_category, popupMenu.getMenu());
        popupMenu
                .setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        return setCategoryShow(item.getItemId());
                    }
                });

    }

    private void onClickListener(int i) {
        MainActivity parent = (MainActivity) getActivity();
        ContactPerson item = contactAdapter.getItem(i);
        switch (item.getStat()) {
            case CustomValuesStorage.CATEGORY_CONNECT:
            case CustomValuesStorage.CATEGORY_GROUP:
                parent.startChat(item);
                break;
            default:
                parent.showContactInfo(item, InfoActivity.EXTRA_CONTACT_MODE);
        }
    }

    private void setAdapters() {
        contactList = new ArrayList<>();
        referenceList = new ArrayList<>();
        contactAdapter = new RecordAdapter(currentActivity, contactList);
        lvRegistered.setAdapter(contactAdapter);
        searchViewListener = new SearchViewListener(contactAdapter);
    }

    /**
     * gets the current active instance of a SearchViewListener listening for search requests
     *
     * @return returns an instance of a SearchViewListener
     */
    public SearchViewListener getSearchViewListener() {
        return searchViewListener;
    }

    private void changeCategory() {
        popupMenu.show();
    }

    /**
     * sets the offline state for all users in the list
     */
    public void setAllOffline() {
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

    /**
     * a custom adapter to make informative list items for the found users list
     */
    public class RecordAdapter extends ArrayAdapter<ContactPerson> implements Filterable {

        final List<ContactPerson> listitems;
        final Context parent;
        final LayoutInflater vi;
        private final FilterAdapterHelper<ContactPerson> contactFilter;

        public RecordAdapter(Context context, List<ContactPerson> items) {
            super(context, R.layout.messenger_main_list_item, items);
            this.listitems = items;
            contactFilter = new FilterAdapterHelper<>(referenceList, contactList, RecordAdapter.this);

            vi = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            parent = context;
        }

        private boolean isConfirmFirst(int position) {
            ContactPerson prev, cur;
            boolean ret = true;
            if (position != 0) {
                cur = listitems.get(position);
                prev = listitems.get(position - 1);
                if (cur.getStat().equals(prev.getStat())) {
                    ret = false;
                }
            }
            return ret;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            DataHolder holder;
            ContactPerson clItem = getItem(position);
            if (v == null) {
                v = vi.inflate(R.layout.messenger_main_list_item, parent, false);
                holder = new DataHolder(v);
                v.setTag(holder);
            } else {
                holder = (DataHolder) v.getTag();
            }
            if (clItem != null) {
                if (categoryShow == CustomValuesStorage.CATEGORY_INVITATION && isConfirmFirst(position)) {
                    holder.tvInviteHead.setVisibility(View.VISIBLE);
                    if (clItem.getStat() == CustomValuesStorage.CATEGORY_INVITATION) {
                        holder.tvInviteHead.setText(R.string.btn_category_invitation_ready);
                    } else {
                        holder.tvInviteHead.setText(R.string.btn_category_invitation_todo);
                    }
                } else {
                    holder.tvInviteHead.setVisibility(View.GONE);
                }

                holder.spSpaceRow.setVisibility(View.VISIBLE);
                holder.tvAvatar.setVisibility(TextView.VISIBLE);
                holder.tvInfo.setVisibility(TextView.VISIBLE);
                holder.tvConnectStat.setVisibility(TextView.VISIBLE);
                holder.tvReject.setVisibility(TextView.GONE);
                holder.tvStatus.setTag(position);

                setDividers(position, holder, clItem);
                setAvatar(holder, clItem);
                setStatus(position, holder, clItem);


                holder.tvName.setText(clItem.getDisplayName());
            }
            return v;

        }

        private void setStatus(int position, DataHolder holder, ContactPerson clItem) {
            if ((clItem.getStat() & CustomValuesStorage.CATEGORY_CONNECT) != 0 ||
                    clItem.getContactType() == ContactPerson.GROUP) {
                holder.tvStatus.setVisibility(TextView.VISIBLE);
                holder.tvStatus.setBackground(
                        ContextCompat.getDrawable(getContext(),
                                R.drawable.ic_info_outline_black_24dp));
                if (clItem.getContactType() == ContactPerson.GROUP) {
                    holder.tvConnectStat.setVisibility(View.GONE);
                } else {
                    boolean confirmed;
                    if (clItem.getMessengerUser().getPublicKey() == null || clItem.getMessengerUser().getPublicKey().isEmpty()) {
                        confirmed = false;
                    } else {
                        confirmed = true;
                    }
                    switch (clItem.getStatusOnline()) {
                        case ONLINE:
                            holder.tvConnectStat.setBackgroundResource(confirmed ? R.drawable.status_online : R.drawable.status_unconfirm);
                            break;
                        case OFFLINE:
                            holder.tvConnectStat.setBackgroundResource(confirmed ? R.drawable.status_offline : R.drawable.status_unconfirm);
                            break;
                        default:// UNREACHABLE
                            holder.tvConnectStat.setBackgroundResource(R.drawable.status_unreachable);
                            break;
                    }
                }
            } else {
                holder.tvConnectStat.setBackgroundResource(R.drawable.status_unknown);
                defineNotconnectButtons(position, holder, clItem);
                if (clItem.getStat() == CustomValuesStorage.CATEGORY_DELETE) {
                    holder.tvStatus.setBackground(
                            ContextCompat.getDrawable(getActivity(), R.drawable.ic_info_outline_black_24dp));
                    holder.tvStatus.setVisibility(TextView.VISIBLE);
                    holder.tvConnectStat.setBackgroundResource(R.drawable.status_unreachable);
                }
            }
        }

        private void setAvatar(DataHolder holder, ContactPerson clItem) {
            if (clItem.getContactType() == ContactPerson.GROUP) {
                ChatUnit currentChat = null;
                try {
                    currentChat = ((MainActivity) getActivity())
                            .getDbChatHistory().getChatByPersId(clItem.getMessengerId());
                } catch (Exception e) {
                    UniversalHelper.logException(e);
                }
                holder.tvAvatar.setBackgroundResource(UniversalHelper.getGroupBackgroundId(currentChat));
                holder.tvAvatar.setText("");
            } else {
                holder.tvAvatar.setBackgroundResource(R.drawable.ava_background_yellow);
                holder.tvAvatar.setText(clItem.getFirstLetter());
            }
        }

        private void setDividers(int position, DataHolder holder, ContactPerson clItem) {
            if (categoryShow == CustomValuesStorage.CATEGORY_CONFIRM && isConfirmFirst(position)) {
                holder.tvGroup.setVisibility(TextView.VISIBLE);
                switch (clItem.getStat()) {
                    case CustomValuesStorage.CATEGORY_CONFIRM_IN:
                        holder.tvGroup.setText(R.string.btn_category_confirm_in);
                        break;
                    case CustomValuesStorage.CATEGORY_CONFIRM_OUT:
                        holder.tvGroup.setText(R.string.btn_category_confirm_out);
                        break;
                    default:
                        holder.tvGroup.setText(R.string.btn_category_confirm_wait);

                }
            } else {
                holder.tvGroup.setVisibility(TextView.GONE);
                holder.tvGroup.setText("");
            }
        }

        private void defineNotconnectButtons(int position, DataHolder holder, ContactPerson clItem) {
            if (clItem.getStat() == CustomValuesStorage.CATEGORY_CONFIRM_IN) {
                holder.tvStatus.setVisibility(TextView.VISIBLE);
                holder.tvStatus.setBackground(
                        ContextCompat.getDrawable(getContext(),
                                R.drawable.ic_add_circle_outline_black_24dp));
                holder.tvReject.setVisibility(TextView.VISIBLE);
                holder.tvReject.setTag(position);
            } else {
                if (clItem.getStat() == CustomValuesStorage.CATEGORY_NOTCONNECT) {
                    holder.tvStatus.setBackground(
                            ContextCompat.getDrawable(getContext(),
                                    R.drawable.ic_request_black));
                    holder.tvStatus.setVisibility(TextView.VISIBLE);
                } else {
                    holder.tvStatus.setVisibility(TextView.GONE);
                }
            }
        }

        @Override
        public Filter getFilter() {
            return contactFilter;
        }

        class DataHolder {
            public final TextView tvName;
            public final TextView tvGroup;
            public final TextView tvAvatar;
            public final TextView tvInfo;
            public final TextView tvStatus;
            public final TextView tvConnectStat;
            public final TextView tvReject;
            public final TextView tvInviteHead;
            public final Space spSpaceRow;

            DataHolder(View v) {
                tvName = (TextView) v.findViewById(R.id.tv_item_main_Name);
                tvAvatar = (TextView) v.findViewById(R.id.tv_item_main_avatar);
                tvInfo = (TextView) v.findViewById(R.id.tv_item_main_info);

                tvGroup = (TextView) v.findViewById(R.id.tv_item_main_group);
                tvInviteHead = (TextView) v.findViewById(R.id.tv_item_main_invite_group);
                spSpaceRow = (Space) v.findViewById(R.id.tv_item_spaceRow);
                tvStatus = (TextView) v.findViewById(R.id.tv_item_main_status);
                tvStatus.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        int position = (Integer) view.getTag();
                        ContactPerson item = contactAdapter.getItem(position);
                        switch (item.getStat()) {
                            case CustomValuesStorage.CATEGORY_NOTCONNECT:
                                ((MainActivity) getActivity()).requestContact(item);
                                break;
                            case CustomValuesStorage.CATEGORY_CONFIRM_IN:
                                ((MainActivity) getActivity()).acceptNotification(item.getMessengerId());
                                notifyDataSetChanged();
                                break;
                            default:
                                ((MainActivity) getActivity()).showContactInfo(item, InfoActivity.EXTRA_CONTACT_MODE);
                        }
                    }
                });
                tvConnectStat = (TextView) v.findViewById(R.id.tv_item_main_connect_stat);
                tvReject = (TextView) v.findViewById(R.id.tv_item_main_reject);
                tvReject.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ContactPerson item;
                        int position;
                        try {
                            position = (Integer) view.getTag();
                            item = contactAdapter.getItem(position);
                            ((MainActivity) getActivity()).rejectNotification(item.getMessengerId());
                            notifyDataSetChanged();
                        } catch (NullPointerException e) {
                            UniversalHelper.logException(e);
                        }
                    }
                });
            }
        }
    }
}
