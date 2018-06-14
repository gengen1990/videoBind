package com.hanzi.videobinddemo.media;


import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.hanzi.videobinddemo.filter.AFilter;
import com.hanzi.videobinddemo.media.Utils.MediaFileMuxer;
import com.hanzi.videobinddemo.media.Utils.decoder.VideoDecoder;
import com.hanzi.videobinddemo.media.Utils.encoder.VideoEncoder;
import com.hanzi.videobinddemo.media.Utils.extractor.VideoExtractor;
import com.hanzi.videobinddemo.media.Variable.MediaBean;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC;

/**
 * Created by gengen on 2018/5/22.
 * 视频拼接+添加效果
 */

public class VideoComposer {
    private final String TAG = "VideoComposer";
    private List<MediaBean> mMediaBeans;
    private AFilter mFilter;
    private String outFilePath;

    private List<VideoExtractor> mMediaExtractors = new ArrayList<>();

    private MediaFileMuxer mediaFileMuxer;
    private long mDuration = 0;

    private int mFrameRate = 15, mOutWidth = -1, mOutHeight = -1;

    private HandlerThread composerThread;
    private Handler composerHandler;

    private Handler decoderHandler;
    private HandlerThread decoderThread;

    static ExecutorService executorService = Executors.newFixedThreadPool(4);

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

        mReadBuf = ByteBuffer.allocate(1048576);

        mediaFileMuxer = new MediaFileMuxer(outFilePath);

        for (MediaBean bean : mMediaBeans) {
            VideoExtractor extractor = new VideoExtractor(bean.getUrl(), bean.getStartTimeUs(), bean.getEndTimeUs());
            mMediaExtractors.add(extractor);
            mDuration += extractor.getDurationUs();
            if (bean.getVideoWidth() == 0) {
                bean.setVideoWidth(extractor.getWidth());
            }
            if (bean.getVideoHeight() == 0) {
                bean.setVideoHeight(extractor.getHeight());
            }
            bean.setRate(extractor.getFrameRate());
//            if (mOutWidth==0 || mOutHeight==0) {
//                mOutWidth = bean.getVideoWidth();
//                mOutHeight = bean.getVideoHeight();
//            }
            mFrameRate = bean.getRate();
        }


        composerThread = new HandlerThread("composer");
        composerThread.start();
        composerHandler = new Handler(composerThread.getLooper());

