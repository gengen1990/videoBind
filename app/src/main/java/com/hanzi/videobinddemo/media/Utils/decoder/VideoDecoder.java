package com.hanzi.videobinddemo.media.Utils.decoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import com.hanzi.videobinddemo.filter.AFilter;
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

    final static int TIMEOUT_USEC = -1;

    private long mFirstSampleTime;
    private long mStartTimeUs;

    public int open(MediaBean mediaBean, AFilter filter, MediaFormat trackFormat,
                    long firstSampleTime, long startTimeUs, VideoDecodeCallBack videoDecodeCallback) {
        try {

            Log.i(TAG, "open: mFirstSampleTime:"+firstSampleTime);
            Log.i(TAG, "open: startTimeUs:"+startTimeUs);
            decoder = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME));

            if (filter!=null) {
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

    public int start() {
        Log.d(TAG, "start: ");
        decoder.start();
        inputBuffers = decoder.getInputBuffers();
        outputBuffers = decoder.getOutputBuffers();
        inputInfo = new MediaCodec.BufferInfo();

        mOutputThread = new Thread(new CDecoderRunnable());
        mOutputThread.start();
//        executorService.execute(new CDecoderRunnable());
        return 0;
    }

    public boolean decode(ByteBuffer byteBuffer, int sampleSize, long sampleTime) {
        int index = decoder.dequeueInputBuffer(TIMEOUT_USEC);
        if (index >= 0) {
            Log.d(TAG, "run: dequeueInputBuffer index:"+index);
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
                if (videoDecodeCallBack!=null)
                videoDecodeCallBack.onInputBuffer();

                return true;
            }
        }
        return false;
    }

    public boolean decode(boolean beEndOfStream) {
        int index = decoder.dequeueInputBuffer(TIMEOUT_USEC);
        mBeEndOfStream = beEndOfStream;

        if (index>=0) {
            Log.i(TAG, "decode: index:"+index);
            if (mBeEndOfStream) {
                Log.d(TAG, "decode: dequeueInputBuffer over");
                decoder.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return false;
            }

            ByteBuffer inputBuffer = inputBuffers[index];
            inputBuffer.clear();

            int size = 0;
            long presentationTimeUs = 0;
            if (videoDecodeCallBack!=null) {
                size= videoDecodeCallBack.putInputData(inputBuffer);
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
        if (decoder!=null) {
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
//            if (mRunning) {
//                Log.e(TAG, "video hardware decoder start again!");
//                return;
//            }
            mRunning = true;

            int idx;
            while (mRunning) {
                try {
                    outputInfo = new MediaCodec.BufferInfo();
                    idx = decoder.dequeueOutputBuffer(outputInfo, TIMEOUT_USEC);
                    Log.i(TAG, "run: dequeueOutputBuffer idx:"+idx);
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
                        Log.d(TAG, "run: doRender:"+doRender);
                        if (doRender) {
                            // This waits for the image and renders it after it arrives.
                            Log.d(TAG, "run: awaitNewImage before");
                            outputSurface.awaitNewImage();

                            Log.d(TAG, "run: drawImage before");
                            outputSurface.drawImage();
                            // Send it to the encoder.

                            Log.d(TAG, "run: outputBuffers.toString():"+outputBuffers.toString());
                            if (videoDecodeCallBack!=null)
                            videoDecodeCallBack.onOutputBufferInfo(outputInfo);

                        }
                        if ((outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            //callback over

                            mRunning = false;
                            if (videoDecodeCallBack!=null)
                            videoDecodeCallBack.decodeOver();

                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "dequeueOutputBuffer exception:" + e);
                    break;
                }
            }
            stop();
            Log.i(TAG, "video hardware decoder output thread exit!");
        }
    }

    public interface VideoDecodeCallBack {

        int putInputData(ByteBuffer byteBuffer);

        long getPresentationTimeUs();

        void onInputBuffer();

        void onOutputBuffer(byte[] bytes);

        void onOutputBufferInfo(MediaCodec.BufferInfo bufferInfo);

        void decodeOver();
    }
}
