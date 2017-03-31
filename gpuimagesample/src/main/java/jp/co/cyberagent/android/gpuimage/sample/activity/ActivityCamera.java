/*
 * Copyright (C) 2012 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.cyberagent.android.gpuimage.sample.activity;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import jp.co.cyberagent.android.gpuimage.GPUImage;
import jp.co.cyberagent.android.gpuimage.GPUImage.OnPictureSavedListener;
import jp.co.cyberagent.android.gpuimage.GPUImageFilter;
import jp.co.cyberagent.android.gpuimage.sample.GPUImageFilterTools;
import jp.co.cyberagent.android.gpuimage.sample.GPUImageFilterTools.FilterAdjuster;
import jp.co.cyberagent.android.gpuimage.sample.GPUImageFilterTools.OnGpuImageFilterChosenListener;
import jp.co.cyberagent.android.gpuimage.sample.R;
import jp.co.cyberagent.android.gpuimage.sample.utils.CameraHelper;
import jp.co.cyberagent.android.gpuimage.sample.utils.CameraUtils;

public class ActivityCamera extends Activity implements OnSeekBarChangeListener,
        OnClickListener {

    private GPUImage mGPUImage;
    private CameraHelper mCameraHelper;
    private CameraLoader mCameraLoader;
    private GPUImageFilter mFilter;
    private FilterAdjuster mFilterAdjuster;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        ((SeekBar) findViewById(R.id.seekBar)).setOnSeekBarChangeListener(this);
        findViewById(R.id.button_choose_filter).setOnClickListener(this);
        findViewById(R.id.button_capture).setOnClickListener(this);
//      1。gpuimage init
        mGPUImage = new GPUImage(this);
        mGPUImage.setFilter(new jp.co.cyberagent.android.gpuimage.GPUImageBilateralFilter(30f));
        mGPUImage.setFilter(new jp.co.cyberagent.android.gpuimage.GPUImageContrastFilter(0.88f));
        mGPUImage.setFilter(new jp.co.cyberagent.android.gpuimage.GPUImageSaturationFilter(0.7f));
        mGPUImage.setFilter(new jp.co.cyberagent.android.gpuimage.GPUImageBrightnessFilter(0.32f));
        mGPUImage.setFilter(new jp.co.cyberagent.android.gpuimage.GPUImageSharpenFilter(0.48f));
        mGPUImage.setGLSurfaceView((GLSurfaceView) findViewById(R.id.surfaceView));

        mCameraHelper = new CameraHelper(this);
        mCameraLoader = new CameraLoader();

        View cameraSwitchView = findViewById(R.id.img_switch_camera);
        cameraSwitchView.setOnClickListener(this);
        if (!mCameraHelper.hasFrontCamera() || !mCameraHelper.hasBackCamera()) {
            cameraSwitchView.setVisibility(View.GONE);
        }

        int permi = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permi != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = {Manifest.permission.CAMERA};
            ActivityCompat.requestPermissions(this, permissions, 111);
        }


        Log.e("systeminfo", "Product Model: " + android.os.Build.MODEL + ","
                + android.os.Build.VERSION.SDK + ","
                + android.os.Build.VERSION.RELEASE);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission Granted
            Toast.makeText(this, "granted", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            // Permission Denied
            Toast.makeText(ActivityCamera.this, "WRITE_CONTACTS Denied", Toast.LENGTH_SHORT)
                    .show();
        }
    }

    boolean hasResumed;

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasResumed) {
            mCameraLoader.onResume();
            hasResumed = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraLoader.onPause();

    }

    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.button_choose_filter:
                GPUImageFilterTools.showDialog(this, new OnGpuImageFilterChosenListener() {

                    @Override
                    public void onGpuImageFilterChosenListener(final GPUImageFilter filter) {
                        switchFilterTo(filter);
                    }
                });
                break;

            case R.id.button_capture:
                if (mCameraLoader.mCameraInstance.getParameters().getFocusMode().equals(
                        Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    takePicture();
                } else {
                    mCameraLoader.mCameraInstance.autoFocus(new Camera.AutoFocusCallback() {

                        @Override
                        public void onAutoFocus(final boolean success, final Camera camera) {
                            takePicture();
                        }
                    });
                }
                break;

            case R.id.img_switch_camera:
                mCameraLoader.switchCamera();
                break;
        }
    }

    private void takePicture() {
        // TODO get a size that is about the size of the screen
        Camera.Parameters params = mCameraLoader.mCameraInstance.getParameters();
        params.setPictureSize(1280, 960);
        params.setRotation(90);
        mCameraLoader.mCameraInstance.setParameters(params);
        for (Camera.Size size2 : mCameraLoader.mCameraInstance.getParameters()
                .getSupportedPictureSizes()) {
            Log.i("ASDF", "Supported: " + size2.width + "x" + size2.height);
        }
        mCameraLoader.mCameraInstance.takePicture(null, null,
                new Camera.PictureCallback() {

                    @Override
                    public void onPictureTaken(byte[] data, final Camera camera) {

                        final File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
                        if (pictureFile == null) {
                            Log.d("ASDF",
                                    "Error creating media file, check storage permissions");
                            return;
                        }

                        try {
                            FileOutputStream fos = new FileOutputStream(pictureFile);
                            fos.write(data);
                            fos.close();
                        } catch (FileNotFoundException e) {
                            Log.d("ASDF", "File not found: " + e.getMessage());
                        } catch (IOException e) {
                            Log.d("ASDF", "Error accessing file: " + e.getMessage());
                        }

                        data = null;
                        Bitmap bitmap = BitmapFactory.decodeFile(pictureFile
                                .getAbsolutePath());
                        // mGPUImage.setImage(bitmap);
                        final GLSurfaceView view = (GLSurfaceView) findViewById(R.id.surfaceView);
                        view.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
                        mGPUImage.saveToPictures(bitmap, "GPUImage",
                                System.currentTimeMillis() + ".jpg",
                                new OnPictureSavedListener() {

                                    @Override
                                    public void onPictureSaved(final Uri
                                                                       uri) {
                                        pictureFile.delete();
                                        camera.startPreview();
                                        view.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
                                    }
                                });
                    }
                });
    }

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    private static File getOutputMediaFile(final int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    private void switchFilterTo(final GPUImageFilter filter) {
        if (mFilter == null
                || (filter != null && !mFilter.getClass().equals(filter.getClass()))) {
            mFilter = filter;
            mGPUImage.setFilter(mFilter);
            mFilterAdjuster = new FilterAdjuster(mFilter);
        }
    }

    @Override
    public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
        if (mFilterAdjuster != null) {
            mFilterAdjuster.adjust(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(final SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(final SeekBar seekBar) {
    }

    private class CameraLoader {
        private int mCurrentCameraId = -1;
        private Camera mCameraInstance;

        public void onResume() {
            Camera.CameraInfo info = new Camera.CameraInfo();
            int numCameras = Camera.getNumberOfCameras();
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    mCurrentCameraId = i;
                    break;
                }
            }
            setUpCamera(mCurrentCameraId);
        }

        public void onPause() {
            releaseCamera();
        }

        public void switchCamera() {
            isfront=!isfront;
            releaseCamera();
            setUpCamera(mCurrentCameraId);
        }

        boolean isfront=true;
        //       2.setupcamera
        private void setUpCamera(final int id) {

//            try {
//                mCameraInstance = getCameraInstance(id);
//            } catch (Exception e) {
//                e.printStackTrace();
//                Toast.makeText(ActivityCamera.this, "init camera failed" + e.getMessage(), Toast.LENGTH_LONG).show();
//            }
//            if (mCameraInstance == null) {
//
//                return;
//            }
//
//            Parameters parameters = mCameraInstance.getParameters();
//            logSize(parameters);
//            // TODO adjust by getting supportedPreviewSizes and then choosing
//            // the best one for screen size (best fill screen)
//            parameters.setPreviewSize(720, 480);
//            if (parameters.getSupportedFocusModes().contains(
//                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
//                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
//            }
//            mCameraInstance.setParameters(parameters);
//
//            int orientation = mCameraHelper.getCameraDisplayOrientation(
//                    ActivityCamera.this, mCurrentCameraId);
//            CameraInfo2 cameraInfo = new CameraInfo2();
//            mCameraHelper.getCameraInfo(mCurrentCameraId, cameraInfo);
//            boolean flipHorizontal = cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT
//                    ? true : false;
//            mGPUImage.setUpCamera(mCameraInstance, orientation, flipHorizontal, false);


            onPause();
            Camera.CameraInfo info = new Camera.CameraInfo();
            int cameraId = 0;
            int numCameras = Camera.getNumberOfCameras();
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (isfront){
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        cameraId = i;
                        try {
                            mCameraInstance = Camera.open(i);
                        } catch (Exception e) {
                            releaseCamera();
                            finish();
                            return;
                        }
                    }
                }else{
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        cameraId = i;
                        try {
                            mCameraInstance = Camera.open(i);
                        } catch (Exception e) {
                            releaseCamera();
                            finish();
                            return;
                        }
                    }
                }
            }
            if (mCameraInstance == null) {
                finish();
                return;
            }

            CameraUtils.setCameraDisplayOrientation(ActivityCamera.this, cameraId, mCameraInstance);

            Camera.Parameters parameters = mCameraInstance.getParameters();
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            mCameraInstance.setDisplayOrientation(90);
            int desiredWidth = 1280;
            int desiredHeight = 720;
            CameraUtils.choosePreviewSize(parameters, desiredWidth, desiredHeight);
            mCameraInstance.setParameters(parameters);


//            mCameraInstance = getCameraInstance(id);
//            Camera.Parameters parameters = mCameraInstance.getParameters();
//            // TODO adjust by getting supportedPreviewSizes and then choosing
//            // the best one for screen size (best fill screen)
//            parameters.setPreviewSize(720, 480);
//            if (parameters.getSupportedFocusModes().contains(
//                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
//                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
//            }
//            mCameraInstance.setParameters(parameters);
//
            int orientation = mCameraHelper.getCameraDisplayOrientation(
                    ActivityCamera.this, cameraId);
            CameraHelper.CameraInfo2 cameraInfo = new CameraHelper.CameraInfo2();
            mCameraHelper.getCameraInfo(cameraId, cameraInfo);
            boolean flipHorizontal = cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
                    ? true : false;
            mGPUImage.setUpCamera(mCameraInstance, orientation, flipHorizontal, false);


        }

        /**
         * A safe way to get an instance of the Camera object.
         */
        private Camera getCameraInstance(final int id) {
            Camera c = null;
            try {
                c = mCameraHelper.openCamera(id);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return c;
        }

        private void releaseCamera() {
            if (mCameraInstance!=null){
                mCameraInstance.setPreviewCallback(null);
                mCameraInstance.release();
                mCameraInstance = null;
            }
        }
    }

    public void logSize(Camera.Parameters mParameters) {
        List<Camera.Size> pictureSizes = mParameters.getSupportedPictureSizes();
        int length = pictureSizes.size();
        for (int i = 0; i < length; i++) {
            Log.d("TEST", "SupportedPictureSizes : " + pictureSizes.get(i).width + "x" + pictureSizes.get(i).height);
        }

//List<Size> previewSizes = mCameraDevice.getCamera().getParameters().getSupportedPreviewSizes();
        List<Camera.Size> previewSizes = mParameters.getSupportedPreviewSizes();
        length = previewSizes.size();
        for (int i = 0; i < length; i++) {
            Log.d("TEST", "SupportedPreviewSizes : " + previewSizes.get(i).width + "x" + previewSizes.get(i).height);
        }
    }

}
