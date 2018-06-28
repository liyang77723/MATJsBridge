package cn.meiauto.matjsbridge.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ViewGroup;

import cn.meiauto.matjsbridge.JsBridgeWebView;

public class MainActivity extends AppCompatActivity {

    private ViewGroup mRootView;
    private JsBridgeWebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRootView = findViewById(R.id.fl_main_root);

        mWebView = JsBridgeWebView.create(this);
        mRootView.addView(mWebView);

        mWebView.setProgressListener(new JsBridgeWebView.OnProgressListener() {
            @Override
            public void onProgress(int progress) {
                Log.d("js_bridge", "onProgress() called with: progress = [" + progress + "]");
            }
        });

        mWebView.loadUrl("http://gank.io/");
    }
}
