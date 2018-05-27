package com.hanzi.videobinddemo.media.Variable;

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
    private long duration=-1;

    private int videoRate =15;
    private int width =0;
    private int height =0;

    private int audioRate = 44100;
    private int channelCount = 2;

    public MediaBean(String url, int type) {
        this.url = url;
        this.type = type;
    }

    public void setTime(long startTimeUs, long endTimeUs) {
      setTime(-1,startTimeUs,endTimeUs);
    }

    public void setTime(long duration, long startTime, long endTime) {
        this.duration= duration;
        this.startTimeUs = startTime;
        this.endTimeUs = endTime;
    }

    public long getStartTimeUs() {
        return startTimeUs;
    }

    public long getEndTimeUs() {
        return endTimeUs;
    }

    public long getDuration() {
        return duration;
    }

    public int getRate() {
        return videoRate;
    }

    public void setRate(int rate) {
        this.videoRate = rate;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
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
}
