package com.toolsapps.camerapro;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.Arrays;


public class MainActivity extends AppCompatActivity implements View.OnClickListener, ToggleButton.OnCheckedChangeListener {
    private TextureView cameraPreview;
    private int currentCameraId = 0;
    private CameraDevice currentCam;
    protected CaptureRequest.Builder captureRequestBuilder;
    protected CameraCaptureSession cameraCaptureSessions;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private ImageReader imageReader;
    private String[] cameraIds;
    private String filepath = "/sdcard/DCIM/CameraPro/";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    CameraManager mCameraManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        cameraPreview = findViewById(R.id.camera_preview);
        cameraPreview.setSurfaceTextureListener(textureListener);
        cameraIds = getCamerasIds();

        // Set Listeners
        findViewById(R.id.photo_button).setOnClickListener(this);
        ((ToggleButton) findViewById(R.id.button_flash)).setOnCheckedChangeListener(this);

        DrawPreview(cameraIds);
    }

    public void DrawPreview(String[] camerasIds) {
         if (!camerasIds[0].equals("error") || camerasIds.length != 0) {
             if (currentCameraId > camerasIds.length - 1) {
                 currentCameraId = 0;
             } else {
                 openCamera(camerasIds[currentCameraId]);
                 mCameraManager = (CameraManager) MainActivity.this.getSystemService(Context.CAMERA_SERVICE);
             }
         } else {
             Toast.makeText(this, R.string.camera_not_found, Toast.LENGTH_SHORT).show();
         }
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.photo_button:
                makePhoto();
                break;
            default:
                Log.d("Buttons", "BUTTONS_ERROR");
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton button, boolean checked) {
        switch(button.getId()) {
            case R.id.button_flash:
                    changeFlash(checked);
                break;
        }
    }

    private void changeFlash(boolean state) {
        if(state) {
            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
        } else {
            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }
    }


    private void makePhoto() {
        //TODO: GPS Location
        // captureRequestBuilder.set(CaptureRequest.JPEG_GPS_LOCATION,);
        captureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte)95);


    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            if (!getCamerasIds()[0].equals("error") || getCamerasIds().length != 0) {
                openCamera(getCamerasIds()[currentCameraId]);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }
    };

    // Camera functions

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            currentCam = cameraDevice;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            currentCam.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            currentCam.close();
            currentCam = null;
        }
    };

    private void createCameraPreview() {
        try {
            SurfaceTexture texture = cameraPreview.getSurfaceTexture();
            if (texture!= null) {
                Surface surface = new Surface(texture);
                captureRequestBuilder = currentCam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequestBuilder.addTarget(surface);
                currentCam.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                        if (null == currentCam) {
                            return;
                        }
                        cameraCaptureSessions = cameraCaptureSession;
                        updatePreview();
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                    }
                }, null);
            }
        } catch (CameraAccessException e) {

        }
    }

    protected void updatePreview() {
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void openCamera(String cameraId) {
        try {
            CameraManager cameraManager = (CameraManager) MainActivity.this.getSystemService(Context.CAMERA_SERVICE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            cameraManager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {

        }
    }

    private void closeCamera() {
        if (null != currentCam) {
            currentCam.close();
            currentCam = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION:
                if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(MainActivity.this, R.string.permission_denied, Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (cameraPreview.isAvailable()) {
            if (!getCamerasIds()[0].equals("error") || getCamerasIds().length != 0) {
                openCamera(getCamerasIds()[currentCameraId]);
            }
        } else {
            cameraPreview.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    public String[] getCamerasIds() {
        String[] camerasIds;
        CameraManager mCameraManager = (CameraManager) MainActivity.this.getSystemService(Context.CAMERA_SERVICE);
        try {
            camerasIds = mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            String[] error = {"error"};
            return error;
        }
        return camerasIds;
    }

    //TODO:Fix permission accepting bug

}
