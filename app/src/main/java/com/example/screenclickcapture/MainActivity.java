package com.example.screenclickcapture;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_STORAGE = 100;
    private static final int REQ_OVERLAY = 101;
    private static final int REQ_CAPTURE = 102;

    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        Button startButton = findViewById(R.id.captureButton);
        startButton.setOnClickListener(v -> startFlow());
    }

    private void startFlow() {
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQ_STORAGE
            );
            return;
        }
        checkOverlayThenCapture();
    }

    private void checkOverlayThenCapture() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, REQ_OVERLAY);
            return;
        }
        requestCapturePermission();
    }

    private void requestCapturePermission() {
        startActivityForResult(
                projectionManager.createScreenCaptureIntent(),
                REQ_CAPTURE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkOverlayThenCapture();
            } else {
                Toast.makeText(this, "需要存储权限才能把截图保存到 Download", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                requestCapturePermission();
            } else {
                Toast.makeText(this, "需要悬浮窗权限才能显示截屏按钮", Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("resultData", data);
                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                finish();
            } else {
                Toast.makeText(this, "你取消了截屏授权", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
