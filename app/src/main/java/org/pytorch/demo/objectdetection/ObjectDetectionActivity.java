package org.pytorch.demo.objectdetection;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Build;
import android.os.VibrationEffect;
import android.util.Log;
import android.view.TextureView;
import android.view.ViewStub;

import android.os.Vibrator;

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
        return ((ViewStub) findViewById(R.id.object_detection_texture_view_stub))
                .inflate()
                .findViewById(R.id.object_detection_texture_view);
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


        for(int i=0;i<results.size();i++){
            Result tmp = results.get(i);
            int idx = tmp.classIndex;
            Rect box = tmp.rect;
            Log.i("object:", PrePostProcessor.mClasses[idx]);

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
                Log.i("isDanger:", "no\n");
            }
            else{
                Log.i("isDanger:", "yes\n");
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
                float ratio = (overlap)/(target1+target2-overlap);

                if(ratio >= threshold){
                    Log.i("threshold:","yes\n");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(1000,100));
                    }
                }
                else{
                    Log.i("threshold:", "no\n");
                }
            }
        }
    }


    protected Bitmap getDepthImage(Bitmap bitmap) {
        MiDASModel myMidas = new MiDASModel(this);
        Bitmap result = myMidas.getDepthMap(bitmap);
        return result;
    }

    @Override
    @WorkerThread
    @Nullable
    protected AnalysisResult analyzeImage(ImageProxy image, int rotationDegrees) {//모델이 예측 수행
        try {
            if (mModule == null) {
                mModule = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(),
                        modelName));//모델 불러오기
            }
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            return null;
        }
        Bitmap bitmap = imgToBitmap(image.getImage());

        //Bitmap depthBitmap = getDepthImage(bitmap);

        Matrix matrix = new Matrix();
        matrix.postRotate(90.0f);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
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

        detectInBox((float)mResultView.getWidth(),(float)mResultView.getHeight(),
                results,
                null, 0);

        return new AnalysisResult(results);
    }
}
