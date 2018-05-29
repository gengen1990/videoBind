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

    final static int TIMEOUT_USEC = -1;

    private MediaCodec decoder;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private MediaCodec.BufferInfo outputInfo;
    private MediaCodec.BufferInfo inputInfo;

    private boolean mRunning = false;

    private Thread mOutputThread;

    private AudioDecodeCallBack audioDecodeCallBack;

    public int open(MediaFormat trackFormat, AudioDecodeCallBack audioDecodeCallBack) {
//        MediaFormat trackFormat = extractor.getTrackFormat(audioTrack);
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
        decoder.stop();
        decoder.release();
        return 0;
    }

    public int destory() {
        return 0;
    }

    public boolean decode(ByteBuffer byteBuffer, int sampleSize, long sampleTime) {
        int index = decoder.dequeueInputBuffer(TIMEOUT_USEC);
        Log.d(TAG, "decode: dequeueInputBuffer index:"+index);
        Log.d(TAG, "decode: dequeueInputBuffer  sampleSize:"+sampleSize);

        if (index >= 0) {
            if (sampleSize < 0) {
                Log.d(TAG, "decode: dequeueInputBuffer over");
                decoder.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return false;
            }

            ByteBuffer inputBuffer = inputBuffers[index];
            inputBuffer.clear();
            inputBuffer.put(byteBuffer);

                inputInfo.offset = 0;
                inputInfo.size = sampleSize;
                inputInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
                inputInfo.presentationTimeUs = sampleTime;
                decoder.queueInputBuffer(index, inputInfo.offset, sampleSize, inputInfo.presentationTimeUs, 0);
                audioDecodeCallBack.onInputBuffer();
                Log.d(TAG, String.format("decode: inputInfo: size %d presentationTimeUs %d ",sampleSize, sampleTime));

                return true;
        }
        return true;
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
            byte[] chunkPCM;
            while (mRunning) {
                try {
                    idx = decoder.dequeueOutputBuffer(outputInfo, TIMEOUT_USEC);
                    Log.d(TAG, "run: idx:"+idx);
                    if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        /**没有可用的解码器output*/
                        Log.d(TAG, "run: INFO_TRY_AGAIN_LATER");
//                        mRunning = false;
                    } else if (idx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        outputBuffers = decoder.getOutputBuffers();
                    } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.i(TAG, "decode output format changed:" + decoder.getOutputFormat().toString());
                    } else if (idx >= 0) {
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
                        Log.d(TAG, "run: bufferinfo:"+outputInfo.presentationTimeUs);

                        //callback send the buffer
                        audioDecodeCallBack.onOutputBuffer(chunkPCM);

                        decoder.releaseOutputBuffer(idx, false);
                        if ((outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            //callback over
                            stop();
                            mRunning = false;
                            audioDecodeCallBack.decodeOver();

                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "dequeueOutputBuffer exception:" + e);
                    break;
                }
            }
            Log.i(TAG, "audio hardware decoder output thread exit!");
        }
    }

    public interface AudioDecodeCallBack {
        void onInputBuffer();

        void onOutputBuffer(byte[] bytes);

        void decodeOver();
    }
}
