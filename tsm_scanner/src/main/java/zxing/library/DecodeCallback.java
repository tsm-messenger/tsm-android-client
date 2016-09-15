package zxing.library;

import com.google.zxing.Result;

public interface DecodeCallback {
    void handleBarcode(Result result);
}
