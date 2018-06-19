package cn.meiauto.matjsbridge;

import android.util.Log;

import com.google.gson.Gson;
import com.tencent.smtt.sdk.WebView;

import java.lang.ref.WeakReference;
import java.util.Locale;

import static android.content.ContentValues.TAG;

/**
 * author : LiYang
 * email  : yang.li@nx-engine.com
 * time   : 2017/12/21
 */
public class JsCallback {
    private WeakReference<WebView> mWebViewRef;
    private String mName;
    private int mParamPos;

    private static final String CALLBACK_JS_FORMAT = "javascript:%s.callback(%d, %d%s);";

    public JsCallback(WebView webView, String name, int paramPos) {
        mWebViewRef = new WeakReference<>(webView);
        mName = name;
        mParamPos = paramPos;
    }

    public void confirm(Object... objects) {
        WebView webView = mWebViewRef.get();
        if (webView == null) {
            return;
        }

        StringBuilder params = new StringBuilder();
        if (!EmptyUtil.isEmpty(objects)) {
            params.append(",");
            for (Object object : objects) {
                if (object == null) {
                    params.append("undefined");
                } else if (object instanceof String) {
                    params.append("\"").append(object).append("\"");
                } else if (object instanceof JsObject) {
                    params.append("\"").append(new Gson().toJson(object)).append("\"");
                } else {
                    params.append(String.valueOf(object));
                }
                params.append(",");
            }
            params.deleteCharAt(params.length() - 1);
        }
        String execJs = String.format(Locale.getDefault(), CALLBACK_JS_FORMAT, mName, mParamPos, 0, params.toString());
        Log.i(TAG, "execJs: " + execJs);
        webView.loadUrl(execJs);
    }
}
