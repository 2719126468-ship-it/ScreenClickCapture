package com.example.aibrowser;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import io.github.jonanorman.android.webviewup.WebViewUpgrade;
import io.github.jonanorman.android.webviewup.source.UpgradeAssetSource;

public class MainActivity extends Activity {

    private WebView webView;
    private FrameLayout container;
    private String currentUrl = "https://chatgpt.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 核心：在任何 WebView 实例化之前，从 assets 升级内核
        WebViewUpgrade.upgrade(new UpgradeAssetSource(this, "webview.apk"));

        setContentView(R.layout.activity_main);
        container = findViewById(R.id.webviewContainer);
        Button btnChatgpt = findViewById(R.id.btnChatgpt);
        Button btnClaude = findViewById(R.id.btnClaude);
        btnChatgpt.setOnClickListener(v -> loadUrl("https://chatgpt.com"));
        btnClaude.setOnClickListener(v -> loadUrl("https://claude.ai"));

        // 延迟 800ms 确保内核切换完成
        container.postDelayed(this::initWebView, 800);
    }

    private void initWebView() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36");
        
        webView.setWebViewClient(new WebViewClient());
        
        container.addView(webView);
        webView.loadUrl(currentUrl);
        Toast.makeText(this, "已加载 Chromium 134 内核", Toast.LENGTH_SHORT).show();
    }

    private void loadUrl(String url) {
        currentUrl = url;
        if (webView != null) webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) { container.removeView(webView); webView.destroy(); }
        super.onDestroy();
    }
}
