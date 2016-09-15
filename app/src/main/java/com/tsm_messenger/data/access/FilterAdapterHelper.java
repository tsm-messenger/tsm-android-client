package com.tsm_messenger.data.access;

import android.widget.ArrayAdapter;
import android.widget.Filter;

import com.tsm_messenger.data.storage.DataObjectCommon;

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
 * <p/>
 */

public class FilterAdapterHelper<T extends DataObjectCommon> extends Filter {
    private final ArrayAdapter<T> adapter;
    private final List<T> sources;
    private final List<T> filtered;
    private ArrayList<T> resultList;

    /**
     * Makes a new instance of FilterAdapterHelper using provided sources
     *
     * @param sources  an initial list of items to filter
     * @param filtered a result list of items after filtering
     * @param adapter  an adapter to show filter results
     */
    public FilterAdapterHelper(List<T> sources, List<T> filtered, ArrayAdapter<T> adapter) {
        this.sources = sources;
        this.filtered = filtered;
        this.adapter = adapter;
    }

    @Override
    protected FilterResults performFiltering(CharSequence constraint) {
        FilterResults results = new FilterResults();
        resultList = new ArrayList<>();

        if (constraint == null || constraint.length() == 0) {
            resultList.addAll(sources);
        } else {
            for (T p : sources) {
                if (p.filterCheck(constraint))
                    resultList.add(p);
            }
        }

        results.values = resultList;
        results.count = resultList.size();
        return results;
    }

    @Override
    protected void publishResults(CharSequence constraint,
                                  FilterResults results) {

        filtered.clear();
        filtered.addAll(resultList);
        adapter.notifyDataSetChanged();
    }
}
