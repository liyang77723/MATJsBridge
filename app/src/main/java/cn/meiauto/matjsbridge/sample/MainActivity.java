package cn.meiauto.matjsbridge.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
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

        mWebView.loadUrl("http://gank.io/");
    }
}
