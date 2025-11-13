package com.example.real_time_edge_detection_viewer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends Activity {

    private static final int REQUEST_CAMERA_PERMISSION = 100;

    private TextureView textureView;
    private GLSurfaceView glSurfaceView;
    private GLRenderer glRenderer;
    private TextView tvDebug;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private ImageReader imageReader;

    private final Size PREVIEW_SIZE = new Size(1280, 720);

    // Native libs
    static {
        System.loadLibrary("opencv_java4");
        System.loadLibrary("native-lib");
    }

    // Native functions
    public native void nativeProcessFrame(byte[] yuv, int width, int height);

    public static native void nativeInitGL();
    public static native void nativeDestroyGL();
    public static native void nativeOnResume();
    public static native void nativeOnPause();
    public static native ByteBuffer nativeGetProcessedFrameBuffer();
    public static native int nativeGetProcessedFrameWidth();
    public static native int nativeGetProcessedFrameHeight();

    // ---------------------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.texture_view);
        glSurfaceView = findViewById(R.id.glSurfaceView);
        tvDebug = findViewById(R.id.tv_debug);

        // GL setup
        glSurfaceView.setEGLContextClientVersion(2);
        glRenderer = new GLRenderer();
        glSurfaceView.setRenderer(glRenderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        nativeInitGL();

        textureView.setSurfaceTextureListener(surfaceTextureListener);
    }

    // ---------------------------------------------------------------------------------------------
    // Surface available
    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
                    tvDebug.setText("Texture Ready");
                    openCamera();
                }

                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
            };

    // ---------------------------------------------------------------------------------------------
    private void openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION
            );
            return;
        }

        try {
            CameraManager mgr = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String camId = getBackCamera(mgr);

            if (camId == null) {
                Toast.makeText(this, "No back camera found", Toast.LENGTH_SHORT).show();
                return;
            }

            imageReader = ImageReader.newInstance(
                    PREVIEW_SIZE.getWidth(),
                    PREVIEW_SIZE.getHeight(),
                    android.graphics.ImageFormat.YUV_420_888,
                    2
            );

            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

            mgr.openCamera(camId, cameraStateCallback, backgroundHandler);

        } catch (Exception e) {
            Log.e("CAMERA", "openCamera: ", e);
        }
    }

    private String getBackCamera(CameraManager mgr) throws CameraAccessException {
        for (String id : mgr.getCameraIdList()) {
            Integer facing = mgr.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING);

            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK)
                return id;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    private final CameraDevice.StateCallback cameraStateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cd) {
                    cameraDevice = cd;
                    tvDebug.setText("Camera Opened");
                    startPreview();
                }

                @Override public void onDisconnected(CameraDevice cd) { cd.close(); }
                @Override public void onError(CameraDevice cd, int err) { cd.close(); }
            };

    private void startPreview() {
        try {
            SurfaceTexture st = textureView.getSurfaceTexture();
            st.setDefaultBufferSize(PREVIEW_SIZE.getWidth(), PREVIEW_SIZE.getHeight());

            Surface previewSurface = new Surface(st);

            previewRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.addTarget(imageReader.getSurface());

            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            captureSession = session;

                            previewRequestBuilder.set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            );

                            try {
                                captureSession.setRepeatingRequest(
                                        previewRequestBuilder.build(),
                                        null,
                                        backgroundHandler
                                );
                                tvDebug.setText("Camera Started");
                            } catch (Exception e) {
                                Log.e("PREVIEW", "startPreview error", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Toast.makeText(MainActivity.this,
                                    "Camera config failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    },
                    backgroundHandler
            );

        } catch (Exception e) {
            Log.e("PREVIEW", "startPreview error", e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = reader -> {
        Image img = reader.acquireLatestImage();
        if (img == null) return;

        int w = img.getWidth();
        int h = img.getHeight();

        byte[] nv21 = YUV_420_888_to_NV21(img);

        nativeProcessFrame(nv21, w, h);   // OpenCV + edge detection

        glSurfaceView.requestRender();    // IMPORTANT – draw processed frame

        img.close();
    };

    // ---------------------------------------------------------------------------------------------
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        glSurfaceView.onResume();
        nativeOnResume();

        if (textureView.isAvailable()) openCamera();
    }

    @Override
    protected void onPause() {
        nativeOnPause();
        glSurfaceView.onPause();
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        nativeDestroyGL();
        super.onDestroy();
    }

    // ---------------------------------------------------------------------------------------------
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBG");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try { backgroundThread.join(); } catch (Exception ignored) {}

            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    private void closeCamera() {
        if (captureSession != null) captureSession.close();
        if (cameraDevice != null) cameraDevice.close();
        if (imageReader != null) imageReader.close();
        captureSession = null;
        cameraDevice = null;
        imageReader = null;
    }

    // ---------------------------------------------------------------------------------------------
    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] results) {

        if (requestCode == REQUEST_CAMERA_PERMISSION &&
                results.length > 0 &&
                results[0] == PackageManager.PERMISSION_GRANTED) {

            openCamera();
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------------------------------------
    private byte[] YUV_420_888_to_NV21(Image image) {
        ByteBuffer y = image.getPlanes()[0].getBuffer();
        ByteBuffer u = image.getPlanes()[1].getBuffer();
        ByteBuffer v = image.getPlanes()[2].getBuffer();

        int ySize = y.remaining();
        int uSize = u.remaining();
        int vSize = v.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        y.get(nv21, 0, ySize);
        v.get(nv21, ySize, vSize);
        u.get(nv21, ySize + vSize, uSize);

        return nv21;
    }
}
