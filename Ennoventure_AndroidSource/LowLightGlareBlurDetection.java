
 package com.example.ennovature;

         import android.Manifest;
         import android.content.Context;
         import android.content.pm.PackageManager;
         import android.content.res.AssetManager;
         import android.content.res.Configuration;
         import android.graphics.Bitmap;
         import android.graphics.Matrix;
         import android.graphics.Rect;
         import android.hardware.Sensor;
         import android.hardware.SensorEvent;
         import android.hardware.SensorEventListener;
         import android.hardware.SensorManager;
         import android.hardware.camera2.CameraCaptureSession;
         import android.hardware.camera2.CameraCharacteristics;
         import android.hardware.camera2.CaptureFailure;
         import android.hardware.camera2.CaptureRequest;
         import android.hardware.camera2.TotalCaptureResult;
         import android.hardware.camera2.params.MeteringRectangle;
         import android.os.Bundle;
         import android.os.Environment;
         import android.util.Log;
         import android.view.MotionEvent;
         import android.view.SurfaceView;
         import android.view.View;
         import android.view.WindowManager;
         import android.widget.Button;
         import android.widget.TextView;
         import android.widget.Toast;

         import androidx.annotation.NonNull;
         import androidx.core.app.ActivityCompat;
         import androidx.core.content.ContextCompat;

         import org.opencv.android.BaseLoaderCallback;
         import org.opencv.android.CameraActivity;
         import org.opencv.android.CameraBridgeViewBase;
         import org.opencv.android.LoaderCallbackInterface;
         import org.opencv.android.OpenCVLoader;
         import org.opencv.android.Utils;
         import org.opencv.core.Core;
         import org.opencv.core.CvType;
         import org.opencv.core.Mat;
         import org.opencv.core.MatOfDouble;
         import org.opencv.core.MatOfKeyPoint;
         import org.opencv.core.MatOfPoint;
         import org.opencv.core.MatOfPoint2f;

         import org.opencv.core.Scalar;
         import org.opencv.core.Size;
         import org.opencv.dnn.Dnn;
         import org.opencv.dnn.Net;
         import org.opencv.features2d.DescriptorMatcher;
         import org.opencv.imgcodecs.Imgcodecs;
         import org.opencv.imgproc.Imgproc;

         import java.io.BufferedInputStream;
         import java.io.File;
         import java.io.FileNotFoundException;
         import java.io.FileOutputStream;
         import java.io.FileWriter;
         import java.io.IOException;
         import java.io.OutputStreamWriter;
         import java.text.SimpleDateFormat;
         import java.util.ArrayList;
         import java.util.Collections;
         import java.util.Date;
         import java.util.List;

         import static android.os.Environment.DIRECTORY_DOCUMENTS;

// https://docs.opencv.org/master/d0/d6c/tutorial_dnn_android.html

