package com.example.aibrowser;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

public class ProxyService extends Service {
    private static final String TAG = "ProxyService";
    private String proxyPort = "1080"; // 默认本地 SOCKS5 端口

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Proxy Service started in :vpn process");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new IAiProxy.Stub() {
            @Override
            public void startProxy(String configJson) throws RemoteException {
                // 将配置写入本地
                try {
                    File configFile = new File(getFilesDir(), "config.json");
                    FileOutputStream fos = new FileOutputStream(configFile);
                    fos.write(configJson.getBytes());
                    fos.close();
                    
                    // TODO: 此处调用 sing-box 的 JNI 加载 libbox.so
                    // BoxService.start(configFile.getAbsolutePath());
                    Log.i(TAG, "sing-box core started with config");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start proxy", e);
                }
            }

            @Override
            public void stopProxy() throws RemoteException {
                // TODO: BoxService.stop();
                Log.i(TAG, "sing-box core stopped");
            }

            @Override
            public String getProxyPort() throws RemoteException {
                return proxyPort;
            }
        };
    }

    @Override
    public void onDestroy() {
        // TODO: BoxService.stop();
        super.onDestroy();
    }
}
