package com.hanzi.videobinddemo.media.Utils.decoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import com.hanzi.videobinddemo.media.Utils.extractor.AudioExtractor;

import java.io.FileOutputStream;
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

    private long mFirstSampleTime, mStartTimeUs;


    private AudioDecodeCallBack audioDecodeCallBack;
    private FileOutputStream fos1;

    public int open(MediaFormat trackFormat,String pcmInFilePath, AudioDecodeCallBack audioDecodeCallBack) {
//        MediaFormat trackFormat = extractor.getTrackFormat(audioTrack);

        try {
            fos1= new FileOutputStream(pcmInFilePath);
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

            int sampleSize = audioDecodeCallBack.putInputData(inputBuffer);
            long sampleTime = audioDecodeCallBack.getPresentationTimeUs();

            inputInfo.offset = 0;
            inputInfo.size = sampleSize;
            inputInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
            inputInfo.presentationTimeUs = sampleTime;
            decoder.queueInputBuffer(index, inputInfo.offset, sampleSize, inputInfo.presentationTimeUs, 0);
            audioDecodeCallBack.onInputBuffer();
            Log.d(TAG, String.format("decode: inputInfo: size %d presentationTimeUs %d ", sampleSize, sampleTime));

            return true;
        }
        return true;
    }

    public boolean decode(AudioExtractor audioExtractor, long firstSampleTime, long durationUs, long startTimeUs) {
        mFirstSampleTime = firstSampleTime;
        mStartTimeUs = startTimeUs;
        int index = decoder.dequeueInputBuffer(TIMEOUT_USEC);
        if (index >= 0) {
            ByteBuffer inputBuffer = inputBuffers[index];
            inputBuffer.clear();
            int readSampleData = audioExtractor.readSampleData(inputBuffer, 0);
            long dur = audioExtractor.getSampleTime() - firstSampleTime - startTimeUs;
            Log.d(TAG, "startAudioCodec: dur:" + dur);
            if ((dur < durationUs) && readSampleData > 0) {
                decoder.queueInputBuffer(index, 0, readSampleData, audioExtractor.getSampleTime(), 0);
//                audioDecodeCallBack.onInputBuffer();
                audioExtractor.advance();
                return true;
            } else {
                decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return false;
            }
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
                    Log.d(TAG, "run: idx:" + idx);
                    if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        /**没有可用的解码器output*/
                        Log.d(TAG, "run: INFO_TRY_AGAIN_LATER");
//                        mRunning = false;
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

//                        audioDecodeCallBack.onOutputBuffer(chunkPCM);
//                        }
                        fos1.write(chunkPCM);
                        fos1.flush();

                        decoder.releaseOutputBuffer(idx, false);
                        if ((outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            //callback over
                            stop();
                            mRunning = false;
                        }

                    }
                } catch (Exception e) {
                    Log.e(TAG, "dequeueOutputBuffer exception:" + e);
                    break;
                }
            }
            try {
                fos1.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            audioDecodeCallBack.decodeOver();
            Log.i(TAG, "audio hardware decoder output thread exit!");
        }
    }

    public interface AudioDecodeCallBack {
        void onInputBuffer();

        void onOutputBuffer(byte[] bytes);

        void decodeOver();

        int putInputData(ByteBuffer byteBuffer);

        long getPresentationTimeUs();
    }
}
