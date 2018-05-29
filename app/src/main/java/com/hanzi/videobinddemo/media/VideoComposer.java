package com.hanzi.videobinddemo.media;


import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.hanzi.videobinddemo.bean.VideoInfo;
import com.hanzi.videobinddemo.filter.AFilter;
import com.hanzi.videobinddemo.media.Utils.MediaFileMuxer;
import com.hanzi.videobinddemo.media.Utils.decoder.VideoDecoder;
import com.hanzi.videobinddemo.media.Utils.encoder.VideoEncoder;
import com.hanzi.videobinddemo.media.Utils.extractor.VideoExtractor;
import com.hanzi.videobinddemo.media.Variable.MediaBean;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC;

/**
 * Created by gengen on 2018/5/22.
 * 视频合成
 */

public class VideoComposer {
    private final String TAG = "VideoComposer";
    private List<MediaBean> mMediaBeans;
    private AFilter mFilter;
    private String outFilePath;

    private List<VideoExtractor> mMediaExtractors = new ArrayList<>();

    private MediaFileMuxer mediaFileMuxer;
    private long mDuration = 0;

    private int mFrameRate = 15, mOutWidth = 0, mOutHeight = 0;

    private HandlerThread mergeThread;
    private Handler mergeHandler;

    private boolean beStop = false;

    private HashMap<Integer, Boolean> videoEditIndex = new HashMap<>();

//    private HashMap<Integer, Boolean> videoEditIndex = new HashMap<>();

    int mOutVideoTrackIndex = -1;

    private ByteBuffer mReadBuf;

    private VideoComposerCallBack videoComposerCallBack;

    public VideoComposer(List<MediaBean> mediaBeans, AFilter filter, int outWidth, int outHeight, String outFilePath) {
        this.mMediaBeans = mediaBeans;
        this.mFilter = filter;
        this.mOutWidth = outWidth;
        this.mOutHeight = outHeight;
        this.outFilePath = outFilePath;
        Log.i(TAG, String.format("VideoComposer:  mOutWidth %d, mOutHeight %d outFilePath %s",
                mOutWidth, mOutHeight, outFilePath));

        mediaFileMuxer = new MediaFileMuxer(outFilePath);

        for (MediaBean bean : mMediaBeans) {
            VideoExtractor extractor = new VideoExtractor(bean.getUrl(), bean.getStartTimeUs(), bean.getEndTimeUs());
            mMediaExtractors.add(extractor);
            mDuration += extractor.getDurationUs();
        }

        mergeThread = new HandlerThread("merge");
        mergeThread.start();
        mergeHandler = new Handler(mergeThread.getLooper());
    }

    public void setVideoComposerCallBack(VideoComposerCallBack videoComposerCallBack) {
        this.videoComposerCallBack = videoComposerCallBack;
    }

    public void start() {
        startMuxer();
        mergeHandler.post(new Runnable() {
            @Override
            public void run() {
                startVideoEdit();//还没有完善，暂时每一个视频都进行编码解码的操作吧
                if (isEditOk()) {
                    videoComposerCallBack.onh264Path();
                }
//                startMerge();
            }
        });
    }

    private boolean isEditOk() {
        boolean isEditOk = false;
        while ((!isEditOk) && (!beStop)) {
            int i = 0;
            for (Integer key : videoEditIndex.keySet()) {
                if (!videoEditIndex.get(key)) {
                    break;
                }
                i++;
            }
            if (i == videoEditIndex.size()) {
                isEditOk = true;
            }
        }
        return isEditOk;
    }

    public void stop() {
        beStop = true;
        stopMuxer();
        stopExtractor();
    }

    private void startVideoEdit() {
        if (mMediaExtractors == null || mMediaExtractors.size() == 0) {
            return;
        }
        int index = 0;
        for (VideoExtractor videoExtractor : mMediaExtractors) {
            long firstSampleTime = videoExtractor.getSampleTime();
            long durationUs = videoExtractor.getDurationUs();
            long startTimeUs = videoExtractor.getStartTimeUs();
            long endTimeUs = videoExtractor.getEndTimeUs();
            if (endTimeUs != -1 && endTimeUs < durationUs) {
                durationUs = endTimeUs - startTimeUs;
            }
            if (videoExtractor.isNeedToChanged()) {
                videoEditIndex.put(index, false);
                oneVideoEdit(index, videoExtractor, firstSampleTime, durationUs, startTimeUs, endTimeUs);
            }

            index++;
        }
    }

    /**
     * 对单个视频进行效果处理
     *
     * @param index
     * @param videoExtractor
     * @param firstSampleTime
     * @param durationUs
     * @param startTimeUs
     * @param endTimeUs
     */
    private void oneVideoEdit(int index, VideoExtractor videoExtractor, long firstSampleTime, long durationUs, long startTimeUs, long endTimeUs) {
        videoExtractor.seekTo(firstSampleTime + startTimeUs, SEEK_TO_PREVIOUS_SYNC);

        VideoEncoder encoder = new VideoEncoder();
        openEncoder(index, encoder, mOutWidth, mOutHeight, mFrameRate);
        encoder.start();

        VideoDecoder decoder = new VideoDecoder();
        openDecoder(index, decoder, videoExtractor, encoder, firstSampleTime, startTimeUs);
        decoder.start();

        inputForDecoder(videoExtractor, firstSampleTime, durationUs, startTimeUs, decoder);


    }

