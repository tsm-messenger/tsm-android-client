package com.tsm_messenger.data.storage;

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

public abstract class DataObjectCommon {
    /**
     * Checks if the generated name of current item fits the presented mask
     *
     * @param mask a char sequence needed to find in a generated name of the item
     * @return true if name of a current item fits the presented mask, or false if not
     */
    public abstract boolean filterCheck(CharSequence mask);
}
