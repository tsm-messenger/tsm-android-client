package com.tsm_messenger.activities.options;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

import com.tsm_messenger.activities.R;

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

public class NumberPickerPreference extends DialogPreference {

    private static final int MAX_VALUE = 3600;
    private static final int MIN_VALUE = 0;

    private int maxValue, minValue;

    private NumberPicker picker;
    private int value;

    /**
     * A constructor to initialize custom preference
     *
     * @param context a context to create a preference
     * @param attrs   the provided attributes
     */
    public NumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.NumberPickerPreference,
                0, 0);

        try {
            maxValue = a.getInteger(R.styleable.NumberPickerPreference_maxValue, MAX_VALUE);
            minValue = a.getInteger(R.styleable.NumberPickerPreference_minValue, MIN_VALUE);
        } finally {
            a.recycle();
        }

    }

    /**
     * A constructor to initialize custom preference
     *
     * @param context      a context to create a preference
     * @param attrs        the provided attributes
     * @param defStyleAttr an attribute in the current theme that contains a
     *                     reference to a style resource that supplies default values for
     *                     the view. Can be 0 to not look for defaults.
     */
    public NumberPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.NumberPickerPreference,
                0, 0);

        try {
            maxValue = a.getInteger(R.styleable.NumberPickerPreference_maxValue, MAX_VALUE);
            minValue = a.getInteger(R.styleable.NumberPickerPreference_minValue, MIN_VALUE);
        } finally {
            a.recycle();
        }

    }

    @Override
    protected View onCreateDialogView() {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER;

        picker = new NumberPicker(getContext());
        picker.setLayoutParams(layoutParams);

        FrameLayout dialogView = new FrameLayout(getContext());
        dialogView.addView(picker);

        return dialogView;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        picker.setMinValue(minValue);
        picker.setMaxValue(maxValue);
        picker.setValue(getValue());
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            setValue(picker.getValue());
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, MIN_VALUE);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        setValue(restorePersistedValue ? getPersistedInt(MIN_VALUE) : (Integer) defaultValue);
    }

    private int getValue() {
        return this.value;
    }

    private void setValue(int value) {
        this.value = value;
        persistInt(this.value);
        callChangeListener(value);
    }
}