package com.tsm_messenger.service;

import com.tsm_messenger.protocol.registration.RegistrationResponse;
import com.tsm_messenger.protocol.registration.Request;
import com.tsm_messenger.protocol.registration.UserPublicKeysResponse;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;


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

public interface TsmRegistrationRest {

    /**
     * A request to get public key of the server to encrypt messages for him
     * or to validate his signature
     *
     * @return the Call instance containing response body
     */
    @GET("/spk")
    Call<ResponseBody> getServerPublickey();

    /**
     * A request sent to register an app at the server
     *
     * @param upub      public key of a user to validate his signature
     * @param ident     user login
     * @param sign      message signature
     * @param keyNumber a number of a server public key used for encryption
     * @return returns a call instance containing response body
     */
    @POST("/register")
    Call<RegistrationResponse> register(@Query(Request.Registration.USER_PUBLIC_KEY) String upub,
                                        @Query(Request.Registration.IDENTIFIER) String ident,
                                        @Query(Request.Registration.USER_SIGNATURE) String sign,
                                        @Query(Request.Registration.KEY_NUMBER) String keyNumber
    );

    /**
     * Gets the public keys from the server
     *
     * @param json a JSON string containing a list of user for which the public keys are needed
     * @return the Call instance containing response body
     */
    @POST("/public")
    Call<UserPublicKeysResponse> getPublicKey(@Query(Request.CheckPublicKeys.USER_REQUEST) String json);

    /**
     * Gets the current transaction server url
     *
     * @return the Call instance containing response body
     */
    @GET("/caddr")
    Call<ResponseBody> getTransactionUrm();
}
