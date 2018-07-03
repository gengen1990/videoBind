package com.hanzi.videobinddemo.drawer;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.hanzi.videobinddemo.bean.VideoInfo;
import com.hanzi.videobinddemo.filter.AFilter;
import com.hanzi.videobinddemo.filter.EffectFilter;
import com.hanzi.videobinddemo.filter.GroupFilter;
import com.hanzi.videobinddemo.filter.NoFilter;
import com.hanzi.videobinddemo.filter.ProcessFilter;
import com.hanzi.videobinddemo.filter.RotationOESFilter;
import com.hanzi.videobinddemo.bean.MediaBean;
import com.hanzi.videobinddemo.utils.BitmapUtils;
import com.hanzi.videobinddemo.utils.EasyGlUtils;
import com.hanzi.videobinddemo.utils.MatrixUtils;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * desc：添加effect和beauty效果
 */

public class VideoDrawer implements GLSurfaceView.Renderer {

    private static final String TAG = VideoDrawer.class.getSimpleName();

    private static final int DEFAULT_VIDEO_RATE = 45;


    /**
     * 用于后台绘制的变换矩阵
     */
    private float[] OM;
    /**
     * 用于显示的变换矩阵
     */
    private float[] SM = new float[16];
    private SurfaceTexture surfaceTexture;
    /**
     * 可选择画面的滤镜
     */
    private RotationOESFilter mRotationOESFilter;
    /**
     * 显示的滤镜
     */
    private AFilter mShow;
    /**
     * beauty的filter
     */
    private AFilter mProcessFilter;
    /**
     * 绘制effect的滤镜
     */
    private final GroupFilter mGroupFilter;
    /**
     * 控件的宽高
     */
    private int viewWidth = 0;
    private int viewHeight = 0;
    /**
     * 视频内容的宽高
     */
    private int contentWidth;
    private int contentHeight;

    public int getShowWidth() {
        return showWidth;
    }

    public int getShowHeight() {
        return showHeight;
    }

    /**
     * 视频显示的宽高
     */
    private int showWidth;
    private int showHeight;

    /**
     * 创建离屏buffer
     */
    private int[] fFrame = new int[1];
    private int[] fTexture = new int[1];
    /**
     * 用于视频旋转的参数
     */
    private int rotation;
    /**
     * 是否开启美颜
     */
    private EffectFilter effectFilter;
    private Context context;
    private MediaBean info;

    private boolean isBinding = false;
    private List<Integer> list = new ArrayList<>();
    private int framePosition;   //记录第几帧
    private long lastRecordFrameTime = 0; //记录上一次帧的时间
    private int frameRate = DEFAULT_VIDEO_RATE;

    private VideoDrawerCallback callback;

    //    int count = 1;      //视频生成记录器
    private Resources res;

    public VideoDrawer(Context context, Resources res, boolean isBinding) {
        this.context = context;
        this.res = res;
        mRotationOESFilter = new RotationOESFilter(res);//旋转相机操作
        mShow = new NoFilter(res);
        mGroupFilter = new GroupFilter(res);
        mProcessFilter = new ProcessFilter(res);
        OM = MatrixUtils.getOriginalMatrix();
        MatrixUtils.flip(OM, false, isBinding);//矩阵上下翻转
//        mGroupFilter.setMatrix(OM);
//        mShow.setMatrix(OM);
        this.isBinding = isBinding;
        lastRecordFrameTime = System.currentTimeMillis();
        effectFilter = new EffectFilter(res);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        int texture[] = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        surfaceTexture = new SurfaceTexture(texture[0]);
        mRotationOESFilter.create();
        mRotationOESFilter.setTextureId(texture[0]);

        mGroupFilter.create();
        mProcessFilter.create();
        mShow.create();
    }

