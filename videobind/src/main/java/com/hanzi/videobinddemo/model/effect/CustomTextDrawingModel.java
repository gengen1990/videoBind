package com.hanzi.videobinddemo.model.effect;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@SuppressLint("ParcelCreator")
public class CustomTextDrawingModel extends DrawingModel {
    public static final String TAG = "CustomTextDrawingModel";
    public static final String CustomText = "CustomText";
    private CustomTextStyle customTextStyle;
    private ImageView imageView;
    private ViewGroup viewGroup;
    private OnEffectDrawingModelListener onEffectDrawingModelListener;
//    private Handler handler = new Handler();

    public CustomTextDrawingModel(String text, String effectId, float x, float y, final OnEffectDrawingModelListener onEffectDrawingModelListener) {
        super(effectId, x, y);
//        this.mScale = 2.0F;
        this.customTextStyle = new CustomTextStyle(text);
        this.onEffectDrawingModelListener = onEffectDrawingModelListener;
//        handler.post(new Runnable() {
//            @Override
//            public void run() {
//                onEffectDrawingModelListener.onLoadEffectBitmapSuccess();
//            }
//        });

    }

    private Bitmap getBitmap() {
        return mBitmaps[0];
    }

    public void setBitmap() {
        if (mBitmaps==null) {
            mBitmaps=new Bitmap[1];
            Bitmap bitmap = this.customTextStyle.getTextBitmap();
            this.mEffectWidth = bitmap.getWidth();
            this.mEffectHeight = bitmap.getHeight();
            mBitmaps[0]=bitmap;
        }
    }
    
    @Override
    public void drawBitmap(long timeUs) {

    }

    public CustomTextStyle getCustomTextStyle() {
        return this.customTextStyle;
    }

    public void onLoadEffectBitmapSuccess() {
        onEffectDrawingModelListener.onLoadEffectBitmapSuccess();
    }

    @Override
    public void setSegment(long currentTimeUs, boolean isTranslucent) {
        this.mCurrentTimeUs = currentTimeUs;
        if (mCurrentTimeUs >= mInitialTimeUs && mCurrentTimeUs <= mInitialTimeUs + mDurationUs) {
            long timeUs = currentTimeUs - mInitialTimeUs;
            drawBitmap(timeUs);
            setMatrix(isTranslucent);
            if (imageView != null) {
                imageView.setVisibility(View.VISIBLE);
            }
        } else {
            if (imageView != null) {
                imageView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void setMatrix(boolean isTranslucent) {
        imageView.setScaleX(mTransform.getScale());
        imageView.setScaleY(mTransform.getScale());
        imageView.setRotation(mTransform.getRotation());
        imageView.setTranslationX(mTransform.getLeftTopPoint().x);
        imageView.setTranslationY(mTransform.getLeftTopPoint().y);
        imageView.setPivotX(0);
        imageView.setPivotY(0);
        imageView.invalidate();
    }

    public Bitmap getRepresentationImage() {
        return getBitmap();
    }

    public boolean hasAdditionalFile() {
        return true;
    }

    protected void hide() {
        if (imageView == null) {
            return;
        }
        imageView.setAlpha(0.0F);
    }

    public void release() {
        removeFromParent();
    }

    public void removeFromParent() {
        if (this.viewGroup != null) {
            this.viewGroup.removeView(imageView);
        }
        this.viewGroup = null;
    }

    @Override
    protected View setupComponents(ViewGroup viewGroup) {
        this.viewGroup = viewGroup;
        Bitmap bitmap = getBitmap();
        imageView = new ImageView(viewGroup.getContext());
        imageView.setAlpha(1.0F);
        imageView.setImageBitmap(bitmap);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(mEffectWidth, mEffectHeight);
        viewGroup.addView(imageView, layoutParams);
        return imageView;
    }

    @Override
    public void playEffect() {
    }

    public void saveAdditionalFile(String path) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(new File(path));
            getBitmap().compress(Bitmap.CompressFormat.PNG, 80, fileOutputStream);
            fileOutputStream.close();
            return;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
