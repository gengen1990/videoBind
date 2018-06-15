package com.hanzi.videobinddemo.media.Utils.decoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import com.hanzi.videobinddemo.filter.AFilter;
import com.hanzi.videobinddemo.filter.BlendingFilter;
import com.hanzi.videobinddemo.media.Variable.MediaBean;
import com.hanzi.videobinddemo.media.surface.OutputSurface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by gengen on 2018/5/22.
 */

public class VideoDecoder {
    private final static String TAG = "VideoDecoder";

    private MediaCodec decoder;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private MediaCodec.BufferInfo outputInfo;
    private MediaCodec.BufferInfo inputInfo;

    private Thread mOutputThread;

    static ExecutorService executorService = Executors.newFixedThreadPool(4);

    private boolean mRunning = false;
    private boolean mBeEndOfStream = false;

    private VideoDecodeCallBack videoDecodeCallBack;

    private OutputSurface outputSurface;
    private AFilter mFilter;

    final static int TIMEOUT_USEC = 0;

    private long mFirstSampleTime;
    private long mStartTimeUs;

    public int open(MediaBean mediaBean, AFilter filter, MediaFormat trackFormat,
                    int width, int height, long firstSampleTime, long startTimeUs, VideoDecodeCallBack videoDecodeCallback) {
        try {
            decoder = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME));

            if (filter != null && filter instanceof BlendingFilter) {
                mFilter = (AFilter) filter.clone();
            }
            outputSurface = new OutputSurface(mediaBean, mFilter);

            decoder.configure(trackFormat, outputSurface.getSurface(), null, 0);
            this.mFirstSampleTime = firstSampleTime;
            this.mStartTimeUs = startTimeUs;
            this.videoDecodeCallBack = videoDecodeCallback;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int start(boolean outputsync) {
        Log.d(TAG, "start: ");
        decoder.start();
        inputBuffers = decoder.getInputBuffers();
        outputBuffers = decoder.getOutputBuffers();
        outputInfo = new MediaCodec.BufferInfo();
        inputInfo = new MediaCodec.BufferInfo();

        if (outputsync) {
            mOutputThread = new Thread(new CDecoderRunnable());
            mOutputThread.start();
        }
//        executorService.execute(new CDecoderRunnable());
        return 0;
    }

    public boolean decode(ByteBuffer byteBuffer, int sampleSize, long sampleTime) {
        int index = decoder.dequeueInputBuffer(TIMEOUT_USEC);
        if (index >= 0) {
            Log.d(TAG, "run: dequeueInputBuffer index:" + index);
            ByteBuffer inputBuffer = inputBuffers[index];
            inputBuffer.clear();
            inputBuffer.put(byteBuffer);

            if (sampleSize < 0) {
                decoder.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return false;
            } else {
                inputInfo.offset = 0;
                inputInfo.size = sampleSize;
                inputInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
                inputInfo.presentationTimeUs = sampleTime;
                decoder.queueInputBuffer(index, inputInfo.offset, sampleSize, inputInfo.presentationTimeUs, 0);
                if (videoDecodeCallBack != null)
                    videoDecodeCallBack.onInputBuffer();

                return true;
            }
        }
        return false;
    }

    public boolean decode(boolean beEndOfStream) {
        int index = decoder.dequeueInputBuffer(TIMEOUT_USEC);

        mBeEndOfStream = beEndOfStream;

        if (index >= 0) {
            Log.i(TAG, "decode: index:" + index);
            if (mBeEndOfStream) {
                Log.d(TAG, "decode: dequeueInputBuffer over");
                decoder.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return false;
            }

            ByteBuffer inputBuffer = inputBuffers[index];
            inputBuffer.clear();

            int size = 0;
            long presentationTimeUs = 0;
            if (videoDecodeCallBack != null) {
                size = videoDecodeCallBack.putInputData(inputBuffer);
                presentationTimeUs = videoDecodeCallBack.getPresentationTimeUs();
            }
            Log.d(TAG, String.format("decode: inputInfo: size %d presentationTimeUs %d ", size, presentationTimeUs));

            if (size >= 0) {
                inputInfo.offset = 0;
                inputInfo.size = size;
                inputInfo.flags = 0;
                inputInfo.presentationTimeUs = presentationTimeUs;
                decoder.queueInputBuffer(index, inputInfo.offset, inputInfo.size, inputInfo.presentationTimeUs, inputInfo.flags);
                if (videoDecodeCallBack != null)
                    videoDecodeCallBack.onInputBuffer();
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    public int stop() {
        if (outputSurface != null) {
            outputSurface.release();
        }
        if (decoder != null) {
            decoder.stop();
            decoder.release();
        }
        return 0;
    }

    public int destory() {
        return 0;
    }

    private final class CDecoderRunnable implements Runnable {
        @Override
        public void run() {
            Log.i(TAG, "video hardware decoder output thread running");
            mRunning = true;
            while (mRunning) {
                if (!decodeOutput()) {
                    mRunning = false;
                }
            }
            stop();
            Log.i(TAG, "video hardware decoder output thread exit!");
        }
    }

    public boolean decodeOutput() {
        int idx;
        try {
            Log.i(TAG, "decodeOutput: before");
            idx = decoder.dequeueOutputBuffer(outputInfo, TIMEOUT_USEC);
            Log.i(TAG, "run: dequeueOutputBuffer idx:" + idx);
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                /**没有可用的解码器output*/
//                        mRunning = false;
                Log.i(TAG, "run: INFO_TRY_AGAIN_LATER");
            } else if (idx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = decoder.getOutputBuffers();
            } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.i(TAG, "decode output format changed:" + decoder.getOutputFormat().toString());
            } else if (idx >= 0) {
                boolean doRender = (outputInfo.size != 0 && outputInfo.presentationTimeUs - mFirstSampleTime > mStartTimeUs);
                decoder.releaseOutputBuffer(idx, doRender);
                Log.d(TAG, "run: doRender:" + doRender);
                if (doRender) {
//                    if (videoDecodeCallBack != null)
//                        videoDecodeCallBack.onOutputMakeCurrent();

//                    outputSurface.makeCurrent();
                    outputSurface.awaitNewImage();
                    outputSurface.drawImage();

                    if (videoDecodeCallBack != null)
                        videoDecodeCallBack.onOutputBufferInfo(outputInfo);
//                    outputSurface.swapBuffers();
                }
                if ((outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    //callback over

//                    mRunning = false;

                    if (videoDecodeCallBack != null)
                        videoDecodeCallBack.decodeOver();
                    return false;
                }

                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "dequeueOutputBuffer exception:" + e);
            return true;
        }
        return true;
    }

    public interface VideoDecodeCallBack {

        int putInputData(ByteBuffer byteBuffer);

        long getPresentationTimeUs();

        void onInputBuffer();

        void onOutputBuffer(byte[] bytes);

        void onOutputMakeCurrent();

        void onOutputBufferInfo(MediaCodec.BufferInfo bufferInfo);

        void decodeOver();
    }
}
