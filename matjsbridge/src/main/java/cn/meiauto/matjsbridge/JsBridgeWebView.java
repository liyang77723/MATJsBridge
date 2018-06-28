package cn.meiauto.matjsbridge;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;
import com.tencent.smtt.export.external.interfaces.IX5WebChromeClient;
import com.tencent.smtt.export.external.interfaces.JsPromptResult;
import com.tencent.smtt.export.external.interfaces.JsResult;
import com.tencent.smtt.sdk.DownloadListener;
import com.tencent.smtt.sdk.ValueCallback;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebSettings;
import com.tencent.smtt.sdk.WebStorage;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;

/**
 * author : LiYang
 * email  : yang.li@nx-engine.com
 * time   : 2017/12/16
 */
public class JsBridgeWebView extends WebView {

    private static final String TAG = "js_bridge";

    private WebSettings mSetting;

    public static JsBridgeWebView create(Activity activity) {
        JsBridgeWebView jsBridgeWebView = new JsBridgeWebView(activity);
        return jsBridgeWebView;
    }

    public static JsBridgeWebView create(Fragment fragment) {
        JsBridgeWebView jsBridgeWebView = new JsBridgeWebView(fragment.getActivity());
        return jsBridgeWebView;
    }

    private JsBridgeWebView(Context context) {
        this(context, null, android.R.attr.webViewStyle);
    }

