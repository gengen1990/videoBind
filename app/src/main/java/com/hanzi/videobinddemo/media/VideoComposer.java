package com.hanzi.videobinddemo.media;


import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.hanzi.videobinddemo.filter.AFilter;
import com.hanzi.videobinddemo.media.Utils.ByteContainer;
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
    private MediaFormat mMuxerFormat;

    private long mDuration = 0;

    private int mFrameRate = 15, mOutWidth = -1, mOutHeight = -1;

    private HandlerThread composerThread;
    private Handler composerHandler;

    private Handler decoderHandler;
    private HandlerThread decoderThread;

    static ExecutorService executorService = Executors.newFixedThreadPool(4);

    private boolean beStop = false;

    private HashMap<Integer, Boolean> videoEditIndex = new HashMap<>();

    int mOutVideoTrackIndex = -1;

    private ByteBuffer mReadBuf;

    private VideoComposerCallBack videoComposerCallBack;

    private HashMap<Integer, ByteContainer> videoDataHashMap = new HashMap<>();

    private VideoProgressCallBack videoProgressCallBack;

    private HashMap<Integer, Integer> resampleRateIndex = new HashMap<>();

    private long ptsOffset = 0;

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
            mDuration += extractor.getCutDurationUs();
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

            if (videoProgressCallBack != null) {
                videoProgressCallBack.onVideoType("视频拼接：");
            }
        }

        mMuxerFormat = mMediaExtractors.get(0).getFormat();
        if (mMuxerFormat != null && mOutHeight != 0 && mOutWidth != 0 && mFrameRate != 0) {
            mMuxerFormat.setInteger("frame-rate", mFrameRate);
            mMuxerFormat.setInteger("width", mOutWidth);
            mMuxerFormat.setInteger("height", mOutHeight);
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
//        startMuxer();
        composerHandler.post(new Runnable() {
            @Override
            public void run() {
                startVideoEdit();
                if (isEditOk()) {
                    startMerge();
                    Log.i(TAG, "run: isOk");
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
                    long totalDurationUs = videoExtractor.getTotalDurationUs();
                    long startTimeUs = videoExtractor.getStartTimeUs();
                    long endTimeUs = videoExtractor.getEndTimeUs();
                    long cutDurationUs = videoExtractor.getCutDurationUs();
//                    if (endTimeUs != -1 && endTimeUs < totalDurationUs) {
//                        totalDurationUs = endTimeUs - startTimeUs;
//                    }

                    Log.d(TAG, String.format("startVideoEdit: firstSampleTime:%d, cutDurationUs:%d, startTimeUs:%d, endTimeUs:%d",
                            firstSampleTime, cutDurationUs, startTimeUs, endTimeUs));

                    if (videoExtractor.isNeedToChanged()) {
                        oneVideoEdit(finalIndex, videoExtractor, firstSampleTime, cutDurationUs, startTimeUs, endTimeUs);
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
        videoExtractor.seekTo(firstSampleTime, SEEK_TO_PREVIOUS_SYNC);//+ startTimeUs
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

        Log.i(TAG, String.format("oneVideoEdit: width：%d，height:%d", width, height));

        VideoEncoder encoder = new VideoEncoder();
        VideoDecoder decoder = new VideoDecoder();

        openEncoder(index, encoder, width, height, frameRate);
        openDecoder(index, decoder, videoExtractor, encoder, width, height, firstSampleTime, startTimeUs);
        decoder.start(false);
        encoder.start(false);
        Log.i(TAG, String.format("oneVideoEdit: durationUs:%d,startTimeUs:%d", durationUs, startTimeUs));
//                inputForDecoder2(videoExtractor, firstSampleTime, durationUs, startTimeUs, decoder);
        composer(index, videoExtractor, firstSampleTime, durationUs, startTimeUs, decoder, encoder);

    }

    private void composer(int index, VideoExtractor videoExtractor, long firstSampleTime,
                          long durationUs, long startTimeUs, VideoDecoder decoder, VideoEncoder encoder) {
        boolean done = false;
        boolean decodeInputDone = false;
        boolean decodeOutputDone = false;
        boolean isDecoding = false;
        while (!done) {
            if (!decodeInputDone) {
                long now;
                if (!beStop) {
                    now = videoExtractor.getSampleTime()- firstSampleTime-startTimeUs;//- startTimeUs//
                } else {
                    now = -1;
                }
                //开始判断如果是未开始编码，先将videoExtractor 定位到 now>=0
                if (!isDecoding && now < 0) {
                    videoExtractor.advance();
                } else {
                    isDecoding = true;
                }

                if (isDecoding) {
                    boolean beEndStream = (now >= durationUs || now < 0);
                    Log.i(TAG, "composer1: sampleTime:" + videoExtractor.getSampleTime());
                    Log.i(TAG, "composer1: index:" + index);
                    Log.i(TAG, "composer1: firstSampleTime:" + firstSampleTime);
                    Log.i(TAG, "composer1: startTimeUs:" + startTimeUs);
                    Log.i(TAG, "composer1: now:" + now);
                    Log.i(TAG, "composer1: duraiton:" + durationUs);
                    Log.d(TAG, "composer1: decodeInputDone ：" + decodeInputDone);
                    if (!decoder.decode(beEndStream)) {
                        decodeInputDone = true;
                        isDecoding = false;
                    }
                }
            }

            if (!decodeOutputDone) {
                if (!decoder.decodeOutput()) {
                    Log.i(TAG, "composer: decodeOutputDone true");
                    decodeOutputDone = true;
                }
            }
            if (encoder != null)
                if (decodeOutputDone) {
//                    encoder.stopEncoder();
                    done = true;
                } else {
                    done = encoder.encodeOutput();
                }
            Log.i(TAG, "composer: done:" + done);

        }
        videoEditIndex.put(index, true);
    }

    private void openEncoder(final int index, VideoEncoder encoder, int width, int height, int frameRate) {
        encoder.open("video/avc", width, height, frameRate,
                new VideoEncoder.VideoEncoderCallBack() {

                    @Override
                    public void onInputBuffer() {

                    }

                    @Override
                    public void formatChanged(MediaFormat mediaFormat) {
//                        startMuxer(mediaFormat);
                        mMuxerFormat = mediaFormat;
                    }

                    @Override
                    public void onOutputBuffer(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
                        if (!videoDataHashMap.containsKey(index)) {
                            ByteContainer byteContainer = new ByteContainer();
                            videoDataHashMap.put(index, byteContainer);
                        }
                        byte[] dst = new byte[byteBuffer.limit()];
                        byteBuffer.get(dst);

                        Log.d(TAG, "onOutputBuffer: bufferInfo:" + bufferInfo.presentationTimeUs);
                        Log.d(TAG, "onOutputBuffer: index:" + index);
                        videoDataHashMap.get(index).putData(dst);
                        videoDataHashMap.get(index).putBufferInfo(bufferInfo);

                        resampleProgress(index, bufferInfo.presentationTimeUs);
//                        if (!muxStarted) {
//                            synchronized (lock) {
//                                if (!muxStarted) {
//                                    try {
//                                        lock.wait();
//                                    } catch (InterruptedException e) {
//                                        e.printStackTrace();
//                                    }
//                                }
//                            }
//                        }
//                        mediaFileMuxer.writeSampleData(mOutVideoTrackIndex, byteBuffer, bufferInfo);

                    }

                    @Override
                    public void encodeOver() {
                        Log.i(TAG, String.format("encodeOve %d", index));

                    }
                });
    }

    private void startMerge() {
        if (beStop) {//&& (mMediaExtractors == null || mMediaExtractors.size() == 0)
            return;
        }

        startMuxer(mMuxerFormat);

        Object[] key_arr = videoDataHashMap.keySet().toArray();
        Arrays.sort(key_arr);
        for (Object key : key_arr) {
            Log.i(TAG, "startMerge: key:" + key);
            mergeByteBuffer(videoDataHashMap.get(key));
        }

    }

    private void mergeByteBuffer(ByteContainer byteContainer) {
        if (byteContainer == null) {
            return;
        }

        long videoPts = 0;
        while (!byteContainer.isEmpty()) {
            byte[] data = byteContainer.getData();
            Log.d(TAG, "mergeByteBuffer: length:" + data.length);
            ByteBuffer byteBuffer = ByteBuffer.allocate(data.length);
            MediaCodec.BufferInfo bufferInfo = byteContainer.getBufferInfo();
            Log.d(TAG, "mergeByteBuffer: bufferInfo.presentationTimeUs:" + bufferInfo.presentationTimeUs);
            videoPts = bufferInfo.presentationTimeUs;
            byteBuffer.put(data);
            byteBuffer.flip();
            bufferInfo.presentationTimeUs = ptsOffset + bufferInfo.presentationTimeUs;
            mediaFileMuxer.writeSampleData(mOutVideoTrackIndex, byteBuffer, bufferInfo);
            mergeProgress(bufferInfo.presentationTimeUs);
        }
        ptsOffset += videoPts;
        ptsOffset += 10000L;//test ，如果不添加，如何
        Log.i(TAG, "finish one file, ptsOffset " + ptsOffset);
    }


    private void startMuxer(MediaFormat mediaFormat) {
        Log.d(TAG, "startMuxer: mediaFormat:" + mediaFormat.toString());
        mOutVideoTrackIndex = mediaFileMuxer.addTrack(mediaFormat);
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
        MediaFormat format = videoExtractor.getFormat();
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
                Log.i(TAG, "decodeOver: signalEndOfInputStream");
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
            if (!decoder.decode(beEndStream)) {
                isRunning = false;
            }
        }
    }

    public long getDurationUs() {
        return mDuration;
    }


    /**
     * 视频重新编解码的进度
     *
     * @param pts
     * @param index
     */
    private void resampleProgress(int index, long pts) {
        Log.d(TAG, "resampleProgress: pts:" + pts);
        if (videoProgressCallBack != null) {
            resampleRateIndex.put(index, (int) pts);
            int sum = 0;
            for (Integer integer : resampleRateIndex.keySet()) {
                sum += resampleRateIndex.get(integer);
            }
            Log.i(TAG, "onOutputBuffer: sum:" + sum);
            Log.i(TAG, "onOutputBuffer: duration:" + mDuration);

            float rate = ((float) sum / mDuration);
            if (rate > 1) {
                rate = 1;
            }
            videoProgressCallBack.onProgress((int) (MediaBind.VIDEO_RECODE_RATE * rate));
        }
    }

    private void mergeProgress(long pts) {
        if (videoProgressCallBack != null) {
            int RATE, exRate;
            RATE = MediaBind.VIDEO_MERGE_RATE;
            exRate = MediaBind.VIDEO_RECODE_RATE;

            float rate = (float) pts / mDuration;
            if (rate > 1) {
                rate = 1;
            }
            Log.i(TAG, "noMixMergeProgress: rate:" + rate);
            Log.i(TAG, "noMixMergeProgress: merge:" + (exRate + RATE * rate));
            videoProgressCallBack.onProgress((int) (exRate + RATE * rate));
        }
    }


    public void setVideoProgressCallBack(VideoProgressCallBack videoProgressCallBack) {
        this.videoProgressCallBack = videoProgressCallBack;
    }

    public interface VideoComposerCallBack {
        public void onh264Path();
    }

    public interface VideoProgressCallBack {
        void onVideoType(String content);

        void onProgress(int rate);
    }
}
