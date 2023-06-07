// Copyright (c) 2020 Facebook, Inc. and its affiliates.
// All rights reserved.
//
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

package org.pytorch.demo.objectdetection;

import static org.pytorch.demo.objectdetection.ObjectDetectionActivity.temp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;


public class ResultView extends View {//결과 출력 관련

    private final static int TEXT_X = 40;
    private final static int TEXT_Y = 35;
    private final static int TEXT_WIDTH = 260;
    private final static int TEXT_HEIGHT = 50;

    private Paint mPaintRectangle;
    private Paint mPaintText;
    private ArrayList<Result> mResults;

    public ResultView(Context context) {
        super(context);
    }

    public ResultView(Context context, AttributeSet attrs){
        super(context, attrs);
        mPaintRectangle = new Paint();
        mPaintText = new Paint();
    }

    @Override
    protected void onDraw(Canvas canvas) {//그리기
        super.onDraw(canvas);

        float cw = canvas.getWidth();
        float ch = canvas.getHeight();

        mPaintRectangle.setStrokeWidth(8);//아마 선 굵기?
        mPaintRectangle.setColor(Color.RED);
        mPaintRectangle.setStyle(Paint.Style.STROKE);

        if(temp != null){
            canvas.drawBitmap(temp,null,new RectF(0,0,cw,ch),null);
        }

        canvas.drawRect(new RectF(cw/3,ch/3,2*cw/3,2*ch/3), mPaintRectangle);

        if (mResults == null) return;

        for (Result result : mResults) {
            mPaintRectangle.setColor(Color.YELLOW);//박스 색 조정
            mPaintRectangle.setStrokeWidth(5);//아마 선 굵기?
            mPaintRectangle.setStyle(Paint.Style.STROKE);
            canvas.drawRect(result.rect, mPaintRectangle);


            //여기를 조정하면 라벨링이 그려지는 모양을 수정할 수 있습니다.
            Path mPath = new Path();
            RectF mRectF = new RectF(result.rect.left, result.rect.top, result.rect.left + TEXT_WIDTH,  result.rect.top + TEXT_HEIGHT);
            mPath.addRect(mRectF, Path.Direction.CW);
            mPaintText.setColor(Color.MAGENTA);
            canvas.drawPath(mPath, mPaintText);

            mPaintText.setColor(Color.WHITE);//글자 색
            mPaintText.setStrokeWidth(0);
            mPaintText.setStyle(Paint.Style.FILL);
            mPaintText.setTextSize(32);//사이즈
            canvas.drawText(String.format("%s %.2f", PrePostProcessor.mClasses[result.classIndex], result.score),
                    result.rect.left + TEXT_X, result.rect.top + TEXT_Y, mPaintText);
            //포맷 수정 가능 ==> 여기서 클래스 명이랑 스코어 가져가도 ok
        }
    }

    public void setResults(ArrayList<Result> results) {
        mResults = results;
    }
}