package org.pytorch.demo.objectdetection;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewStub;

import android.os.Vibrator;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.camera.core.ImageProxy;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.demo.depthestimation.MiDASModel;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class ObjectDetectionActivity extends AbstractCameraXActivity<ObjectDetectionActivity.AnalysisResult> {
    private String modelName = "yolov5s.torchscript";
    private Module mModule = null;
    private ResultView mResultView;

    public static Bitmap temp = null;
    private static boolean isMidas = false;

    public class depthFinding{

    }
    public boolean mode;

    static class AnalysisResult {
        private final ArrayList<Result> mResults;

        public AnalysisResult(ArrayList<Result> results) {
            mResults = results;
        }
    }

    @Override
    protected int getContentViewLayoutId() {
        return R.layout.activity_object_detection;
    }

    @Override
    protected TextureView getCameraPreviewTextureView() {
        mResultView = findViewById(R.id.resultView);

        final ViewStub camera = findViewById(R.id.object_detection_texture_view_stub);
        final ResultView box = findViewById(R.id.resultView);
        final TextView text = findViewById(R.id.resultTextView);
        final TextureView texture =((ViewStub) findViewById(R.id.object_detection_texture_view_stub))
                .inflate()
                .findViewById(R.id.object_detection_texture_view);

        if(MainActivity.hidemode)
        {
            camera.setAlpha(0.0f);
            box.setAlpha(0.0f);
            text.setAlpha(0.0f);
            texture.setAlpha(0.0f);
        }
        else{
            camera.setAlpha(1.0f);
            box.setAlpha(1.0f);
            text.setAlpha(1.0f);
            texture.setAlpha(1.0f);
        }

        return texture;
     //           = ((ViewStub) findViewById(R.id.object_detection_texture_view_stub))
      //          .inflate()
       //         .findViewById(R.id.object_detection_texture_view);
    }

    @Override
    protected void applyToUiAnalyzeImageResult(AnalysisResult result) {
        mResultView.setResults(result.mResults);
        mResultView.invalidate();
    }

    private Bitmap imgToBitmap(Image image) {//이미지를 비트맵으로 변환해야 모델이 사용가능함.
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    protected void detectInBox(float rX, float rY,
                               ArrayList<Result> results,
                               int[] classes, float threshold){
        Vibrator vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
        float tX1 = rX/3;   float tY1 = rY/3;
        float tX2 = rX*2/3; float tY2 = rY*2/3;
        int count = 0;
        int detected[] = new int[PrePostProcessor.nClass];

        StringBuilder resultBuilder = new StringBuilder();

        for(int i=0;i<results.size();i++){
            Result tmp = results.get(i);
            int idx = tmp.classIndex;
            Rect box = tmp.rect;
            //Log.i("object:", PrePostProcessor.mClasses[idx]);
            //Log.i("threshold:",box.left + " " + box.right+ " " + box.top + " " + box.bottom);

            boolean isDanger = false;
            int l = 0;
            if(classes != null)
                l = classes.length;
            for(int j=0;j<l;j++){
                if(classes[j] == idx){
                    isDanger = true;
                    break;
                }
            }

            if(!isDanger){
                //Log.i("isDanger:", "no\n");
            }
            else{
                //Log.i("isDanger:", "yes\n");
            }

            if(tX1 >= box.right || box.left >= tX2 ||
                    tY1 >= box.bottom || box.top >= tY2 ) {
            }
            else{
                float x1 = tX1 >= box.left ? tX1 : box.left;
                float x2 = tX2 <= box.right ? tX2 : box.right;
                float y1 = tY1 >= box.top ? tY1 : box.top;
                float y2 = tY2 <= box.bottom ? tY2 : box.bottom;

                float target1 = (tX2-tX1)*(tY2-tY1);
                float target2 = (box.right-box.left)*(box.bottom - box.top);
                float overlap = (x2-x1)*(y2-y1);
                float ratio = (overlap)/(target2) > (overlap)/(target1) ? (overlap)/(target2) : (overlap)/(target1);
                //Log.i("threshold:",overlap + " " + target1 + " " + target2 + " " +  ratio);
                //Log.i("threshold:",rX + " " + rY + " " + box.right + " " +  box.left + " " + box.top + " " + box.bottom + " " + overlap);
                if(ratio >= threshold){
                    //Log.i("threshold:","yes\n");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(10,100));
                    }
                    detected[idx]++;
                    count++;

                    resultBuilder.append("Class: ").append(PrePostProcessor.mClasses[idx]).append(", ");
                    resultBuilder.append("tX1: ").append(tX1).append(", ");
                    resultBuilder.append("tX2: ").append(tX2).append(", ");
                    resultBuilder.append("tY1: ").append(tY1).append(", ");
                    resultBuilder.append("tY2: ").append(tY2).append(", ");
                    resultBuilder.append("\n");
                }
                else{
                    Log.i("threshold:", "no\n");
                }
            }
        }

        TextView resultTextView = findViewById(R.id.resultTextView);
        resultTextView.setText(resultBuilder.toString());

        //tts
        if(!this.TTS){//아직 tts로 알려준지 얼마 안됐음.
            return;
        }

        if(count == 0){
            //Log.i("tts:","no target objects to tts");
        }
        else{
            CharSequence text;
            String temp = "";
            voice.setPitch((float)0.6); // 음성 톤 높이 지정
            voice.setSpeechRate((float)1.5); // 음성 속도 지정

            this.mLastAlertTime = this.mLastAnalysisResultTime;
            Log.i("detected for TTS: ","in time" + this.mLastAlertTime);
            for(int i=0;i<PrePostProcessor.nClass;i++){
                if(detected[i] > 0){
                    temp += detected[i] +PrePostProcessor.mClasses[i] +",";
                }
            }
            voice.setSpeechRate((float)6); // 음성 속도 지정
            temp += "가 영역내에서 감지되었습니다.";
            Log.i("",temp);
            text = temp;
            voice.speak(text, TextToSpeech.QUEUE_ADD, null, "id1");
        }

    }

    private MiDASModel myMidas = null;

    protected Bitmap getDepthImage(Bitmap bitmap, long t1) {
        Log.i( "in ObjectDetection", "MiDaSStart inference speed: " + (System.currentTimeMillis() - t1));
        Bitmap result = myMidas.getDepthMap(bitmap, t1);
        Log.i( "in ObjectDetection", "MiDaSEnd inference speed: " + (System.currentTimeMillis() - t1));
        return result;
    }

    @Override
    @WorkerThread
    @Nullable
    protected AnalysisResult analyzeImage(ImageProxy image, int rotationDegrees) {//모델이 예측 수행

        long t1 = System.currentTimeMillis();

        try {
            if (mModule == null) {
                mModule = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(),
                        modelName));//모델 불러오기
            }
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            return null;
        }


        Log.i( "in ObjectDetection", "setModel inference speed: " + (System.currentTimeMillis() - t1));

        Bitmap bitmap = imgToBitmap(image.getImage());

        Log.i( "in ObjectDetection", "getBitmap inference speed: " + (System.currentTimeMillis() - t1));


        Log.i( "in ObjectDetection", "getDepth inference speed: " + (System.currentTimeMillis() - t1));

        Matrix matrix = new Matrix();
        matrix.postRotate(90.0f);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        matrix.postRotate(-90.0f);

        Bitmap finalBitmap = bitmap;
        Thread thread = new Thread(() -> {
            myMidas = new MiDASModel(this);
            isMidas = true;
            Bitmap depthBitmap = getDepthImage(finalBitmap, t1);
            temp = depthBitmap;
            temp = Bitmap.createBitmap(depthBitmap, 0, 0, depthBitmap.getWidth(), depthBitmap.getHeight(), matrix, true);
            isMidas = false;

        });

        if(!isMidas){
            thread.start();
        }

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true);

        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB);
        IValue[] outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple();
        final Tensor outputTensor = outputTuple[0].toTensor();
        final float[] outputs = outputTensor.getDataAsFloatArray();

        float imgScaleX = (float)bitmap.getWidth() / PrePostProcessor.mInputWidth;
        float imgScaleY = (float)bitmap.getHeight() / PrePostProcessor.mInputHeight;
        float ivScaleX = (float)mResultView.getWidth() / bitmap.getWidth();
        float ivScaleY = (float)mResultView.getHeight() / bitmap.getHeight();


        final ArrayList<Result> results = PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, 0, 0);

        Log.i( "in ObjectDetection", "getDetection inference speed: " + (System.currentTimeMillis() - t1));

        detectInBox((float)mResultView.getWidth(),(float)mResultView.getHeight(),
                results,
                null, 0.5f);


        Log.i( "in ObjectDetection", "ObjectDetection inference speed: " + (System.currentTimeMillis() - t1));

        return new AnalysisResult(results);
    }
}
