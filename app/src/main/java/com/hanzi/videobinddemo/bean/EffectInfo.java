package com.hanzi.videobinddemo.bean;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.VideoView;

import com.hanzi.videobinddemo.filter.AFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Create by xjs
 * _______date : 18/3/8
 * _______description:
 */
public class EffectInfo implements Parcelable {

    public List<ListBean> data;   //动画变化时间

    public AFilter filter;

    public VideoView videoView;

    public long duration;    //视频时长 毫秒

    public int rate;       //视频帧率，不传默认15

    public EffectInfo(){

    }

    public EffectInfo(Parcel in) {
        duration = in.readLong();
        rate = in.readInt();
    }

    public static final Creator<EffectInfo> CREATOR = new Creator<EffectInfo>() {
        @Override
        public EffectInfo createFromParcel(Parcel in) {
            return new EffectInfo(in);
        }

        @Override
        public EffectInfo[] newArray(int size) {
            return new EffectInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(duration);
        dest.writeInt(rate);
    }


    public static class ListBean {

        @Override
        public String toString() {
            return "EffectInfo{" +
                    "paths=" + paths +
                    ", bitmaps=" + bitmaps +
//                    ", path_id=" + path_id +
                    ", effectStartTimeMs=" + effectStartTime +
                    ", effectEndTimeMs=" + effectEndTime +
                    ", intervalMs=" + interval +
                    ", angle=" + angle +
                    ", x=" + x +
                    ", y=" + y +
                    ", w=" + w +
                    ", h=" + h +
                    ", effectPos=" + effectPos +
                    ", videoLastTime=" + videoLastTime +
                    ", videoFrameTimeList=" + videoFrameList +
                    '}';
        }

        public int id;

        public List<String> paths = new ArrayList<>();

        public List<Bitmap> bitmaps = new ArrayList<>();

//        public List<Integer> path_id = new ArrayList<>();

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