        decoderThread = new HandlerThread("decoder");
        decoderThread.start();
        decoderHandler = new Handler(decoderThread.getLooper());
    }

    public void setVideoComposerCallBack(VideoComposerCallBack videoComposerCallBack) {
        this.videoComposerCallBack = videoComposerCallBack;
    }

    public void start() {
        startMuxer();
        composerHandler.post(new Runnable() {
            @Override
            public void run() {
                startVideoEdit();
                if (isEditOk()) {
                    startMerge();

                    videoComposerCallBack.onh264Path();
                    stop();
                }


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
        if (beStop && (mMediaExtractors == null || mMediaExtractors.size() == 0)) {
            return;
        }
        int index = 0;
        for (final VideoExtractor videoExtractor : mMediaExtractors) {
            final int finalIndex = index;
            Log.i(TAG, "startVideoEdit: index:" + index);
            videoEditIndex.put(finalIndex, false);
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    long firstSampleTime = videoExtractor.getSampleTime();
                    long durationUs = videoExtractor.getDurationUs();
                    long startTimeUs = videoExtractor.getStartTimeUs();
                    long endTimeUs = videoExtractor.getEndTimeUs();
                    if (endTimeUs != -1 && endTimeUs < durationUs) {
                        durationUs = endTimeUs - startTimeUs;
                    }

                    Log.d(TAG, String.format("startVideoEdit: firstSampleTime:%d, durationUs:%d, startTimeUs:%d, endTimeUs:%d",
                            firstSampleTime, durationUs, startTimeUs, endTimeUs));

                    if (videoExtractor.isNeedToChanged()) {


                        oneVideoEdit(finalIndex, videoExtractor, firstSampleTime, durationUs, startTimeUs, endTimeUs);
                    }
                }
            });
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
    private void oneVideoEdit(int index, final VideoExtractor videoExtractor, final long firstSampleTime, final long durationUs, final long startTimeUs, long endTimeUs) {
        videoExtractor.seekTo(firstSampleTime + startTimeUs, SEEK_TO_PREVIOUS_SYNC);
        int width = 0, height = 0, frameRate = 0;
        if (mOutWidth == 0) {
            width = videoExtractor.getWidth();
        } else {
            width = mOutWidth;
        }


        if (mOutHeight == 0) {
            height = videoExtractor.getHeight();
        } else {
            height = mOutHeight;
        }

        frameRate = videoExtractor.getFrameRate();

        VideoEncoder encoder = new VideoEncoder();


        Log.i(TAG, "oneVideoEdit: width:" + width);
        Log.i(TAG, "oneVideoEdit: height:" + height);
        final VideoDecoder decoder = new VideoDecoder();
        openDecoder(index, decoder, videoExtractor, encoder, width, height, firstSampleTime, startTimeUs);
        openEncoder(index, encoder, width, height, frameRate);
        decoder.start(false);
        encoder.start(false);

//                inputForDecoder2(videoExtractor, firstSampleTime, durationUs, startTimeUs, decoder);
        composer(videoExtractor, firstSampleTime, durationUs, startTimeUs, decoder, encoder);

    }

    private void composer(VideoExtractor videoExtractor, long firstSampleTime,
                          long durationUs, long startTimeUs, VideoDecoder decoder, VideoEncoder encoder) {
        boolean done = false;
        boolean decodeInputDone = false;
        boolean decodeOutputDone = false;
        while (!done) {
            if (!decodeInputDone) {
                long now;
                if (!beStop) {
                    now = videoExtractor.getSampleTime() - firstSampleTime - startTimeUs;
                } else {
                    now = -1;
                }
                boolean beEndStream = (now >= durationUs || now == -1);
//            Log.i(TAG, "inputForDecoder2: beEndStream:" + beEndStream);
//            Log.i(TAG, "inputForDecoder2: now:" + now);
                if (!decoder.decode(beEndStream)) {
                    decodeInputDone = true;
                }
            }

            if (!decodeOutputDone) {
                if (!decoder.decodeOutput()) {
                    decodeOutputDone = true;
                }
            }
            if (encoder != null)
                done = encoder.encodeOutput();

        }

    }

    private void openEncoder(final int index, VideoEncoder encoder, int width, int height, int frameRate) {
        encoder.open("video/avc", width, height, frameRate,
                new VideoEncoder.VideoEncoderCallBack() {

                    @Override
                    public void onInputBuffer() {

                    }

                    @Override
                    public void onOutputBuffer(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
                        byte[] dst = new byte[byteBuffer.limit()];
                        byteBuffer.get(dst);
                        Log.i(TAG, "onOutputBuffer: byte:" + Arrays.toString(dst));
                        mediaFileMuxer.writeSampleData(mOutVideoTrackIndex, byteBuffer, bufferInfo);
                    }

                    @Override
                    public void encodeOver() {
                        Log.i(TAG, "encodeOver: true");
                        videoEditIndex.put(index, true);
                    }

                    @Override
                    public void formatChanged() {
//                        startMuxer();
                    }
                });
    }

    private void startMerge() {
        if (beStop && (mMediaExtractors == null || mMediaExtractors.size() == 0)) {
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
        if (mediaFormat != null && mOutHeight != 0 && mOutWidth != 0 && mFrameRate != 0) {
            mediaFormat.setInteger("frame-rate", mFrameRate);
            mediaFormat.setInteger("width", mOutWidth);
            mediaFormat.setInteger("height", mOutHeight);
        }

        Log.d(TAG, "startMuxer: mediaFormat:" + mediaFormat.toString());

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
                             final VideoEncoder videoEncoder, int width, int height, long firstSampleTime, long startTimeUs) {

        Log.d(TAG, "openDecoder: ");
        MediaFormat format = videoExtractor.getFormat();
//        VideoInfo videoInfo = new VideoInfo();
//        videoInfo.width = videoExtractor.getWidth();
//        videoInfo.height = videoExtractor.getHeight();
//        videoInfo.rotation = 0;

        Log.i(TAG, "openDecoder: format:" + format);
        MediaBean mediaBean = mMediaBeans.get(index);

        decoder.open(mediaBean, mFilter, format, width, height, firstSampleTime, startTimeUs, new VideoDecoder.VideoDecodeCallBack() {
            @Override
            public int putInputData(ByteBuffer byteBuffer) {
                return videoExtractor.readSampleData(byteBuffer, 0);
            }

            @Override
            public long getPresentationTimeUs() {
                return videoExtractor.getSampleTime();
            }

            @Override
            public void onInputBuffer() {
                videoExtractor.advance();
                Log.d(TAG, "onInputBuffer: ");
            }

            @Override
            public void onOutputBuffer(byte[] bytes) {
                Log.d(TAG, "onOutputBuffer: length:" + bytes.length);
            }

            @Override
            public void onOutputMakeCurrent() {
                videoEncoder.makeCurrent();
            }

            @Override
            public void onOutputBufferInfo(MediaCodec.BufferInfo bufferInfo) {
                Log.d(TAG, "onOutputBufferInfo: " + bufferInfo);
                videoEncoder.encoder(bufferInfo);

            }

            @Override
            public void decodeOver() {
                videoEncoder.signalEndOfInputStream();
                Log.i(TAG, "decodeOver: ");
            }
        });
    }

    private void inputForDecoder(VideoExtractor videoExtractor, long firstSampleTime,
                                 long durationUs, long startTimeUs, VideoDecoder decoder) {
        boolean isRunning = true;
//        mReadBuf.rewind();
        while (isRunning) {
            int sampleSize = videoExtractor.readSampleData(mReadBuf, 0);
            long sampleTime = videoExtractor.getSampleTime();
            long now = videoExtractor.getSampleTime() - firstSampleTime - startTimeUs;

            Log.d(TAG, String.format("now %d sampleSize %d  sampleTime %d", now, sampleSize, sampleTime));

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

    private void inputForDecoder2(VideoExtractor videoExtractor, long firstSampleTime,
                                  long durationUs, long startTimeUs, VideoDecoder decoder) {
        boolean isRunning = true;
//        mReadBuf.rewind();
        while (isRunning) {
            long now;
            if (!beStop) {
                now = videoExtractor.getSampleTime() - firstSampleTime - startTimeUs;
            } else {
                now = -1;
            }
            boolean beEndStream = (now >= durationUs || now == -1);
//            Log.i(TAG, "inputForDecoder2: beEndStream:" + beEndStream);
//            Log.i(TAG, "inputForDecoder2: now:" + now);
            if (!decoder.decode(beEndStream)) {
                isRunning = false;
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
