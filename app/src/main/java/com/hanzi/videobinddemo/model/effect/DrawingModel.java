package com.hanzi.videobinddemo.model.effect;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.hanzi.videobinddemo.model.Transform;

import java.util.ArrayList;
import java.util.List;

public abstract class DrawingModel {

    private static final String TAG = DrawingModel.class.getSimpleName();
    public static final int DEFAULT_COLOR = -1;
    public static final long DEFAULT_DURATION = -255L;
    protected static int screenHeight;
    protected static int screenWidth;

    protected int mColor = -1;

    protected EffectModel mEffectModel;
    protected long mEffectMinTime;
    protected long mEffectMaxTime;

    protected int mEffectWidth;
    protected int mEffectHeight;
//    protected float mRotation = 0;
//    protected float mScale = 1.0F;

    protected int mDrawingModelId=-1;

    protected String mEffectId;

    protected long mInitialTimeUs = 0;
    protected long mDurationUs = -1;
    protected long mCurrentTimeUs = 0;

    protected Bitmap[] mBitmaps;

    public float mFrameTimeUs = 0;

    protected float mCenterX;
    protected float mCenterY;

    protected Transform mTransform;

    protected List<Translation> mTranslationList;

    protected boolean isBitmapSuccess = false;

    protected DrawingModel(String effectId, float x, float y) {
        mEffectModel = EffectLibrary.findEffectModel(effectId);
        mEffectId = effectId;
        if (mEffectModel == null) {
            throw new RuntimeException("there is no type that matches effectId " + effectId + " + make sure your using effectId correctly");
        }
        mTransform = new Transform();
        mCenterX = x;
        mCenterY = y;
//        mScale = 1.0F;
        mInitialTimeUs = 0;
        mCurrentTimeUs = 0;
        mDurationUs = mEffectModel.durationUs;
        mTranslationList = new ArrayList();

        mEffectMinTime = mEffectModel.minimumDurationUs;
        mEffectMaxTime = mEffectModel.maximumDurationUs;

        if (mEffectModel.repeatCount<=0) {
            mEffectModel.repeatCount=1;
        }
        mFrameTimeUs = ((float) mEffectModel.overlapDurationUs) / (mEffectModel.repeatCount * mEffectModel.imageCount);
        Log.d(TAG, "DrawingModel: mFrameTimeUs:" + mFrameTimeUs);
        Log.d(TAG, "DrawingModel: totalCount:" + (mEffectModel.repeatCount * mEffectModel.imageCount));
    }

    protected DrawingModel(Parcel in) {
        mColor = in.readInt();
        mEffectMinTime = in.readLong();
        mEffectMaxTime = in.readLong();
        mEffectWidth = in.readInt();
        mEffectHeight = in.readInt();
        mDrawingModelId = in.readInt();
        mEffectId = in.readString();
        mInitialTimeUs = in.readLong();
        mDurationUs = in.readLong();
        mCurrentTimeUs = in.readLong();
        mBitmaps = in.createTypedArray(Bitmap.CREATOR);
        mFrameTimeUs = in.readFloat();
        mCenterX = in.readFloat();
        mCenterY = in.readFloat();
        isBitmapSuccess = in.readByte() != 0;
    }

    public static DrawingModel createOverlayEffectDrawingModel(Context context, int width, int height,
                                                               int centerX, int centerY, String effectId,
                                                               OnEffectDrawingModelListener onEffectDrawingModelListener) {
        screenWidth = width;
        screenHeight = height;
        return new OverlayEffectDrawingModel(context, effectId, centerX, centerY, onEffectDrawingModelListener);
    }

    public static CustomTextDrawingModel createCustomText(String text, int width, int height, int centerX, int centerY, OnEffectDrawingModelListener onEffectDrawingModelListener) {
        screenWidth = width;
        screenHeight = height;
        return new CustomTextDrawingModel(text, CustomTextDrawingModel.CustomText, centerX, centerY, onEffectDrawingModelListener);
    }

    public static int getScreenHeight() {
        return screenHeight;
    }

    public static int getScreenWidth() {
        return screenWidth;
    }

    public static void updateScreenSize(int width, int height) {
        screenWidth = width;
        screenHeight = height;
    }

    public String getEffectId() {
        return mEffectId;
    }

    public int getBitmapCount() {
        return getBitmaps().length;
    }


    public void checkLimitation() {
        if (mDurationUs < mEffectModel.minimumDurationUs) {
            mDurationUs = mEffectModel.minimumDurationUs;
        }
        if (mDurationUs > mEffectModel.maximumDurationUs) {
            mDurationUs = mEffectModel.maximumDurationUs;
        }

        if (mCurrentTimeUs < mInitialTimeUs) {
            mCurrentTimeUs = mInitialTimeUs;
        }
        if (mCurrentTimeUs > mInitialTimeUs + mDurationUs) {
            mCurrentTimeUs = mInitialTimeUs + mDurationUs;
        }
    }

