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

package com.google.zxing.client.android.camera;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A class which deals with reading, parsing, and setting the camera parameters which are used to
 * configure the camera hardware.
 */
final class CameraConfigurationManager {

    private static final String TAG = "CameraConfiguration";

    // This is bigger than the size of a small screen, which is still supported. The routine
    // below will still select the default (presumably 320x240) size for these. This prevents
    // accidental selection of very low resolution on some devices.
    private static final int MIN_PREVIEW_PIXELS = 470 * 320; // normal screen
    private static final int MAX_PREVIEW_PIXELS = 1280 * 720;

    private final Context context;
    private Point screenResolution;
    private Point cameraResolution;

    CameraConfigurationManager(Context context) {
        this.context = context.getApplicationContext();
    }

    private static String findSettableValue(Collection<String> supportedValues,
                                            String... desiredValues) {
        String result = null;
        if (supportedValues != null) {
            for (String desiredValue : desiredValues) {
                if (supportedValues.contains(desiredValue)) {
                    result = desiredValue;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Reads, one time, values from the camera that are needed by the app.
     */
    void initFromCameraParameters(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);

        screenResolution = new Point();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        screenResolution.set(width, height);

        cameraResolution = findBestPreviewSizeValue(parameters, screenResolution);
    }

    void setDesiredCameraParameters(Camera camera, boolean safeMode) {
        // Checkout of screen orientation
        WindowManager manager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);

        Display defaultDisplay = manager.getDefaultDisplay();
        Point dSize = new Point();
        defaultDisplay.getSize(dSize);
        int rotation = dSize.x < dSize.y ? Surface.ROTATION_0 : Surface.ROTATION_90;
        int rotationVector = defaultDisplay.getRotation();
        if (rotation == Surface.ROTATION_0) {
            if (rotationVector == Surface.ROTATION_90) {
                camera.setDisplayOrientation(270);
            } else {
                camera.setDisplayOrientation(90);
            }
        }

        Camera.Parameters parameters = camera.getParameters();

        String focusMode;
        if (safeMode) {
            focusMode = findSettableValue(parameters.getSupportedFocusModes(),
                    Camera.Parameters.FOCUS_MODE_AUTO);
        } else {
            focusMode = findSettableValue(parameters.getSupportedFocusModes(),
                    "continuous-picture", // Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE in 4.0+
                    "continuous-video",   // Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO in 4.0+
                    Camera.Parameters.FOCUS_MODE_AUTO);
        }
        // Maybe selected auto-focus but not available, so fall through here:
        if (!safeMode && focusMode == null) {
            focusMode = findSettableValue(parameters.getSupportedFocusModes(),
                    Camera.Parameters.FOCUS_MODE_MACRO,
                    "edof"); // Camera.Parameters.FOCUS_MODE_EDOF in 2.2+
        }
        if (focusMode != null) {
            parameters.setFocusMode(focusMode);
        }

        parameters.setPreviewSize(cameraResolution.x, cameraResolution.y);
        camera.setParameters(parameters);
    }

    Point getCameraResolution() {
        return cameraResolution;
    }

    Point getScreenResolution() {
        return screenResolution;
    }

    private Point findBestPreviewSizeValue(Camera.Parameters parameters, Point screenResolution) {

        List<Camera.Size> rawSupportedSizes = parameters.getSupportedPreviewSizes();
        if (rawSupportedSizes == null) {
            Camera.Size defaultSize = parameters.getPreviewSize();
            return new Point(defaultSize.width, defaultSize.height);
        }

        // Sort by size, descending
        List<Camera.Size> supportedPreviewSizes = new ArrayList<>(rawSupportedSizes);
        Collections.sort(supportedPreviewSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });

        Point bestSize = null;
        float screenAspectRatio = (float) screenResolution.x / (float) screenResolution.y;

        float diff = Float.POSITIVE_INFINITY;
        for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
            int realWidth = supportedPreviewSize.width;
            int realHeight = supportedPreviewSize.height;
            int pixels = realWidth * realHeight;
            if (pixels < MIN_PREVIEW_PIXELS || pixels > MAX_PREVIEW_PIXELS) {
                continue;
            }
            boolean isCandidatePortrait = realWidth < realHeight;
            int maybeFlippedWidth = isCandidatePortrait ? realHeight : realWidth;
            int maybeFlippedHeight = isCandidatePortrait ? realWidth : realHeight;
            if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y) {
                Point exactPoint = new Point(realWidth, realHeight);
                return exactPoint;
            }
            float aspectRatio = (float) maybeFlippedWidth / (float) maybeFlippedHeight;
            float newDiff = Math.abs(aspectRatio - screenAspectRatio);
            if (newDiff < diff) {
                bestSize = new Point(realWidth, realHeight);
                diff = newDiff;
            }
        }

        if (bestSize == null) {
            Camera.Size defaultSize = parameters.getPreviewSize();
            bestSize = new Point(defaultSize.width, defaultSize.height);
        }

        return bestSize;
    }
}
