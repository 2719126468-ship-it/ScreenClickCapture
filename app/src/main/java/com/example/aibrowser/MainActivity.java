package com.example.aibrowser;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
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
    
    private IAiProxy proxyService;
    private boolean isProxyBound = false;

    private ServiceConnection proxyConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            proxyService = IAiProxy.Stub.asInterface(service);
            isProxyBound = true;
            try {
                // 发送配置（此处可替换为从网络下载的自动订阅配置）
                proxyService.startProxy("{\"log\":{\"level\":\"info\"},\"inbounds\":[{\"type\":\"socks\",\"listen\":\"127.0.0.1\",\"listen_port\":1080}],\"outbounds\":[{\"type\":\"direct\"}]}");
                String port = proxyService.getProxyPort();
                initGeckoView(port);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            proxyService = null;
            isProxyBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 启动并绑定子进程服务
        Intent intent = new Intent(this, ProxyService.class);
        bindService(intent, proxyConnection, Context.BIND_AUTO_CREATE);
    }

    private void initGeckoView(String proxyPort) {
        FrameLayout container = findViewById(R.id.webviewContainer);
        Button btnChatgpt = findViewById(R.id.btnChatgpt);
        Button btnClaude = findViewById(R.id.btnClaude);

        btnChatgpt.setOnClickListener(v -> loadUrl("https://chatgpt.com"));
        btnClaude.setOnClickListener(v -> loadUrl("https://claude.ai"));

        // 核心：给 GeckoView 设置 SOCKS5 代理
        GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                .arguments(new String[]{"socks://127.0.0.1:" + proxyPort})
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
        Toast.makeText(this, "网络层已就绪，开始加载网页", Toast.LENGTH_SHORT).show();
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
        if (isProxyBound) {
            try {
                proxyService.stopProxy();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            unbindService(proxyConnection);
        }
        if (session != null) {
            session.close();
            session = null;
        }
        super.onDestroy();
    }
}
