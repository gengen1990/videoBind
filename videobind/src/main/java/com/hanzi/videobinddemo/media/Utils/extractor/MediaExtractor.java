package com.hanzi.videobinddemo.media.Utils.extractor;

import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by gengen on 2018/5/21.
 */

public class MediaExtractor {
    private final String TAG = "MediaExtractor";
    public final static int VIDEO_TYPE = 0;
    public final static int AUDIO_TYPE = 1;
    public int type = VIDEO_TYPE;

    protected android.media.MediaExtractor mMediaExtractor;

    protected MediaFormat format;

    protected int trackIndex = 0;

    protected long startTimeUs = 0;
    protected long endTimeUs = -1;

    public MediaExtractor(String url, int type) {
        try {
            this.type = type;
            mMediaExtractor = new android.media.MediaExtractor();
            mMediaExtractor.setDataSource(url);
//            isExistedTrackType(type);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public MediaExtractor(String url, int type, long startTimeUs, long endTimeUs) {
        this(url, type);
        this.startTimeUs = startTimeUs;
        this.endTimeUs = endTimeUs;
    }

    public boolean isExistedTrackType(int type) {
        String mediaType = "video/";
        switch (type) {
            case VIDEO_TYPE:
                mediaType = "video/";
                break;
            case AUDIO_TYPE:
                mediaType = "audio/";
                break;
            default:
                break;
        }
        Log.i(TAG, "isExistedTrackType: count:"+mMediaExtractor.getTrackCount());
        for (int i = 0; i < mMediaExtractor.getTrackCount(); i++) {
            MediaFormat format = mMediaExtractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith(mediaType)) {
                trackIndex = i;
                this.format = format;
                this.type = type;
                return true;
            }
        }
        return false;
    }

    public void selectTrack(int index) {
        mMediaExtractor.selectTrack(index);
    }

    public MediaFormat getFormat() {
        return format;
    }

    public void setFormat(MediaFormat format) {
        this.format = format;
    }

    public void setInfo() {
    }

    public long getSampleTime() {
        return mMediaExtractor.getSampleTime();
    }

    public long getStartTimeUs() {
        return startTimeUs;
    }

    public void setStartTimeUs(long startTimeUs) {
        this.startTimeUs = startTimeUs;
    }

    public long getEndTimeUs() {
        return endTimeUs;
    }

    public void setEndTimeUs(long endTimeUs) {
        this.endTimeUs = endTimeUs;
    }

    public void seekTo(long time, int mode) {
        mMediaExtractor.seekTo(time, mode);
    }

    public int readSampleData(ByteBuffer byteBuffer, int offset) {
        return mMediaExtractor.readSampleData(byteBuffer, offset);
    }

    public void advance() {
        mMediaExtractor.advance();
    }

    public int getTrackIndex() {
        return trackIndex;
    }

    public int getSampleFlags() {
        return mMediaExtractor.getSampleFlags();
    }

    public void release() {
        mMediaExtractor.release();
    }
}
