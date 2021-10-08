package org.opencv.android;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Toast;

import org.opencv.BuildConfig;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * This class is an implementation of the Bridge View between OpenCV and Java Camera.
 * This class relays on the functionality available in base class and only implements
 * required functions:
 * connectCamera - opens Java camera and sets the PreviewCallback to be delivered.
 * disconnectCamera - closes the camera and stops preview.
 * When frame is delivered via callback from Camera - it processed via OpenCV to be
 * converted to RGBA32 and then passed to the external callback for modifications if required.
 */
public class JavaCameraView extends CameraBridgeViewBase implements PreviewCallback  {

    private static final int MAGIC_TEXTURE_ID = 10;
    private static final String TAG = "JavaCameraView";

    private byte mBuffer[];
    private Mat[] mFrameChain;
    private int mChainIdx = 0;
    private Thread mThread;
    private boolean mStopThread;
    private int viewWidth;
    private int viewHeight;
    protected Camera mCamera;
    protected JavaCameraFrame[] mCameraFrame;
    private SurfaceTexture mSurfaceTexture;
    private int mPreviewFormat = ImageFormat.NV21;

    @Override
    public void OnTouchListener(View v, MotionEvent event) {
        setTapToFocus();
    }


    public static class JavaCameraSizeAccessor implements ListItemAccessor {

        @Override
        public int getWidth(Object obj) {
            Camera.Size size = (Camera.Size) obj;
            return size.width;
        }

        @Override
        public int getHeight(Object obj) {
            Camera.Size size = (Camera.Size) obj;
            return size.height;
        }
    }



    public JavaCameraView(Context context, int cameraId) {
        super(context, cameraId);
    }

