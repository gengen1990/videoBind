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

    private Thread mOutputThread;

    private long mCnt = 0;

    private AudioEncoderCallBack audioEncoderCallback;

    private boolean mRunning = false;

    public int open(String tag, String encodeType, int sampleRate, int channelCount, int bitRate, int inputSize, AudioEncoderCallBack audioEncoderCallback) {
        try {
            MediaFormat encodeFormat = MediaFormat.createAudioFormat(encodeType, sampleRate, channelCount);//mime type 采样率 声道数
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);//比特率
            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);

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

        Log.d(TAG, "encode: ");
        int index = encoder.dequeueInputBuffer(TIMEOUT_USEC);

        Log.d(TAG, "encode: index:" + index);

        if (index >= 0) {
            ByteBuffer inputBuffer = inputBuffers[index];
            inputBuffer.clear();

            if (endOfStream) {
                Log.d(TAG, "encode: endOfStream over:" + index);
                encoder.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return false;
            }

//            if (chunkPCM == null) {
//                Log.d(TAG, "encode: null");
////                length = chunkPCM.length;
//                return true;
//            }
//            Log.d(TAG, String.format("AudioEncoder encode" +
//                    "chunkPCM length %d, endOfStream %b", length, endOfStream));

//                if (inputInfo.size < 4096) {//这里看起来应该是16位单声道转16位双声道
//                    //说明是单声道的,需要转换一下
//                    byte[] stereoBytes = new byte[inputInfo.size * 2];
//                    for (int i = 0; i < inputInfo.size; i += 2) {
//                        stereoBytes[i * 2 + 0] = chunkPCM[i];
//                        stereoBytes[i * 2 + 1] = chunkPCM[i + 1];
//                        stereoBytes[i * 2 + 2] = chunkPCM[i];
//                        stereoBytes[i * 2 + 3] = chunkPCM[i + 1];
//                    }
//                    inputBuffer.put(stereoBytes);
//                    encoder.queueInputBuffer(index, 0, stereoBytes.length, inputInfo.presentationTimeUs, 0);
//
//                    Log.d(TAG, String.format("inputInfo.size < 4096"));
//
//                } else {

            inputBuffer.position(0).limit(chunkPCM.length);
            inputBuffer.put(chunkPCM);
            inputBuffer.flip();
            long pts = 1000000 * mCnt++ / 20;
            encoder.queueInputBuffer(index, 0, chunkPCM.length, pts, 0);
            inputBuffer.clear();
//                }
            audioEncoderCallback.onInputBuffer();
            return true;
        }
        return true;
    }

    public int stop() {
        stopEncoder();
        stopThread();
        if (encoder != null) {
            encoder.stop();
            encoder.release();
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
//            if (mRunning) {
//                Log.e(TAG, "video hardware decoder start again!");
//                return;
//            }
            mRunning = true;
            long lastStamp = -1;
            while (mRunning) {
                try {

                    MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
                    int index = encoder.dequeueOutputBuffer(outputInfo, 1000);
                    Log.d(TAG, "dequeueOutputBuffer: index:"+index);

                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                        mRunning = false;
                        Log.d(TAG, "run: INFO_TRY_AGAIN_LATER");
                    } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        outputBuffers = encoder.getOutputBuffers();
                        Log.d(TAG, "run: INFO_OUTPUT_BUFFERS_CHANGED");
                    } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = encoder.getOutputFormat();
                        Log.d(TAG, "run: newFormat:" + newFormat.toString());
                    } else if (index < 0) {
                        Log.d(TAG, "run: index:"+index);
                    } else {

                        ByteBuffer outputData = outputBuffers[index];
                        boolean done = (outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        if (done) {
                            Log.d(TAG, "run: BUFFER_FLAG_END_OF_STREAM:" +  done);
                            mRunning = false;
                            if (audioEncoderCallback!=null)
                                audioEncoderCallback.encodeOver();
                        }


                        Log.d(TAG, "run: presentationTimeUs:" + outputInfo.presentationTimeUs);
//                        if (outputInfo.presentationTimeUs == 0 && !done) {
//                            Log.d(TAG, "run: CEncoderRunnable:continue");
//                            continue;
//                        }
                        Log.d(TAG, "run: outputInfo.size:"+outputInfo.size);

                        if (outputInfo.size != 0 && outputInfo.presentationTimeUs > 0) {
                            Log.d(TAG, "run: outputData.limit():" + outputData.limit());
//                            Log.d(TAG, String.format("output: index %d size %d presentationTimeUs:%d", index, outputInfo.size, outputInfo.presentationTimeUs));
//                            if (outputInfo.presentationTimeUs > lastStamp) {//为了避免有问题的数据
                            byte[] data = new byte[outputData.limit()];
                            outputData.get(data);
                            if (audioEncoderCallback != null)
                                audioEncoderCallback.onOutputBuffer(data, outputInfo);
                            lastStamp = outputInfo.presentationTimeUs;
                            outputData.clear();
//                            }
                        }
                        encoder.releaseOutputBuffer(index, false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "dequeueOutputBuffer exception:" + e);
                    break;
                }
            }
//            stopThread();

            Log.d(TAG, "video hardware decoder output thread exit!");
        }
    }

    public interface AudioEncoderCallBack {
        void onInputBuffer();

        void onOutputBuffer(byte[] bytes, MediaCodec.BufferInfo bufferInfo);

        void encodeOver();
    }
}
