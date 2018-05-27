package com.hanzi.videobinddemo.media.Utils.extractor;


import com.hanzi.videobinddemo.utils.MuxerUtils;

/**
 * Created by gengen on 2018/5/22.
 */

public class AudioExtractor extends MediaExtractor {
    public static String TAG = "AudioExtractor";

    private int sampleRate = 44100;
    private int channelCount = 2;
    private long durationUs = 0;
    private int outSampleRate = 44100;
    private int maxInputSize = 8192;

    public AudioExtractor(String url, long startTime, long endTime) {
        super(url, AUDIO_TYPE, startTime, endTime);
        setInfo();
        selectTrack(trackIndex);
    }

    @Override
    public void setInfo() {
        if (isExistedTrackType(AUDIO_TYPE)) {
            sampleRate = Integer.parseInt(MuxerUtils.getValue(format.toString(), "sample-rate"));
            channelCount = Integer.parseInt(MuxerUtils.getValue(format.toString(), "channel-count"));
            durationUs = Long.parseLong(MuxerUtils.getValue(format.toString(),"durationUs"));
            maxInputSize = Integer.parseInt(MuxerUtils.getValue(format.toString(),"max-input-size"));
//            Log.d(TAG, "setInfo: durationUs:"+durationUs);
//            Log.d(TAG, "setInfo: sample-rate:"+sampleRate);
        }
    }

    public int getInitSampleRate(){
        return sampleRate;
    }


    public boolean isNeedToResample(int outSampleRate) {
        this.outSampleRate = outSampleRate;
//        Log.d(TAG, "isNeedToResample: outSampleRate:"+outSampleRate);
//        Log.d(TAG, "isNeedToResample: sampleRate:"+sampleRate);
        if (sampleRate!=outSampleRate) {
            return true;
        }else {
            return false;
        }
    }

    public long getDurationUs() {
//        Log.d(TAG, "getDurationUs: "+durationUs);
//        Log.d(TAG, "getDurationUs: sampleRate:"+sampleRate);
//        Log.d(TAG, "getDurationUs: channelCount:"+channelCount);
        return durationUs;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public void setChannelCount(int channelCount) {
        this.channelCount = channelCount;
    }

    public int getMaxInputSize() {
        return maxInputSize;
    }

    public void setMaxInputSize(int maxInputSize) {
        this.maxInputSize = maxInputSize;
    }

    public int getOutSampleRate() {
        return outSampleRate;
    }

    public void setOutSampleRate(int outSampleRate) {
        this.outSampleRate = outSampleRate;
    }
}
