package com.hanzi.videobinddemo.media.Utils.decoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by gengen on 2018/5/21.
 */

public class AudioDecoder {
    private final static String TAG = "AudioDecoder";

    final static int TIMEOUT_USEC = 0;

    private MediaCodec decoder;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private MediaCodec.BufferInfo outputInfo;
    private MediaCodec.BufferInfo inputInfo;

    private boolean mRunning = false;
    private boolean mBeEndOfStream = false;

    private Thread mOutputThread;

    private AudioDecodeCallBack audioDecodeCallBack;

    public int open(MediaFormat trackFormat, AudioDecodeCallBack audioDecodeCallBack) {
        try {
            decoder = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME));
            decoder.configure(trackFormat, null, null, 0);
            this.audioDecodeCallBack = audioDecodeCallBack;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int start() {
        decoder.start();
        inputBuffers = decoder.getInputBuffers();
        outputBuffers = decoder.getOutputBuffers();
        outputInfo = new MediaCodec.BufferInfo();
        inputInfo = new MediaCodec.BufferInfo();

        mOutputThread = new Thread(new CDecoderRunnable());
        mOutputThread.start();
        return 0;
    }

    public int stop() {
//        stopThread();
        if (decoder!=null) {
            decoder.stop();
            decoder.release();
            decoder = null;
        }
        return 0;
    }

    private void stopThread() {
//        mRunning = false;
        if (mOutputThread != null) {
            try {
                Log.d(TAG, "stopThread: before");
                mOutputThread.join();
                Log.d(TAG, "stopThread: after");
            } catch (Exception e) {
                Log.e(TAG, "output thread join failed:" + e);
            }
            mOutputThread = null;
        }

    }

    public int destory() {
        return 0;
    }

    public boolean decode(boolean beEndOfStream) {

        int index = decoder.dequeueInputBuffer(TIMEOUT_USEC);

        Log.d(TAG, "decode: dequeueInputBuffer index:" + index);
        mBeEndOfStream = beEndOfStream;
        if (index >= 0) {
            if (mBeEndOfStream) {
                Log.d(TAG, "decode: dequeueInputBuffer over");
                decoder.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return false;
            }

            ByteBuffer inputBuffer = inputBuffers[index];
            inputBuffer.clear();

            int size = 0;
            long presentationTimeUs = 0;
            if (audioDecodeCallBack != null) {
                size = audioDecodeCallBack.putInputData(inputBuffer);
                presentationTimeUs = audioDecodeCallBack.getPresentationTimeUs();
            }

            if (size >= 0) {
                inputInfo.offset = 0;
                inputInfo.size = size;
                inputInfo.flags = 0;
                inputInfo.presentationTimeUs = presentationTimeUs;
                decoder.queueInputBuffer(index, inputInfo.offset, inputInfo.size, inputInfo.presentationTimeUs, inputInfo.flags);
                if (audioDecodeCallBack != null)
                    audioDecodeCallBack.onInputBuffer();

                Log.d(TAG, String.format("decode: inputInfo: size %d presentationTimeUs %d ", size, presentationTimeUs));
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    private final class CDecoderRunnable implements Runnable {
        @Override
        public void run() {
            Log.i(TAG, "video hardware decoder output thread running");
            mRunning = true;

            int idx;
            byte[] chunkPCM;
            while (mRunning) {
                try {
                    idx = decoder.dequeueOutputBuffer(outputInfo, TIMEOUT_USEC);
                    Log.d(TAG, "run: idx:" + idx);
                    if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        /**没有可用的解码器output*/
                        Log.d(TAG, "run: INFO_TRY_AGAIN_LATER");
                    } else if (idx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        outputBuffers = decoder.getOutputBuffers();
                    } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.i(TAG, "decode output format changed:" + decoder.getOutputFormat().toString());
                    } else if (idx >= 0) {
//                        boolean canEncode = (outputInfo.size != 0 && outputInfo.presentationTimeUs - mFirstSampleTime > mStartTimeUs);
//                        boolean endOfStream = (outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

//                        if (canEncode && !endOfStream) {
                        ByteBuffer outputBuffer;
                        if (Build.VERSION.SDK_INT >= 21) {
                            outputBuffer = decoder.getOutputBuffer(idx);
                        } else {
                            outputBuffer = outputBuffers[idx];
                        }

                        chunkPCM = new byte[outputInfo.size];
                        outputBuffer.get(chunkPCM);
                        outputBuffer.clear();

                        Log.d(TAG, String.format("decode: output: idx %d chunkPCM.length %d ", idx, chunkPCM.length));
                        Log.d(TAG, "run: bufferinfo:" + outputInfo.presentationTimeUs);

                        //callback send the buffer
                        long pts= outputInfo.presentationTimeUs;
                        if (audioDecodeCallBack != null)
                            audioDecodeCallBack.onOutputBuffer(chunkPCM, pts);
//                        }

                        decoder.releaseOutputBuffer(idx, false);
                        if ((outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            audioDecodeCallBack.decodeOver();
                            mRunning = false;
                        }

                    }
                } catch (Exception e) {
                    Log.e(TAG, "dequeueOutputBuffer exception:" + e);
                    break;
                }
            }
            stop();

            Log.i(TAG, "audio hardware decoder output thread exit!");
        }
    }

    public interface AudioDecodeCallBack {

        int putInputData(ByteBuffer byteBuffer);

        long getPresentationTimeUs();

        void onInputBuffer();

        void onOutputBuffer(byte[] bytes,long presentationTimeUs);

        void decodeOver();
    }
}
