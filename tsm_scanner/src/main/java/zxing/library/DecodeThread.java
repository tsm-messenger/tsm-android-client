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

package zxing.library;

import android.os.Handler;
import android.os.Looper;

import com.google.zxing.DecodeHintType;
import com.google.zxing.ResultPointCallback;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * This thread does all the heavy lifting of decoding the images.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
final class DecodeThread extends Thread {

    public static final String BARCODE_BITMAP = "barcode_bitmap";

    private final ZXingFragment fragment;
    private final Map<DecodeHintType, Object> hints;
    private final CountDownLatch handlerInitLatch;
    private Handler handler;

    DecodeThread(ZXingFragment fragment,
                 ResultPointCallback resultPointCallback) {

        this.fragment = fragment;
        handlerInitLatch = new CountDownLatch(1);

        hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, null);
        hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
    }

    public Handler getHandler() {
        try {
            handlerInitLatch.await();
        } catch (InterruptedException ie) {
            // continue
        }
        return handler;
    }

    @Override
    public void run() {
        Looper.prepare();
        handler = new DecodeHandler(fragment, hints);
        handlerInitLatch.countDown();
        Looper.loop();
    }

}
