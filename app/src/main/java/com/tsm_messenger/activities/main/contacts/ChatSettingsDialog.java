package com.tsm_messenger.activities.main.contacts;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.tsm_messenger.activities.R;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.service.UniversalHelper;

import java.util.concurrent.TimeUnit;

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
public abstract class ChatSettingsDialog extends Dialog {

    /**
     * a constructor that initializes a dialog instance
     *
     * @param context    a context to create a dialog body
     * @param secureType a chat secure type to set it as default
     */
    public ChatSettingsDialog(Context context, int secureType) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.chat_settings_dialog);

        TextView tvSequrity = (TextView) findViewById(R.id.sequreLabel);
        tvSequrity.setVisibility(View.VISIBLE);
        Spinner spinSecure = (Spinner) findViewById(R.id.sequreType);
        spinSecure.setAdapter(ArrayAdapter.createFromResource(getContext(),
                R.array.sequretype_array,
                R.layout.simple_list_item));
        spinSecure.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                EditText liveTime = (EditText) findViewById(R.id.tbLiveTime);
                Spinner spinTime = (Spinner) findViewById(R.id.timeType);
                LinearLayout timeLayout = (LinearLayout) findViewById(R.id.timeLayout);
                TextView deliveryTime = (TextView) findViewById(R.id.lbl_delivery_time);

                int visibility = position == 2 ? View.VISIBLE : View.GONE;

                liveTime.setVisibility(visibility);
                timeLayout.setVisibility(visibility);
                spinTime.setVisibility(visibility);
                deliveryTime.setVisibility(visibility);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                //do nothing
            }
        });
        Button btnCancel = (Button) findViewById(R.id.btnDialog_cancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        Button btnOk = (Button) findViewById(R.id.btnDialog_ok);
        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                refreshSecureType();
            }
        });

        initSecureState(secureType);
    }

    private void initSecureState(int secureType) {
        Spinner spinSequre = (Spinner) findViewById(R.id.sequreType);
        if (secureType == Integer.valueOf(Param.ChatSecureLevel.ALL_HISTORY_KEEP)) {
            spinSequre.setSelection(0);
        } else if (secureType == Integer.valueOf(Param.ChatSecureLevel.NOTHING_KEEP)) {
            spinSequre.setSelection(1);
        } else {
            spinSequre.setSelection(2);
            EditText liveTime = (EditText) findViewById(R.id.tbLiveTime);
            Spinner spinTime = (Spinner) findViewById(R.id.timeType);
            if (secureType == Integer.valueOf(Param.ChatSecureLevel.KEEP_UNTIL_DELIVERY)) {
                liveTime.setText("0");
            } else {
                int lifeTimeMarker = UniversalHelper.getLifeTimeMarker(secureType);
                spinTime.setSelection(lifeTimeMarker);
                switch (lifeTimeMarker) {
                    case 0:
                        liveTime.setText(String.valueOf(secureType));
                        break;
                    case 1:
                        liveTime.setText(String.valueOf(TimeUnit.MINUTES.toHours(secureType)));
                        break;
                    case 2:
                        liveTime.setText(String.valueOf(TimeUnit.MINUTES.toDays(secureType)));
                        break;
                    default:
                        liveTime.setText(String.valueOf(TimeUnit.MINUTES.toDays(secureType) / 7));
                }
            }
        }
    }

    /**
     * applies secureType changes to the UI. should be overridden by caller
     */
    protected abstract void refreshSecureType();

    private int getMessageLifeTime() {
        EditText liveTime = (EditText) findViewById(R.id.tbLiveTime);
        Spinner spinTime = (Spinner) findViewById(R.id.timeType);

        String val = liveTime.getText().toString();
        if (val.isEmpty() || "0".equals(val)) {
            return Integer.valueOf(Param.ChatSecureLevel.KEEP_UNTIL_DELIVERY);
        } else {
            return convertToMinute(Math.min(Integer.valueOf(val), Integer.MAX_VALUE), spinTime.getSelectedItemPosition());
        }
    }

    private int convertToMinute(int minutes, int type) {
        int result;
        switch (type) {
            case 0:// Minute
                result = minutes;
                break;
            case 1:// Hour
                result = (int) TimeUnit.HOURS.toMinutes((long) minutes);
                break;
            case 2:// Day
                result = (int) TimeUnit.DAYS.toMinutes((long) minutes);
                break;
            case 3:// Week
                result = (int) TimeUnit.DAYS.toMinutes((long) minutes) * 7;
                break;
            default:
                result = minutes;
        }
        return result;
    }

    /**
     * gets the integer number of the secure type
     *
     * @return returns the integer representation of the current secure type
     */
    protected int getIntSecureType() {
        int result = 0;
        Spinner spinSequre = (Spinner) findViewById(R.id.sequreType);
        switch ((int) spinSequre.getSelectedItemId()) {
            case 0:
                result = Integer.valueOf(Param.ChatSecureLevel.ALL_HISTORY_KEEP);
                break;
            case 1:
                result = Integer.valueOf(Param.ChatSecureLevel.NOTHING_KEEP);
                break;
            case 2:
                result = getMessageLifeTime();
                break;
            default: //do nothing
        }
        return result;
    }
}
