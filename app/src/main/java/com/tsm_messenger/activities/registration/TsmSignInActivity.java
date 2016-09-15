package com.tsm_messenger.activities.registration;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.zxing.client.android.ScannerActivity;
import com.tsm_messenger.activities.BuildConfig;
import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.control.TsmMessageDialog;
import com.tsm_messenger.activities.main.MainActivity;
import com.tsm_messenger.activities.options.AppCompatPreferenceActivity;
import com.tsm_messenger.activities.options.TsmInfoActivity;
import com.tsm_messenger.activities.service.OpenFileActivity;
import com.tsm_messenger.activities.service.ServiceParameters;
import com.tsm_messenger.activities.service.TsmBackgroundService;
import com.tsm_messenger.activities.service.TsmDatabaseService;
import com.tsm_messenger.connection.SocketConnector;
import com.tsm_messenger.crypto.Crypter;
import com.tsm_messenger.crypto.EdDsaSigner;
import com.tsm_messenger.crypto.IKeyExportImporter;
import com.tsm_messenger.crypto.UserKeyExportImportManager;
import com.tsm_messenger.data.storage.TsmPasswordStorage;
import com.tsm_messenger.protocol.registration.Identifier;
import com.tsm_messenger.protocol.registration.RegistrationResponse;
import com.tsm_messenger.protocol.registration.Request;
import com.tsm_messenger.protocol.registration.Response;
import com.tsm_messenger.protocol.registration.ResponseBody;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.CustomValuesStorage;
import com.tsm_messenger.service.SharedPreferencesAccessor;
import com.tsm_messenger.service.UniversalHelper;

import org.bouncycastle.util.encoders.Hex;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * **********************************************************************
 * <p>
 * TELESENS CONFIDENTIAL
 * __________________
 * <p>
 * [2014] Telesens International Limited
 * All Rights Reserved.
 * <p>
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

