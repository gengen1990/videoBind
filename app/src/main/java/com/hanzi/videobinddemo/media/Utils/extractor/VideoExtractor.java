package com.hanzi.videobinddemo.media.Utils.extractor;


import android.util.Log;

import com.hanzi.videobinddemo.utils.MuxerUtils;

/**
 * Created by gengen on 2018/5/22.
 */

public class VideoExtractor extends MediaExtractor {
    private final String TAG = "VideoExtractor";
    private int frameRate = 0;
    private int width = 0;
    private int height = 0;
    private long totalDurationUs = 0;
    private long cutDurationUs = 0;

    public VideoExtractor(String url, long startTimeUs, long endTimeUs) {
        super(url, VIDEO_TYPE, startTimeUs, endTimeUs);
        setInfo();
        selectTrack(trackIndex);
    }

    @Override
    public void setInfo() {
        if (isExistedTrackType(VIDEO_TYPE)) {
            frameRate = Integer.parseInt(MuxerUtils.getValue(format.toString(), "frame-rate"));
            width = Integer.parseInt(MuxerUtils.getValue(format.toString(), "width"));
            height = Integer.parseInt(MuxerUtils.getValue(format.toString(), "height"));
            totalDurationUs = Long.parseLong(MuxerUtils.getValue(format.toString(), "durationUs"));

            Log.d(TAG, String.format("VideoExtractor setInfo:  frameRate %d, width %d height %d, totalDurationUs %d",
                    frameRate, width, height, totalDurationUs));
            if (startTimeUs>=0 && endTimeUs>startTimeUs && endTimeUs<=totalDurationUs) {
                cutDurationUs = endTimeUs-startTimeUs;
            }
        }
    }

    public long getTotalDurationUs() {
        return totalDurationUs;
    }

    public long getCutDurationUs(){

        if (cutDurationUs==0){
            cutDurationUs=totalDurationUs;
        }
        return cutDurationUs;
    }

    public boolean isNeedToChanged() {
        return true;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
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
}
