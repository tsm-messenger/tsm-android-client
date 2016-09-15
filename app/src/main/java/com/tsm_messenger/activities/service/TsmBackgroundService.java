package com.tsm_messenger.activities.service;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tsm_messenger.activities.BuildConfig;
import com.tsm_messenger.connection.MessagePostman;
import com.tsm_messenger.data.storage.DataPreKeyStorage;
import com.tsm_messenger.data.storage.DbPreKey;
import com.tsm_messenger.protocol.registration.RegistrationResponse;
import com.tsm_messenger.protocol.registration.Request;
import com.tsm_messenger.protocol.registration.UserPublicKeysRequest;
import com.tsm_messenger.protocol.registration.UserPublicKeysResponse;
import com.tsm_messenger.service.SharedPreferencesAccessor;
import com.tsm_messenger.service.TsmRegistrationRest;
import com.tsm_messenger.service.UniversalHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

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
public class TsmBackgroundService extends IntentService {

    private static final String TAG = "TsmBackgroundService";


    /**
     * a constructor to initialize current instance
     */
    public TsmBackgroundService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getStringExtra(ServiceParameters.BACKGROUNDACTION);

        if (action.equals(ServiceParameters.BackgroundTask.NEW_KEYS)) {
            generateNewKeys();
            return;
        }
        if (action.equals(ServiceParameters.BackgroundTask.TRANSACT_URL)) {
            getTransactionUrl();
            return;
        }

        if (action.equals(ServiceParameters.BackgroundTask.USERKEY)) {
            userPublicKeyRequest(intent);
            return;
        }

