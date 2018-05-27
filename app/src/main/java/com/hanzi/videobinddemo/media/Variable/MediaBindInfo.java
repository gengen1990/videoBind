package com.hanzi.videobinddemo.media.Variable;

import android.graphics.Bitmap;

import com.hanzi.videobinddemo.filter.AFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gengen on 2018/5/22.
 */

public class MediaBindInfo {
    private List<MediaBean> mediaBeans;

    private MediaBean bgmBean;
    private AFilter filter;
    private List<EffectInfo> effectInfos;

    private long mOutputDuration;
    private int mRate;
    private int mOutputWidth;
    private int mOutputHeight;

    public int getOutputWidth() {
        return mOutputWidth;
    }

    public void setmOutputWidth(int mOutputWidth) {
        this.mOutputWidth = mOutputWidth;
    }

    public int getOutputHeight() {
        return mOutputHeight;
    }

    public void setVideoHeight(int mVideoHeight) {
        this.mOutputHeight = mVideoHeight;
    }

    public long getDuration() {
        return mOutputDuration;
    }

    public void setDuration(long mDuration) {
        this.mOutputDuration = mDuration;
    }

    public int getRate() {
        return mRate;
    }

    public void setRate(int mRate) {
        this.mRate = mRate;
    }

    public List<MediaBean> getMediaBeans() {
        return mediaBeans;
    }

    public void setMediaBeans(List<MediaBean> mediaBeans) {
        this.mediaBeans = mediaBeans;
    }

    public MediaBean getBgm() {
        return bgmBean;
    }

    public void setBgm(MediaBean bgmBean) {
        this.bgmBean = bgmBean;
    }

    public AFilter getFilter() {
        return filter;
    }

    public void setFilter(AFilter filter) {
        this.filter = filter;
    }

    public List<EffectInfo> getEffectInfos() {
        return effectInfos;
    }

    public void setEffectInfos(List<EffectInfo> effectInfos) {
        this.effectInfos = effectInfos;
    }

    public static class EffectInfo {

        @Override
        public String toString() {
            return "EffectInfo{" +
                    ", bitmaps=" + bitmaps +
//                    ", path_id=" + path_id +
                    ", effectStartTime=" + effectStartTime +
                    ", effectEndTime=" + effectEndTime +
                    ", interval=" + interval +
                    ", angle=" + angle +
                    ", x=" + x +
                    ", y=" + y +
                    ", w=" + w +
                    ", h=" + h +
                    ", effectPos=" + effectPos +
                    ", videoLastTime=" + videoLastTime +
                    ", videoFrameList=" + videoFrameList +
                    '}';
        }

        public int id;

        public List<Bitmap> bitmaps = new ArrayList<>();

        public long effectStartTime;  //effect 的开始时间 毫秒

        public long effectEndTime;    //Effect 的结束时间 毫秒

        public int interval;    //动画变化时间 毫秒

        public float angle;       //旋转的角度 顺时针

        public float x;           //位置 x坐标

        public float y;           //位置 y坐标

        public float scale;     //图片放大度

        public int w;           //图片原始宽度

        public int h;           //图片原始高度

        //       ===================================
        public int effectPos = -1;        //在某个时刻 ，effect 在第几张图片 -1代表无效果

        public int mf = 0;//mf表示要多久才切换一次effect的bitmap

        public int mfCount;//表示停留在第effectPos张图片已经有多少次

        public int videoLastTime = 0;   //记录上一张时间

        public List<Integer> videoFrameList = new ArrayList<>();  //视频 第几帧需要加Effect特效
    }
}
