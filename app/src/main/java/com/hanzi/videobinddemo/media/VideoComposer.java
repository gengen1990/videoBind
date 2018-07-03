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
import com.hanzi.videobinddemo.bean.MediaBean;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.media.MediaExtractor.SAMPLE_FLAG_SYNC;
import static android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC;

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
    private HashMap<Integer, Long> startTimeHashMap = new HashMap<>();
    private HashMap<Integer, Long> startSynTimeHashMap = new HashMap<>();

    private VideoProgressCallBack videoProgressCallBack;

    private HashMap<Integer, Integer> resampleRateIndex = new HashMap<>();

    private long ptsOffset = 0;

    public VideoComposer(List<MediaBean> mediaBeans, AFilter filter, int outWidth, int outHeight, String outFilePath) {
        this.mMediaBeans = mediaBeans;
        this.mFilter = filter;
        this.mOutWidth = outWidth;
        this.mOutHeight = outHeight;
        this.outFilePath = outFilePath;

        mReadBuf = ByteBuffer.allocate(1048576);

        mediaFileMuxer = new MediaFileMuxer(outFilePath);

        for (MediaBean bean : mMediaBeans) {
            VideoExtractor extractor = new VideoExtractor(bean.getUrl(), bean.getStartTimeUs(), bean.getEndTimeUs());
            mMediaExtractors.add(extractor);
            mDuration += extractor.getCutDurationUs();
            if (bean.getContentWidth() == 0) {
                bean.setContentWidth(extractor.getWidth());
            }
            if (bean.getContentHeight() == 0) {
                bean.setContentHeight(extractor.getHeight());
            }
            bean.setRate(extractor.getFrameRate());
//            if (mOutWidth==0 || mOutHeight==0) {
//                mOutWidth = bean.getContentWidth();
//                mOutHeight = bean.getContentHeight();
//            }
            mFrameRate = bean.getRate();

            if (videoProgressCallBack != null) {
                videoProgressCallBack.onVideoType("视频拼接：");
            }
        }

        if (mOutWidth == 0 || mOutHeight == 0) {
            float rate = 9999;
            int width = 0;
            int height = 0;
            for (int i = 0; i < mediaBeans.size(); i++) {
                int tmpWidth = mediaBeans.get(i).getContentWidth();
                int tmpHeight = mediaBeans.get(i).getContentHeight();

                float tmp = 1 - tmpHeight / tmpWidth;
                if (Math.abs(tmp) < Math.abs(rate)) {
                    rate = tmp;
                    width = tmpWidth;
                    height = tmpHeight;
                }
            }
            mOutWidth = width;
            mOutHeight = height;
        }

        Log.i(TAG, String.format("VideoComposer:  mOutWidth %d, mOutHeight %d outFilePath %s",
                mOutWidth, mOutHeight, outFilePath));

        composerThread = new HandlerThread("VideoComposer");
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
//                    stop(false);
                    stopMuxer();
                    stopExtractor();
                }


            }
        });
    }

    private boolean isEditOk() {
        boolean isEditOk = false;
        while ((!isEditOk)) {//&& (!beStop)
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

    public void stop(boolean beStopThread) {
        beStop = true;
//        isResampleStopOk();
//        if (beStopThread) {
//            Log.i(TAG, "stop: stopComposerThread ");
//            stopComposerThread();
//            Log.i(TAG, "stop: stopComposerThread after");
//        }
//        stopMuxer();
//        stopExtractor();

    }

    private void stopComposerThread() {
        if (composerThread != null) {
//            composerThread.quitSafely();
            try {
                composerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            composerThread = null;
        }
        composerThread = null;
    }

    private void isResampleStopOk() {
        boolean isResampleStop = false;
        while (!isResampleStop) {
            Log.i(TAG, "isResampleStopOk: while");
            int i = 0;
            for (Integer key : videoEditIndex.keySet()) {
                if (!videoEditIndex.get(key)) {
                    break;
                }
                i++;
            }
            int j = 0;
            for (Integer key : videoEditIndex.keySet()) {
                if (!videoEditIndex.get(key)) {
                    break;
                }
                j++;
            }
            if (i == videoEditIndex.size() && j == videoEditIndex.size()) {
                isResampleStop = true;
            }
        }
    }


    private void startVideoEdit() {
        if (beStop || (mMediaExtractors == null || mMediaExtractors.size() == 0)) {
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

                    if (endTimeUs == -1) {
                        endTimeUs = videoExtractor.getTotalDurationUs();
                    }

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
        Log.i(TAG, "composer1: startTimeUs:" + startTimeUs);
        Log.i(TAG, "composer1: endTimeUs:" + endTimeUs);
        if ((endTimeUs != -1 && startTimeUs > endTimeUs) || startTimeUs > videoExtractor.getTotalDurationUs()) {
            return;
        }

        videoExtractor.seekTo(firstSampleTime, SEEK_TO_CLOSEST_SYNC);
        //先求出距离startTime 最近的关键帧 时间
        long startSynSampleTimeUs = 0;

        while (videoExtractor.getSampleTime() <= startTimeUs) {
            if (videoExtractor.getSampleFlags() == SAMPLE_FLAG_SYNC) {
                startSynSampleTimeUs = videoExtractor.getSampleTime();
            }
            videoExtractor.advance();
        }
        Log.i(TAG, "composer: startSynSampleTime:" + startSynSampleTimeUs);
        videoExtractor.seekTo(startSynSampleTimeUs, SEEK_TO_CLOSEST_SYNC);
        startSynTimeHashMap.put(index, startSynSampleTimeUs);
        startTimeHashMap.put(index, startTimeUs);

        if (mMediaBeans!=null) {
            mMediaBeans.get(index).setPreTimeS(((float)(startTimeUs - startSynSampleTimeUs))/1000000);
            Log.i(TAG, "oneVideoEdit: setPreTimeS:"+(startTimeUs-startSynSampleTimeUs));
        }

        //设置 输出的宽高
        Log.i(TAG, "composer11: startSampleTime:" + videoExtractor.getSampleTime());
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
        VideoDecoder decoder = new VideoDecoder();

        openEncoder(index, encoder, width, height, frameRate);
        openDecoder(index, decoder, videoExtractor, encoder, width, height, firstSampleTime, startTimeUs);
        decoder.start(false);
        encoder.start(false);
//                inputForDecoder2(videoExtractor, firstSampleTime, durationUs, startTimeUs, decoder);
        composer(index, videoExtractor, firstSampleTime, endTimeUs, startSynSampleTimeUs, decoder, encoder);
    }

    private void composer(int index, VideoExtractor videoExtractor, long firstSampleTime,
                          long endTime, long startSynSampleTime, VideoDecoder decoder, VideoEncoder encoder) {
        boolean done = false;
        boolean decodeInputDone = false;
        boolean decodeOutputDone = false;
        while (!done) {
            if (!decodeInputDone) {
                long outPts;

                if (!beStop) {
                    outPts = videoExtractor.getSampleTime() - firstSampleTime - startSynSampleTime;//- startTimeUs//
                } else {
                    outPts = -1 - firstSampleTime;
                }
                Log.i(TAG, "composer1: endTime:" + endTime);
                Log.i(TAG, "composer1: startSynSampleTime:" + startSynSampleTime);

                boolean beEndStream = (outPts >= endTime - startSynSampleTime || outPts == -1 - firstSampleTime);//test 如果之后出现多了秒数或者 和音频对不上，就需要解决//+ 1000000
//                    Log.i(TAG, "composer: now:" + now);
                if (!decoder.decode(outPts, beEndStream)) {
                    decodeInputDone = true;
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

        if (encoder != null) {
            encoder.stop();
        }
        if (decoder != null) {
            decoder.stop();
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
                        Log.d(TAG, "startMuxer1: formatChanged:" + mediaFormat.toString());
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
                        Log.i(TAG, String.format("encodeOver %d", index));

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
            mergeByteBuffer(key, videoDataHashMap.get(key));
        }

    }

    private void mergeByteBuffer(Object key, ByteContainer byteContainer) {
        if (byteContainer == null) {
            return;
        }
        byte[] startMergeData = null;
        MediaCodec.BufferInfo startMergeBufferInfo = new MediaCodec.BufferInfo();
        startMergeBufferInfo.presentationTimeUs = 0;
        long startSynTime = 0;

        long videoPts = 0;

        //求出第一帧的视频数据
        if (startTimeHashMap.containsKey(key) && startSynTimeHashMap.containsKey(key)) {
            long mergeStartTime = startTimeHashMap.get(key) - startSynTimeHashMap.get(key);
            Log.i(TAG, "mergeByteBuffer: mergeStartTime:" + mergeStartTime);
            while (startMergeBufferInfo.presentationTimeUs <= mergeStartTime) {

                MediaCodec.BufferInfo bufferInfo = byteContainer.getBufferInfo();
                byte[] data = byteContainer.getData();
                if (bufferInfo.flags == 1) {
                    startMergeData = data;
                    startMergeBufferInfo = bufferInfo;
                    Log.i(TAG, "mergeByteBuffer: ok:" + startMergeBufferInfo.presentationTimeUs);
                }
            }
        }
        if (startMergeData != null) {
            Log.i(TAG, "mergeByteBuffer: starMergeBufferInfo.flag:" + startMergeBufferInfo.flags);
            Log.i(TAG, "mergeByteBuffer: starMergeBufferInfo.pts:" + startMergeBufferInfo.presentationTimeUs);

            startSynTime = startMergeBufferInfo.presentationTimeUs;
            startMergeBufferInfo.presentationTimeUs = ptsOffset;
            ByteBuffer byteBuffer = ByteBuffer.allocate(startMergeData.length);
            byteBuffer.put(startMergeData);
            byteBuffer.flip();
            mediaFileMuxer.writeSampleData(mOutVideoTrackIndex, byteBuffer, startMergeBufferInfo);
            mergeProgress(startMergeBufferInfo.presentationTimeUs);
        }

        //开始正式把所需要的数据写进文件中
        while (!byteContainer.isEmpty()) {
            MediaCodec.BufferInfo bufferInfo = byteContainer.getBufferInfo();
            byte[] data = byteContainer.getData();

            videoPts = bufferInfo.presentationTimeUs - startSynTime;

            bufferInfo.presentationTimeUs = ptsOffset + videoPts;

            Log.i(TAG, "mergeByteBuffer: bufferInfo.presentationTimeUs:" + bufferInfo.presentationTimeUs);
            Log.i(TAG, "mergeByteBuffer: sampleFlag:" + bufferInfo.flags);

            ByteBuffer byteBuffer = ByteBuffer.allocate(data.length);
            byteBuffer.put(data);
            byteBuffer.flip();

            mediaFileMuxer.writeSampleData(mOutVideoTrackIndex, byteBuffer, bufferInfo);
            mergeProgress(bufferInfo.presentationTimeUs);
        }
        ptsOffset += videoPts;
        ptsOffset += 10000L;//test ，如果不添加，如何
        Log.i(TAG, "finish one file, ptsOffset " + ptsOffset);
    }


    private void startMuxer(MediaFormat mediaFormat) {
        Log.d(TAG, "startMuxer1: mediaFormat:" + mediaFormat.toString());
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
        Log.i(TAG, "stopExtractor: ");
        for (VideoExtractor extractor : mMediaExtractors) {
            extractor.release();
        }
    }

    private void openDecoder(int index, VideoDecoder decoder, final VideoExtractor videoExtractor,
                             final VideoEncoder videoEncoder, int outWidth, int outHeight, final long firstSampleTime, final long startTimeUs) {
        MediaFormat format = videoExtractor.getFormat();
        MediaBean mediaBean = mMediaBeans.get(index);
        decoder.open(mediaBean, mFilter, format, outWidth, outHeight, firstSampleTime, startTimeUs, new VideoDecoder.VideoDecodeCallBack() {
            @Override
            public int putInputData(ByteBuffer byteBuffer) {
                return videoExtractor.readSampleData(byteBuffer, 0);
            }

            @Override
            public long getPresentationTimeUs() {
                return videoExtractor.getSampleTime() - firstSampleTime - startTimeUs;
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
            if (!decoder.decode(now, beEndStream)) {
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