    public void onRotationOESFilterChanged(VideoInfo info) {
        setRotation(info.rotation);
        if (info.rotation == 0 || info.rotation == 180) {
            MatrixUtils.getShowMatrix(SM, info.width, info.height, showWidth, showHeight);
        } else {
            MatrixUtils.getShowMatrix(SM, info.height, info.width, showWidth, showHeight);
        }

        mRotationOESFilter.setMatrix(SM);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.i(TAG, "onSurfaceChanged: width:" + width);
        Log.i(TAG, "onSurfaceChanged: height:" + height);
        Log.i(TAG, "onSurfaceChanged: contentWidth:" + contentWidth);
        Log.i(TAG, "onSurfaceChanged: contentHeight:" + contentHeight);
        viewWidth = width;
        viewHeight = height;
        if (contentWidth == 0 || contentHeight == 0) {
            showWidth = width;
            showHeight = height;
        } else {
            if ((float) viewWidth / viewHeight > (float) contentWidth / contentHeight) {
                showWidth = contentWidth * viewHeight / contentHeight;
                showHeight = viewHeight;
            } else {
                showWidth = viewWidth;
                showHeight = viewWidth * contentHeight / contentWidth;
            }
        }
        Log.i(TAG, "onSurfaceChanged: showWidth:" + showWidth);
        Log.i(TAG, "onSurfaceChanged: showHeight:" + showHeight);
        Log.i(TAG, "onSurfaceChanged: x:" + (viewWidth - showWidth) / 2);
        Log.i(TAG, "onSurfaceChanged: y:" + (viewHeight - showHeight) / 2);

        GLES20.glDeleteFramebuffers(1, fFrame, 0);
        GLES20.glDeleteTextures(1, fTexture, 0);
        GLES20.glGenFramebuffers(1, fFrame, 0);
        EasyGlUtils.genTexturesWithParameter(1, fTexture, 0, GLES20.GL_RGBA, showWidth, showHeight);
        mGroupFilter.setSize(showWidth, showHeight);
        mProcessFilter.setSize(showWidth, showHeight);

    }

    @Override
    public void onDrawFrame(GL10 gl) {
        Log.i(TAG, "onDrawFrame: showWidth:" + showWidth);
        Log.i(TAG, "onDrawFrame: showHeight:" + showHeight);
        ++framePosition;
        bindImage();
        surfaceTexture.updateTexImage();
        EasyGlUtils.bindFrameTexture(fFrame[0], fTexture[0]);
        GLES20.glViewport(0, 0, showWidth, showHeight);
        mRotationOESFilter.draw();
        EasyGlUtils.unBindFrameBuffer();
        mGroupFilter.setTextureId(fTexture[0]);
        mGroupFilter.draw();
        mProcessFilter.setTextureId(mGroupFilter.getOutputTexture());
        int rate =info.getRate()==0?15:info.getRate();
        ((ProcessFilter) mProcessFilter).setVideoRate(rate);
        ((ProcessFilter) mProcessFilter).setFramePosition(framePosition);

        mProcessFilter.draw();

        GLES20.glViewport((viewWidth - showWidth) / 2, (viewHeight - showHeight) / 2, showWidth, showHeight);
        mShow.setTextureId(mProcessFilter.getOutputTexture());
        mShow.draw();
        if (!isBinding) {
            caculateFrameRate();
        }
    }

    private void caculateFrameRate() {
        if (framePosition % DEFAULT_VIDEO_RATE == 0) {
            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastRecordFrameTime) / 1000 != 0) {
                frameRate = (int) (DEFAULT_VIDEO_RATE / ((currentTime - lastRecordFrameTime) / 1000));
            }

