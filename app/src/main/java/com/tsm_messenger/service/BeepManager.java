/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tsm_messenger.service;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Vibrator;

import com.tsm_messenger.activities.R;

import java.io.IOException;
import java.util.Date;

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
 * --------------------------------------------------------------------------------
 * NOTICES FOR BARCODE4J
 * --------------------------------------------------------------------------------
 * Barcode4J
 * Copyright 2002-2010 Jeremias Märki
 * Copyright 2005-2006 Dietmar Bürkle
 * Portions of this software were contributed under section 5 of the
 * Apache License. Contributors are listed under:
 * http://barcode4j.sourceforge.net/contributors.html
 * <p/>
 * --------------------------------------------------------------------------------
 * NOTICES FOR BARCODE4J
 * --------------------------------------------------------------------------------
 * Barcode4J
 * Copyright 2002-2010 Jeremias Märki
 * Copyright 2005-2006 Dietmar Bürkle
 * Portions of this software were contributed under section 5 of the
 * Apache License. Contributors are listed under:
 * http://barcode4j.sourceforge.net/contributors.html
 * <p/>
 * --------------------------------------------------------------------------------
 * NOTICES FOR BARCODE4J
 * --------------------------------------------------------------------------------
 * Barcode4J
 * Copyright 2002-2010 Jeremias Märki
 * Copyright 2005-2006 Dietmar Bürkle
 * Portions of this software were contributed under section 5 of the
 * Apache License. Contributors are listed under:
 * http://barcode4j.sourceforge.net/contributors.html
 * <p/>
 * --------------------------------------------------------------------------------
 * NOTICES FOR BARCODE4J
 * --------------------------------------------------------------------------------
 * Barcode4J
 * Copyright 2002-2010 Jeremias Märki
 * Copyright 2005-2006 Dietmar Bürkle
 * Portions of this software were contributed under section 5 of the
 * Apache License. Contributors are listed under:
 * http://barcode4j.sourceforge.net/contributors.html
 * <p/>
 * --------------------------------------------------------------------------------
 * NOTICES FOR BARCODE4J
 * --------------------------------------------------------------------------------
 * Barcode4J
 * Copyright 2002-2010 Jeremias Märki
 * Copyright 2005-2006 Dietmar Bürkle
 * Portions of this software were contributed under section 5 of the
 * Apache License. Contributors are listed under:
 * http://barcode4j.sourceforge.net/contributors.html
 * <p/>
 * --------------------------------------------------------------------------------
 * NOTICES FOR BARCODE4J
 * --------------------------------------------------------------------------------
 * Barcode4J
 * Copyright 2002-2010 Jeremias Märki
 * Copyright 2005-2006 Dietmar Bürkle
 * Portions of this software were contributed under section 5 of the
 * Apache License. Contributors are listed under:
 * http://barcode4j.sourceforge.net/contributors.html
 * <p/>
 * --------------------------------------------------------------------------------
 * NOTICES FOR BARCODE4J
 * --------------------------------------------------------------------------------
 * Barcode4J
 * Copyright 2002-2010 Jeremias Märki
 * Copyright 2005-2006 Dietmar Bürkle
 * Portions of this software were contributed under section 5 of the
 * Apache License. Contributors are listed under:
 * http://barcode4j.sourceforge.net/contributors.html
 */
/**
 * --------------------------------------------------------------------------------
 * NOTICES FOR BARCODE4J
 * --------------------------------------------------------------------------------
 * Barcode4J
 * Copyright 2002-2010 Jeremias Märki
 * Copyright 2005-2006 Dietmar Bürkle
 * Portions of this software were contributed under section 5 of the
 * Apache License. Contributors are listed under:
 * http://barcode4j.sourceforge.net/contributors.html
 */

/**
 * CHANGES:
 * changed package from com.google.zxing.client.android to com.telesens.scanner
 * removed reference to classes:
 *     android.content.SharedPreferences
 *     android.content.res.AssetFileDescriptor
 *     android.media.AudioManager
 *     android.media.MediaPlayer
 *     android.preference.PreferenceManager
 *     android.util.Log
 *     java.io.IOException
 * removed fields
 *     String TAG, float BEEP_VOLUME, MediaPlayer mediaPlayer, boolean playBeep, boolean vibrate
 * removed row from method playBeep():
 *     this.mediaPlayer = null;
 * removed all rows from updatePrefs() method, except
 *     vibrate = true;
 * renamed playBeepSoundAndVibrate() method to vibrate() and removed lines:
 *     if (playBeep && mediaPlayer != null) {
 *         mediaPlayer.start();
 *     }
 * removed methods shoudBeep() and buildMediaPlayer()
 */
public final class BeepManager
        implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private static final long VIBRATE_DURATION = 200L;
    private static final float BEEP_VOLUME = 0.10f;
    private static final BeepManager INSTANCE = new BeepManager();
    private Activity activity;
    private int vibrateSilentPeriod = 0;
    private MediaPlayer mediaPlayer;
    private boolean vibrate;
    private long lastVibrated = 0;
    private SharedPreferences settings;

    private BeepManager() {
    }

    /**
     * Gets the current active instance of a BeepManager
     *
     * @param activity the current active activity to bind
     * @return an updated instance of a BeepManager
     */
    public static BeepManager getInstance(Activity activity) {
        INSTANCE.activity = activity;
        INSTANCE.mediaPlayer = null;
        INSTANCE.updatePrefs();
        return INSTANCE;
    }

    /**
     * Updates parameters of a BeepManager according to the current state of SharedPreferences
     */
    public void updatePrefs() {
        vibrate = true;
        if (settings != null) {
            vibrateSilentPeriod = settings.getInt(SharedPreferencesAccessor.VIBRATE_SILENT_TIME, 2);
            vibrateSilentPeriod *= 1000;
        }
        if (mediaPlayer == null) {
            // The volume on STREAM_SYSTEM is not adjustable, and users found it too loud,
            // so we now play on the music stream.
            activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer = buildMediaPlayer(activity);
        }
    }

    /**
     * Performs vibratin if needed
     */
    public void vibrate() {
        if (activity != null) {
            long currentTimestamp = new Date().getTime();
            if ((currentTimestamp - lastVibrated > vibrateSilentPeriod) && vibrate) {
                Vibrator vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
                vibrator.vibrate(VIBRATE_DURATION);
                lastVibrated = currentTimestamp + VIBRATE_DURATION;
            }
        }
    }

    private MediaPlayer buildMediaPlayer(Context activity) {
        if (activity != null) {
            MediaPlayer player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setOnCompletionListener(this);
            player.setOnErrorListener(this);

            AssetFileDescriptor file = activity.getResources().openRawResourceFd(R.raw.beep);
            try {
                player.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
                file.close();
                player.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                player.prepare();
            } catch (IOException ioe) {
                UniversalHelper.logException(ioe);
                player = null;
            }
            return player;
        } else {
            return null;
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mp != null) {
            // When the beep has finished playing, rewind to queue up another one.
            mp.seekTo(0);
        }
    }

    @Override
    public synchronized boolean onError(MediaPlayer mp, int what, int extra) {
        if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
            if (activity != null) {
                // we are finished, so put up an appropriate error toast if required and finish
                activity.finish();
            }
        } else {
            if (mp != null) {
                // possibly media player error, so release and recreate
                mp.release();
                mediaPlayer = null;
                updatePrefs();
            }
        }
        return true;
    }

    /**
     * Updates the SharedPreferences object for current BeepManager instance
     *
     * @param settings a new SharedPreferences object
     */
    public void setSettings(SharedPreferences settings) {
        this.settings = settings;
    }
}