    public float getFrameTimeUs() {
        return mFrameTimeUs;
    }

    public Bitmap[] getBitmaps() {
        return mBitmaps;
    }

    public boolean contains(float x, float y) {
        return mTransform.contains(x, y);
    }

    public abstract void setSegment(long currentTimeUs, boolean isTranslucent);

    public abstract void drawBitmap(long timeUs);

    public abstract void setMatrix(boolean isTranslucent);

    public int getColor() {
        return mColor;
    }

    public long getDurationUs() {
        return mDurationUs;
    }

    public EffectModel getEffectModel() {
        return mEffectModel;
    }

    public int getHeight() {
        return mEffectHeight;
    }

    public long getInitialTimeUs() {
        return mInitialTimeUs;
    }

    public float getMeasuredHeight() {
        return mTransform.getScale() * mEffectHeight;
    }

    public float getMeasuredWidth() {
        return mTransform.getScale() * mEffectWidth;
    }

    public abstract Bitmap getRepresentationImage();

    public float getRotation() {
        return mTransform.getRotation();
    }

    public float getScale() {
        return mTransform.getScale();
    }

    public List<Translation> getTranslationList() {
        return mTranslationList;
    }

    public int getWidth() {
        return mEffectWidth;
    }

    public float getX() {
        return mCenterX;
    }

    public float getY() {
        return mCenterY;
    }

    public float getLeftTopX() {
        return mTransform.getLeftTopPoint().x;
    }

    public float getLeftTopY() {
        return mTransform.getLeftTopPoint().y;
    }

    public float getLeftBottomX() {
        return mTransform.getLeftBottomPoint().x;
    }

    public float getLeftBottomY() {
        return mTransform.getLeftBottomPoint().y;
    }

    public float getRightTopX() {
        return mTransform.getRightTopPoint().x;
    }

    public float getRightTopY() {
        return mTransform.getRightTopPoint().y;
    }

    public float getRightBottomX(){
        return mTransform.getRightBottomPoint().x;
    }

    public float getRightBottomY(){
        return mTransform.getRightBottomPoint().y;
    }


    public boolean hasAdditionalFile() {
        return false;
    }

    protected abstract void hide();

    public abstract void release();

    public abstract void removeFromParent();

    public void setColor(@ColorInt int color) {
        mColor = color;
    }

    public void setKeyFramePosition(int pos, float x, float y) {
        if (mTranslationList == null) {
            return;
        }
        (mTranslationList.get(pos)).setX(x);
        (mTranslationList.get(pos)).setY(y);
    }

    public void setInitialTimeUs(long initialTimeUs) {
        this.mInitialTimeUs = initialTimeUs;
        checkLimitation();
    }

    public void setDurationUs(long durationUs) {
        this.mDurationUs = durationUs;
        checkLimitation();
    }

    public void setSegmentTime(long initialTimeUs, long currentTimeUs, long durationUs) {

        this.mInitialTimeUs = initialTimeUs;
        this.mCurrentTimeUs = currentTimeUs;
        this.mDurationUs = durationUs;
        checkLimitation();
        Log.d(TAG, "setSegmentTime: test initialTime:" + mInitialTimeUs);
    }

    public void setTransform(float x, float y, float scale, float rotation) {
        mCenterX = x;
        mCenterY = y;
        mTransform.setTransform(mEffectWidth, mEffectHeight, mCenterX, mCenterY, rotation, scale);
    }

    public void setTransform(Transform mTransform) {
        this.mTransform = mTransform.copy();
    }

    //
    @Nullable
    public View setup(ViewGroup viewGroup) {
        return setupComponents(viewGroup);
    }

    protected abstract View setupComponents(ViewGroup viewGroup);

    public abstract void playEffect();

    public long getEffectMinTime() {
        return mEffectMinTime;
    }


    public long getEffectMaxTime() {
        return mEffectMaxTime;
    }

    public long getCurrentTimeUs() {
        return mCurrentTimeUs;
    }

    public Transform getmTransform() {
        return mTransform;
    }

    public void setDrawingModelId(int id) {
        mDrawingModelId = id;
    }

    public int getDrawingModelId() {
        return mDrawingModelId;
    }

    public boolean isPlaying() {
        return (mCurrentTimeUs >= mInitialTimeUs && mCurrentTimeUs <= mInitialTimeUs + mDurationUs);
    }

    public interface OnEffectDrawingModelListener {
        void onLoadEffectModelError(EffectModel paramEffectModel);

        void onLoadEffectBitmapSuccess();
    }
}
