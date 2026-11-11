package com.example.screenclickcapture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1001;

    private MediaProjectionManager projectionManager;
    private Button captureButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        captureButton = findViewById(R.id.captureButton);

        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(
                        projectionManager.createScreenCaptureIntent(),
                        REQUEST_CODE_SCREEN_CAPTURE
                );
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_CODE_SCREEN_CAPTURE) {
            return;
        }

        if (resultCode == RESULT_OK && data != null) {
            MediaProjection projection = projectionManager.getMediaProjection(resultCode, data);
            if (projection != null) {
                captureScreen(projection);
            } else {
                Toast.makeText(this, "无法获取屏幕捕获权限", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "你取消了屏幕捕获授权", Toast.LENGTH_SHORT).show();
        }
    }

    private void captureScreen(final MediaProjection projection) {
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        final int width = metrics.widthPixels;
        final int height = metrics.heightPixels;
        final int density = metrics.densityDpi;

        final ImageReader reader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                2
        );

        final HandlerThread handlerThread = new HandlerThread("ScreenCaptureThread");
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());
        final AtomicBoolean captured = new AtomicBoolean(false);
        final VirtualDisplay[] virtualDisplayHolder = new VirtualDisplay[1];

        reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                if (!captured.compareAndSet(false, true)) {
                    return;
                }

                Image image = null;
                try {
                    image = imageReader.acquireLatestImage();
                    if (image == null) {
                        captured.set(false);
                        return;
                    }

                    Image.Plane plane = image.getPlanes()[0];
                    ByteBuffer buffer = plane.getBuffer();
                    int pixelStride = plane.getPixelStride();
                    int rowStride = plane.getRowStride();
                    int rowPadding = rowStride - pixelStride * width;

                    Bitmap paddedBitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                    );

                    buffer.rewind();
                    paddedBitmap.copyPixelsFromBuffer(buffer);

                    Bitmap croppedBitmap = Bitmap.createBitmap(
                            paddedBitmap,
                            0,
                            0,
                            width,
                            height
                    );

                    final File savedFile = saveBitmap(croppedBitmap);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(
                                    MainActivity.this,
                                    "已保存: " + savedFile.getAbsolutePath(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });

                } catch (Exception e) {
                    final String message = e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(
                                    MainActivity.this,
                                    "截屏失败: " + message,
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });
                } finally {
                    if (image != null) {
                        image.close();
                    }
                    if (virtualDisplayHolder[0] != null) {
                        virtualDisplayHolder[0].release();
                        virtualDisplayHolder[0] = null;
                    }
                    reader.close();
                    projection.stop();
                    handlerThread.quitSafely();
                }
            }
        }, handler);

        virtualDisplayHolder[0] = projection.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(),
                null,
                null
        );
    }

    private File saveBitmap(Bitmap bitmap) throws Exception {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir == null) {
            dir = getFilesDir();
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("无法创建保存目录");
        }

        String fileName = "screenshot_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) +
                ".png";

        File file = new File(dir, fileName);
        FileOutputStream fos = new FileOutputStream(file);
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
        } finally {
            fos.close();
            bitmap.recycle();
        }
        return file;
    }
}
