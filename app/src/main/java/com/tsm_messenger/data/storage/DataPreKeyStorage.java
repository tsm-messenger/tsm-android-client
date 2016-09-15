package com.tsm_messenger.data.storage;

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

public abstract class DataPreKeyStorage {

    private DataPreKeyStorage() {
    }

    /**
     * Gets a package of new Diffie-Hellman pre-keys
     *
     * @param preKeysNeededCount the expected count of pre-keys in package
     */
    public static List<DbPreKey.PairDbPreKey> generatePreKeys(int preKeysNeededCount) {
        List<DbPreKey.PairDbPreKey> newPreKeysList = new ArrayList<>();
        DbPreKey currentNewKey;
        for (int i = 0; i < preKeysNeededCount; i++) {
            currentNewKey = new DbPreKey();
            newPreKeysList.add(currentNewKey.getPublicPart());
        }
        return newPreKeysList;
    }
}
