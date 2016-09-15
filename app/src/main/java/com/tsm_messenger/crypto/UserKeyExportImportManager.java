package com.tsm_messenger.crypto;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.control.TsmMessageDialog;

import java.util.ArrayList;

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

public class UserKeyExportImportManager {
    private static final UserKeyExportImportManager INSTANCE = new UserKeyExportImportManager();

    private UserKeyExportImportManager() {
    }

    /**
     * Gets instance of UserKeyExportImportManager object
     *
     * @return the instance of UserKeyExportImportManager
     */
    public static UserKeyExportImportManager getInstance() {
        return INSTANCE;
    }

    /**
     * Shows modes of export as a dialog
     *
     * @param exporter an activity showing export actions via UI
     */
    public void exportKey(final IKeyExportImporter exporter) {
        if (exporter != null && exporter.getContext() != null) {

            final Context context = exporter.getContext();
            final TsmMessageDialog chooseModeDialog = new TsmMessageDialog(context);

            showChooseModeDialog(exporter, context, R.string.title_choose_export_mode,
                    chooseModeDialog, new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            if (i == 1) {
                                EdDsaSigner.getInstance().showPrivateUserKeyQr(context);
                            } else {
                                exporter.chooseFile();
                            }
                            chooseModeDialog.dismiss();
                        }
                    });
        }
    }

    /**
     * Imports user keys from the file or QR code
     *
     * @param importer an activity showing import results via UI
     */
    public void importKey(final IKeyExportImporter importer) {

        if (importer != null && importer.getContext() != null) {
            final Context context = importer.getContext();
            final TsmMessageDialog chooseModeDialog = new TsmMessageDialog(context);

            showChooseModeDialog(importer, context, R.string.title_choose_import_mode,
                    chooseModeDialog, new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            if (i == 1) {
                                importer.scanQrCodeForImport();
                            } else {
                                importer.chooseFile();
                            }
                            chooseModeDialog.dismiss();
                        }
                    });
        }
    }

    private void showChooseModeDialog(final IKeyExportImporter uiAgent, Context context, int title, TsmMessageDialog chooseModeDialog, AdapterView.OnItemClickListener itemClickListener) {
        chooseModeDialog.setTitle(title);
        ArrayAdapter<String> keyTransformModeAdapter = getKeyTransformModeAdapter(context);
        chooseModeDialog.setList(keyTransformModeAdapter, itemClickListener);
        chooseModeDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                uiAgent.showResults(IKeyExportImporter.Result.RESULT_CANCELED);
            }
        });
        chooseModeDialog.show();
    }

    private ArrayAdapter<String> getKeyTransformModeAdapter(Context context) {
        if (context != null) {
            ArrayList<String> keyTransformList = new ArrayList<>();
            keyTransformList.add(context.getString(R.string.lbl_text_file));
            keyTransformList.add(context.getString(R.string.lbl_qr_code));
            return new ArrayAdapter<>(
                    context, R.layout.simple_list_item, keyTransformList);
        } else {
            return null;
        }
    }
}
