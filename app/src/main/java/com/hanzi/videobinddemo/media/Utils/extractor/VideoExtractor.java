package com.hanzi.videobinddemo.media.Utils.extractor;


import com.hanzi.videobinddemo.utils.MuxerUtils;

/**
 * Created by gengen on 2018/5/22.
 */

public class VideoExtractor extends MediaExtractor{
    private int frameRate = 0;
    private int width= 0;
    private int height =0;
    private long durationUs=0;

    public VideoExtractor(String url, long startTimeUs, long endTimeUs) {
        super(url,VIDEO_TYPE, startTimeUs,endTimeUs);
    }

    @Override
    public void setInfo() {
        if (isExistedTrackType(AUDIO_TYPE)) {
            frameRate = Integer.parseInt(MuxerUtils.getValue(format.toString(), "frame-rate"));
            width = Integer.parseInt(MuxerUtils.getValue(format.toString(), "width"));
            height = Integer.parseInt(MuxerUtils.getValue(format.toString(), "height"));
            durationUs = Long.parseLong(MuxerUtils.getValue(format.toString(),"durationUs"));
        }
    }
}
