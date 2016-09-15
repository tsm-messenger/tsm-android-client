package com.tsm_messenger.activities.options;


import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.service.OpenFileActivity;
import com.tsm_messenger.service.SharedPreferencesAccessor;
import com.tsm_messenger.service.UniversalHelper;

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

public class TsmPreferencesActivity extends AppCompatPreferenceActivity {

    private static final Preference.OnPreferenceChangeListener
            sBindPreferenceSummaryToValueListener = new PreferenceChangeListener();

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        if (isIntPreference(preference)) {
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    preference.getContext().getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME,
                            SharedPreferencesAccessor.PREFS_MODE)
                            .getInt(preference.getKey(), 0));
        } else {
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    preference.getContext().getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME,
                            SharedPreferencesAccessor.PREFS_MODE)
                            .getString(preference.getKey(), ""));
        }
    }

    private static boolean isIntPreference(Preference preference) {
        String key = preference.getKey();
        return key.equals(SharedPreferencesAccessor.VIBRATE_SILENT_TIME) ||
                key.equals(SharedPreferencesAccessor.PRIVATEKEY_SHOW_TIME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     *
     * @param fragmentName a name of a fragment to validate
     */
    protected boolean isValidFragment(String fragmentName) {
        try {
            return Class.forName(fragmentName).isInstance(new GeneralPreferenceFragment());
        } catch (ClassNotFoundException e) {
            UniversalHelper.logException(e);
            return false;
        }
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    public static class GeneralPreferenceFragment extends PreferenceFragment {

        private Preference openFileDialogPreference;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            PreferenceManager prefMgr = getPreferenceManager();
            prefMgr.setSharedPreferencesName(SharedPreferencesAccessor.PREFS_NAME);
            prefMgr.setSharedPreferencesMode(SharedPreferencesAccessor.PREFS_MODE);

            addPreferencesFromResource(R.xml.pref_general);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference(SharedPreferencesAccessor.VIBRATE_SILENT_TIME));
            bindPreferenceSummaryToValue(findPreference(SharedPreferencesAccessor.PRIVATEKEY_SHOW_TIME));
            openFileDialogPreference = findPreference(SharedPreferencesAccessor.DOWNLOAD_FILE_FOLDER);
            bindPreferenceSummaryToValue(openFileDialogPreference);
            openFileDialogPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startOpenFileDialog();
                    return true;
                }
            });
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

            if (resultCode == RESULT_OK) {
                String fileName = data.getExtras().getString(OpenFileActivity.EXTRA_URI, "");
                openFileDialogPreference.getSharedPreferences()
                        .edit().putString(SharedPreferencesAccessor.DOWNLOAD_FILE_FOLDER, fileName).apply();
                sBindPreferenceSummaryToValueListener
                        .onPreferenceChange(openFileDialogPreference, fileName);
            } else {
                if (data != null) {
                    Integer intExtra = data.getIntExtra(OpenFileActivity.EXTRA_URI, RESULT_OK);
                    if (intExtra == RESULT_CANCELED) {
                        Toast.makeText(getActivity(), R.string.file_select_canceled, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }

        private void startOpenFileDialog() {
            String currentDownloadFolder = openFileDialogPreference.getSharedPreferences()
                    .getString(SharedPreferencesAccessor.DOWNLOAD_FILE_FOLDER, getString(R.string.config_download_folder));

            Intent intent = new Intent(getActivity(), OpenFileActivity.class);
            intent.putExtra(OpenFileActivity.EXTRA_OPEN_FOR_WRITE, true);
            intent.putExtra(OpenFileActivity.EXTRA_VIEW_MODE, OpenFileActivity.MODE_DIRECTORIES_ONLY);
            intent.putExtra(OpenFileActivity.EXTRA_HOME_DIRECTORY, currentDownloadFolder);
            startActivityForResult(intent, 0);
        }
    }

}