            lastRecordFrameTime = currentTime;
//            Log.i(TAG, "caculateFrameRate: (currentTime-lastRecordFrameTime)/1000 :");
//            Log.i(TAG, "caculateFrameRate: lastRecordFrameTime:"+lastRecordFrameTime);
//            Log.i(TAG, "caculateFrameRate: "+frameRate);
//            Log.i(TAG, "caculateFrameRate: framePosition:"+framePosition
//            );
            if (callback != null)
                callback.onVideoRate(frameRate);
        }
    }

    private void bindImage() {
        if (info != null) {
                //判断整个视频的第几帧需要添加effect
            List<MediaBean.EffectInfo> effectInfos= info.getEffectInfos();
                for (int i = 0; i < effectInfos.size(); i++) {
                    MediaBean.EffectInfo effectInfo = effectInfos.get(i);
                    if (effectInfo.mOneEffectRate == 0) {
                        effectInfo.mOneEffectRate = (info.getRate() == 0 ? 15 : info.getRate()) * effectInfo.intervalMs / 1000;
                    }

                    int framStart= (int) ((effectInfo.videoFrameTimeSList.get(0)+info.getPreTimeS())*info.getRate());
                    int frameEnd = (int) ((effectInfo.videoFrameTimeSList.get(effectInfo.videoFrameTimeSList.size() - 1)+info.getPreTimeS())* info.getRate());

//                    Log.i(TAG, "bindImage: url:"+info.getUrl());
                    Log.i(TAG, "bindImage: effectInfo.intervalMs:"+effectInfo.intervalMs);
                    Log.i(TAG, "bindImage: size:"+effectInfos.size());
                    Log.i(TAG, "bindImage: rate:"+info.getRate());
                    Log.i(TAG, "bindImage: frameStart:"+framStart);
                    Log.i(TAG, "bindImage: frameEnd:"+frameEnd);
                    Log.i(TAG, "bindImage: framePosition:"+framePosition);


                    if (effectInfo.videoFrameTimeSList.size() > 0
                            && framePosition >= framStart
                            && framePosition <=frameEnd) {
                        Log.i(TAG, "bindImage: effectInfo.mOneEffectCount:"+effectInfo.mOneEffectCount);
                        Log.i(TAG, "bindImage: effectInfo.mOneEffectRate:"+effectInfo.mOneEffectRate);

                        if (effectInfo.mOneEffectCount >= effectInfo.mOneEffectRate) {

                            if (effectInfo.effectPos == effectInfo.bitmaps.size() - 1) {
                                effectInfo.effectPos = 0;
                            } else {
                                effectInfo.effectPos = effectInfo.effectPos + 1;
                            }
                            effectInfo.mOneEffectCount = 1;
                        } else {
                            ++effectInfo.mOneEffectCount;
                        }
                    } else {
                        effectInfo.effectPos = -1;
                    }

                    Log.i(TAG, "bindImage: effectPos:"+effectInfo.effectPos);
            }

            Bitmap bitmap = BitmapUtils.bitmapMix(context, effectInfos, showWidth, showHeight);  //无数据，返回null
            effectFilter.setBitmap(bitmap);
            effectFilter.setPosition(0, 0, showWidth, showHeight);
            effectFilter.setMatrix(OM);
            mGroupFilter.clearAll();
            mGroupFilter.addFilter(effectFilter);

        } else {
            mShow.setMatrix(OM);
        }
    }

    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
        if (mRotationOESFilter != null) {
            mRotationOESFilter.setRotation(this.rotation);
        }
    }

    /**
     * 切换开启beauty效果
     */
    public void setMediaBean(MediaBean info) {
        this.info = info;
    }


    public void checkGlError(String s) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            throw new RuntimeException(s + ": glError " + error);
        }
    }

    public void setFilter(AFilter filter) {
        if (filter == null) {
            return;
        }
        mProcessFilter.deleteProgram();
        mProcessFilter = new ProcessFilter(filter, res);
        mProcessFilter.create();
        mProcessFilter.setSize(viewWidth, viewHeight);
    }

    public void destory() {
        mProcessFilter.deleteProgram();
        mGroupFilter.deleteProgram();
        mRotationOESFilter.deleteProgram();
        effectFilter.deleteProgram();
    }

    public void removeVideoInfo() {
        info = null;
    }

    public void onVideoChanged(VideoInfo info) {
        Log.i(TAG, "onVideoChanged: width:" + info.width);
        Log.i(TAG, "onVideoChanged: height:" + info.height);
        contentWidth = info.width;
        contentHeight = info.height;
        onSurfaceChanged(null, viewWidth, viewHeight);
    }

    public void onVideoChanged(int contentWidth, int contentHeight, int viewWidth, int viewHeight) {
        Log.i(TAG, "onVideoChanged: contentWidth:" + contentWidth);
        Log.i(TAG, "onVideoChanged: contentHeight:" + contentHeight);
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;
        onSurfaceChanged(null, viewWidth, viewHeight);
    }

    public void setVideoDrawerCallback(VideoDrawerCallback callback) {
        this.callback = callback;
    }

    public void setFramePosition(int time) {
        framePosition = (time / 1000) * frameRate;
    }

    public interface VideoDrawerCallback {
        void onVideoRate(int rate);
    }
}
