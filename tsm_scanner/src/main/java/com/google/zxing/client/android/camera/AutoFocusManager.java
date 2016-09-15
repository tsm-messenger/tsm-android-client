/*
 * Copyright (C) 2012 ZXing authors
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

package com.google.zxing.client.android.camera;

import android.hardware.Camera;
import android.os.AsyncTask;

import com.google.zxing.client.android.common.executor.AsyncTaskExecInterface;
import com.google.zxing.client.android.common.executor.AsyncTaskExecManager;

final class AutoFocusManager implements Camera.AutoFocusCallback {

    private static final long AUTO_FOCUS_INTERVAL_MS = 2000L;
    private final boolean useAutoFocus;
    private final Camera camera;
    private final AsyncTaskExecInterface taskExec;
    private boolean active;
    private AutoFocusTask outstandingTask;

    AutoFocusManager(Camera camera) {
        this.camera = camera;
        taskExec = new AsyncTaskExecManager().build();
        useAutoFocus = true;
        start();
    }

    @Override
    public synchronized void onAutoFocus(boolean success, Camera theCamera) {
        if (active) {
            outstandingTask = new AutoFocusTask();
            taskExec.execute(outstandingTask);
        }
    }

    private synchronized void start() {
        if (useAutoFocus) {
            active = true;
            try {
                camera.getParameters().setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                camera.autoFocus(this);
            } catch (RuntimeException re) {
                // Have heard RuntimeException reported in Android 4.0.x+; continue
            }
        }
    }

    synchronized void stop() {
        if (useAutoFocus) {
            try {
                camera.cancelAutoFocus();
            } catch (RuntimeException re) {
                // Have heard RuntimeException reported in Android 4.0.x+; continue
            }
        }
        if (outstandingTask != null) {
            outstandingTask.cancel(true);
            outstandingTask = null;
        }
        active = false;
    }

    private final class AutoFocusTask extends AsyncTask<Object, Object, Object> {
        @Override
        protected Object doInBackground(Object... voids) {
            try {
                Thread.sleep(AUTO_FOCUS_INTERVAL_MS);
            } catch (InterruptedException e) {
                // continue
            }
            synchronized (AutoFocusManager.this) {
                if (active) {
                    start();
                }
            }
            return null;
        }
    }

}
