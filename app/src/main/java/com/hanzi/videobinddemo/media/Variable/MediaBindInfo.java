package com.hanzi.videobinddemo.media.Variable;

import com.hanzi.videobinddemo.filter.AFilter;

import java.util.List;

/**
 * Created by gengen on 2018/5/22.
 */

public class MediaBindInfo {
    private List<MediaBean> mediaBeans;

    private MediaBean bgmBean;
    private AFilter filter;

    private long mOutputDuration;
    private int mRate;
    private int mOutputWidth;
    private int mOutputHeight;

    private boolean isMute=false;

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

    public boolean isMute() {
        return isMute;
    }

    public void setMute(boolean mute) {
        isMute = mute;
    }
}
