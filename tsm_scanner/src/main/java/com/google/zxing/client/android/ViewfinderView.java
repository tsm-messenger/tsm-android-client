/*
 * Copyright (C) 2008 ZXing authors
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

package com.google.zxing.client.android;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.CameraManager;

import java.util.ArrayList;
import java.util.List;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

    private static final long ANIMATION_DELAY = 80L;
    private static final int CURRENT_POINT_OPACITY = 0xA0;
    private static final int MAX_RESULT_POINTS = 20;
    private static final int POINT_SIZE = 6;
    private final Object resultPointLock;
    private final Paint paint;
    private final int resultPointColor;
    private final List<ResultPoint> possibleResultPoints;
    private CameraManager cameraManager;
    private Bitmap resultBitmap;
    private List<ResultPoint> lastPossibleResultPoints;

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Resources resources = getResources();
        resultPointColor = resources.getColor(R.color.possible_result_points);
        possibleResultPoints = new ArrayList<>(5);
        lastPossibleResultPoints = null;

        resultPointLock = new Object();
    }

    public void setCameraManager(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (cameraManager == null) {
            return; // not ready yet, early draw before done configuring
        }
        Rect frame = cameraManager.getFramingRect();
        if (frame == null) {
            return;
        }

        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(CURRENT_POINT_OPACITY);
            canvas.drawBitmap(resultBitmap, null, frame, paint);
        } else {
            Rect previewFrame = cameraManager.getFramingRectInPreview();

            float width = previewFrame != null ? previewFrame.width() : 1;
            float height = previewFrame != null ? previewFrame.height() : 1;
            float scaleX = frame.width() / width;
            float scaleY = frame.height() / height;

            int frameLeft = frame.left;
            int frameTop = frame.top;
            if (possibleResultPoints.isEmpty()) {
                lastPossibleResultPoints = null;
            } else {
                possibleResultPoints.clear();
                lastPossibleResultPoints = possibleResultPoints;
                paint.setAlpha(CURRENT_POINT_OPACITY);
                paint.setColor(resultPointColor);
                synchronized (resultPointLock) {
                    for (ResultPoint point : possibleResultPoints) {
                        canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                                frameTop + (int) (point.getY() * scaleY),
                                POINT_SIZE, paint);
                    }
                }
            }
            if (lastPossibleResultPoints != null) {
                paint.setAlpha(CURRENT_POINT_OPACITY / 2);
                paint.setColor(resultPointColor);
                synchronized (resultPointLock) {
                    float radius = POINT_SIZE / 2.0f;
                    for (ResultPoint point : lastPossibleResultPoints) {
                        canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                                frameTop + (int) (point.getY() * scaleY),
                                radius, paint);
                    }
                }
            }

            // Request another update at the animation interval, but only repaint the laser line,
            // not the entire viewfinder mask.
            postInvalidateDelayed(ANIMATION_DELAY,
                    frame.left - POINT_SIZE,
                    frame.top - POINT_SIZE,
                    frame.right + POINT_SIZE,
                    frame.bottom + POINT_SIZE);
        }
    }

    public void drawViewfinder() {
        Bitmap resultBitmap = this.resultBitmap;
        this.resultBitmap = null;
        if (resultBitmap != null) {
            resultBitmap.recycle();
        }
        invalidate();
    }

    public void addPossibleResultPoint(ResultPoint point) {
        synchronized (resultPointLock) {
            possibleResultPoints.add(point);
            int size = possibleResultPoints.size();
            if (size > MAX_RESULT_POINTS) {
                // trim it
                possibleResultPoints.subList(0, size - MAX_RESULT_POINTS / 2).clear();
            }
        }
    }

}
