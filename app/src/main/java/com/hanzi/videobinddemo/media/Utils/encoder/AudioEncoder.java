package com.hanzi.videobinddemo.media.Utils.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by gengen on 2018/5/21.
 */

public class AudioEncoder {
    private String TAG = "AudioEncoder";
    private MediaCodec encoder;
    final int TIMEOUT_USEC = -1;

    ByteBuffer[] inputBuffers;
    ByteBuffer[] outputBuffers;
//    MediaCodec.BufferInfo inputInfo;
//    private MediaCodec.BufferInfo outputInfo;

    long apts = 0;
    long offsetPts = 0;

    private Thread mOutputThread;

    private boolean mEndOfStream = false;

    private long mCnt = 0;

    private AudioEncoderCallBack audioEncoderCallback;

    private boolean mRunning = false;

    public int open(String tag, String encodeType, int sampleRate, int channelCount, int bitRate, int inputSize, AudioEncoderCallBack audioEncoderCallback) {
        try {

            MediaFormat encodeFormat = MediaFormat.createAudioFormat(encodeType, sampleRate, channelCount);//mime type 采样率 声道数
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);//比特率
            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, inputSize);

            encoder = MediaCodec.createEncoderByType(encodeType);
            encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            this.audioEncoderCallback = audioEncoderCallback;

            Log.d(TAG, String.format("AudioEncoder open" +
                            "sampleRate %d, channelCount %s, bitRate %s,inputSize %d"
                    , sampleRate, channelCount, bitRate, inputSize));

            this.TAG = tag;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int start() {
        encoder.start();

        inputBuffers = encoder.getInputBuffers();
        outputBuffers = encoder.getOutputBuffers();
//        inputInfo = new MediaCodec.BufferInfo();

        mOutputThread = new Thread(new CEncoderRunnable());
        mOutputThread.start();
        return 0;
    }

    public boolean encode(byte[] chunkPCM, boolean endOfStream) {
        if (endOfStream) {
            mEndOfStream = endOfStream;
        }
        int index = encoder.dequeueInputBuffer(TIMEOUT_USEC);
        Log.d(TAG, "encode: index:" + index);

        if (index >= 0) {
            ByteBuffer inputBuffer = inputBuffers[index];
            inputBuffer.clear();

            if (mEndOfStream) {
                Log.d(TAG, "encode: endOfStream over:" + index);
                encoder.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return false;
            }

            inputBuffer.position(0).limit(chunkPCM.length);
            inputBuffer.put(chunkPCM);
            inputBuffer.flip();
            long pts = 0;
//            long pts = 1000000 * mCnt++ / 50;
            Log.i(TAG, "encode: pts:" + pts);
            encoder.queueInputBuffer(index, 0, chunkPCM.length, pts, 0);
            inputBuffer.clear();
            audioEncoderCallback.onInputBuffer();
            return true;
        }
        return true;
    }

    public int stop() {
//        stopEncoder();
//        stopThread();
        if (encoder != null) {
            Log.i(TAG, "stop: test");
            encoder.stop();
            encoder.release();
            encoder = null;
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

    private int stopEncoder() {
        try {
            int idx = encoder.dequeueInputBuffer(-1);

            encoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        } catch (IllegalStateException e) {
            Log.e(TAG, "dequeueOutputBuffer exception:" + e);
        }
        return 0;
    }

    public int destory() {
        return 0;
    }

    private final class CEncoderRunnable implements Runnable {
        @Override
        public void run() {
            Log.i(TAG, "video hardware decoder output thread running");
            mRunning = true;
            long lastStamp = -1;

            while (mRunning) {
                try {
                    MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
                    int index = encoder.dequeueOutputBuffer(outputInfo, TIMEOUT_USEC);
                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        Log.d(TAG, "run: INFO_TRY_AGAIN_LATER");
                    } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        outputBuffers = encoder.getOutputBuffers();
                        Log.d(TAG, "run: INFO_OUTPUT_BUFFERS_CHANGED");
                    } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = encoder.getOutputFormat();
                        audioEncoderCallback.setFormat(newFormat);
                        Log.d(TAG, "run: newFormat:" + newFormat.toString());
                    } else if (index < 0) {
                        Log.d(TAG, "run: index:" + index);
                    } else {
                        ByteBuffer outputData = outputBuffers[index];
                        boolean done = (outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        if (done) {
                            Log.d(TAG, "run: BUFFER_FLAG_END_OF_STREAM:" + done);
                            mRunning = false;
                            if (audioEncoderCallback != null)
                                audioEncoderCallback.encodeOver();
                        }

                        Log.d(TAG, "run: presentationTimeUs:" + outputInfo.presentationTimeUs);
                        Log.d(TAG, "run: outputInfo.size:" + outputInfo.size);

                        if (outputInfo.presentationTimeUs >= apts) {
                            apts = outputInfo.presentationTimeUs;
                        } else  {
                            offsetPts += apts;
                            apts = 0;
                        }

                        outputInfo.presentationTimeUs=outputInfo.presentationTimeUs+offsetPts;

                        if (outputInfo.size != 0 && outputInfo.presentationTimeUs > 0) {
                            Log.d(TAG, String.format("output: index %d size %d presentationTimeUs:%d", index, outputInfo.size, outputInfo.presentationTimeUs));
                            byte[] data = new byte[outputData.limit()];
                            outputData.get(data);

                            if (audioEncoderCallback != null)
                                audioEncoderCallback.onOutputBuffer(data, outputInfo);
                            outputData.clear();
                        }
                        Log.i(TAG, "run: releaseOutputBuffer");
                        encoder.releaseOutputBuffer(index, false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "dequeueOutputBuffer exception:" + e);
                    break;
                }
            }
            stop();
            Log.d(TAG, "audio hardware decoder output thread exit!");
        }
    }

    public interface AudioEncoderCallBack {
        void onInputBuffer();

        void onOutputBuffer(byte[] bytes, MediaCodec.BufferInfo bufferInfo);

        void encodeOver();

        void setFormat(MediaFormat newFormat);
    }
}
