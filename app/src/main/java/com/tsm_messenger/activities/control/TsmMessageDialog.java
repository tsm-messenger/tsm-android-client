package com.tsm_messenger.activities.control;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tsm_messenger.activities.R;

/*************************************************************************
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

public class TsmMessageDialog extends Dialog {
    private final ListView lvList;
    private final TextView tvTitle;
    private final TextView tvMessage;
    private final TextView tvSeparator;
    private final Button btLeft;
    private final Button btMiddle;
    private final Button btRight;
    private final EditText tbInput;
    private final ScrollView svMessageWrapper;
    private final ImageView imageView;
    private final LinearLayout llBody;
    private int btnCount;

    /**
     * A constructor to initialize the dialog instance
     *
     * @param context a context to build a dialog body
     */
    public TsmMessageDialog(Context context) {
        super(context);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.alert_dialog);
        llBody = (LinearLayout) findViewById(R.id.layout_dialog);
        tvTitle = (TextView) findViewById(R.id.lblTitle_dialog);
        tvMessage = (TextView) findViewById(R.id.lblMessage_dialog);
        tvSeparator = (TextView) findViewById(R.id.horisontalSeparator_dialog);
        lvList = (ListView) findViewById(R.id.lvList_dialog);
        btLeft = (Button) findViewById(R.id.btnDialog_left);
        btMiddle = (Button) findViewById(R.id.btnDialog_middle);
        btRight = (Button) findViewById(R.id.btnDialog_right);
        tbInput = (EditText) findViewById(R.id.tbPhoneInput_dialog);
        imageView = (ImageView) findViewById(R.id.img_dialog);
        svMessageWrapper = (ScrollView) findViewById(R.id.scrollView);

        btnCount = 0;

        setCanceledOnTouchOutside(false);

    }

    /**
     * Makes a "positive" button active and visible
     *
     * @param labelResId the ID of a string to set a label to the button
     * @param listener   an onClick listener for button
     */
    public void setPositiveButton(int labelResId, View.OnClickListener listener) {
        btnCount++;
        btRight.setVisibility(View.VISIBLE);
        btRight.setText(labelResId);
        btRight.setOnClickListener(listener);
        addSeparator();
    }

    /**
     * Makes a "negative" button active and visible
     *
     * @param labelResId the ID of a string to set a label to the button
     * @param listener   an onClick listener for the button
     */
    public void setNegativeButton(int labelResId, View.OnClickListener listener) {
        btnCount++;
        btLeft.setVisibility(View.VISIBLE);
        btLeft.setText(labelResId);
        btLeft.setOnClickListener(listener);
        addSeparator();
    }

    /**
     * Makes a "neutral" button active and visible
     *
     * @param labelResId id of a string to set a label to the button
     * @param listener   an onClick listener for the button
     */
    public void setNeutralButton(int labelResId, View.OnClickListener listener) {
        btnCount++;
        btMiddle.setVisibility(View.VISIBLE);
        btMiddle.setText(labelResId);
        btMiddle.setOnClickListener(listener);
        addSeparator();
    }

    /**
     * Makes a "neutral" button active and visible
     *
     * @param text     a string to set a label to the button
     * @param listener an onClick listener for the button
     */
    private void setNeutralButton(CharSequence text, View.OnClickListener listener) {
        btnCount++;
        btMiddle.setVisibility(View.VISIBLE);
        btMiddle.setText(text);
        btMiddle.setOnClickListener(listener);
        addSeparator();
    }

    /**
     * Makes a message of the dialog visible
     *
     * @param resourceId the ID of a text to display as a message
     */
    public void setMessage(int resourceId) {
        svMessageWrapper.setVisibility(View.VISIBLE);
        tvMessage.setVisibility(View.VISIBLE);
        tvMessage.setText(resourceId);
    }

    /**
     * Makes a message of the dialog visible
     *
     * @param message a text to display as a message
     */
    public void setMessage(CharSequence message) {
        svMessageWrapper.setVisibility(View.VISIBLE);
        tvMessage.setVisibility(View.VISIBLE);
        tvMessage.setText(message);
    }

    /**
     * Gets the text of the text box in the dialog
     *
     * @return a string containing the text from text box
     */
    public String getTextFromTextBox() {
        return tbInput.getText().toString();
    }

    @Override
    public void setTitle(int titleId) {
        super.setTitle(titleId);
        tvTitle.setVisibility(View.VISIBLE);
        tvTitle.setText(titleId);
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        tvTitle.setVisibility(View.VISIBLE);
        tvTitle.setText(title);
    }

    /**
     * Makes the listView of the dialog visible
     *
     * @param contents          content list for a listView
     * @param itemClickListener a listener for an itemClick event
     */
    public void setList(ListAdapter contents, AdapterView.OnItemClickListener itemClickListener) {
        lvList.setAdapter(contents);
        lvList.setOnItemClickListener(itemClickListener);
        lvList.setVisibility(View.VISIBLE);
        lvList.setClickable(true);
        llBody.setMinimumHeight(0);
    }

    private void addSeparator() {
        if (tvSeparator.getVisibility() != View.VISIBLE) {
            tvSeparator.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Sets the provided title and message and shows the dialog
     *
     * @param titleId   the ID of a title text
     * @param messageId the ID of a message text
     */
    public void show(int titleId, int messageId) {
        setTitle(titleId);
        setMessage(messageId);
        setNeutralButton(R.string.btn_ok, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        show();
    }

    /**
     * Sets the provided title and message and shows the dialog
     *
     * @param title   a title text
     * @param message a message text
     */
    public void show(String title, String message) {
        setTitle(title);
        setMessage(message);
        setNeutralButton(R.string.btn_ok, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        show();
    }

    @Override
    public void show() {
        if (btnCount == 1) {
            Button activeButton = getActiveButton();
            ViewGroup.LayoutParams params = activeButton.getLayoutParams();
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            activeButton.setLayoutParams(params);
            activeButton.setMinHeight(-1);

            LinearLayout llButtonContainer = (LinearLayout) findViewById(R.id.llButtonContainer);
            llButtonContainer.setWeightSum((float) 2.5);
        }
        super.show();
    }

    private Button getActiveButton() {
        if (btLeft.getVisibility() == View.VISIBLE) {
            return btLeft;
        } else if (btRight.getVisibility() == View.VISIBLE) {
            return btRight;
        } else {
            return btMiddle;
        }
    }

    /**
     * Makes the text box active and visible
     *
     * @param text a text to place into a textBox
     */
    public void setTextBox(String text) {
        tbInput.setVisibility(View.VISIBLE);
        tbInput.setText(text);
        tbInput.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        tbInput.setHint("");
        tbInput.setSelection(text.length());
    }

    /**
     * Makes the image visible
     *
     * @param image an image to show
     */
    public void setImage(Bitmap image) {
        imageView.setVisibility(View.VISIBLE);
        imageView.setImageBitmap(image);
    }

    /**
     * Hides the "positive" button
     */
    public void hidePositiveButton() {
        btRight.setVisibility(View.GONE);
    }

    /**
     * Hides the "negative" button
     */
    public void hideNegativeButton() {
        btLeft.setVisibility(View.GONE);
    }
}
