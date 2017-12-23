package com.sh1r0.caffe_android_demo;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import static com.sh1r0.caffe_android_demo.MainActivity.IMAGENET_CLASSES;

public class CameraView extends Activity implements CNNListener {
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
   // private Long StartProcessTime ;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;

    ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 1);

    private TextView tvLabel;
    private Long startTimeP;
    private int count = 0;
    private String TAG = "Caffe-Camera";

    ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            byte[] buffer = null;
            byte[] buffer2 = null;
            try {
                image = reader.acquireLatestImage();
                if (System.currentTimeMillis() - startTimeP >= 1500 && image != null) {
                    Log.i(TAG, "The New image is available in the image reader ");
                    startTimeP = System.currentTimeMillis();
                    buffer = FileManager.YUV_420_888toNV21(image);
                    Log.i(TAG, "Image converted to NV21");
                    buffer2 = FileManager.NV21toJPEG(buffer, 640, 480);
                    Log.i(TAG, "Image converted to JPEG");
                    String name = "IMG_"+String.valueOf(System.currentTimeMillis()) + ".jpeg";
                    FileManager.writeFrame(name, buffer2);
                    Log.i(TAG, "File written to external storage");
                    CNNTask cnnTask = new CNNTask(CameraView.this,System.currentTimeMillis());
                    cnnTask.execute(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Caffe-Live-Capture/" + name);
                }
            } finally {
                Log.i(TAG, "Closing image and file output stream");
                if (image != null) {
                    image.close();
                }
            }

        }

    };
    private String cameraId;
    private Size imageDimension;
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private TextureView textureView;

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.i(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_view);
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        tvLabel = (TextView) findViewById(R.id.lblCapture);
        startTimeP = System.currentTimeMillis();

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

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            captureRequestBuilder.addTarget(reader.getSurface());
            cameraDevice.createCaptureSession(Arrays.asList(surface, reader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(CameraView.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.i(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
           /* if (Activity.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Activity.requestPermissions(CameraView.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }*/
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "openCamera X");
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.i(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onTaskCompleted(int result) {
        tvLabel.setText("");
        tvLabel.setText(IMAGENET_CLASSES[result]);
    }

    public class CNNTask extends AsyncTask<String, Void, Integer> {
        private CNNListener listener;
        private long startTime;
        private long startall;
        private String fileName;

        public CNNTask(CNNListener listener, Long startAll) {
            this.listener = listener;
            this.startall = startAll;
        }

        @Override
        protected Integer doInBackground(String... strings) {
            startTime = SystemClock.uptimeMillis();
            fileName = strings[0];
            return MainActivity.caffeMobile.predictImage(fileName)[0];
        }

        @Override
        protected void onPostExecute(Integer integer) {
            Long nowTime = SystemClock.uptimeMillis();
            Long now = System.currentTimeMillis();
            Long elapsedTime = nowTime - startTime;
            Long totalElapsed = now - startall;
            Log.i(TAG, String.format("elapsed wall time: %d ms, and total elapsed time is %d ms", elapsedTime, totalElapsed));
            String Message = fileName +";" + elapsedTime + " ms;"+ totalElapsed+" ms;"+ IMAGENET_CLASSES[integer];
            appendLog(Message);
            listener.onTaskCompleted(integer);
            super.onPostExecute(integer);
        }
    }

    public static void appendLog(String message) {
        File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Caffe-Live-Capture");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File logFile = new File(dir, "Log.txt");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(message);
            buf.newLine();
            buf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}

