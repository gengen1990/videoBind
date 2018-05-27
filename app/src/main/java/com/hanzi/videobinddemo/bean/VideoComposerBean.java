package com.hanzi.videobinddemo.bean;

/**
 * Created by gengen on 2018/5/11.
 */

public class VideoComposerBean {
    private long startTime=0;
    private long duration =-1;
    private String path;

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
