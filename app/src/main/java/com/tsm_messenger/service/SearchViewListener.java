package com.tsm_messenger.service;

import android.support.v7.widget.SearchView;
import android.widget.ArrayAdapter;

import com.tsm_messenger.data.storage.ContactPerson;

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

public class SearchViewListener implements SearchView.OnQueryTextListener {

    private final ArrayAdapter<ContactPerson> adapter;

    /**
     * A constructor initializing an adapter object for current instance
     *
     * @param adapter an adapter listening for a SearchView events
     */
    public SearchViewListener(ArrayAdapter<ContactPerson> adapter) {
        this.adapter = adapter;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        if (query != null) {
            String mask = query.trim();
            adapter.getFilter().filter(mask);
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (newText != null) {
            String mask = newText.trim();
            adapter.getFilter().filter(mask);
        }
        return true;
    }
}