public class TsmSignInActivity extends AppCompatActivity
        implements IKeyExportImporter {

    private static final String REG_LAYOUT_MODE = "regLayoutMode";
    private static final int LAYOUT_NEW_USER = 1;
    private static final int LAYOUT_EXISTING_USER = 2;
    private static final int LAYOUT_EXISTING_USER_CREDENTIALS = 3;
    private static final int MODE_REG = 0;
    private static final String RP_SERVER_PUBLIC_KEY = "spk";
    private static final String RP_SERVER_KEY_ID = "sKeyId";
    private static final String RP_USER_PUBLIC_KEY = "upk";
    private static final String RP_USER_SERCET_KEY = "usk";
    private static final String RP_LOGIN = "login";
    private static final String RP_PASS = "pass";
    private static final String RP_CON_IDENT = "conident";
    private static final String PREFERENCE_REG_MODE = "regMode";
    private static final byte MAX_PIN_BAD_INPUT = 10;
    private static int connectTryCount = 0;
    private int currentMode = MODE_REG;
    private boolean readyForRegister = false;
    private ProgressDialog mProgress;
    private Bundle regParam = new Bundle();
    private SharedPreferences settings;
    private BroadcastReceiver regReceiver;
    private int layoutId = 0;
    private boolean startMain = false;

    private static synchronized void incrementConnectTryCount() {
        connectTryCount++;
    }

    private static synchronized void resetConnectTryCount() {
        connectTryCount = 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }

        if (savedInstanceState != null) {
            Bundle bb = savedInstanceState.getBundle(PREFERENCE_REG_MODE);
            if (bb != null) {
                regParam = bb;
            }
        }
        initializeUI();
        putVersionToSettings();
        checkTaskRoot();

        settings.edit()
                .putString(SharedPreferencesAccessor.DOWNLOAD_FILE_FOLDER, getString(R.string.config_download_folder)).apply();

        if (regParam.getString(RP_SERVER_PUBLIC_KEY) == null) {
            Thread generateKey = new Thread() {
                @Override
                public void run() {
                    Crypter.KeyPair keyPair = Crypter.generateKeyPair();
                    regParam.putString(RP_USER_SERCET_KEY, keyPair.getSecretKey());
                    regParam.putString(RP_USER_PUBLIC_KEY, keyPair.getPublicKey());
                }
            };
            generateKey.start();
        }

        tryToLaunch();


    }

    @Override
    protected void onResume() {

        setLayoutEnabled();
        requestGrants();

        Intent bgSrv = new Intent(TsmSignInActivity.this, TsmBackgroundService.class);
        bgSrv.putExtra(ServiceParameters.BACKGROUNDACTION, ServiceParameters.BackgroundTask.TRANSACT_URL);
        startService(bgSrv);


        regReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onReceiveBroadcast(intent);
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(regReceiver,
                new IntentFilter(ServiceParameters.TSM_BROADCAST_REGISTRATION));

        ((ActivityGlobalManager) getApplication()).setCurrentActivity(this);

        refreshCheckPasswordListener();

        super.onResume();
    }

    private void refreshCheckPasswordListener() {
        SharedPreferences prefs = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE);
        boolean regState = prefs.getBoolean(CustomValuesStorage.REG_STATE, false);

        if (getIntent().getIntExtra(REG_LAYOUT_MODE, -1) != -1 || regState) {
            CheckBox chbCheckPin = (CheckBox) findViewById(R.id.chbCheckPin);
            if (chbCheckPin != null) {
                chbCheckPin.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        EditText ePass = (EditText) findViewById(R.id.reg_password);
                        if (isChecked) {
                            ePass.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                        } else {
                            ePass.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        }
                    }
                });
            }
        }
    }

    @Override
    protected void onPause() {
        clearReferences();
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(regReceiver);
        regReceiver = null;
    }

    @Override
    public void onBackPressed() {
        int layoutMode = getIntent().getIntExtra(REG_LAYOUT_MODE, -1);
        if (layoutMode != -1) {
            getIntent().removeExtra(REG_LAYOUT_MODE);
            if (layoutMode == LAYOUT_EXISTING_USER_CREDENTIALS) {
                getIntent().putExtra(REG_LAYOUT_MODE, LAYOUT_EXISTING_USER);
            }
            initializeUI();
        } else {
            UniversalHelper.pressBackButton(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.not_logged_in, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_help) {
            Intent intent;
            intent = new Intent(this, TsmInfoActivity.class);
            intent.putExtra(AppCompatPreferenceActivity.EXTRA_SHOW_FRAGMENT,
                    TsmInfoActivity.GeneralPreferenceFragment.class.getName());
            intent.putExtra(AppCompatPreferenceActivity.EXTRA_NO_HEADERS, true);
            startActivity(intent);
        }
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("mode", currentMode);
        outState.putBundle(PREFERENCE_REG_MODE, regParam);
        boolean isProgressShowing = mProgress.isShowing();
        outState.putBoolean("mProgress", isProgressShowing);
        if (isProgressShowing) {
            mProgress.dismiss();
        }

    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        currentMode = savedInstanceState.getInt("mode");
        //TODO check if regParam needs to be found by this key
        String regParamBundle = "regParm";
        Bundle bundle = savedInstanceState.getBundle(regParamBundle);
        if (bundle != null) {
            regParam = bundle;
        }
        boolean isProgressShowing = savedInstanceState.getBoolean("mProgress");
        if (isProgressShowing) {
            mProgress = ProgressDialog.show(this, "", getString(R.string.lbl_progress_wait));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 0) {

                String contents = data.getStringExtra("SCAN_RESULT");
                String uri = data.getStringExtra(OpenFileActivity.EXTRA_URI);
                if (contents != null) {
                    processQrScanResult(contents);
                } else {

                    Uri selectedImage = data.getData();
                    String filePath = UniversalHelper.getPath(this, selectedImage);
                    processFileImportResult(filePath);

                }

            } else {
                String uri = data.getStringExtra(OpenFileActivity.EXTRA_URI);
                if (uri != null) {
                    TextView tvExport = (TextView) findViewById(R.id.tvExportFolder);
                    if (tvExport != null) {
                        tvExport.setText(uri);
                    }
                }
            }
        } else {
            if (requestCode == 0) {
                showResults(Result.RESULT_CANCELED);
            } else {
                Toast.makeText(this, R.string.info_file_choose_canceled, Toast.LENGTH_SHORT).show();
            }
        }
    }

    //initialize-used methods------------------------------------------------------

    private void processFileImportResult(String fileName) {
        int ret;
        byte[] keyToRead = new byte[0];
        if (fileName != null) {
            File file = new File(fileName);
            file.mkdirs();

            BufferedInputStream bufferedInput = null;
            FileInputStream in = null;
            if (file.exists()) {
                try {
                    in = new FileInputStream(file.getPath());
                    bufferedInput = new BufferedInputStream(in);

                    int length = (int) file.length();
                    byte[] chars = new byte[length];
                    keyToRead = new byte[length];
                    int bytesRead = bufferedInput.read(chars);
                    System.arraycopy(chars, 0, keyToRead, 0, bytesRead);
                    ret = IKeyExportImporter.Result.RESULT_OK;

                    in.close();
                    bufferedInput.close();
                } catch (Exception e) {
                    UniversalHelper.logException(e);
                    keyToRead = null;
                    ret = IKeyExportImporter.Result.ERR_IMPORT_FILE_READ;
                } finally {
                    try {
                        if (in != null) {
                            in.close();
                        }
                        if (bufferedInput != null) {
                            bufferedInput.close();
                        }
                    } catch (IOException ex) {
                        UniversalHelper.logException(ex);
                    }
                }
            } else {
                keyToRead = null;
                ret = IKeyExportImporter.Result.ERR_IMPORT_FILE_READ;
            }
        } else {
            ret = IKeyExportImporter.Result.ERR_IMPORT_FILE_READ;
        }
        EdDsaSigner.getInstance().keyImportResult(ret, keyToRead, this);
    }

    private void processQrScanResult(String contents) {
        try {
            byte[] contentBytes = new BigInteger(contents, 16).toByteArray();
            if (contentBytes.length > 152) {//three keys having each 64 bytes length
                byte[] trash = contentBytes;
                contentBytes = new byte[152];
                for (int i = 1; i <= 152; i++) {
                    contentBytes[152 - i] = trash[trash.length - i];
                }
            }
            EdDsaSigner.getInstance().keyImportResult(IKeyExportImporter.Result.RESULT_OK, contentBytes, this);
        } catch (Exception e) {
            UniversalHelper.logException(e);
            EdDsaSigner.getInstance().showImportFail(this, IKeyExportImporter.Result.ERR_IMPORT_SCAN);
        }
    }

    private void onReceiveBroadcast(Intent intent) {
        String action = intent.getStringExtra(ServiceParameters.ACTION);
        String param = intent.getStringExtra(ServiceParameters.PARAM);
        String state = intent.getStringExtra(ServiceParameters.STATE);
        EditText edLogin = (EditText) TsmSignInActivity.this.findViewById(R.id.reg_login);
        if (mProgress != null) {
            mProgress.dismiss();
        }
        if (action.equals(ServiceParameters.RegistrationBroadcast.TSM_REG_ACTION_CHECKUSER)) {
            responseConnectionParam(param, state);
            return;
        }
        if (state.equals(ServiceParameters.OK)) {
            if (action.equals(ServiceParameters.RegistrationBroadcast.TSM_REG_ACTION_GETSERVERKEY)) {
                responseForSignIn(param);
                return;
            }
            if (action.equals(ServiceParameters.RegistrationBroadcast.TSM_REG_ACTION_REGISTRATION)) {
                processRegistrationResponse(param, edLogin);
            }
        } else {
            if (layoutId == R.layout.activity_tsm_sign_in)
                edLogin.setError(param);
            if (currentMode == MODE_REG) {

                TsmMessageDialog offlineReg = new TsmMessageDialog(this);
                offlineReg.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        TsmSignInActivity.this.finish();
                    }
                });
                offlineReg.show(R.string.title_tsm_offline, R.string.error_offline_reg_impossible);

            }
        }
    }

    @Override
    public void setContentView(int layoutResID) {
        this.layoutId = layoutResID;
        super.setContentView(layoutResID);
    }

    private void responseConnectionParam(String param, String state) {
        SharedPreferences prefs = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME,
                SharedPreferencesAccessor.PREFS_MODE);
        SharedPreferences.Editor editor = prefs.edit();


        if (state != null && !state.isEmpty() && ServiceParameters.OK.equals(state)
                && param != null && !param.isEmpty() && param.contains(":") && param.indexOf(":") != 0) {
            final ViewGroup viewGroup = (ViewGroup) ((ViewGroup) this
                    .findViewById(android.R.id.content)).getChildAt(0);
            viewGroup.setEnabled(true);
            ActivityGlobalManager.setTransactURL(param.substring(0, param.indexOf(":")),
                    Integer.valueOf(param.substring(param.indexOf(":") + 1)));

            editor.putInt(SharedPreferencesAccessor.FAILED_CONNECT, 0);
            editor.apply();
            setLayoutEnabled();
            if (startMain) {
                startMainActivity(ServiceParameters.SIGNIN, null, null);
            }

        } else {
            if (prefs.getInt(SharedPreferencesAccessor.FAILED_CONNECT, 0) > SharedPreferencesAccessor.TRY_CONNECT_COUNT) {
                String newUrl;
                if (BuildConfig.BALANCER_URL
                        .equals(prefs
                                .getString(SharedPreferencesAccessor.LAST_BALANCER_URL, BuildConfig.BALANCER_URL))) {
                    newUrl = BuildConfig.BALANCER2_URL;
                } else {
                    newUrl = BuildConfig.BALANCER_URL;
                }
                editor.putString(SharedPreferencesAccessor.LAST_BALANCER_URL,
                        newUrl);
                editor.putInt(SharedPreferencesAccessor.FAILED_CONNECT, 0);
                editor.apply();
            }
            if (connectTryCount > 10) {
                UniversalHelper.showSnackBar(
                        getWindow().getDecorView().getRootView(),
                        this, getString(R.string.error_offline_reg_impossible));
                resetConnectTryCount();
                return;
            }
            if (ActivityGlobalManager.getTransactionUrl() == null && connectTryCount <= 10) {
                Intent bgSrv = new Intent(TsmSignInActivity.this, TsmBackgroundService.class);
                bgSrv.putExtra(ServiceParameters.BACKGROUNDACTION, ServiceParameters.BackgroundTask.TRANSACT_URL);
                incrementConnectTryCount();
                startService(bgSrv);
            }
        }


    }

    private void requestGrants() {

        String[] grants = {Manifest.permission.CAMERA
                , Manifest.permission.READ_EXTERNAL_STORAGE
                , Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        for (String perms : grants) {
            if (ContextCompat.checkSelfPermission(this, perms) != PackageManager.PERMISSION_GRANTED &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(this, perms)) {
                ActivityCompat.requestPermissions(this, new String[]{perms}, 0);
            }
        }
    }

    private void setLayoutEnabled() {
        SharedPreferences prefs = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE);
        boolean regState = prefs.getBoolean(CustomValuesStorage.REG_STATE, false);

        if (!regState) {
            prefs.edit().putBoolean(SharedPreferencesAccessor.SD_CARD_WRITABLE, UniversalHelper.isSdCardWritable()).apply();
            int layout_reg_mode = getIntent().getIntExtra(REG_LAYOUT_MODE, -1);
            if (layout_reg_mode == -1) {
                preTermOfUse();
                if (ActivityGlobalManager.getTransactionUrl() == null) {
                    findViewById(R.id.btExistUser).setEnabled(false);
                    findViewById(R.id.btNewUser).setEnabled(false);
                } else {
                    findViewById(R.id.btExistUser).setEnabled(true);
                    findViewById(R.id.btNewUser).setEnabled(true);
                }
            }
        }

    }

    private void processRegistrationResponse(String param, EditText edLogin) {
        RegistrationResponse response = RegistrationResponse.createFromJson(param);
        if (response.getResult().equals(ServiceParameters.OK)) {
            EdDsaSigner.getInstance().saveServerKey(response.getServerEDSA_PK());

            ((ActivityGlobalManager) getApplicationContext()).setPin(regParam.getString(RP_PASS));
            TsmPasswordStorage.getInstance().initKey(regParam.getString(RP_PASS), regParam.getString(RP_LOGIN));

            String cryptKey = Crypter.getSharedKeyDH(getSecretKey(), getPublicKey());

            String responseBody = Crypter.decryptMessage(response.getEncryptedResonseBodyJSON(), cryptKey);
            ResponseBody body = ResponseBody.createFromJson(responseBody);
            Gson gson = (new GsonBuilder()).setDateFormat("yyyy-MM-dd\'T\'HH:mm:ssZ").create();
            String contactList = null;
            String chatList = null;
            String userId;

            if (body != null) {
                if (body.getContactsStatus() != null) {
                    contactList = gson.toJson(body.getContactsStatus());
                    chatList = gson.toJson(body.getGroupChatList());
                }
                userId = String.valueOf(body.getUserId());
            } else {
                userId = ((EditText) findViewById(R.id.reg_login)).getText().toString().trim();
            }

            SharedPreferences.Editor editor = settings.edit();
            editor.putString(
                    com.tsm_messenger.protocol.transaction.Request.BaseRequest.CON_IDENT,
                    regParam.getString(RP_CON_IDENT));
            editor.putBoolean(CustomValuesStorage.REG_STATE, true);
            editor.putString(
                    SharedPreferencesAccessor.USER_ID,
                    userId);
            editor.putString(
                    SharedPreferencesAccessor.USER_NICKNAME,
                    regParam.getString(RP_LOGIN));

            editor.apply();
            startMainActivity(ServiceParameters.REGISTRATION, contactList, chatList);
        } else {
            String error = response.getError();
            final TsmMessageDialog errorMessage = new TsmMessageDialog(this);

            switch (error) {
                case Response.ErrorCode.PUBLIC_USER_KEY_NULL:
                    errorMessage.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            tryToRegister();
                        }
                    });
                    errorMessage.show(R.string.title_error, R.string.info_user_key_not_exist_at_transact);
                    EdDsaSigner.getInstance().setKeyNotImported();
                    EdDsaSigner.getInstance().generateKey();
                    break;
                case Response.ErrorCode.NAME_IS_USED:
                    if (currentMode == MODE_REG) {
                        if (getIntent().getIntExtra(REG_LAYOUT_MODE, -1) == LAYOUT_NEW_USER) {
                            edLogin.setError(getString(R.string.error_name_used));
                        } else {
                            errorMessage.show(
                                    R.string.title_error, R.string.error_incorrect_credentials);
                            EdDsaSigner.getInstance().setPrivateKeyNotDecrypted();
                        }
                    } else {
                        edLogin.setError(error);
                    }
                    break;
                case Response.ErrorCode.KEY_IS_USED:
                    errorMessage.show(R.string.title_error, R.string.error_key_used);
                    break;
                default:
                    edLogin.setError(error);
                    break;
            }
        }
    }

    private void responseForSignIn(String param) {
        if (param.length() > 64) {
            regParam.putCharArray(RP_SERVER_PUBLIC_KEY, param.substring(0, 64).toCharArray());
            regParam.putString(RP_SERVER_KEY_ID, param.substring(64));
        } else {
            regParam.putCharArray(RP_SERVER_PUBLIC_KEY, param.toCharArray());
        }
        if (readyForRegister) {
            tryToRegister();
        }
    }

    @NonNull
    private String getPublicKey() {
        char[] charArray = regParam.getCharArray(RP_SERVER_PUBLIC_KEY);
        return new String(charArray != null ? charArray : new char[0]);
    }

    @NonNull
    private String getSecretKey() {
        String secretKey = regParam.getString(RP_USER_SERCET_KEY);
        secretKey = secretKey != null ? secretKey : "";
        return secretKey;
    }

    private void initializeUI() {
        SharedPreferences prefs = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE);
        boolean regState = prefs.getBoolean(CustomValuesStorage.REG_STATE, false);

        if (regState) {
            setContentView(R.layout.activity_tsm_sign_in);
            currentMode = MODE_REG + 1;
            switchLayout(currentMode, 0);
            refreshCheckPasswordListener();
            findViewById(R.id.btnBack_toolbar).setVisibility(View.GONE);
        } else {
            currentMode = MODE_REG;
            prefs.edit().putBoolean(SharedPreferencesAccessor.SD_CARD_WRITABLE, UniversalHelper.isSdCardWritable()).apply();
            int layout_reg_mode = getIntent().getIntExtra(REG_LAYOUT_MODE, -1);
            if (layout_reg_mode == -1) {
                setContentView(R.layout.activity_tsm_sign_in_entry_points);
                preTermOfUse();
            } else {
                switchLayout(currentMode, layout_reg_mode);
            }
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mProgress = ProgressDialog.show(this, "", getString(R.string.lbl_progress_wait));
        mProgress.dismiss();
    }

    private void showCredentialsTextBoxes() {
        setContentView(R.layout.activity_tsm_sign_in);
        refreshCheckPasswordListener();
    }

    private void preTermOfUse() {
        CheckBox cbAccept = (CheckBox) findViewById(R.id.cb_termofuse);

        findViewById(R.id.layoutRegButton).setVisibility(cbAccept.isChecked() ? View.VISIBLE : View.GONE);

        TextView tvUrl = (TextView) findViewById(R.id.tvLicinse_url);
        tvUrl.setText(getText(R.string.lbl_term_of_use));
        tvUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = "http://tsm-messenger.com/terms_en.html";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });

        cbAccept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (((CheckBox) view).isChecked()) {
                    findViewById(R.id.layoutRegButton).setVisibility(View.VISIBLE);
                } else {
                    findViewById(R.id.layoutRegButton).setVisibility(View.GONE);
                }
            }
        });

    }

    private void checkTaskRoot() {
        if (!isTaskRoot()) {
            final Intent rootActivityIntent = getIntent();
            final String intentAction = rootActivityIntent.getAction();
            if (rootActivityIntent.hasCategory(Intent.CATEGORY_LAUNCHER) && intentAction != null && intentAction.equals(Intent.ACTION_MAIN)) {
                finish();
            }
        }
    }

    private void putVersionToSettings() {
        settings = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME, SharedPreferencesAccessor.PREFS_MODE);
        //show in logs current version of an app
        try {
            String versionName = getApplicationContext().getPackageManager()
                    .getPackageInfo(getApplicationContext().getPackageName(), 0).versionName;
            settings.edit()
                    .putString(SharedPreferencesAccessor.APP_VERSION, versionName).apply();
        } catch (PackageManager.NameNotFoundException e) {
            UniversalHelper.logException(e);
        }
    }

    private void tryToLaunch() {
        if (ActivityGlobalManager.getTransactionUrl() == null) {
            Intent bgSrv = new Intent(TsmSignInActivity.this, TsmBackgroundService.class);
            bgSrv.putExtra(ServiceParameters.BACKGROUNDACTION, ServiceParameters.BackgroundTask.TRANSACT_URL);
            incrementConnectTryCount();
            startService(bgSrv);
        }

        if (currentMode != MODE_REG) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                loadKeyToSignIn();
            }
        } else {
            char[] serverPublicKey = regParam.getCharArray(RP_SERVER_PUBLIC_KEY);
            if (serverPublicKey == null || serverPublicKey.length == 0) {
                getServerPublicKey();
            }
        }
    }

    private void loadKeyToSignIn() {
        Long unlockTimestamp = settings.getLong(SharedPreferencesAccessor.UNLOCK_TIMESTAMP, new Date().getTime() - 10);

        if (new Date().getTime() <= unlockTimestamp) {
            showAppLockedMessage();
        }
    }

    private void clearReferences() {
        Activity currActivity = ((ActivityGlobalManager) getApplication()).getCurrentActivity();
        if (this.equals(currActivity))
            ((ActivityGlobalManager) getApplication()).setCurrentActivity(null);
    }

    private void tryToRegister() {
        EditText edLogin = (EditText) this.findViewById(R.id.reg_login);
        EditText ePass = (EditText) this.findViewById(R.id.reg_password);
        final String login = edLogin.getText().toString().trim();
        final String pass = ePass.getText().toString().trim();

        ActivityGlobalManager globalManager = (ActivityGlobalManager) getApplication();
        if (!globalManager.loginIsCorrect(login)) {
            new TsmMessageDialog(this)
                    .show(R.string.title_error, R.string.error_invalid_login);
            mProgress.dismiss();
        } else if (!passwordIsCorrect()) {
            new TsmMessageDialog(this)
                    .show(R.string.title_error, R.string.error_password_properties);
            mProgress.dismiss();
        } else if (!EdDsaSigner.getInstance().checkLogin(login)) {
            new TsmMessageDialog(this).show(R.string.title_error, R.string.error_incorrect_login_owner);
            mProgress.dismiss();
        } else {
            int layoutMode = getIntent().getIntExtra(REG_LAYOUT_MODE, -1);
            if (layoutMode != LAYOUT_EXISTING_USER_CREDENTIALS) {
                final TsmMessageDialog regDialog = new TsmMessageDialog(this);
                regDialog.setTitle(R.string.remember_your_password_and_keys);
                regDialog.setMessage(R.string.alert_remember_pass_keys);
                regDialog.setPositiveButton(R.string.btn_next, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new RegTask(login, pass).execute();
                        regDialog.dismiss();
                    }
                });
                regDialog.setNegativeButton(R.string.btn_back, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mProgress.dismiss();
                        regDialog.dismiss();
                    }
                });
                regDialog.show();
            } else {
                new RegTask(login, pass).execute();
            }
        }
    }
    //methods to manipulate UI-----------------------------------------------------


    private boolean passwordIsCorrect() {
        Boolean passIsCorrect;
        String errorMessage = getString(R.string.error_password_properties);
        errorMessage += "\n\n" + getString(R.string.error_password_problems);

        String mistakes = detectMistakes(errorMessage);
        passIsCorrect = errorMessage.equals(mistakes);

        if (!passIsCorrect) {
            final TsmMessageDialog messageDialog = new TsmMessageDialog(this);
            messageDialog.setMessage(mistakes + ".");
            messageDialog.setTitle(R.string.title_invalid_password);
            messageDialog.setNeutralButton(R.string.btn_ok, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    messageDialog.dismiss();
                }
            });

            messageDialog.show();
        }
        return passIsCorrect;
    }

    private String detectMistakes(String errorMessage) {
        String resultMessage = errorMessage;
        EditText tbPass = (EditText) findViewById(R.id.reg_password);
        String pin = tbPass.getText().toString();
        if ((pin.length() < 8) || (pin.length() > 20)) {
            resultMessage += "\n" + getString(R.string.error_password_invalid_length);
        }
        if (!pin.matches(".*[a-z].*")) {
            resultMessage += "\n" + getString(R.string.error_password_no_lowercase_found);
        }
        if (!pin.matches(".*[A-Z].*")) {
            resultMessage += "\n" + getString(R.string.error_password_no_uppercase_found);
        }
        if (!pin.matches(".*[0-9].*")) {
            resultMessage += "\n" + getString(R.string.error_password_no_digit_found);
        }
        if (!pin.matches("\\S+")) {
            resultMessage += "\n" + getString(R.string.error_password_space_symbol_found);
        }
        return resultMessage;
    }

    private void switchLayout(int mode, int layoutMode) {
        if (mode == MODE_REG) {
            switch (layoutMode) {
                case LAYOUT_NEW_USER:
                    showCredentialsTextBoxes();
                    EdDsaSigner.getInstance().setKeyNotImported();
                    ((Button) findViewById(R.id.btnSignIn)).setText(R.string.btn_register);
                    findViewById(R.id.tv_login_rules).setVisibility(View.VISIBLE);
                    findViewById(R.id.tv_pass_rules).setVisibility(View.VISIBLE);
                    final View btnChangeFolder = findViewById(R.id.btnChangeFolder);
                    btnChangeFolder.setVisibility(View.VISIBLE);
                    CheckBox chbExport = (CheckBox) findViewById(R.id.chbExportKey);
                    chbExport.setVisibility(View.VISIBLE);
                    chbExport.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            btnChangeFolder.setEnabled(isChecked);
                        }
                    });
                    findViewById(R.id.btn_securetype_info).setVisibility(View.VISIBLE);

                    TextView exportFolderView = (TextView) findViewById(R.id.tvExportFolder);
                    exportFolderView.setVisibility(View.VISIBLE);
                    exportFolderView.setText(CustomValuesStorage.KEYS_DIRECTORY);

                    break;
                case LAYOUT_EXISTING_USER:
                    setContentView(R.layout.activity_tsm_sign_in_load_key);
                    break;
                default:
                    showCredentialsTextBoxes();
                    ((Button) findViewById(R.id.btnSignIn)).setText(R.string.btn_login);
            }
        } else {
            findViewById(R.id.reg_login).setVisibility(View.GONE);
            ((Button) findViewById(R.id.btnSignIn)).setText(R.string.btn_login);
        }
    }

    private void startMainActivity(String state, String contactList, String chatList) {

        if (ActivityGlobalManager.getTransactionUrl() == null) {
            Intent bgSrv = new Intent(TsmSignInActivity.this, TsmBackgroundService.class);
            bgSrv.putExtra(ServiceParameters.BACKGROUNDACTION, ServiceParameters.BackgroundTask.TRANSACT_URL);
            incrementConnectTryCount();
            startService(bgSrv);
            if (ServiceParameters.REGISTRATION.equals(state))
                return;
        }
        Intent wsService = new Intent(this, SocketConnector.class);
        startService(wsService);

        Intent dbService = new Intent(this, TsmDatabaseService.class);
        dbService.putExtra(CustomValuesStorage.IntentExtras.INTENT_CONTACT_LIST, contactList);
        dbService.putExtra(CustomValuesStorage.IntentExtras.INTENT_CHAT_LIST, chatList);
        startService(dbService);

        ((ActivityGlobalManager) getApplicationContext()).bindServices();

        Intent mainActivity = new Intent(this, MainActivity.class);
        mainActivity.putExtra(ServiceParameters.MODE, state);
        if (currentMode == MODE_REG && getIntent().getIntExtra(REG_LAYOUT_MODE, -1) == LAYOUT_NEW_USER) {
            CheckBox chbExport = (CheckBox) findViewById(R.id.chbExportKey);
            if (chbExport != null) {
                if (chbExport.isChecked()) {
                    TextView tvExportFolder = (TextView) findViewById(R.id.tvExportFolder);
                    if (tvExportFolder != null && tvExportFolder.getVisibility() == View.VISIBLE) {
                        mainActivity.putExtra(MainActivity.EXTRA_EXPORT_FOLDER, tvExportFolder.getText());
                    }
                } else {
                    mainActivity.putExtra(MainActivity.EXTRA_PERFORM_EXPORT, false);
                }
            }
        }
        if (ServiceParameters.REGISTRATION.equals(state)) {
            int layout_reg_mode = getIntent().getIntExtra(REG_LAYOUT_MODE, -1);
            mainActivity.putExtra(REG_LAYOUT_MODE, layout_reg_mode);
        }
        mProgress.dismiss();
        startActivity(mainActivity);

        finish();
    }

    private void getServerPublicKey() {
        Intent bgSrv = new Intent(this, TsmBackgroundService.class);
        bgSrv.putExtra(ServiceParameters.BACKGROUNDACTION, ServiceParameters.BackgroundTask.GETSERVERPUBLICKEY);
        startService(bgSrv);
    }

    /**
     * Performs operations to register an app if it is not registered
     * or to login to the app if it is already registered
     *
     * @param view a button which called the method by OnClick event
     */
    public void btnConClickSignUp(View view) {
        mProgress.show();
        boolean regState;
        regState = settings.getBoolean(CustomValuesStorage.REG_STATE, false);
        if (regState) {
            EditText ePass = (EditText) this.findViewById(R.id.reg_password);
            String pass = ePass.getText().toString();
            String login = settings.getString(SharedPreferencesAccessor.USER_NICKNAME, "");
            if (TsmPasswordStorage.getInstance().validate(pass, login)) {
                ((ActivityGlobalManager) getApplicationContext()).setPin(pass);
                EdDsaSigner.getInstance().initPassword(pass, pass, this, false);
                SharedPreferences.Editor editor = settings.edit();
                editor.putInt(SharedPreferencesAccessor.INCORRECT_PIN_COUNT, 0);
                editor.putLong(SharedPreferencesAccessor.UNLOCK_TIMESTAMP, new Date().getTime() - 10);
                editor.apply();
                startMain = true;
                startMainActivity(ServiceParameters.SIGNIN, null, null);
            } else {
                ActivityGlobalManager manager = (ActivityGlobalManager) getApplication();
                int remainingEfforts = getRemainingAttempts();
                if (remainingEfforts > 0) {
                    String messageText = getString(R.string.error_password_validate);
                    String remainingAttemptsText = String.format(getResources()
                            .getQuantityString(R.plurals.attempt, remainingEfforts), remainingEfforts);
                    String err = String.format(messageText, remainingAttemptsText);
                    ePass.setError(err);
                    ePass.setText("");
                } else {
                    manager.showAuthEpicFailDialog(this);
                }
                mProgress.dismiss();
            }
        } else {
            readyForRegister = true;
            char[] serverPublicKey = regParam.getCharArray(RP_SERVER_PUBLIC_KEY);
            if (serverPublicKey == null || serverPublicKey.length == 0) {
                getServerPublicKey();
            } else {
                tryToRegister();
            }
        }
    }

    private int getRemainingAttempts() {

        SharedPreferences.Editor editor = settings.edit();

        int remainingEfforts = settings.getInt(SharedPreferencesAccessor.INCORRECT_PIN_COUNT, 0) + 1;

        int remainingAttempts = MAX_PIN_BAD_INPUT - remainingEfforts;
        if (remainingAttempts < 0) remainingAttempts = 0;
        editor.putInt(SharedPreferencesAccessor.INCORRECT_PIN_COUNT, remainingEfforts);

        long unlockTimestamp = new Date().getTime() + (remainingAttempts == 0 ? 60000 : -10);

        editor.putLong(SharedPreferencesAccessor.UNLOCK_TIMESTAMP, unlockTimestamp);
        editor.apply();
        return remainingAttempts;
    }

    /**
     * Starts operations to load private user key
     *
     * @param view a button which called the method by OnClick event
     */
    public void btn_loadKey(View view) {
        startImport();
    }

    private void startImport() {
        UserKeyExportImportManager.getInstance().importKey(this);
    }
    //methods to show warnings and dialogs-----------------------------------------

    private void showAppLockedMessage() {
        TsmMessageDialog appLockedMessage = new TsmMessageDialog(this);
        appLockedMessage.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                finish();
            }
        });
        appLockedMessage.show(R.string.title_app_locked, R.string.error_app_locked);
    }

    //methods to implement IKeyExportImporter interface

    @Override
    public void exportPrivateUserKey(boolean ignore) {
        //key export is done in MainActivity
    }

    @Override
    public void importPrivateUSerKey() {
        startImport();
    }

    @Override
    public void showResults(int result) {
        final TsmMessageDialog resultShowDialog = new TsmMessageDialog(this);
        int titleId;
        int msgId;
        View.OnClickListener retryListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resultShowDialog.dismiss();
                importPrivateUSerKey();
            }
        };
        View.OnClickListener ignoreListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resultShowDialog.dismiss();
                startExistingUserCredentialsLayout();
            }
        };
        switch (result) {
            case Result.ERR_IMPORT_SCAN:
                msgId = R.string.error_import_scan;
                break;
            case Result.ERR_IMPORT_FILE_READ:
                msgId = R.string.error_import_file_read;
                break;
            case Result.RESULT_CANCELED:
                msgId = R.string.info_import_canceled;
                break;
            case Result.ERR_IMPORT_INCORRECT_LOGIN:
                msgId = R.string.error_incorrect_login_owner;
                break;
            default:
                msgId = R.string.info_import_success;
                EdDsaSigner.getInstance().setPrivateKeyNotDecrypted();
                findViewById(R.id.btnLoadPrivateKey).setVisibility(View.GONE);
        }
        if (result == IKeyExportImporter.Result.RESULT_OK) {
            titleId = R.string.title_success;
            resultShowDialog.setNeutralButton(R.string.btn_ok, ignoreListener);
        } else {
            titleId = R.string.title_error;
            resultShowDialog.setPositiveButton(R.string.btn_yes, retryListener);
            resultShowDialog.setNegativeButton(R.string.btn_no, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    resultShowDialog.dismiss();
                }
            });
        }
        resultShowDialog.setTitle(titleId);
        resultShowDialog.setMessage(msgId);
        resultShowDialog.show();
    }

    @Override
    public Context getContext() {
        return TsmSignInActivity.this;
    }

    @Override
    public void scanQrCodeForImport() {
        Intent intent = new Intent(this, ScannerActivity.class);
        startActivityForResult(intent, 0);
    }

    @Override
    public void chooseFile() {
        Intent intent = new Intent(this, OpenFileActivity.class);
        intent.putExtra(OpenFileActivity.EXTRA_OPEN_FOR_WRITE, false);
        intent.putExtra(OpenFileActivity.EXTRA_VIEW_MODE, OpenFileActivity.MODE_FILES_ONLY);
        intent.putExtra(OpenFileActivity.EXTRA_HOME_DIRECTORY, CustomValuesStorage.KEYS_DIRECTORY);
        startActivityForResult(intent, 0);
    }

    /**
     * Changes the layout of a signIn activity to a registration mode
     *
     * @param view a button which called the method by OnClick event
     */
    public void startNewUserLayout(View view) {
        EdDsaSigner.getInstance().clearUserKeys();
        Intent intentToStart = getIntent();
        intentToStart.putExtra(REG_LAYOUT_MODE, LAYOUT_NEW_USER);
        initializeUI();
    }

    /**
     * Changes the layout of a signIn activity to a login mode
     *
     * @param view a button which called the method by OnClick event
     */
    public void startExistingUserLayout(View view) {
        Intent intentToStart = getIntent();
        intentToStart.putExtra(REG_LAYOUT_MODE, LAYOUT_EXISTING_USER);
        initializeUI();
    }

    /**
     * Changes the layout of a signIn activity to show input-credentials text boxes
     */
    public void startExistingUserCredentialsLayout() {
        Intent intentToStart = getIntent();
        intentToStart.putExtra(REG_LAYOUT_MODE, LAYOUT_EXISTING_USER_CREDENTIALS);
        initializeUI();
    }

    /**
     * Calls the BACK button press event by the custom button pressing
     *
     * @param view a button which called the method by OnClick event
     */
    public void backPressManual(View view) {
        onBackPressed();
    }

    /**
     * shows a dialog with key export properties description
     *
     * @param view a button that called such method with an onClick listener
     */
    public void btnKeyExportDetails_onClick(View view) {

        final TsmMessageDialog exportInfoDialog = new TsmMessageDialog(this);
        exportInfoDialog.show(R.string.title_info, R.string.info_export);
    }

    /**
     * calls an OpenFileActivity to change folder, where the private key will be stored
     *
     * @param view a button that called such method with an onClick listener
     */
    public void btnChangeFolder_onClick(View view) {
        Intent intent = new Intent(this, OpenFileActivity.class);
        intent.putExtra(OpenFileActivity.EXTRA_OPEN_FOR_WRITE, true);
        intent.putExtra(OpenFileActivity.EXTRA_VIEW_MODE, OpenFileActivity.MODE_DIRECTORIES_ONLY);
        intent.putExtra(OpenFileActivity.EXTRA_HOME_DIRECTORY, CustomValuesStorage.KEYS_DIRECTORY);
        startActivityForResult(intent, 1);
    }

    private class RegTask extends AsyncTask<Void, Void, Void> {

        private final String pass;
        private final String login;

        public RegTask(String login, String pass) {
            this.pass = pass;
            this.login = login;
        }

        @Override
        protected Void doInBackground(Void... params) {

            if (!EdDsaSigner.getInstance().isKeyImported()) {
                EdDsaSigner.getInstance().generateKey();
            }
            EdDsaSigner.getInstance().initPassword(pass, pass, TsmSignInActivity.this, true);

            regParam.putString(RP_LOGIN, login);
            regParam.putString(RP_PASS, pass);

            ((ActivityGlobalManager) getApplicationContext()).setPin(pass);
            String conIdent = UUID.randomUUID().toString();
            regParam.putString(RP_CON_IDENT, conIdent);

            Identifier identifier = new Identifier(Hex.toHexString(EdDsaSigner.getInstance().getRegistrationKey()));
            identifier.setHardwareId(((ActivityGlobalManager) getApplicationContext()).getHardwareId());
            identifier.setUserName(login);
            identifier.setUserPubKeyEDSA(EdDsaSigner.getInstance().getPublicUserKey());
            identifier.setConIdent(conIdent);
            String cryptKey = Crypter.getSharedKeyDH(getSecretKey(), getPublicKey());

            Gson gson = (new GsonBuilder()).setDateFormat(Param.DATE_FORMAT).create();
            String json = gson.toJson(identifier, Identifier.class);
            String enc = Crypter.encryptMessage(json, cryptKey);
            String sign = "";
            try {
                sign = EdDsaSigner.getInstance().sign(enc);
            } catch (Exception e) {
                UniversalHelper.logException(e);
            }

            Intent bgSrv = new Intent(TsmSignInActivity.this, TsmBackgroundService.class);
            bgSrv.putExtra(ServiceParameters.BACKGROUNDACTION, ServiceParameters.BackgroundTask.REGISTRATION);
            bgSrv.putExtra(Request.Registration.USER_PUBLIC_KEY, regParam.getString(RP_USER_PUBLIC_KEY));
            bgSrv.putExtra(Request.Registration.KEY_NUMBER, regParam.getString(RP_SERVER_KEY_ID));
            bgSrv.putExtra(Request.Registration.IDENTIFIER, enc);
            bgSrv.putExtra(Request.Registration.USER_SIGNATURE, sign);
            startService(bgSrv);
            return null;
        }
    }
}
