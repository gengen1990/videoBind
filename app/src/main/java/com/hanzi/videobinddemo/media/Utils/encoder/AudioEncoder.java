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
    private final static String TAG = "AudioEncoder";
    private MediaCodec encoder;
    final int TIMEOUT_USEC = 0;

    ByteBuffer[] inputBuffers;
    ByteBuffer[] encodeOutputBuffers;
    MediaCodec.BufferInfo inputInfo;
//    private MediaCodec.BufferInfo outputInfo;

    private Thread mOutputThread;

    private AudioEncoderCallBack audioEncoderCallback;

    private boolean mRunning = false;

    public int open(String encodeType, int sampleRate, int channelCount, int bitRate, int inputSize, AudioEncoderCallBack audioEncoderCallback) {
        try {
            MediaFormat encodeFormat = MediaFormat.createAudioFormat(encodeType, sampleRate, channelCount);//mime type 采样率 声道数
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);//比特率
            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, inputSize);

            encoder = MediaCodec.createEncoderByType(encodeType);
            encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            this.audioEncoderCallback = audioEncoderCallback;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int start() {
        encoder.start();

        inputBuffers = encoder.getInputBuffers();
        encodeOutputBuffers = encoder.getOutputBuffers();
        inputInfo = new MediaCodec.BufferInfo();

        mOutputThread = new Thread(new CEncoderRunnable());
        mOutputThread.start();
        return 0;
    }

    public boolean encode(byte[] chunkPCM, boolean endOfStream) {
        int index = encoder.dequeueInputBuffer(TIMEOUT_USEC);

        if (index >= 0) {
            ByteBuffer inputBuffer = inputBuffers[index];
            inputBuffer.clear();

            if (endOfStream) {
                encoder.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return false;
            } else {
                if (inputInfo.size < 4096) {//这里看起来应该是16位单声道转16位双声道
                    //说明是单声道的,需要转换一下
                    byte[] stereoBytes = new byte[inputInfo.size * 2];
                    for (int i = 0; i < inputInfo.size; i += 2) {
                        stereoBytes[i * 2 + 0] = chunkPCM[i];
                        stereoBytes[i * 2 + 1] = chunkPCM[i + 1];
                        stereoBytes[i * 2 + 2] = chunkPCM[i];
                        stereoBytes[i * 2 + 3] = chunkPCM[i + 1];
                    }
                    inputBuffer.put(stereoBytes);
                    encoder.queueInputBuffer(index, 0, stereoBytes.length, inputInfo.presentationTimeUs, 0);
                } else {
                    inputBuffer.put(chunkPCM);
                    encoder.queueInputBuffer(index, inputInfo.offset, inputInfo.size, inputInfo.presentationTimeUs, 0);
                }
                audioEncoderCallback.onInputBuffer();
                return true;
            }
        }
        return false;
    }

    public int stop() {
        encoder.stop();
        encoder.release();
        return 0;
    }

    public int destory() {
        return 0;
    }

    private final class CEncoderRunnable implements Runnable {
        @Override
        public void run() {
            Log.i(TAG, "video hardware decoder output thread running");
            if (mRunning) {
                Log.e(TAG, "video hardware decoder start again!");
                return;
            }
            mRunning = true;
            long lastStamp = -1;
            while (mRunning) {
                try {
                    MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
                    int index = encoder.dequeueOutputBuffer(outputInfo, TIMEOUT_USEC);
                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        mRunning = false;
                    } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        encodeOutputBuffers = encoder.getOutputBuffers();
                    } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = encoder.getOutputFormat();
                        Log.d(TAG, "run: newFormat:" + newFormat.toString());
                    } else if (index < 0) {

                    } else {
                        ByteBuffer outputData = inputBuffers[index];
                        boolean done = (outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        if (done) {
                            mRunning = false;
                            stop();
                            audioEncoderCallback.encodeOver();
                        }
                        if (outputInfo.presentationTimeUs == 0 && !done) {
                            continue;
                        }

                        if (outputInfo.size != 0 && outputInfo.presentationTimeUs > 0) {
                            if (outputInfo.presentationTimeUs > lastStamp) {//为了避免有问题的数据
                                audioEncoderCallback.onOutputBuffer(outputData, outputInfo);
                                lastStamp = outputInfo.presentationTimeUs;
                            }
                        }
                        encoder.releaseOutputBuffer(index, false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "dequeueOutputBuffer exception:" + e);
                    break;
                }
            }
            Log.i(TAG, "video hardware decoder output thread exit!");
        }
    }

    public interface AudioEncoderCallBack {
        void onInputBuffer();

        void onOutputBuffer(ByteBuffer bytes, MediaCodec.BufferInfo bufferInfo);

        void encodeOver();
    }
}
