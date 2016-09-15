package zxing.library;

import android.os.Handler;
import android.os.Message;

import com.google.zxing.Result;
import com.google.zxing.client.android.R;
import com.google.zxing.client.android.ViewfinderResultPointCallback;
import com.google.zxing.client.android.camera.CameraManager;

public class FragmentHandler extends Handler {

    private final DecodeThread decodeThread;
    private final CameraManager cameraManager;
    private final ZXingFragment fragment;
    private State state;

    public FragmentHandler(ZXingFragment fragment,
                           CameraManager cameraManager) {
        this.fragment = fragment;
        decodeThread = new DecodeThread(fragment,
                new ViewfinderResultPointCallback(fragment.getViewfinderView()));
        decodeThread.start();
        state = State.SUCCESS;

        // Start ourselves capturing previews and decoding.
        this.cameraManager = cameraManager;
        cameraManager.startPreview();
        restartPreviewAndDecode();
    }

    @Override
    public void handleMessage(Message message) {
        if (message.what == R.id.restart_preview) {
            restartPreviewAndDecode();
        } else if (message.what == R.id.decode_succeeded) {
            state = State.SUCCESS;
            fragment.handleDecode((Result) message.obj);
        } else if (message.what == R.id.decode_failed) {
            // We're decoding as fast as possible, so when one decode fails,
            // start another.
            state = State.PREVIEW;
            cameraManager.requestPreviewFrame(decodeThread.getHandler(),
                    R.id.decode);
        }
    }

    public void quitSynchronously() {
        state = State.DONE;
        cameraManager.stopPreview();
        Message quit = Message.obtain(decodeThread.getHandler(), R.id.quit);
        quit.sendToTarget();
        try {
            // Wait at most half a second; should be enough time, and onPause()
            // will timeout quickly
            decodeThread.join(500L);
        } catch (InterruptedException e) {
            // continue
        }

        // Be absolutely sure we don't send any queued up messages
        removeMessages(R.id.decode_succeeded);
        removeMessages(R.id.decode_failed);
    }

    private void restartPreviewAndDecode() {
        if (state == State.SUCCESS) {
            state = State.PREVIEW;
            cameraManager.requestPreviewFrame(decodeThread.getHandler(),
                    R.id.decode);
            fragment.drawViewfinder();
        }
    }

    private enum State {
        PREVIEW, SUCCESS, DONE
    }

}
