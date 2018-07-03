package com.hanzi.videobinddemo.bean.model.effect;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.Request;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.IOException;
import java.io.InputStream;

public class OverlayEffectDrawingModel
        extends DrawingModel implements Parcelable {

    private static final String TAG = OverlayEffectDrawingModel.class.getSimpleName();
    private static final Interpolator INTERPOLATOR = new DecelerateInterpolator();
    private int imageCount;
    private OnEffectDrawingModelListener onEffectDrawingModelListener;
    private OverlayView view;
    private Boolean[] beBitmapGet;
    private ViewGroup mViewGroup;

    private Handler mainHandler;


    public OverlayEffectDrawingModel(Context context, String effectId, float x, float y,
                                     OnEffectDrawingModelListener onEffectDrawingModelListener) {
        super(effectId, x, y);
        this.onEffectDrawingModelListener = onEffectDrawingModelListener;
        loadImage(context);
//        Looper.prepare();
//        mainHandler = new Handler() {
//            @Override
//            public void handleMessage(android.os.Message i) {
//
//                drawBitmap((long) (mCurrentTimeUs - mInitialTimeUs + i.what * mFrameTimeUs));
//
//            }
//        };
//        ((Activity)context).runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                drawBitmap((long) (mCurrentTimeUs - mInitialTimeUs + i.what * mFrameTimeUs));
//            }
//        });
    }


    protected OverlayEffectDrawingModel(Parcel in) {
        super(in);
        imageCount = in.readInt();
    }

    public static final Creator<OverlayEffectDrawingModel> CREATOR = new Creator<OverlayEffectDrawingModel>() {
        @Override
        public OverlayEffectDrawingModel createFromParcel(Parcel in) {
            return new OverlayEffectDrawingModel(in);
        }

        @Override
        public OverlayEffectDrawingModel[] newArray(int size) {
            return new OverlayEffectDrawingModel[size];
        }
    };

    private void loadImage(Context context) {
        imageCount = mEffectModel.imageCount;
        Log.d(TAG, "loadImage: count:" + imageCount);
        mBitmaps = new Bitmap[imageCount];
        beBitmapGet = new Boolean[imageCount];
        for (int i = 0; i < imageCount; i++) {
            beBitmapGet[i] = false;
        }
        InputStream inputStream;
        try {
            inputStream = context.getAssets().open(mEffectModel.getEffectPath());
            for (int i = 0; i < imageCount; i++) {

                byte[] data;

                data = new byte[mEffectModel.getImageChunkSize(i)];
                int j = inputStream.read(data);
                Log.d(TAG, "loadImage: size:" + data.length);
                if (i == 0) {
                    BitmapFactory.Options localOptions = new BitmapFactory.Options();
                    localOptions.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, localOptions);
                    mEffectWidth = localOptions.outWidth;
                    mEffectHeight = localOptions.outHeight;
                }

                if ((mEffectWidth <= 0) || (mEffectHeight <= 0)) {
                    throw new IOException("width or height <= 0");
                }
                if (mEffectModel.isFullscreen()) {
                    mCenterX = 0.5F;
                    mCenterY = 0.5F;
//                    mScale = (1.0F * getScreenHeight() / mEffectHeight);
                }
                RequestOptions requestOptions = new RequestOptions().format(DecodeFormat.PREFER_RGB_565).diskCacheStrategy(DiskCacheStrategy.ALL).disallowHardwareConfig();
                Glide.with(context).asBitmap().load(data).apply(requestOptions).into(new OverlayTarget(mEffectWidth, mEffectHeight, i));
            }

            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
            onEffectDrawingModelListener.onLoadEffectModelError(mEffectModel);
            return;
        }
    }

    public void setMatrix(boolean isTranslucent) {
        if (view==null) {
            return;
        }
        view.setScaleX(mTransform.getScale());
        view.setScaleY(mTransform.getScale());
        view.setRotation(mTransform.getRotation());
        view.setTranslationX(mTransform.getLeftTopPoint().x);
        view.setTranslationY(mTransform.getLeftTopPoint().y);
        view.setPivotX(0);
        view.setPivotY(0);
        view.invalidate();
    }

    @Override
    public void setSegment(long currentTimeUs, boolean isTranslucent) {
        this.mCurrentTimeUs = currentTimeUs;
        if (mCurrentTimeUs >= mInitialTimeUs && mCurrentTimeUs <= mInitialTimeUs + mDurationUs) {
            long timeUs = currentTimeUs - mInitialTimeUs;
            drawBitmap(timeUs);
            setMatrix(isTranslucent);
            if (view != null) {
                view.setVisibility(View.VISIBLE);
            }
        } else {
            if (view != null) {
                view.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void drawBitmap(long timeUs) {
        if (view == null) {
            return;
        }
        if (!isBitmapSuccess) {
            return;
        }

        view.alpha1 = 1.0F;
        view.alpha2 = 0.0F;

        if (timeUs < mEffectModel.overlapStartTimeUs) {
            view.bitmap1 = mBitmaps[0];
        }

        if (timeUs >= mEffectModel.overlapStartTimeUs + mEffectModel.overlapDurationUs) {
            view.bitmap1 = mBitmaps[(imageCount - 1)];
        } else {
            int repeatCount = mEffectModel.repeatCount;
            if (repeatCount<=0) {
                repeatCount=1;
            }
            long OneTimeUs = (mEffectModel.overlapDurationUs - mEffectModel.overlapStartTimeUs) / repeatCount;
            long time0 = (timeUs - mEffectModel.overlapStartTimeUs) % OneTimeUs;
            long averImgTimeUs = OneTimeUs / imageCount;
            int i = (int) (time0 / averImgTimeUs);
            if (i < imageCount - 1) {
                float f1 = (float) (time0 - i * averImgTimeUs) / (float) averImgTimeUs;
                float f2 = 1.0F - f1;
                if (i % 2 == 0) {
                    view.bitmap1 = mBitmaps[(i + 1)];
                    view.bitmap2 = mBitmaps[i];
                    view.alpha1 = INTERPOLATOR.getInterpolation(f1);
                    view.alpha2 = INTERPOLATOR.getInterpolation(f2);
                } else {
                    view.bitmap1 = mBitmaps[i];
                    view.bitmap2 = mBitmaps[(i + 1)];
                    view.alpha1 = INTERPOLATOR.getInterpolation(f2);
                    view.alpha2 = INTERPOLATOR.getInterpolation(f1);
                }
            } else {
                view.bitmap1 = mBitmaps[(imageCount - 1)];
            }
        }

    }


    @Override
    public Bitmap getRepresentationImage() {
        if ((mBitmaps == null) || (mBitmaps.length == 0)) {
            return null;
        }
        return mBitmaps[mEffectModel.representationIndex];
    }

    @Override
    protected void hide() {
        if (view == null) {
            return;
        }
        view.setAlpha(0.0F);
    }

    public void release() {
        removeFromParent();
        if (mBitmaps == null) {
            return;
        }
        Bitmap[] bitmaps = mBitmaps;
        int j = bitmaps.length;
        int i = 0;
        while (i < j) {
            Bitmap localBitmap = bitmaps[i];
            if ((localBitmap != null) && (!localBitmap.isRecycled())) {
                localBitmap.recycle();
            }
            i += 1;
        }
        mBitmaps = null;
    }

    public void removeFromParent() {
        if (mViewGroup != null) {
            mViewGroup.removeView(view);
            mViewGroup = null;
        }
        view = null;
    }


//    @Override
//    public void setColor(@ColorInt int paramInt) {
//        if ((mColor == paramInt) || (view == null)) {
//            return;
//        }
//        super.setColor(paramInt);
//        view.setColor(paramInt);
//    }

    public View setupComponents(ViewGroup viewGroup) {
        if ((mBitmaps == null) || (viewGroup == null)) {
            return null;
        }
        mViewGroup = viewGroup;
        view = new OverlayView(mViewGroup.getContext(), 1.0F, 1.0F);
        view.bitmap1 = mBitmaps[0];
        view.bitmap2 = mBitmaps[1];
        Log.d(TAG, "setupComponents: ");
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(mEffectWidth, mEffectHeight);
        mViewGroup.addView(view, layoutParams);
        if (mEffectModel.isColorBlendingEnabled()) {
            view.paint.setColor(mColor);
        }
        return view;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(imageCount);
    }


    class OverlayTarget
            extends SimpleTarget<Bitmap> {
        private int pos;

        public OverlayTarget(int width, int height, int position) {
            super(width, height);
            pos = position;
        }

        @Override
        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
            getBitmaps()[pos] = resource;
            beBitmapGet[pos] = true;
            for (int i = 0; i < imageCount; i++) {
                Log.d(TAG, "onResourceReady: " + i);
                if (!beBitmapGet[i]) {
                    return;
                }
            }
            if (!isBitmapSuccess) {
                Log.d(TAG, "onResourceReady: success");
                onEffectDrawingModelListener.onLoadEffectBitmapSuccess();
                isBitmapSuccess = true;
            }
        }

        @Override
        public void setRequest(@Nullable Request request) {
            super.setRequest(request);
        }

        @Nullable
        @Override
        public Request getRequest() {
            return super.getRequest();
        }

        @Override
        public void onLoadCleared(@Nullable Drawable placeholder) {
            super.onLoadCleared(placeholder);
            Log.d(TAG, "onLoadCleared: ");
        }

        @Override
        public void onLoadStarted(@Nullable Drawable placeholder) {
            super.onLoadStarted(placeholder);
            Log.d(TAG, "onLoadStarted: ");
        }

        @Override
        public void onLoadFailed(@Nullable Drawable errorDrawable) {
            super.onLoadFailed(errorDrawable);
            Log.d(TAG, "onLoadFailed: ");
        }

        @Override
        public void onStart() {
            super.onStart();
        }

        @Override
        public void onStop() {
            super.onStop();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
        }
    }

    class OverlayView
            extends View {
        float alpha1;
        float alpha2;
        Bitmap bitmap1;
        Bitmap bitmap2;
        Paint paint = new Paint(2);

        public OverlayView(Context context) {
            super(context);
            if (mEffectModel.isColorBlendingEnabled()) {
                paint.setColorFilter(new PorterDuffColorFilter(mColor, PorterDuff.Mode.MULTIPLY));
            }

        }

        public OverlayView(Context context, float alpha1, float alpha2) {
            super(context);
            this.alpha1 = alpha1;
            this.alpha2 = alpha2;
        }

        private void setColor(@ColorInt int color) {
            if (mEffectModel.isColorBlendingEnabled()) {
                paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            }
        }


        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            Log.d(TAG, "onDraw: ");
            if ((bitmap1 != null) && (!bitmap1.isRecycled())) {
                paint.setAlpha((int) (alpha1 * 255.0F));
                canvas.drawBitmap(bitmap1, 0.0F, 0.0F, paint);
            }
            if ((bitmap2 != null) && (!bitmap2.isRecycled())) {
                paint.setAlpha((int) (alpha2 * 255.0F));
                canvas.drawBitmap(bitmap2, 0.0F, 0.0F, paint);
            }
        }
    }

    @Override
    public void playEffect() {
        int frameCount = (int) ((mDurationUs - (mCurrentTimeUs - mInitialTimeUs)) / mFrameTimeUs) + 1;
        Log.d(TAG, "playEffect: frameCount:" + frameCount);
        Log.d(TAG, "playEffect: mDurationUs:" + mDurationUs);
        Log.d(TAG, "playEffect: mCurrentTimeUs:" + mCurrentTimeUs);
        Log.d(TAG, "playEffect: mInitialTimeUs:" + mInitialTimeUs);

        for (int i = 0; i < frameCount; i++) {
            Message msg = new Message();
            msg.what = i;
//            mainHandler.sendMessageDelayed(msg, (long) (mFrameTimeUs / 1000) * i);
        }
    }
}
