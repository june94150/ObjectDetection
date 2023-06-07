
package org.pytorch.demo.objectdetection;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements Runnable {//c언어 main이라 생각하면 됩니다ㅏ

    private String modelName = "yolov5s.torchscript";
    private String metaName = "classes.txt";

    private int mImageIndex = 0;
    private String[] mTestImages = {"test1.png", "test2.jpg", "test3.png"};//테스트 이미지 셋. 나중에는 live빼고는 다 없애지 않을까 싶음

    private ImageView mImageView;
    private ResultView mResultView;
    private Button mButtonDetect;
    private ProgressBar mProgressBar;
    private Bitmap mBitmap = null;//이미지를 비트맵으로 변환 후, 프로세싱함
    private Module mModule = null;//이미지 모델 로드
    private float mImgScaleX, mImgScaleY, mIvScaleX, mIvScaleY, mStartX, mStartY;//이미지 프로세싱

    static public boolean hidemode = false;
    static public boolean vib = true;
    static public boolean showlog = true;


    protected class backgroundThread extends Thread{
        protected boolean isRun = true;
    }

    public static String assetFilePath(Context context, String assetName) throws IOException {//asset   파일들의 위치 경로를 설정해줌. 건들x
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {//객체 생성
        super.onCreate(savedInstanceState);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        //권한 관련

        setContentView(R.layout.activity_main);//


        final Button buttonLive = findViewById(R.id.liveButton);//라이브 버튼. 아마 이것도 없애지 않을까. 그냥 없애고 기본으로 이모드가 설정되게 하려나
        buttonLive.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final Intent intent = new Intent(MainActivity.this, ObjectDetectionActivity.class);
                startActivity(intent);
            }
        });
        final Button selectPage = findViewById(R.id.selectButton);
        final Button setPage = findViewById(R.id.setButton);
        final Button hidePage = findViewById(R.id.hideButton);

        selectPage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final Intent intent = new Intent(MainActivity.this, SelectDetect.class);
                startActivity(intent);
            }
        });

        setPage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final Intent intent = new Intent(MainActivity.this, SettingActivity.class);
                startActivity(intent);
            }
        });

        hidePage.setOnClickListener(new View.OnClickListener() {
            boolean isVisible = true;
            @Override
            public void onClick(View v) {
                if (isVisible) {
                    hidePage.setAlpha(0.0f);
                    selectPage.setAlpha(0.0f);
                    setPage.setAlpha(0.0f);
                    buttonLive.setAlpha(0.0f);
                    isVisible = false;
                    hidemode = true;
                } else {
                    hidePage.setAlpha(1.0f);
                    selectPage.setAlpha(1.0f);
                    setPage.setAlpha(1.0f);
                    buttonLive.setAlpha(1.0f);
                    isVisible = true;
                    hidemode = false;
                }
            }
        });







        try {
            mModule = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), modelName));//
            BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open(metaName)));
            //에셋 파일에 추가한 모델 + 모델에 학습된 클래스명들을 순서대로 \n으로 구분해서 같이 투입. 내부적으론 숫자로 관리하니까 무조건 txt도 넣어줘야함
            String line;
            List<String> classes = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                classes.add(line);
            }
            PrePostProcessor.mClasses = new String[classes.size()];
            classes.toArray(PrePostProcessor.mClasses);
            //여기를 잘 조정하면 yaml같은것도 가능인데 귀찮고 굳이 그렇게 해야할 이유도 없어보이니 그냥 txt로 만들어주셈
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            switch (requestCode) {
                case 0:
                    if (resultCode == RESULT_OK && data != null) {
                        mBitmap = (Bitmap) data.getExtras().get("data");//카메라에 찍힌 걸 비트맵으로 변환
                        Matrix matrix = new Matrix();
                        matrix.postRotate(90.0f);//90도 회전 시켜주는 행렬. 아마 보통 카메라는 가로가 더 넓은 비율이니, 폰에서 쓸 수 있게 90도 돌려주는 걸로 예상함
                        mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                        mImageView.setImageBitmap(mBitmap);
                    }
                    break;
                case 1://이부분은 미리 찍혀있는 사진관련ㅇ 인듯.
                    if (resultCode == RESULT_OK && data != null) {
                        Uri selectedImage = data.getData();
                        String[] filePathColumn = {MediaStore.Images.Media.DATA};
                        if (selectedImage != null) {
                            Cursor cursor = getContentResolver().query(selectedImage,
                                    filePathColumn, null, null, null);
                            if (cursor != null) {
                                cursor.moveToFirst();
                                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                                String picturePath = cursor.getString(columnIndex);
                                mBitmap = BitmapFactory.decodeFile(picturePath);
                                Matrix matrix = new Matrix();
                                matrix.postRotate(90.0f);
                                mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                                mImageView.setImageBitmap(mBitmap);
                                cursor.close();
                            }
                        }
                    }
                    break;
            }
        }
    }

    @Override
    public void run() {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(mBitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true);//리사이즈
        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB);
        //저번에 말한 int16, float8 변환으로 용량 줄이기 하려면 여기랑 prepostprocessor 만져야하는데 굳이 안해도 좋을듯
        IValue[] outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple();
        //실제로 모델이 예측을 수행하는 문장
        final Tensor outputTensor = outputTuple[0].toTensor();
        final float[] outputs = outputTensor.getDataAsFloatArray();
        final ArrayList<Result> results =  PrePostProcessor.outputsToNMSPredictions(outputs, mImgScaleX, mImgScaleY, mIvScaleX, mIvScaleY, mStartX, mStartY);
        //모델이 예측한 것을 박스라벨링으로 바꿔주는 문장

        runOnUiThread(() -> {//그냥 ui
            mButtonDetect.setEnabled(true);
            mButtonDetect.setText(getString(R.string.detect));
            mProgressBar.setVisibility(ProgressBar.INVISIBLE);
            mResultView.setResults(results);
            mResultView.invalidate();
            mResultView.setVisibility(View.VISIBLE);
        });
    }
}