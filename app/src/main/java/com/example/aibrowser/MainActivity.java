package com.example.aibrowser;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

public class MainActivity extends Activity {

    private GeckoSession session;
    private GeckoView geckoView;
    private String currentUrl = "https://chatgpt.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 启动子进程网络代理服务
        Intent intent = new Intent(this, ProxyService.class);
        intent.putExtra("config", "{\"log\":{\"level\":\"info\"},\"inbounds\":[{\"type\":\"socks\",\"listen\":\"127.0.0.1\",\"listen_port\":1080}],\"outbounds\":[{\"type\":\"direct\"}]}");
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        // 2. 初始化渲染引擎 (连接本地 1080 代理)
        initGeckoView();
    }

    private void initGeckoView() {
        FrameLayout container = findViewById(R.id.webviewContainer);
        Button btnChatgpt = findViewById(R.id.btnChatgpt);
        Button btnClaude = findViewById(R.id.btnClaude);

        btnChatgpt.setOnClickListener(v -> loadUrl("https://chatgpt.com"));
        btnClaude.setOnClickListener(v -> loadUrl("https://claude.ai"));

        // 核心：给 GeckoView 设置 SOCKS5 代理
        GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                .arguments(new String[]{"socks://127.0.0.1:1080"})
                .build();

        GeckoRuntime runtime = GeckoRuntime.create(this, settings);

        geckoView = new GeckoView(this);
        geckoView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        session = new GeckoSession();
        session.open(runtime);
        geckoView.setSession(session);
        container.addView(geckoView);

        loadUrl(currentUrl);
        Toast.makeText(this, "Gecko内核已加载，正在连接代理...", Toast.LENGTH_SHORT).show();
    }

    private void loadUrl(String url) {
        currentUrl = url;
        if (session != null) {
            session.loadUri(url);
        }
    }

    @Override
    public void onBackPressed() {
        if (session != null) {
            session.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        // 关闭代理服务
        stopService(new Intent(this, ProxyService.class));
        if (session != null) {
            session.close();
            session = null;
        }
        super.onDestroy();
    }
}