    private JsBridgeWebView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.webViewStyle);
    }

    private JsBridgeWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(context);
    }

    /**
     * android.webkit.AccessibilityInjector$TextToSpeechWrapper
     * 此问题在4.2.1和4.2.2比较集中，关闭辅助功能
     * {@link AccessibilityManager}
     */
    public static void disableAccessibility(Context context) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (context != null) {
                try {
                    try {
                        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
                        if (am == null) {
                            return;
                        }
                        if (!am.isEnabled()) {
                            //Not need to disable accessibility
                            return;
                        }

                        @SuppressLint("PrivateApi") Method setState = am.getClass().getDeclaredMethod("setState", int.class);
                        setState.setAccessible(true);
                        setState.invoke(am, 0);
                    } catch (Exception ignored) {
                        ignored.printStackTrace();
                    }

                } catch (Exception ignored) {
                    ignored.printStackTrace();
                }
            }
        }
    }

    /**
     * android.content.pm.PackageManager$NameNotFoundException
     * 在创建 WebView 时崩溃
     */
    @Override
    public void setOverScrollMode(int mode) {
        try {
            super.setOverScrollMode(mode);
        } catch (Throwable e) {
            String trace = Log.getStackTraceString(e);
            if (trace.contains("android.content.pm.PackageManager$NameNotFoundException")
                    || trace.contains("java.lang.RuntimeException: Cannot load WebView")
                    || trace.contains("android.webkit.WebViewFactory$MissingWebViewPackageException: Failed to load WebView provider: No WebView installed")) {
                e.printStackTrace();
            } else {
                throw e;
            }
        }
    }

    /**
     * android.webkit.WebViewClassic.clearView
     * 这个bug是在某些设备上发生的，是在调用webView.destroy() 之前调用了loadurl操作发生的，也不是必现问题，所以只能跟进源码查看，
     * 在清空 webview destroy 时，调用清理方法，内部可能时机有问题，会出现，WebViewClassic 中 mWebViewCore 对象为null，其内部为handler消息机制。
     */
    @Override
    public void loadUrl(String url) {
        try {
            super.loadUrl(url);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void init(Context context) {

        removeJavascriptInterface("searchBoxJavaBridge_");
        removeJavascriptInterface("accessibility");
        removeJavascriptInterface("accessibilityTraversal");

        disableAccessibility(context);

        mSetting = getSettings();
        mSetting.setJavaScriptEnabled(true); // enable js
        // WebView跨源（加载本地文件）攻击分析：http://blogs.360.cn/360mobile/2014/09/22/webview%E8%B7%A8%E6%BA%90%E6%94%BB%E5%87%BB%E5%88%86%E6%9E%90/
        mSetting.setAllowFileAccess(false);

        mSetting.setSupportZoom(false); //支持缩放，默认为true。是下面那个的前提。
        mSetting.setBuiltInZoomControls(false); //设置内置的缩放控件。若为false，则该WebView不可缩放
        mSetting.setDisplayZoomControls(false); //隐藏原生的缩放控件

        mSetting.setSavePassword(false);
        mSetting.setDomStorageEnabled(true);

        //TODO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mSetting.setMediaPlaybackRequiresUserGesture(true);
        }

        if (isNetAvailable(context)) {
            mSetting.setCacheMode(WebSettings.LOAD_DEFAULT);//根据cache-control决定是否从网络上取数据。
        } else {
            mSetting.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);//没网，则从本地获取，即离线加载
        }

        //TODO file download
        setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {

                Log.d(TAG, "onDownloadStart() called with: url = [" + url + "], userAgent = [" + userAgent + "], contentDisposition = [" + contentDisposition + "], mimetype = [" + mimetype + "], contentLength = [" + contentLength + "]");
            }
        });

        setWebViewClient(new MyWebViewClient(getContext()));
        setWebChromeClient(new MyWebChromeClient(getContext()));

        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                HitTestResult result = ((WebView) v).getHitTestResult();
                Log.d(TAG, "result: " + result);
                if (null == result) {
                    return false;
                }
                int type = result.getType();
                Log.d(TAG, "type: " + type + " extra: " + result.getExtra());
                if (type == HitTestResult.UNKNOWN_TYPE) {
                    return false;
                }
                // 这里可以拦截很多类型，我们只处理图片类型就可以了
                switch (type) {
                    case HitTestResult.PHONE_TYPE: // 处理拨号
                        break;
                    case HitTestResult.EMAIL_TYPE: // 处理Email
                        break;
                    case HitTestResult.GEO_TYPE: // 地图类型
                        break;
                    case HitTestResult.SRC_ANCHOR_TYPE: // 超链接
                        break;
                    case HitTestResult.SRC_IMAGE_ANCHOR_TYPE:
                        break;
                    case HitTestResult.IMAGE_TYPE: // 处理长按图片的菜单项
                        // 获取图片的路径
                        String saveImgUrl = result.getExtra();
                        Log.d(TAG, "saveImgUrl: " + saveImgUrl);
                        if (mOnImageLongClick != null) {
                            mOnImageLongClick.onLongClick(v, saveImgUrl);
                        }
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
    }

    /**
     * 网络是否连接
     */
    private boolean isNetAvailable(Context context) {
        if (context != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo;
            if (mConnectivityManager != null) {
                mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
                if (mNetworkInfo != null) {
                    return mNetworkInfo.isAvailable();
                }
            }
        }
        return false;
    }

    private OnImageLongClick mOnImageLongClick;

    public void setOnImageLongClick(OnImageLongClick onImageLongClick) {
        mOnImageLongClick = onImageLongClick;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (canGoBack() && keyCode == KeyEvent.KEYCODE_BACK) {
            goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    //TODO 域名过滤
    //TODO 进度条
    //TODO title
    //TODO video
    private class MyWebViewClient extends WebViewClient {
        private final WeakReference<Context> mContextRef;

        public MyWebViewClient(Context context) {
            mContextRef = new WeakReference<>(context);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Log.d(TAG, "onPageStarted  mInjectJs\n" + mInjectJs);
            loadUrl(mInjectJs);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (progressListener != null) {
                progressListener.onProgress(100);
            }
            Log.d(TAG, "onPageFinished  mInjectJs\n" + mInjectJs);
            loadUrl(mInjectJs);
        }
    }

    private class MyWebChromeClient extends WebChromeClient {
        private final WeakReference<Context> mContextRef;

        public MyWebChromeClient(Context context) {
            mContextRef = new WeakReference<>(context);
        }

        @Override
        public void onProgressChanged(WebView view, int i) {
            super.onProgressChanged(view, i);
            if (progressListener != null) {
                progressListener.onProgress(i);
            }
        }

        //android 3.0 ↓
        @SuppressWarnings("unused")
        public void openFileChooser(ValueCallback<Uri> valueCallback) {
            if (mFileChooseType != 0) {
                uploadMessage = valueCallback;
                doChooseFile();
            }
        }

        //android 3.0 - android4.0
        @SuppressWarnings("unused")
        public void openFileChooser(ValueCallback<Uri> valueCallback, String acceptType) {
            if (mFileChooseType != 0) {
                uploadMessage = valueCallback;
                doChooseFile();
            }
        }

        //android 4.0 - android 4.3 / android 4.4.4
        @SuppressWarnings("unused")
        public void openFileChooser(ValueCallback<Uri> valueCallback, String acceptType, String capture) {
            if (mFileChooseType != 0) {
                uploadMessage = valueCallback;
                doChooseFile();
            }
        }

        //android 5.0 ↑
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]>
                filePathCallback, FileChooserParams fileChooserParams) {
            if (mFileChooseType != 0) {
                uploadMessageAboveL = filePathCallback;
                doChooseFile();
                return true;
            }
            return false;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            Log.d(TAG, "onJsAlert() called with:  url = [" + url + "], message = [" + message + "]");
            Context context = mContextRef.get();
            if (context == null) {
                return false;
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            result.cancel();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
            Context context = mContextRef.get();
            if (context == null) {
                return false;
            }
            new AlertDialog.Builder(context)
                    .setTitle(message)
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            result.cancel();
                        }
                    })
                    .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            result.cancel();
                        }
                    })
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            result.confirm();
                        }
                    })
                    .create()
                    .show();
            Log.d(TAG, "onJsConfirm() called with:  url = [" + url + "], message = [" + message + "]");
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
            Log.d(TAG, "onJsPrompt() called with:  url = [" + url + "], message = [" + message + "], defaultValue = [" + defaultValue + "]");
            if (TextUtils.equals(message, PROMPT_FLAG) && !TextUtils.isEmpty(defaultValue) && defaultValue.startsWith(PROMPT_FLAG)) {
                String json = defaultValue.substring(PROMPT_FLAG.length());
                String confirmResult = null;
                try {
                    JSONObject jsonObject = new JSONObject(json);
                    String injectName = jsonObject.optString("obj");
                    String methodName = jsonObject.optString("method");
                    JSONArray types = jsonObject.optJSONArray("types");
                    String[] ts = null;
                    if (types != null) {
                        int l = types.length();
                        ts = new String[l];
                        for (int i = 0; i < l; i++) {
                            ts[i] = types.optString(i);
                        }
                    }

                    String key = buildMethodMapKey(methodName, ts);
                    Method method = mMethods.get(key);

                    if (method == null) {
                        confirmResult = getReturnJs(500, "Method is not existing : " + key);
                    } else {
                        JSONArray args = jsonObject.optJSONArray("args");
                        Object[] params = null;
                        Class[] paramTypes = method.getParameterTypes();
                        if (args != null) {
                            int length = args.length();
                            params = new Object[length];
                            Class dataType;
                            for (int i = 0; i < length; i++) {
                                dataType = paramTypes[i];
                                if (dataType == double.class || dataType == Double.class) {
                                    params[i] = args.optDouble(i);
                                } else if (dataType == int.class || dataType == Integer.class) {
                                    params[i] = args.optInt(i);
                                } else if (paramTypes[i] == String.class) {
                                    params[i] = args.optString(i);
                                } else if (JsObject.class.isAssignableFrom(dataType)) {
                                    String jsObjectJson = args.getString(i);
                                    params[i] = new Gson().fromJson(jsObjectJson, dataType);
                                } else if (JsCallback.class.isAssignableFrom(dataType)) {
                                    params[i] = new JsCallback(view, injectName, i);
                                }
                            }
                        }

                        confirmResult = getReturnJs(200, method.invoke(mObject, params));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    confirmResult = getReturnJs(500, e.getMessage());
                } finally {
                    result.confirm(confirmResult);
                    Log.i(TAG, "confirmResult: " + confirmResult);
                }
            } else {
                Context context = mContextRef.get();
                if (context == null) {
                    return false;
                }
                final EditText editText = new EditText(context);
                editText.setHint(defaultValue);
                new AlertDialog.Builder(context)
                        .setTitle(message)
                        .setView(editText)
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                result.cancel();
                            }
                        })
                        .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                result.cancel();
                            }
                        })
                        .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                result.confirm(editText.getText().toString());
                            }
                        })
                        .create()
                        .show();
            }
            return true;
        }

        @Override
        public void onShowCustomView(View view, IX5WebChromeClient.CustomViewCallback callback) {
            if (myCallback != null) {
                myCallback.onCustomViewHidden();
                myCallback = null;
                return;
            }

            long id = Thread.currentThread().getId();
            Log.i(TAG, "rong debug in showCustomView Ex: " + id);

            ViewGroup parent = (ViewGroup) JsBridgeWebView.this.getParent();
            String s = parent.getClass().getName();
            Log.i(TAG, "rong debug Ex: " + s);
            parent.removeView(JsBridgeWebView.this);
            parent.addView(view);
            myView = view;
            myCallback = callback;
        }

        private View myView = null;
        private IX5WebChromeClient.CustomViewCallback myCallback = null;


        @Override
        public void onHideCustomView() {
            super.onHideCustomView();

            long id = Thread.currentThread().getId();
            Log.i(TAG, "rong debug in hideCustom Ex: " + id);


            if (myView != null) {

                if (myCallback != null) {
                    myCallback.onCustomViewHidden();
                    myCallback = null;
                }

                ViewGroup parent = (ViewGroup) myView.getParent();
                parent.removeView(myView);
                parent.addView(JsBridgeWebView.this);
                myView = null;
            }
        }
    }

    private String getReturnJs(int code, Object object) {
        String result;
        if (object == null) {
            result = "null";
        } else if (object instanceof String) {
            result = "\"" + object + "\"";
        } else {
            result = String.valueOf(object);
        }
        return String.format(Locale.getDefault(), "{\"code\": %d, \"result\": %s}", code, result);
    }

    @SuppressLint({"JavascriptInterface", "AddJavascriptInterface"})
    @Override
    public void addJavascriptInterface(Object object, String name) {
//        addMyJavascriptInterface(object, name);//TODO test use

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            super.addJavascriptInterface(object, name);
        } else {
            addMyJavascriptInterface(object, name);
        }
    }

    private static final String PROMPT_FLAG = "js-bridge";

    private static final String JS = "javascript: try { (function(win) { if (window.ANDROID_NAME) { return; } console.log(\">>>>>  Start to inject ANDROID_NAME.\"); var android = { queue: [], callback: function() { var argumentArray = Array.prototype.slice.call(arguments, 0); var index = argumentArray.shift(); var executeOnce = argumentArray.shift(); console.log(\"callback executeOnce ---> \" + executeOnce); this.queue[index].apply(this, argumentArray); if (executeOnce) { delete this.queue[index]; } } }; CUSTOM_FUNCTION function() { var androidArguments = Array.prototype.slice.call(arguments, 0); if (androidArguments.length < 1) { throw \"ANDROID_NAME call error, message:miss method name\"; } var androidParamTypes = []; for (var i = 1; i < androidArguments.length; i++) { var paramValue = androidArguments[i]; var paramType = typeof paramValue; androidParamTypes[androidParamTypes.length] = paramType; if (paramType == \"function\") { var length = android.queue.length; android.queue[length] = paramValue; androidArguments[i] = length; } } var methodName = androidArguments.shift(); if (window.location.search.endsWith(\"debug\")) { return \"Call ANDROID_NAME \" + methodName + \" success.\"; } else { var javaReturnJson = prompt(\"ANDROID_FLAG\", \"ANDROID_FLAG\" + JSON.stringify({ obj: \"ANDROID_NAME\", method: methodName, types: androidParamTypes, args: androidArguments })); } if (javaReturnJson) { console.log(\"javaReturnJson --> \" + javaReturnJson); var javaResult = JSON.parse(javaReturnJson); if (javaResult.code != 200) { throw \"ANDROID_NAME call error, code:\" + javaResult.code + \", message:\" + javaResult.result; } return javaResult.result; } }; Object.getOwnPropertyNames(android).forEach(function(propertyName) { var callbackFunction = android[propertyName]; if (typeof callbackFunction === \"function\" && propertyName !== \"callback\") { android[propertyName] = function() { return callbackFunction.apply(android, [propertyName].concat(Array.prototype.slice.call(arguments, 0))); } } }); win.ANDROID_NAME = android; console.log(\">>>>>  ANDROID_NAME has been injected success.\"); })(window) } catch (e) { console.error(e); }";

    private String mInjectJs;
    private HashMap<String, Method> mMethods;
    private Object mObject;

    private void addMyJavascriptInterface(Object object, String name) {
        try {
            mObject = object;

            if (mMethods == null) {
                mMethods = new HashMap<>();
            }

            StringBuilder methodStr = new StringBuilder();
            Method[] methods = object.getClass().getDeclaredMethods();
            String methodName;
            for (Method method : methods) {
                methodName = method.getName();
                if (!TextUtils.equals("access$super", methodName)) {
                    methodStr.append("android.").append(methodName).append("=");
                    mMethods.put(buildMethodMapKey2Js(method), method);
                }
            }

            Log.i(TAG, "mMethods: " + mMethods.toString());

            mInjectJs = JS
                    .replaceAll("ANDROID_NAME", name)
                    .replaceAll("ANDROID_FLAG", PROMPT_FLAG)
                    .replace("CUSTOM_FUNCTION", methodStr.toString());

            loadUrl(mInjectJs);

            Log.e(TAG, "js ---> \n" + mInjectJs);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * build method key in java
     *
     * @return example: test(number,string)
     */
    private String buildMethodMapKey2Js(Method method) {
        StringBuilder sBuilder = new StringBuilder(method.getName());
        sBuilder.append("(");
        Class[] paramTypes = method.getParameterTypes();

        if (!EmptyUtil.isEmpty(paramTypes)) {
            for (Class clazz : paramTypes) {
                if (clazz == String.class) {
                    sBuilder.append("string");
                } else if (clazz == boolean.class || clazz == Boolean.class) {
                    sBuilder.append("boolean");
                } else if (JsObject.class.isAssignableFrom(clazz)) {
                    sBuilder.append("object");
                } else if (JsCallback.class.isAssignableFrom(clazz)) {
                    sBuilder.append("function");
                } else {//number //TODO split number int double
                    sBuilder.append("number");
                }
                sBuilder.append(",");
            }
            sBuilder.deleteCharAt(sBuilder.length() - 1);
        }
        sBuilder.append(")");
        Log.i(TAG, "buildMethodMapKey2Js: " + sBuilder.toString());
        return sBuilder.toString();
    }

    private String buildMethodMapKey(String methodName, String[] jsParamTypes) {
        StringBuilder sBuilder = new StringBuilder(methodName);
        sBuilder.append("(");
        if (!EmptyUtil.isEmpty(jsParamTypes)) {
            for (String type : jsParamTypes) {
                sBuilder.append(type).append(",");
            }
            sBuilder.deleteCharAt(sBuilder.length() - 1);
        }
        sBuilder.append(")");
        Log.i(TAG, "buildMethodMapKey: " + sBuilder.toString());
        return sBuilder.toString();
    }

    /*-----------选择文件-----------*/
    private final static int FILE_CHOOSER_RESULT_CODE = 7723;

    private int mFileChooseType;
    private static final int ACTIVITY_OPEN = 1;
    private static final int FRAGMENT_OPEN = 2;

    private Activity mActivity;//文件选择用
    private Fragment mFragment;//文件选择用

    private ValueCallback<Uri> uploadMessage;
    private ValueCallback<Uri[]> uploadMessageAboveL;

    /**
     * enable file choose when webview in activity
     */
    public void enableFileChoose(Activity activity) {
        mFileChooseType = ACTIVITY_OPEN;
        mActivity = activity;
    }

    /**
     * enable file choose when webview in fragment
     */
    public void enableFileChoose(Fragment fragment) {
        mFileChooseType = FRAGMENT_OPEN;
        mFragment = fragment;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (null == uploadMessage && null == uploadMessageAboveL) {
                return;
            }
            Uri result = data == null || resultCode != Activity.RESULT_OK ? null : data.getData();
            if (uploadMessageAboveL != null) {
                handleActivityResult(requestCode, resultCode, data);
            } else if (uploadMessage != null) {
                uploadMessage.onReceiveValue(result);
                uploadMessage = null;
            }
        }
    }

    private void doChooseFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        if (mFileChooseType == ACTIVITY_OPEN) {//activity
            mActivity.startActivityForResult(Intent.createChooser(i, "选择要打开的应用"), FILE_CHOOSER_RESULT_CODE);
        } else if (mFileChooseType == FRAGMENT_OPEN) {//fragment
            mFragment.startActivityForResult(Intent.createChooser(i, "选择要打开的应用"), FILE_CHOOSER_RESULT_CODE);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void handleActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode != FILE_CHOOSER_RESULT_CODE || uploadMessageAboveL == null) {
            return;
        }
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            if (intent != null) {
                String dataString = intent.getDataString();
                ClipData clipData = intent.getClipData();
                if (clipData != null) {
                    results = new Uri[clipData.getItemCount()];
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        ClipData.Item item = clipData.getItemAt(i);
                        results[i] = item.getUri();
                    }
                }
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
        }
        uploadMessageAboveL.onReceiveValue(results);
        uploadMessageAboveL = null;
    }
    /*-----------选择文件-----------*/

    /**
     * 只能在Android 4.4 KitKat以上
     * 设置是否全局开启Chrome调试，google chrome 输入chrome://inspect即可
     */
    public static void setWebDebugEnabled(boolean debuggable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(debuggable);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        pauseTimers();
    }

    @Override
    public void onResume() {
        super.onResume();
        resumeTimers();
    }

    public void onDestroy() {
        destroy();
    }

    @Override
    public void destroy() {
        WebStorage.getInstance().deleteAllData();

        setVisibility(GONE);
        pauseTimers();
        removeAllViewsInLayout();

        //fixed still attached
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup mContainer = (ViewGroup) parent;
            mContainer.removeAllViews();
        }

        releaseAllWebViewCallback();

        super.destroy();
    }

    private void releaseAllWebViewCallback() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                Field field = WebView.class.getDeclaredField("mWebViewCore");
                field = field.getType().getDeclaredField("mBrowserFrame");
                field = field.getType().getDeclaredField("sConfigCallback");
                field.setAccessible(true);
                field.set(null, null);
            } else {
                @SuppressLint("PrivateApi") Class clazz = Class.forName("android.webkit.BrowserFrame");
                if (clazz != null) {
                    Field sConfigCallback = clazz.getDeclaredField("sConfigCallback");
                    if (sConfigCallback != null) {
                        sConfigCallback.setAccessible(true);
                        sConfigCallback.set(null, null);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface OnProgressListener {
        void onProgress(int progress);
    }

    private OnProgressListener progressListener;


    public void setProgressListener(OnProgressListener progressListener) {
        this.progressListener = progressListener;
    }
}