public class LowLightGlareBlurDetection extends CameraActivity implements
        CameraBridgeViewBase.CvCameraViewListener2, View.OnTouchListener, CallbackInterface  {

    StringBuilder stringBuilder;
    TextView txt_Status;
    private static final String TAG = CaptureImage.class.getSimpleName();


    private Net net;
    private CameraBridgeViewBase mOpenCvCameraView;

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int STORAGE_PERMISSION_CODE = 101;
    //  FeatureDetector detector;
    // DescriptorExtractor descriptor;
    DescriptorMatcher matcher;
    Mat descriptors2, descriptors1;
    Mat img1;
    MatOfKeyPoint keypoints1, keypoints2;
    Bitmap bitmapClear = null;
    Mat CurrentFrame = null;
    String IsToggle;
    int imgCount = 0, blurCount = 0, notBlurCount = 0;

    SensorManager mSensorManager;
    Sensor mLightSensor;
    private float mLightQuantity;

    boolean isLowLightDetected = false;
    Button btn_Capture;
    CallbackInterface m1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "called onCreate");

        super.onCreate(savedInstanceState);



        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_low_light_glare_blur_detection);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.java_camera_view);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setFocusableInTouchMode(true);
        mOpenCvCameraView.setFocusable(true);




      mOpenCvCameraView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                Toast.makeText(getApplicationContext(), "touch", Toast.LENGTH_SHORT).show();


                mOpenCvCameraView.OnTouchListener(v,event);

                return true;
            }
        });
        btn_Capture = (Button) findViewById(R.id.takePicture);

      //  txtGlareValue = (TextView) findViewById(R.id.txt_GlareValue);
        txt_Status = (TextView) findViewById(R.id.txt_Status);

        //  txtValue = (TextView) findViewById(R.id.txt_Value);
        //  txtLightValue = (TextView) findViewById(R.id.txt_LightValue);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);


        SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                mLightQuantity = event.values[0];
                Log.i("LightQuantity", mLightQuantity + "");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        if (mLightQuantity < 10.0)
                        {
                            isLowLightDetected = false;
                            txt_Status.setVisibility(View.VISIBLE);
                            txt_Status.setText("Low-Light Detected");
                        }
                        else
                         {
                             txt_Status.setVisibility(View.INVISIBLE);
                             isLowLightDetected = true;
                             txt_Status.setText("");
                           //  btn_Capture.setVisibility(View.VISIBLE);
                         }

                    }
                });


            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };


        mSensorManager.registerListener(
                listener, mLightSensor, SensorManager.SENSOR_DELAY_UI);

        stringBuilder = new StringBuilder();



        btn_Capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (CurrentFrame != null) {
                    // new SaveImageTask().execute(bitmapClear);
                    // storeImage(bitmapClear,"BtnClickEvent");
                    // BlurRcognize(CurrentFrame);

                    //  MultipleGlareDetection(CurrentFrame,"btn_click");
                    //  PythonGlareDetection(CurrentFrame,"btn_click");



                    //Low Light Detection and Capture Code

                  /*  if(mLightQuantity < 10.0)
                    {
                        captureBitmap(CurrentFrame,"LowLightImg");
                    }
                    else
                    {
                        captureBitmap(CurrentFrame,"SufficentLightImg");
                    }*/

                    captureBitmap(CurrentFrame,"GoodImg");



                } else {
                    Toast.makeText(v.getContext(), "Frame Value Null", Toast.LENGTH_LONG).show();
                }


                //  Read0ImageFromExternalStorage();
                //showImageList();
