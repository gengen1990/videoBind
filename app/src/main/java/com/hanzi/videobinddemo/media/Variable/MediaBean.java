package com.hanzi.videobinddemo.media.Variable;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gengen on 2018/5/22.
 * 描述需要使用的视频信息
 */

public class MediaBean implements Cloneable {
//    public final static int AudioType = 0;
//    public final static int VideoType = 1;

    private String url;
    private int type;

    private long startTimeUs =0;
    private long endTimeUs =-1;
    private long durationUs =-1;

    private int videoRate =15;
    private int videoWidth =0;
    private int videoHeight =0;

    private int audioRate = 44100;
    private int channelCount = 2;

    private List<EffectInfo> effectInfos=new ArrayList<>();

    public MediaBean(String url, int type) {
        this.url = url;
        this.type = type;
    }

    public void setTime(long startTimeUs, long endTimeUs) {
      setTime(-1,startTimeUs,endTimeUs);
    }

    public void setTime(long duration, long startTime, long endTime) {
        this.durationUs = duration;
        this.startTimeUs = startTime;
        this.endTimeUs = endTime;
    }

    public long getStartTimeUs() {
        return startTimeUs;
    }

    public long getEndTimeUs() {
        return endTimeUs;
    }

    public void  setDurationUs(long durationUs) {
        this.durationUs = durationUs;
    }

    public long getDurationUs() {
        return durationUs;
    }

    public int getRate() {
        return videoRate;
    }

    public void setRate(int rate) {
        this.videoRate = rate;
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public void setVideoWidth(int videoWidth) {
        this.videoWidth = videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    public void setVideoHeight(int videoHeight) {
        this.videoHeight = videoHeight;
    }

    public int getAudioRate() {
        return audioRate;
    }

    public void setAudioRate(int audioRate) {
        this.audioRate = audioRate;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public void setChannelCount(int channelCount) {
        this.channelCount = channelCount;
    }

    public List<EffectInfo> getEffectInfos() {
        return effectInfos;
    }

    public void setEffectInfos(List<EffectInfo> effectInfos) {
        this.effectInfos = effectInfos;
    }

    @Override
    public MediaBean clone() throws CloneNotSupportedException {
        return (MediaBean)super.clone();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public static class EffectInfo {

        @Override
        public String toString() {
            return "EffectInfo{" +
                    ", bitmaps=" + bitmaps +
//                    ", path_id=" + path_id +
                    ", effectStartTimeMs=" + effectStartTimeMs +
                    ", effectEndTimeMs=" + effectEndTimeMs +
                    ", intervalMs=" + intervalMs +
                    ", angle=" + angle +
                    ", x=" + x +
                    ", y=" + y +
                    ", w=" + w +
                    ", h=" + h +
                    ", effectPos=" + effectPos +
                    ", videoLastTime=" + videoLastTime +
                    ", videoFrameTimeList=" + videoFrameTimeList +
                    '}';
        }

        public int id;

        public List<Bitmap> bitmaps = new ArrayList<>();

        public long effectStartTimeMs;  //effect 的开始时间 毫秒

        public long effectEndTimeMs;    //Effect 的结束时间 毫秒

        public int intervalMs;    //动画变化时间 毫秒

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

        public List<Integer> videoFrameTimeList = new ArrayList<>();  //视频 时间需要加Effect特效
    }
}