    private void openEncoder(final int index, VideoEncoder encoder, int width, int height, int frameRate) {
        encoder.open("video/avc", width, height, frameRate,
                new VideoEncoder.VideoEncoderCallBack() {

                    @Override
                    public void onInputBuffer() {

                    }

                    @Override
                    public void onOutputBuffer(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
                        mediaFileMuxer.writeSampleData(mOutVideoTrackIndex, byteBuffer, bufferInfo);
                    }

                    @Override
                    public void encodeOver() {
                        videoEditIndex.put(index, true);
                    }
                });
    }

    private void startMerge() {
        if (mMediaExtractors == null || mMediaExtractors.size() == 0) {
            return;
        }
        int index = 0;
        for (VideoExtractor videoExtractor : mMediaExtractors) {
            long firstSampleTime = videoExtractor.getSampleTime();
            long durationUs = videoExtractor.getDurationUs();
            long startTimeUs = videoExtractor.getStartTimeUs();
            long endTimeUs = videoExtractor.getEndTimeUs();
            if (endTimeUs != -1 && endTimeUs < durationUs) {
                durationUs = endTimeUs - startTimeUs;
            }
            mergeWithoutChanged(videoExtractor, firstSampleTime, durationUs, startTimeUs, endTimeUs);
        }

    }

    private void mergeWithoutChanged(VideoExtractor videoExtractor, long firstSampleTime, long durationUs,
                                     long startTimeUs, long endTimeUs) {
        videoExtractor.seekTo(firstSampleTime + startTimeUs, SEEK_TO_PREVIOUS_SYNC);
    }


    private void startMuxer() {
        MediaFormat mediaFormat = mMediaExtractors.get(0).getFormat();
        if (mediaFormat != null) {
            mediaFormat.setInteger("frame-rate", mFrameRate);
            mediaFormat.setInteger("width", mOutWidth);
            mediaFormat.setInteger("height", mOutHeight);
        }

        mOutVideoTrackIndex = mediaFileMuxer.addTrack(mediaFormat);
        Log.d(TAG, "startMuxer: mediaFormat:" + mediaFormat.toString());
        Log.d(TAG, "startMuxer: mOutVideoTrackIndex:" + mOutVideoTrackIndex);
        mediaFileMuxer.start();
    }

    private void stopMuxer() {
        if (mediaFileMuxer != null) {
            try {
                mediaFileMuxer.stop();
                mediaFileMuxer.release();
            } catch (Exception e) {
                Log.e(TAG, "Muxer close error. No data was written");
            }
            mediaFileMuxer = null;
        }
    }

    private void stopExtractor() {
        for (VideoExtractor extractor : mMediaExtractors) {
            extractor.release();
        }
    }

    private void openDecoder(int index, VideoDecoder decoder, final VideoExtractor videoExtractor,
                             final VideoEncoder videoEncoder, long firstSampleTime, long startTimeUs) {
        MediaFormat format = videoExtractor.getFormat();
        VideoInfo videoInfo = new VideoInfo();
        videoInfo.width = videoExtractor.getWidth();
        videoInfo.height = videoExtractor.getHeight();
        videoInfo.rotation = 0;
        MediaBean mediaBean = mMediaBeans.get(index);

        decoder.open(mediaBean, mFilter, format, firstSampleTime, startTimeUs, new VideoDecoder.VideoDecodeCallBack() {
            @Override
            public void onInputBuffer() {
                videoExtractor.advance();
            }

            @Override
            public void onOutputBuffer(byte[] bytes) {

            }

            @Override
            public void onOutputBufferInfo(MediaCodec.BufferInfo bufferInfo) {
                videoEncoder.encoder(bufferInfo);
            }

            @Override
            public void decodeOver() {
                videoEncoder.signalEndOfInputStream();
            }
        });
    }

    private void inputForDecoder(VideoExtractor videoExtractor, long firstSampleTime,
                                 long durationUs, long startTimeUs, VideoDecoder decoder) {
        boolean isRunning = true;
        mReadBuf.rewind();
        while (isRunning) {
            int sampleSize = videoExtractor.readSampleData(mReadBuf, 0);
            long sampleTime = videoExtractor.getSampleTime();
            long now = videoExtractor.getSampleTime() - firstSampleTime - startTimeUs;

            Log.d(TAG, String.format("now %d sampleSize %d  sampleTime %d",now,sampleSize,sampleTime));

            if (sampleSize < 0 || (now >= durationUs || now == -1)) {
                isRunning = false;
            } else {
                mReadBuf.rewind();
                if (!decoder.decode(mReadBuf, sampleSize, sampleTime)) {
                    isRunning = false;
                }
            }
        }
    }

    public long getDurationUs() {
        return mDuration;
    }

    public interface VideoComposerCallBack {
        public void onh264Path();
    }
}