//                imgCount = 0;
//                blurCount = 0;
//                notBlurCount = 0;

            }
        });


        checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, STORAGE_PERMISSION_CODE);
        checkPermission(Manifest.permission.CAMERA, CAMERA_PERMISSION_CODE);






        SimpleOrientationListener mOrientationListener = new SimpleOrientationListener(
                this) {
            @Override
            public void onSimpleOrientationChanged(int orientation) {
                if(orientation == Configuration.ORIENTATION_LANDSCAPE){

                    //   Toast.makeText(getApplicationContext(),"ORIENTATION_LANDSCAPE",Toast.LENGTH_LONG).show();

                }else if(orientation == Configuration.ORIENTATION_PORTRAIT){
                    //    Toast.makeText(getApplicationContext(),"ORIENTATION_PORTRAIT",Toast.LENGTH_LONG).show();
                }
            }
        };
        mOrientationListener.enable();





    }


    public void checkPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(LowLightGlareBlurDetection.this, permission) == PackageManager.PERMISSION_DENIED) {

            // Requesting the permission
            ActivityCompat.requestPermissions(LowLightGlareBlurDetection.this, new String[]{permission}, requestCode);
        } else {
            //  Toast.makeText(CaptureImage.this, "Permission already granted", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,
                permissions,
                grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(LowLightGlareBlurDetection.this, "Camera Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(LowLightGlareBlurDetection.this, "Camera Permission Denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(LowLightGlareBlurDetection.this, "Storage Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(LowLightGlareBlurDetection.this, "Storage Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }


    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, "onCameraViewStarted");

        String proto = getPath("MobileNetSSD_deploy.prototxt", this);
        String weights = getPath("MobileNetSSD_deploy.caffemodel", this);
        net = Dnn.readNetFromCaffe(proto, weights);
        Log.i(TAG, "Network loaded successfully");
    }

    @Override
    public void onCameraViewStopped() {
        Log.d(TAG, "onCameraViewStopped");
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        if (isLowLightDetected)
        PythonGlareDetection(inputFrame.rgba(),"");


        CurrentFrame = inputFrame.rgba();
        return inputFrame.rgba();
    }

    @Override
    public void AutoFocuEvent()
    {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                // captureBitmap(CurrentFrame,"AutoFocus");
                Toast.makeText(getApplicationContext(),"AutoFocus Start", Toast.LENGTH_SHORT).show();
            }
        });

    }


    public void PythonGlareDetection(Mat aInputFrame , String event) {

        try {

            final Mat Rgba = aInputFrame;
            Mat grayScaleGaussianBlur = new Mat();
            Mat grayScalethresh = new Mat();

            Imgproc.cvtColor(Rgba, grayScaleGaussianBlur, Imgproc.COLOR_BGR2GRAY);
            Imgproc.threshold(grayScaleGaussianBlur,grayScalethresh,30,255, Imgproc.THRESH_BINARY);

            MatOfDouble mu = new MatOfDouble(); // mean
            MatOfDouble sigma = new MatOfDouble(); // standard deviation
            Core.meanStdDev(grayScalethresh, mu, sigma);
            double variance = Math.pow(sigma.get(0,0)[0], 2);
            Log.i("variance", String.valueOf(variance));

           // if (variance >6000 && variance < 18000) // working fine
            if (variance >12000 && variance < 18000)
            {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        txt_Status.setVisibility(View.VISIBLE);
                        txt_Status.setText("Glare Detected");
                       // btn_Capture.setVisibility(View.INVISIBLE);


                      //  if(event == "btn_click")
                       //     captureBitmap(aInputFrame,"Glare");
                    }
                });
            }
            else
            {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                     //   txt_Status.setText("Not Glare \n"+variance);

                        txt_Status.setVisibility(View.INVISIBLE);
                        BlurRcognize(aInputFrame);
                      //  btn_Capture.setVisibility(View.VISIBLE);
                       // txt_Status.setText("");

                       // if(event == "btn_click")
                        //    captureBitmap(aInputFrame,"NotGlare");
                    }
                });


            }



        } catch (Exception ex) {

        }
    }





    public void BlurRcognize(Mat aInputFrame) {
        Mat laplacianImage8bit = new Mat();
        try {


            // Imgproc.cvtColor(aInputFrame, aInputFrame, Imgproc.COLOR_RGB2GRAY);
            descriptors2 = new Mat();
            keypoints2 = new MatOfKeyPoint();
            //  detector.detect(aInputFrame, keypoints2);
            //  descriptor.compute(aInputFrame, keypoints2, descriptors2);

            Bitmap image = Bitmap.createBitmap(aInputFrame.cols(), aInputFrame.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(aInputFrame, image);

            int l = CvType.CV_8UC1; //8-bit grey scale image
            Mat matImage = new Mat();
            Utils.bitmapToMat(image, matImage);
            Mat matImageGrey = new Mat();
            Imgproc.cvtColor(aInputFrame, matImageGrey, Imgproc.COLOR_BGR2GRAY);
            Bitmap destImage;
            destImage = Bitmap.createBitmap(image);
            Mat dst2 = new Mat();
            Utils.bitmapToMat(destImage, dst2);
            Mat laplacianImage = new Mat();
            dst2.convertTo(laplacianImage, l);
            Imgproc.Laplacian(matImageGrey, laplacianImage, CvType.CV_8U);

            laplacianImage.convertTo(laplacianImage8bit, l);
            Bitmap bmp = Bitmap.createBitmap(laplacianImage8bit.cols(), laplacianImage8bit.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(laplacianImage8bit, bmp);
            int[] pixels = new int[bmp.getHeight() * bmp.getWidth()];
            bmp.getPixels(pixels, 0, bmp.getWidth(), 0, 0, bmp.getWidth(), bmp.getHeight());
            float maxLap = -16777216; // 16m
            for (float pixel : pixels) {
                if (pixel > maxLap)
                    maxLap = pixel;
            }
            float soglia = -6118750;
            //  int soglia = -8118750;

            Mat gray = new Mat();
            Mat canny = new Mat();
            ArrayList<MatOfPoint> contours = new ArrayList<>();


            //   System.out.println("CountersList-->"+contours.toArray().toString());


            Imgproc.cvtColor(aInputFrame, gray, Imgproc.COLOR_RGB2GRAY);

            // Calculating borders of image using the Canny algorithm
            Imgproc.Canny(gray, canny, 180, 210);


            Imgproc.GaussianBlur(canny, canny, new Size(5, 5), 5);

            // Calculate the contours
            Imgproc.findContours(canny, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            int contourIdx = 0;
            Scalar redColor = new Scalar(255, 0, 0, 255);
            contourIdx = findLargestContour(contours);
            MatOfPoint2f approxCurve = new MatOfPoint2f();
            MatOfPoint2f contour2f = new MatOfPoint2f(contours.get(contourIdx).toArray());
            //Processing on mMOP2f1 which is in type MatOfPoint2f
            double approxDistance = Imgproc.arcLength(contour2f, true) * 0.02;
            Imgproc.approxPolyDP(contour2f, approxCurve, approxDistance, true);


            //Convert back to MatOfPoint
            MatOfPoint points = new MatOfPoint(approxCurve.toArray());


            if (maxLap <= soglia) {
                //Blur Image

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        txt_Status.setVisibility(View.VISIBLE);
                        txt_Status.setText("Blur Detected");
                      //  btn_Capture.setVisibility(View.INVISIBLE);
                        //  if(event == "btn_click")
                        //     captureBitmap(aInputFrame,"Glare");
                    }


                });
            } else {

                // Not Blur Image
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        txt_Status.setVisibility(View.INVISIBLE);
                      //  btn_Capture.setVisibility(View.VISIBLE);

                        //  if(event == "btn_click")
                        //     captureBitmap(aInputFrame,"Glare");
                    }


                });

            }

        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();

        }

    }



    public int findLargestContour(ArrayList<MatOfPoint> contours) {

        double maxVal = 0;
        int maxValIdx = 0;
        for (int contourIdx = 0; contourIdx < contours.size(); contourIdx++) {
            double contourArea = Imgproc.contourArea(contours.get(contourIdx));
            if (maxVal < contourArea) {
                maxVal = contourArea;
                maxValIdx = contourIdx;
            }
        }
        return maxValIdx;
    }



    public static Bitmap RotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private void captureBitmap(Mat mRgba, String imageType) {
        Bitmap bitmap = Bitmap.createBitmap(mOpenCvCameraView.getWidth() / 4, mOpenCvCameraView.getHeight() / 4, Bitmap.Config.ARGB_8888);
        try {
            bitmap = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mRgba, bitmap);

            // ImageView image = (ImageView) findViewById(R.id.x2);


            //  Bitmap edited = increaseBrightness(bitmap,50);
            //   image.setImageBitmap(RotateBitmap(edited,90));

            //  image.setImageBitmap(RotateBitmap(bitmap, 90));


            bitmapClear = RotateBitmap(bitmap, 90);
            storeImage(bitmapClear, imageType);

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    private void storeImage(Bitmap image, String imageType) {
        File pictureFile = getOutputMediaFile(imageType);
        if (pictureFile == null) {
            Log.d(TAG,
                    "Error creating media file, check storage permissions: ");// e.getMessage());
            return;
        }
        try {

            int newWidth = 1560;
            int newHeight = 2080;

            FileOutputStream fos = new FileOutputStream(pictureFile);
            //Bitmap resizedBitmap = Bitmap.createScaledBitmap(image, newWidth, newHeight, false);
            // resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos); // Save new height and width

            image.compress(Bitmap.CompressFormat.PNG, 100, fos);

            fos.close();

            //   Toast.makeText(CaptureImage.this, "Image saved success -" + pictureFile.toString(), Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
    }

    /**
     * Create a File for saving an image or video
     */
    private File getOutputMediaFile(String imageType) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        //String root = Environment.getExternalStorageDirectory().toString();

        File mediaStorageDir = null;
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.
        if (imageType == "Good") {
            //  mediaStorageDir = new File(root + "/Ennoventure/GoodImg");
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).getPath()
                    + "/Ennoventure/GoodImg");
        } else if (imageType == "Bad") {
            //  mediaStorageDir = new File(root + "/Ennoventure/BadImg");
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).getPath()
                    + "/Ennoventure/BadImg");
        }
        else if (imageType == "NotGlare") {
            //  mediaStorageDir = new File(root + "/Ennoventure/BadImg");
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).getPath()
                    + "/Ennoventure/NotGlare");
        }
        else if (imageType == "NotBlur") {
            //  mediaStorageDir = new File(root + "/Ennoventure/BadImg");
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).getPath()
                    + "/Ennoventure/NotBlur");
        }
        else if (imageType == "Glare") {
            //  mediaStorageDir = new File(root + "/Ennoventure/BadImg");
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).getPath()
                    + "/Ennoventure/GlareImg");
        }
        else if (imageType == "Blur") {
            //  mediaStorageDir = new File(root + "/Ennoventure/BadImg");
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).getPath()
                    + "/Ennoventure/BlurImg");
        }
        else if (imageType == "AutoFocus") {
            //  mediaStorageDir = new File(root + "/Ennoventure/BadImg");
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).getPath()
                    + "/Ennoventure/AutoFocus");
        }
        else if (imageType == "LowLightImg") {
            //  mediaStorageDir = new File(root + "/Ennoventure/BadImg");
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).getPath()
                    + "/Ennoventure/LowLightImg");
        }
        else if (imageType == "SufficentLightImg") {
            //  mediaStorageDir = new File(root + "/Ennoventure/BadImg");
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).getPath()
                    + "/Ennoventure/SufficentLightImg");
        } else if (imageType == "GoodImg") {
            //  mediaStorageDir = new File(root + "/Ennoventure/BadImg");
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).getPath()
                    + "/Ennoventure/GoodImg");
        } else {
            // mediaStorageDir = new File(root + "/Ennoventure");
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).getPath()
                    + "/Ennoventure");
        }

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }
        // Create a media file name

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String currentDateandTime = sdf.format(new Date());
        File mediaFile;
        String mImageName = currentDateandTime + ".png";
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
        return mediaFile;
    }

    @Override
    public boolean onTouch(final View v, MotionEvent event) {
        Log.d(TAG, "onTouch");

        return false;
    }

    // Upload file to storage and return a path.
    private static String getPath(String file, Context context) {
        AssetManager assetManager = context.getAssets();
        BufferedInputStream inputStream = null;
        try {
            // Read data from assets.
            inputStream = new BufferedInputStream(assetManager.open(file));
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            inputStream.close();
            // Create copy file in storage.
            File outFile = new File(context.getFilesDir(), file);
            FileOutputStream os = new FileOutputStream(outFile);
            os.write(data);
            os.close();
            // Return a path to file which may be read in common way.
            return outFile.getAbsolutePath();
        } catch (IOException ex) {
            Log.i(TAG, "Failed to upload a file");
        }
        return "";
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };




    @Override
    public void OnTouchListener() {

    }
}


