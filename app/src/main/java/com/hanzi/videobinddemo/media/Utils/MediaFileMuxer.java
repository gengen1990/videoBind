package com.hanzi.videobinddemo.media.Utils;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by gengen on 2018/5/21.
 */

public class MediaFileMuxer {
    private MediaMuxer mediaMuxer;

    public MediaFileMuxer(String outputPath) {
        try {
            this.mediaMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int addTrack(MediaFormat format) {
        if (format == null) {
            return -1;
        }
        return mediaMuxer.addTrack(format);
    }

    public void start() {
        mediaMuxer.start();
    }

    public void writeSampleData(int outTrackIndex, ByteBuffer byteBuffer, MediaCodec.BufferInfo info) {
        mediaMuxer.writeSampleData(outTrackIndex, byteBuffer, info);
    }

    public void stop() {
        mediaMuxer.stop();
    }

    public void release() {
        mediaMuxer.release();
    }
}
