// Copyright (c) 2020 Facebook, Inc. and its affiliates.
// All rights reserved.
//
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

package org.pytorch.demo.objectdetection;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraX;
import androidx.camera.core.FlashMode;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.core.app.ActivityCompat;

//import java.util.concurrent.Executor;
//import androidx.camera.core.CameraSelector;
//import androidx.core.content.ContextCompat;
import androidx.camera.core.PreviewConfig;
import androidx.camera.core.ImageAnalysisConfig;

import java.security.Policy;
import java.util.Locale;

public abstract class AbstractCameraXActivity<R> extends BaseModuleActivity implements TextToSpeech.OnInitListener{//라이브러리 조작에 뼈대가 되는 activity (사용 라이브러리 하나당 베이스모듈 1개)
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 200;
    private static final String[] PERMISSIONS = {Manifest.permission.CAMERA};//카메라 권한 관련

    private static CameraManager mCameraManager;
    private static String mCameraId;
    private static boolean mFlashOn = false;

    protected long mLastAnalysisResultTime;//마지막으로 이미지 처리한 시점부터 지난 시간 == interval
    protected long mLastAlertTime;
    protected boolean TTS = true;
    protected TextToSpeech voice;
    protected abstract int getContentViewLayoutId();

    protected abstract TextureView getCameraPreviewTextureView();

    @Override
    protected void onCreate(Bundle savedInstanceState) {//생성
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayoutId());

        startBackgroundThread();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS,
                    REQUEST_CODE_CAMERA_PERMISSION);//권한 설정 요청
        } else {
            setupCameraX();//카메라 권한 설정에 성공시 카메라 객체 생성
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(
                                this,
                                "You can't use object detection example without granting CAMERA permission",
                                Toast.LENGTH_LONG)
                        .show();
                finish();//이 액티비티를 종료함 (권한 허용 안했으니까 프로그램을 아예 종료)
            } else {
                setupCameraX();//카메라 객체 생성함
            }
        }
    }

    private void temp(){
        return;
    }

    private void setupCameraX() {
        voice = new TextToSpeech(this, this);
        final TextureView textureView = getCameraPreviewTextureView();//카메라에 찍힌 거를 화면에 보여줌
        final PreviewConfig previewConfig = new PreviewConfig.Builder().build();
        final Preview preview = new Preview(previewConfig);
        preview.setOnPreviewOutputUpdateListener(output -> textureView.setSurfaceTexture(output.getSurfaceTexture()));
        ImageCaptureConfig icc = new ImageCaptureConfig.Builder()
                .setFlashMode(FlashMode.AUTO)
                .setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                .build();
        ImageCapture ic = new ImageCapture(icc);
        //이미지 프로세싱에 필요한 정보들을 설정(카메라 크기, 프레임, 등)
        final ImageAnalysisConfig imageAnalysisConfig =
                new ImageAnalysisConfig.Builder()
                        .setTargetResolution(new Size(480, 640))
                        .setCallbackHandler(mBackgroundHandler)
                        .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                        .build();
        final ImageAnalysis imageAnalysis = new ImageAnalysis(imageAnalysisConfig);
        imageAnalysis.setAnalyzer((image, rotationDegrees) -> {//이건 가로, 세로인지 방향 조정 가능
            ic.takePicture(new ImageCapture.OnImageCapturedListener(){
            });
            if (SystemClock.elapsedRealtime() - mLastAnalysisResultTime < 10) {/////이거 조정하면 될듯. 적는 숫자가 제한 프레임. 없애면 프레임 계속 들쭉날쭉
                return;
            }
            if (SystemClock.elapsedRealtime() - mLastAlertTime < 3000) {/////이거 조정하면 될듯. 적는 숫자가 제한 프레임. 없애면 프레임 계속 들쭉날쭉
                TTS = false;
            }
            else{
                TTS = true;

            }
            final R result = analyzeImage(image, rotationDegrees);
            if (result != null) {
                mLastAnalysisResultTime = SystemClock.elapsedRealtime(); // 위에서 프레임 조정에 필요
                runOnUiThread(() -> applyToUiAnalyzeImageResult(result));//박스 라벨링을 보이는 화면에 적용
            }
            ic.takePicture(new ImageCapture.OnImageCapturedListener(){
            });
        });
        CameraX.bindToLifecycle(this, preview, ic, imageAnalysis);//유저가 카메라에 켜고 끔에따라 객체가 생성/소멸되도록 함

    }

    @Override
    public void onDestroy() {
        if(voice!=null){ // 사용한 TTS객체 제거
            voice.stop();
            voice.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onInit(int status) { // OnInitListener를 통해서 TTS 초기화
        if(status == TextToSpeech.SUCCESS){
            int result = voice.setLanguage(Locale.KOREA); // TTS언어 한국어로 설정

            if(result == TextToSpeech.LANG_NOT_SUPPORTED || result == TextToSpeech.LANG_MISSING_DATA){
                Log.e("TTS", "This Language is not supported");
            }
        }else{
            Log.e("TTS", "Initialization Failed!");
        }
    }
    @WorkerThread
    @Nullable
    protected abstract R analyzeImage(ImageProxy image, int rotationDegrees);

    @UiThread
    protected abstract void applyToUiAnalyzeImageResult(R result);
}