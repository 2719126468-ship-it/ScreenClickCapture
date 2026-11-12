package com.example.aibrowser;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

public class ProxyService extends Service {
    private static final String TAG = "ProxyService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Proxy Service started in :vpn process");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String configJson = intent.getStringExtra("config");
            if (configJson != null) {
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
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        // TODO: BoxService.stop();
        Log.i(TAG, "sing-box core stopped");
        super.onDestroy();
    }
}