        if (action.equals(ServiceParameters.BackgroundTask.GETSERVERPUBLICKEY)) {
            getServerKey();
        } else if (action.equals(ServiceParameters.BackgroundTask.REGISTRATION)) {
            registrationRequest(intent);
        }
    }

    private void getServerKey() {
        Retrofit restAdapter = new Retrofit.Builder()
                .baseUrl(getregistrationUrl())
                .build();
        TsmRegistrationRest tsmReg = restAdapter.create(TsmRegistrationRest.class);
        Call<ResponseBody> call = tsmReg.getServerPublickey();
        Intent syncResult = new Intent(ServiceParameters.TSM_BROADCAST_REGISTRATION);
        syncResult.putExtra(ServiceParameters.ACTION, ServiceParameters.RegistrationBroadcast.TSM_REG_ACTION_GETSERVERKEY);

        try {
            if(!performCall(call, syncResult)){
                syncResult.putExtra(ServiceParameters.STATE, ServiceParameters.FAIL);
                syncResult.putExtra(ServiceParameters.PARAM, "error");
            }
        } catch (IOException e) {
            UniversalHelper.logException(e);
            syncResult.putExtra(ServiceParameters.STATE, ServiceParameters.FAIL);
            syncResult.putExtra(ServiceParameters.PARAM, e.getMessage());
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(syncResult);
    }

    private boolean performCall(Call<ResponseBody> call, Intent syncResult) throws IOException {
        Response<ResponseBody> response;
        response = call.execute();
        boolean ret = false;

        if (response.isSuccessful()) {
            ret = true;
            String result = response.body().string();
            response.body().close();
            syncResult.putExtra(ServiceParameters.STATE, ServiceParameters.OK);
            syncResult.putExtra(ServiceParameters.PARAM, result);
        } else {
            response.errorBody().close();
        }
        return ret;
    }

    private void generateNewKeys() {
        int preKeysNeeded = 10;
        List<DbPreKey.PairDbPreKey> newPreKeys = DataPreKeyStorage.generatePreKeys(preKeysNeeded);
        MessagePostman.getInstance().sendPreKeysPackage(newPreKeys);
    }

    private void getTransactionUrl() {
        SharedPreferences settings = getSharedPreferences(
                SharedPreferencesAccessor.PREFS_NAME,
                SharedPreferencesAccessor.PREFS_MODE);
        String balanserUrl = settings.getString(SharedPreferencesAccessor.LAST_BALANCER_URL, BuildConfig.BALANCER_URL)
                + ":" + BuildConfig.BALANCER_PORT;

        Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                .create();
        Retrofit restAdapter = new Retrofit.Builder()
                .baseUrl(balanserUrl)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        TsmRegistrationRest tsmReg = restAdapter.create(TsmRegistrationRest.class);
        Call<ResponseBody> call = tsmReg.getTransactionUrm();
        Intent syncResult = new Intent(ServiceParameters.TSM_BROADCAST_REGISTRATION);
        syncResult.putExtra(ServiceParameters.ACTION, ServiceParameters.RegistrationBroadcast.TSM_REG_ACTION_CHECKUSER);

        try {
            if(!performCall(call, syncResult)){
                int cnt = settings.getInt(SharedPreferencesAccessor.FAILED_CONNECT, 0) + 1;
                SharedPreferences.Editor editor = settings.edit();
                editor.putInt(SharedPreferencesAccessor.FAILED_CONNECT, cnt);
                editor.apply();
                syncResult.putExtra(ServiceParameters.PARAM, ServiceParameters.RECONNECT);
                syncResult.putExtra(ServiceParameters.PARAM, "error");
            }
        } catch (java.net.ConnectException conE) {
            UniversalHelper.logException(conE);
            int cnt = settings.getInt(SharedPreferencesAccessor.FAILED_CONNECT, 0) + 1;
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(SharedPreferencesAccessor.FAILED_CONNECT, cnt);
            editor.apply();
            syncResult.putExtra(ServiceParameters.STATE, ServiceParameters.FAIL);
            syncResult.putExtra(ServiceParameters.PARAM, ServiceParameters.RECONNECT);
            syncResult.putExtra(SharedPreferencesAccessor.FAILED_CONNECT, cnt);

        } catch (Exception e) {
            UniversalHelper.logException(e);
            int cnt = settings.getInt(SharedPreferencesAccessor.FAILED_CONNECT, 0) + 1;
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(SharedPreferencesAccessor.FAILED_CONNECT, cnt);
            editor.apply();
            syncResult.putExtra(ServiceParameters.PARAM, ServiceParameters.RECONNECT);
            syncResult.putExtra(ServiceParameters.PARAM, e.getMessage());
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(syncResult);
    }

    private void registrationRequest(Intent intent) {

        Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                .create();
        Retrofit restAdapter = new Retrofit.Builder()
                .baseUrl(getregistrationUrl())
                //.baseUrl("http://10.4.30.154:8080")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        TsmRegistrationRest tsmReg = restAdapter.create(TsmRegistrationRest.class);

        String userPub = intent.getStringExtra(Request.Registration.USER_PUBLIC_KEY);
        String ident = intent.getStringExtra(Request.Registration.IDENTIFIER);
        String sign = intent.getStringExtra(Request.Registration.USER_SIGNATURE);
        String keyNumber = intent.getStringExtra(Request.Registration.KEY_NUMBER);

        Call<RegistrationResponse> call = tsmReg.register(userPub, ident, sign, keyNumber);
        Intent syncResult = new Intent(ServiceParameters.TSM_BROADCAST_REGISTRATION);
        syncResult.putExtra(ServiceParameters.ACTION, ServiceParameters.RegistrationBroadcast.TSM_REG_ACTION_REGISTRATION);

        try {
            Response<RegistrationResponse> response;
            response = call.execute();

            if (response.isSuccessful()) {
                RegistrationResponse result = response.body();
                syncResult.putExtra(ServiceParameters.STATE, ServiceParameters.OK);
                syncResult.putExtra(ServiceParameters.PARAM, gson.toJson(result, RegistrationResponse.class));
            } else {
                syncResult.putExtra(ServiceParameters.STATE, ServiceParameters.FAIL);
                syncResult.putExtra(ServiceParameters.PARAM, "error");
            }

        } catch (IOException e) {
            UniversalHelper.logException(e);
            syncResult.putExtra(ServiceParameters.STATE, ServiceParameters.FAIL);
            syncResult.putExtra(ServiceParameters.PARAM, e.getMessage());
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(syncResult);
    }

    private void userPublicKeyRequest(Intent intent) {
        UserPublicKeysRequest request = new UserPublicKeysRequest();
        List<Long> userList = new ArrayList<>();

        ArrayList<String> src;
        src = intent.getStringArrayListExtra(ServiceParameters.USER_LIST);
        Collections.sort(src);
        for (String id : src) {
            try {
                userList.add(Long.valueOf(id));
            } catch (NumberFormatException e) {
                UniversalHelper.logException(e);
            }
        }
        request.setUserIdList(userList);

        Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                .create();
        Retrofit restAdapter = new Retrofit.Builder()
                .baseUrl(getregistrationUrl())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        TsmRegistrationRest tsmReg = restAdapter.create(TsmRegistrationRest.class);

        String json = gson.toJson(request, UserPublicKeysRequest.class);
        Call<UserPublicKeysResponse> call = tsmReg.getPublicKey(json);

        Intent syncResult = new Intent(ServiceParameters.TSM_USER_PUBLIC_LIST);
        syncResult.putExtra(ServiceParameters.ACTION, ServiceParameters.TSM_USER_PUBLIC_LIST);

        try {
            Response<UserPublicKeysResponse> response;
            response = call.execute();

            if (response.isSuccessful()) {

                UserPublicKeysResponse result = response.body();

                syncResult.putExtra(ServiceParameters.STATE, ServiceParameters.OK);
                syncResult.putExtra(ServiceParameters.PARAM, gson.toJson(result, UserPublicKeysResponse.class));
            } else {
                syncResult.putExtra(ServiceParameters.STATE, ServiceParameters.FAIL);
                syncResult.putExtra(ServiceParameters.PARAM, "error");
            }

        } catch (IOException e) {
            UniversalHelper.logException(e);
            syncResult.putExtra(ServiceParameters.STATE, ServiceParameters.FAIL);
            syncResult.putExtra(ServiceParameters.PARAM, e.getMessage());
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(syncResult);
    }

    private String getregistrationUrl() {
        SharedPreferences settins = getSharedPreferences(
                SharedPreferencesAccessor.PREFS_NAME,
                SharedPreferencesAccessor.PREFS_MODE);
        return settins.getString(SharedPreferencesAccessor.LAST_BALANCER_URL,
                BuildConfig.BALANCER_URL)
                + ":" + BuildConfig.REGISTRATION_PORT;
    }
}
