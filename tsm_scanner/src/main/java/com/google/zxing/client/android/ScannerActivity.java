package com.google.zxing.client.android;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import com.google.zxing.Result;

import zxing.library.DecodeCallback;
import zxing.library.ZXingFragment;


public class ScannerActivity extends FragmentActivity {

    private VibrateManager vibrateManager;
    private boolean resultPosted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);
        vibrateManager = new VibrateManager(this);
        resultPosted = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        final ZXingFragment xf = (ZXingFragment) getSupportFragmentManager().findFragmentById(R.id.scanner);
        xf.setDecodeCallback(new DecodeCallback() {

            @Override
            public void handleBarcode(Result result) {
                resultPosted = true;
                vibrateManager.vibrate();
                Intent data = new Intent();
                data.putExtra("SCAN_RESULT", result.getText());
                setResult(RESULT_OK, data);
                finish();
            }

        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!resultPosted) {
            Intent data = new Intent();
            data.putExtra("SCAN_RESULT", "");
            setResult(RESULT_CANCELED, data);
            finish();
        }
    }
}
