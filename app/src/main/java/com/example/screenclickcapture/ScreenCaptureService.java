package com.example.screenclickcapture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID = "screen_capture_channel";
    private static final int NOTIFICATION_ID = 1001;

    // ===== 外观参数，想改只改这里 =====
    private static final int DP_SIZE = 56;              // 悬浮球直径
    private static final int COLOR_BG = 0xF2FFFFFF;     // 球体背景（95% 白）
    private static final int COLOR_ICON = 0xFF333333;   // 图标主色（深灰）
    private static final int COLOR_LENS = 0xFFFFFFFF;   // 镜头内圈（白）
    // =================================

    private MediaProjection projection;
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams floatingParams;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "截屏服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();

        if (intent != null && intent.hasExtra("resultCode") && projection == null) {
            int resultCode = intent.getIntExtra("resultCode", 0);
            Intent resultData = intent.getParcelableExtra("resultData");
            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (manager != null && resultData != null) {
                projection = manager.getMediaProjection(resultCode, resultData);
            }
        }

        if (projection == null) {
            Toast.makeText(this, "截屏授权无效，请重新打开应用", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        showFloatingButton();
        return START_STICKY;
    }

    private void startAsForeground() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setContentTitle("悬浮截屏运行中")
                .setContentText("点击屏幕上的圆形相机按钮即可截屏")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true);
        startForeground(NOTIFICATION_ID, builder.build());
    }

    private void showFloatingButton() {
        if (floatingView != null) {
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        final int sizePx = (int) (getResources().getDisplayMetrics().density * DP_SIZE);

        floatingView = new View(this) {
            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint lensPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);

                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float r = Math.min(getWidth(), getHeight()) / 2f;

                // 背景圆 + 阴影
                bgPaint.setColor(COLOR_BG);
                bgPaint.setStyle(Paint.Style.FILL);
                bgPaint.setShadowLayer(r * 0.28f, 0, r * 0.10f, 0x55000000);
                canvas.drawCircle(cx, cy, r - r * 0.10f, bgPaint);

                // 相机机身
                float iconW = r * 1.15f;
                float iconH = r * 0.80f;
                float left = cx - iconW / 2f;
                float top = cy - iconH / 2f + r * 0.07f;
                float right = cx + iconW / 2f;
                float bottom = cy + iconH / 2f + r * 0.07f;

                iconPaint.setColor(COLOR_ICON);
                iconPaint.setStyle(Paint.Style.FILL);
                RectF body = new RectF(left, top, right, bottom);
                canvas.drawRoundRect(body, iconH * 0.22f, iconH * 0.22f, iconPaint);

                // 顶部凸起
                RectF bump = new RectF(
                        cx - iconW * 0.20f,
                        top - iconH * 0.16f,
                        cx + iconW * 0.20f,
                        top + iconH * 0.10f
                );
                canvas.drawRoundRect(bump, iconH * 0.06f, iconH * 0.06f, iconPaint);

                // 镜头（白圈 + 深色内芯）
                lensPaint.setColor(COLOR_LENS);
                lensPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy + r * 0.09f, iconH * 0.30f, lensPaint);

                lensPaint.setColor(COLOR_ICON);
                canvas.drawCircle(cx, cy + r * 0.09f, iconH * 0.19f, lensPaint);
            }
        };
        floatingView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        floatingView.setAlpha(0.92f);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        floatingParams = new WindowManager.LayoutParams(
                sizePx,
                sizePx,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        floatingParams.gravity = Gravity.TOP | Gravity.START;
        floatingParams.x = 100;
        floatingParams.y = 300;

        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = floatingParams.x;
                        startY = floatingParams.y;
                        moved = false;
                        v.setAlpha(0.55f);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - downX);
                        int dy = (int) (event.getRawY() - downY);
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            moved = true;
                        }
                        floatingParams.x = startX + dx;
                        floatingParams.y = startY + dy;
                        windowManager.updateViewLayout(v, floatingParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setAlpha(0.92f);
                        if (!moved) {
                            captureNow();
                        }
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(floatingView, floatingParams);
        } catch (Exception e) {
            Toast.makeText(this, "无法显示悬浮按钮: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void captureNow() {
        if (projection == null) {
            toast("授权已失效，请重新打开应用");
            return;
        }

        if (floatingView != null) {
            floatingView.setVisibility(View.GONE);
        }

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                doCapture();
            }
        }, 80);
    }

    private void doCapture() {
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        final int width = metrics.widthPixels;
        final int height = metrics.heightPixels;
        final int density = metrics.densityDpi;

        final ImageReader reader = ImageReader.newInstance(
                width, height, PixelFormat.RGBA_8888, 2
        );
        final VirtualDisplay[] displayHolder = new VirtualDisplay[1];

        reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                Image image = null;
                try {
                    image = imageReader.acquireLatestImage();
                    if (image == null) {
                        return;
                    }

                    Image.Plane plane = image.getPlanes()[0];
                    ByteBuffer buffer = plane.getBuffer();
                    int pixelStride = plane.getPixelStride();
                    int rowStride = plane.getRowStride();
                    int rowPadding = rowStride - pixelStride * width;

                    Bitmap padded = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                    );
                    buffer.rewind();
                    padded.copyPixelsFromBuffer(buffer);

                    Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);

                    File dir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists() && !dir.mkdirs()) {
                        throw new Exception("无法创建 Download 目录");
                    }

                    String fileName = "screenshot_" +
                            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) +
                            ".png";
                    File output = new File(dir, fileName);
                    FileOutputStream fos = new FileOutputStream(output);
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();

                    // 通知系统扫描，让相册 / 文件管理器立即可见
                    android.media.MediaScannerConnection.scanFile(
                            ScreenCaptureService.this,
                            new String[]{output.getAbsolutePath()},
                            new String[]{"image/png"},
                            null
                    );

                    toast("已保存到 Download: " + output.getName());
                } catch (Exception e) {
                    toast("截屏失败: " + e.getMessage());
                } finally {
                    if (image != null) {
                        image.close();
                    }
                    if (displayHolder[0] != null) {
                        displayHolder[0].release();
                        displayHolder[0] = null;
                    }
                    reader.close();
                    restoreFloatingButton();
                }
            }
        }, new Handler(Looper.getMainLooper()));

        displayHolder[0] = projection.createVirtualDisplay(
                "FloatingCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(),
                null,
                null
        );
    }

    private void restoreFloatingButton() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (floatingView != null) {
                    floatingView.setVisibility(View.VISIBLE);
                    floatingView.setAlpha(0.92f);
                }
            }
        });
    }

    private void toast(final String message) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ScreenCaptureService.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroy() {
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception ignored) {
            }
            floatingView = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
        super.onDestroy();
    }
}
