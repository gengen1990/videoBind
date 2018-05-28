package com.hanzi.videobinddemo.media.Utils.decoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import com.hanzi.videobinddemo.filter.AFilter;
import com.hanzi.videobinddemo.media.Variable.MediaBean;
import com.hanzi.videobinddemo.media.surface.OutputSurface;

import java.io.IOException;
import java.nio.ByteBuffer;

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

    private boolean mRunning = false;

    private VideoDecodeCallBack videoDecodeCallBack;

    private OutputSurface outputSurface;

    final static int TIMEOUT_USEC = 0;

    private long mFirstSampleTime;
    private long mStartTimeUs;

    public int open(MediaBean mediaBean, AFilter filter, MediaFormat trackFormat,
                    long firstSampleTime, long startTimeUs, VideoDecodeCallBack videoDecodeCallback) {
        try {
            decoder = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME));
            outputSurface = new OutputSurface(mediaBean, filter);
            decoder.configure(trackFormat, outputSurface.getSurface(), null, 0);
            this.mFirstSampleTime = firstSampleTime;
            this.mStartTimeUs = startTimeUs;
            this.videoDecodeCallBack = videoDecodeCallback;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int start() {
        decoder.start();
        inputBuffers = decoder.getInputBuffers();
        outputBuffers = decoder.getOutputBuffers();
        inputInfo = new MediaCodec.BufferInfo();

        mOutputThread = new Thread(new CDecoderRunnable());
        mOutputThread.start();
        return 0;
    }

    public boolean decode(ByteBuffer byteBuffer, int sampleSize, long sampleTime) {
        int index = decoder.dequeueInputBuffer(TIMEOUT_USEC);
        if (index >= 0) {
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
                videoDecodeCallBack.onInputBuffer();
                return true;
            }
        }
        return false;
    }

    public int stop() {
        if (outputSurface != null) {
            outputSurface.release();
        }
        decoder.stop();
        decoder.release();
        return 0;
    }

    public int destory() {
        return 0;
    }

    private final class CDecoderRunnable implements Runnable {
        @Override
        public void run() {
            Log.i(TAG, "video hardware decoder output thread running");
            if (mRunning) {
                Log.e(TAG, "video hardware decoder start again!");
                return;
            }
            mRunning = true;

            int idx;
            byte[] chunkPCM;
            while (mRunning) {
                try {
                    outputInfo = new MediaCodec.BufferInfo();
                    idx = decoder.dequeueOutputBuffer(outputInfo, TIMEOUT_USEC);
                    if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        /**没有可用的解码器output*/
                        mRunning = false;
                    } else if (idx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        outputBuffers = decoder.getOutputBuffers();
                    } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.i(TAG, "decode output format changed:" + decoder.getOutputFormat().toString());
                    } else if (idx >= 0) {
                        boolean doRender = (outputInfo.size != 0 && outputInfo.presentationTimeUs - mFirstSampleTime > mStartTimeUs);
                        decoder.releaseOutputBuffer(idx, doRender);
                        if (doRender) {
                            // This waits for the image and renders it after it arrives.
                            outputSurface.awaitNewImage();

                            outputSurface.drawImage();
                            // Send it to the encoder.
                            videoDecodeCallBack.onOutputBufferInfo(outputInfo);

                        }
                        if ((outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            //callback over
                            stop();
                            mRunning = false;
                            videoDecodeCallBack.decodeOver();

                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "dequeueOutputBuffer exception:" + e);
                    break;
                }
            }
            Log.i(TAG, "video hardware decoder output thread exit!");
        }
    }

    public interface VideoDecodeCallBack {
        void onInputBuffer();

        void onOutputBuffer(byte[] bytes);

        void onOutputBufferInfo(MediaCodec.BufferInfo bufferInfo);

        void decodeOver();
    }
}