    public JavaCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected boolean initializeCamera(int width, int height) {



        Log.d(TAG, "Initialize java camera");
        boolean result = true;
        synchronized (this) {
            mCamera = null;

            if (mCameraIndex == CAMERA_ID_ANY) {
                Log.d(TAG, "Trying to open camera with old open()");
                try {
                    mCamera = Camera.open();
                }
                catch (Exception e){
                    Log.e(TAG, "Camera is not available (in use or does not exist): " + e.getLocalizedMessage());
                }

                if(mCamera == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    boolean connected = false;
                    for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                        Log.d(TAG, "Trying to open camera with new open(" + Integer.valueOf(camIdx) + ")");
                        try {
                            mCamera = Camera.open(camIdx);
                            connected = true;
                        } catch (RuntimeException e) {
                            Log.e(TAG, "Camera #" + camIdx + "failed to open: " + e.getLocalizedMessage());
                        }
                        if (connected) break;
                    }
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    int localCameraIndex = mCameraIndex;
                    if (mCameraIndex == CAMERA_ID_BACK) {
                        Log.i(TAG, "Trying to open back camera");
                        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                        for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                            Camera.getCameraInfo( camIdx, cameraInfo );
                            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                                localCameraIndex = camIdx;
                                break;
                            }
                        }
                    } else if (mCameraIndex == CAMERA_ID_FRONT) {
                        Log.i(TAG, "Trying to open front camera");
                        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                        for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                            Camera.getCameraInfo( camIdx, cameraInfo );
                            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                localCameraIndex = camIdx;
                                break;
                            }
                        }
                    }
                    if (localCameraIndex == CAMERA_ID_BACK) {
                        Log.e(TAG, "Back camera not found!");
                    } else if (localCameraIndex == CAMERA_ID_FRONT) {
                        Log.e(TAG, "Front camera not found!");
                    } else {
                        Log.d(TAG, "Trying to open camera with new open(" + Integer.valueOf(localCameraIndex) + ")");
                        try {
                            mCamera = Camera.open(localCameraIndex);
                        } catch (RuntimeException e) {
                            Log.e(TAG, "Camera #" + localCameraIndex + "failed to open: " + e.getLocalizedMessage());
                        }
                    }
                }
            }

            if (mCamera == null)
                return false;

            /* Now set camera parameters */
            try {
                Camera.Parameters params = mCamera.getParameters();
                Log.d(TAG, "getSupportedPreviewSizes()");
                List<android.hardware.Camera.Size> sizes = params.getSupportedPreviewSizes();

                if (sizes != null) {
                    /* Select the size that fits surface considering maximum size allowed */
                    Size frameSize = calculateCameraFrameSize(sizes, new JavaCameraSizeAccessor(), width, height);


                    /* Image format NV21 causes issues in the Android emulators */
                    if (Build.FINGERPRINT.startsWith("generic")
                            || Build.FINGERPRINT.startsWith("unknown")
                            || Build.MODEL.contains("google_sdk")
                            || Build.MODEL.contains("Emulator")
                            || Build.MODEL.contains("Android SDK built for x86")
                            || Build.MANUFACTURER.contains("Genymotion")
                            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                            || "google_sdk".equals(Build.PRODUCT))
                        params.setPreviewFormat(ImageFormat.YV12);  // "generic" or "android" = android emulator
                    else
                        params.setPreviewFormat(ImageFormat.NV21);

                    mPreviewFormat = params.getPreviewFormat();

                    Log.d(TAG, "Set preview size to " + Integer.valueOf((int)frameSize.width) + "x" + Integer.valueOf((int)frameSize.height));
                    params.setPreviewSize((int)frameSize.width, (int)frameSize.height);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && !android.os.Build.MODEL.equals("GT-I9100"))
                        params.setRecordingHint(true);

                    List<String> FocusModes = params.getSupportedFocusModes();
                    if (FocusModes != null && FocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
                    {
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                    }

                    mCamera.setParameters(params);
                    params = mCamera.getParameters();

                    mFrameWidth = params.getPreviewSize().width;
                    mFrameHeight = params.getPreviewSize().height;

                    if ((getLayoutParams().width == LayoutParams.MATCH_PARENT) && (getLayoutParams().height == LayoutParams.MATCH_PARENT))
                        mScale = Math.min(((float)height)/mFrameHeight, ((float)width)/mFrameWidth);
                    else
                        mScale = 0;

                    if (mFpsMeter != null) {
                        mFpsMeter.setResolution(mFrameWidth, mFrameHeight);
                    }

                    int size = mFrameWidth * mFrameHeight;
                    size  = size * ImageFormat.getBitsPerPixel(params.getPreviewFormat()) / 8;
                    mBuffer = new byte[size];

                    mCamera.addCallbackBuffer(mBuffer);
                    mCamera.setPreviewCallbackWithBuffer(this);

                    mFrameChain = new Mat[2];
                    mFrameChain[0] = new Mat(mFrameHeight + (mFrameHeight/2), mFrameWidth, CvType.CV_8UC1);
                    mFrameChain[1] = new Mat(mFrameHeight + (mFrameHeight/2), mFrameWidth, CvType.CV_8UC1);

                    AllocateCache();

                    mCameraFrame = new JavaCameraFrame[2];
                    mCameraFrame[0] = new JavaCameraFrame(mFrameChain[0], mFrameWidth, mFrameHeight);
                    mCameraFrame[1] = new JavaCameraFrame(mFrameChain[1], mFrameWidth, mFrameHeight);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        mSurfaceTexture = new SurfaceTexture(MAGIC_TEXTURE_ID);

                        mCamera.setPreviewTexture(mSurfaceTexture);
                    } else
                       mCamera.setPreviewDisplay(null);

                    /* Finally we are ready to start the preview */
                    Log.d(TAG, "startPreview");
                    mCamera.startPreview();

                  /*  mCamera.setAutoFocusMoveCallback(new Camera.AutoFocusMoveCallback() {
                        @Override
                        public void onAutoFocusMoving(boolean success, Camera camera) {
                            if(!success) {
                               // AutoCallBack();
                                mCamera.cancelAutoFocus();
                            }
                        }
                    });
                     mCamera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                     public void onAutoFocus(boolean success, Camera camera) {

                            mCamera.cancelAutoFocus();

                        }
                    });*/





                }
                else
                    result = false;
            } catch (Exception e) {
                result = false;
                e.printStackTrace();
            }
        }

        return result;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void setTapToFocus() {
        setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP &&
                        !focusTo(v, event.getX(), event.getY())) {
                    v.setOnTouchListener(null);
                    return false;
                }
                v.performClick();
                return true;
            }
        });
    }

    public boolean focusTo(final View v, float x, float y) {
        if (mCamera == null) {
            return false;
        }
        // catch possible RuntimeException's for autoFocus()
        // as there a devices with broken camera drivers
        try {
            mCamera.cancelAutoFocus();
            if (!setFocusArea(calculateFocusRect(x, y, 100))) {
                return false;
            }
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    v.removeCallbacks(focusRunnable);
                    v.postDelayed(focusRunnable, 3000);
                }
            });
        } catch (RuntimeException e) {
            return false;
        }
        return true;
    }

    private final Runnable focusRunnable = new Runnable() {
        @Override
        public void run() {
            setFocusArea(null);
        }
    };

    public Rect calculateFocusRect(float x, float y, int radius) {
        int cx = Math.round(2000f / viewWidth * x - 1000f);
        int cy = Math.round(2000f / viewHeight * y - 1000f);


        System.out.println("CX value"+cx);
        System.out.println("CY value"+cy);


     //   return  new Rect(311,58,511,258);

         return new Rect(
                Math.max(-1000, cx - radius),
                Math.max(-1000, cy - radius),
                Math.min(1000, cx + radius),
                Math.min(1000, cy + radius));
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public boolean setFocusArea(Rect area) {
        if (mCamera == null || Build.VERSION.SDK_INT <
                Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return false;
        }
        try {
            Camera.Parameters parameters = mCamera.getParameters();
            if (parameters.getMaxNumFocusAreas() > 0) {
                if (area != null) {
                    List<Camera.Area> focusAreas =
                            new ArrayList<Camera.Area>();

                    List<String> focusModes = parameters.getSupportedFocusModes();



                    focusAreas.add(new Camera.Area(area, 1000));
                    parameters.setFocusAreas(focusAreas);
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                } else {
                    parameters.setFocusAreas(null);
                    JavaCameraView.setAutoFocus(parameters);
                }
            }
            mCamera.setParameters(parameters);
            return true;
        } catch (RuntimeException e) {

            String ex = e.getMessage().toString();
            System.out.println("Error"+ex);
            return false;
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static boolean setAutoFocus(Camera.Parameters parameters) {
        // best for taking pictures, API >= ICE_CREAM_SANDWICH
        String continuousPicture =
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
        // less aggressive than CONTINUOUS_PICTURE, API >= GINGERBREAD
        String continuousVideo =
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
        // last resort
        String autoFocus = Camera.Parameters.FOCUS_MODE_AUTO;

        // prefer feature detection instead of checking BUILD.VERSION
        List<String> focusModes = parameters.getSupportedFocusModes();

        if (focusModes.contains(continuousPicture)) {
            parameters.setFocusMode(continuousPicture);
        } else if (focusModes.contains(continuousVideo)) {
            parameters.setFocusMode(continuousVideo);
        } else if (focusModes.contains(autoFocus)) {
            parameters.setFocusMode(autoFocus);
        } else {
            return false;
        }

        return true;
    }


    protected void releaseCamera() {
        synchronized (this) {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.setPreviewCallback(null);

                mCamera.release();
            }
            mCamera = null;
            if (mFrameChain != null) {
                mFrameChain[0].release();
                mFrameChain[1].release();
            }
            if (mCameraFrame != null) {
                mCameraFrame[0].release();
                mCameraFrame[1].release();
            }
        }
    }

    private boolean mCameraFrameReady = false;

    @Override
    protected boolean connectCamera(int width, int height) {

        /* 1. We need to instantiate camera
         * 2. We need to start thread which will be getting frames
         */
        /* First step - initialize camera connection */

        viewHeight = height;
        viewWidth= width;


        Log.d(TAG, "Connecting to camera");
        if (!initializeCamera(width, height))
            return false;

        mCameraFrameReady = false;

        /* now we can start update thread */
        Log.d(TAG, "Starting processing thread");
        mStopThread = false;
        mThread = new Thread(new CameraWorker());
        mThread.start();

        return true;
    }

    @Override
    protected void disconnectCamera() {
        /* 1. We need to stop thread which updating the frames
         * 2. Stop camera and release it
         */
        Log.d(TAG, "Disconnecting from camera");
        try {
            mStopThread = true;
            Log.d(TAG, "Notify thread");
            synchronized (this) {
                this.notify();
            }
            Log.d(TAG, "Waiting for thread");
            if (mThread != null)
                mThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mThread =  null;
        }

        /* Now release camera */
        releaseCamera();

        mCameraFrameReady = false;
    }

    @Override
    public void onPreviewFrame(byte[] frame, Camera arg1) {
        if (BuildConfig.DEBUG)
            Log.d(TAG, "Preview Frame received. Frame size: " + frame.length);
        synchronized (this) {
            mFrameChain[mChainIdx].put(0, 0, frame);
            mCameraFrameReady = true;
            this.notify();
        }
        if (mCamera != null)
            mCamera.addCallbackBuffer(mBuffer);
    }







    private class JavaCameraFrame implements CvCameraViewFrame {
        @Override
        public Mat gray() {
            return mYuvFrameData.submat(0, mHeight, 0, mWidth);
        }

        @Override
        public Mat rgba() {
            if (mPreviewFormat == ImageFormat.NV21)
                Imgproc.cvtColor(mYuvFrameData, mRgba, Imgproc.COLOR_YUV2RGBA_NV21, 4);
            else if (mPreviewFormat == ImageFormat.YV12)
                Imgproc.cvtColor(mYuvFrameData, mRgba, Imgproc.COLOR_YUV2RGB_I420, 4);  // COLOR_YUV2RGBA_YV12 produces inverted colors
            else
                throw new IllegalArgumentException("Preview Format can be NV21 or YV12");

            return mRgba;
        }

        public JavaCameraFrame(Mat Yuv420sp, int width, int height) {
            super();
            mWidth = width;
            mHeight = height;
            mYuvFrameData = Yuv420sp;
            mRgba = new Mat();
        }

        public void release() {
            mRgba.release();
        }

        private Mat mYuvFrameData;
        private Mat mRgba;
        private int mWidth;
        private int mHeight;
    };

    private class CameraWorker implements Runnable {

        @Override
        public void run() {
            do {
                boolean hasFrame = false;
                synchronized (JavaCameraView.this) {
                    try {
                        while (!mCameraFrameReady && !mStopThread) {
                            JavaCameraView.this.wait();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (mCameraFrameReady)
                    {
                        mChainIdx = 1 - mChainIdx;
                        mCameraFrameReady = false;
                        hasFrame = true;
                    }
                }

                if (!mStopThread && hasFrame) {
                    if (!mFrameChain[1 - mChainIdx].empty())
                        deliverAndDrawFrame(mCameraFrame[1 - mChainIdx]);
                }
            } while (!mStopThread);
            Log.d(TAG, "Finish processing thread");
        }
    }
}
