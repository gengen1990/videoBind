package com.hanzi.videobinddemo.media.Utils.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import com.hanzi.videobinddemo.media.surface.InputSurface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by gengen on 2018/5/21.
 */

public class VideoEncoder {
    private final static String TAG = "VideoEncoder";
    private MediaCodec encoder;
    private InputSurface inputSurface;
    final int TIMEOUT_USEC = -1;

    ByteBuffer[] inputBuffers;
    ByteBuffer[] outputBuffers;
    MediaCodec.BufferInfo inputInfo;

    private Thread mOutputThread;

    private VideoEncoderCallBack videoEncoderCallback;

    private boolean mRunning = false;

    public int open(String encodeType, int width, int height, int frameRate,
                    VideoEncoderCallBack videoEncoderCallback) {
        Log.d(TAG, "open: frameRate:" + frameRate);
        Log.d(TAG, "open: width:" + width);
        Log.d(TAG, "open: height:" + height);
        try {
            encoder = MediaCodec.createEncoderByType(encodeType);

            MediaFormat mediaFormat = MediaFormat.createVideoFormat(encodeType, width, height);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 3000000);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
//            inputSurface.makeCurrent();
            this.videoEncoderCallback = videoEncoderCallback;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int start(boolean outputsync) {
        Log.d(TAG, "start: ");
        encoder.start();

        inputBuffers = encoder.getInputBuffers();
        outputBuffers = encoder.getOutputBuffers();
        inputInfo = new MediaCodec.BufferInfo();

        if(outputsync) {
            mOutputThread = new Thread(new CEncoderRunnable());
            mOutputThread.start();
        }
        return 0;
    }

    public boolean encoder(MediaCodec.BufferInfo info) {
//        inputSurface.makeCurrent();
        inputSurface.setPresentationTime(info.presentationTimeUs * 1000 );
        inputSurface.swapBuffers();
        Log.i(TAG, "encoder: swapBuffers after");
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.i(TAG, "encoder: signalEndOfInputStream");
            encoder.signalEndOfInputStream();
            return false;
        }
        return true;
    }

    public int stop() {
        stopEncoder();
//        stopThread();
        if (encoder != null) {
            encoder.stop();
            encoder.release();
        }
        return 0;
    }

    private void stopThread() {
        if (mOutputThread != null) {
            try {
                mOutputThread.join();
            } catch (Exception e) {
                Log.e(TAG, "output thread join failed:" + e);
            }
            mOutputThread = null;
        }

    }

    public int destory() {
        return 0;
    }

    public void signalEndOfInputStream() {
        Log.i(TAG, "encoder ok: signalEndOfInputStream");
        encoder.signalEndOfInputStream();
    }

    public void makeCurrent() {
        if (inputSurface!=null) {
            inputSurface.makeCurrent();
        }
    }

    private final class CEncoderRunnable implements Runnable {
        @Override
        public void run() {
            encodeOutput();
        }
    }

    public boolean encodeOutput() {
        Log.i(TAG, "video hardware encoder output thread running");
//            if (mRunning) {
//                Log.e(TAG, "video hardware decoder start again!");
//                return;
//            }
        mRunning = true;
        long lastStamp = -1;
        while (mRunning) {
            try {
                MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
                int index = encoder.dequeueOutputBuffer(outputInfo, TIMEOUT_USEC);
                Log.i(TAG, "run: index:" + index);
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                        mRunning = false;
//                        Log.d(TAG, "run: INFO_TRY_AGAIN_LATER");
                    continue;
                } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = encoder.getOutputBuffers();
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = encoder.getOutputFormat();
                    Log.d(TAG, "run: newFormat:" + newFormat.toString());
                    if (videoEncoderCallback != null) {
                        videoEncoderCallback.formatChanged();
                    }
                } else if (index < 0) {

                } else {
                    ByteBuffer outputData = outputBuffers[index];
                    boolean done = (outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (done) {
                        if (videoEncoderCallback != null)
                            videoEncoderCallback.encodeOver();
                        mRunning = false;
                        return false;
                    }
                    if (outputInfo.presentationTimeUs == 0 && !done) {
                        continue;
                    }

                    if (outputInfo.size != 0 && outputInfo.presentationTimeUs > 0) {
                        outputData.position(outputInfo.offset);
                        outputData.limit(outputInfo.offset + outputInfo.size);
                        if (outputInfo.presentationTimeUs > lastStamp) {//为了避免有问题的数据
                            Log.i(TAG, "run: presentationTimeUs :" + outputInfo.presentationTimeUs);
                            if (videoEncoderCallback != null)
                                videoEncoderCallback.onOutputBuffer(outputData, outputInfo);
                            lastStamp = outputInfo.presentationTimeUs;
                        }
                    }
                    encoder.releaseOutputBuffer(index, false);
                }
                return true;
            } catch (Exception e) {
                Log.e(TAG, "dequeueOutputBuffer exception:" + e);
                return false;
            }
        }
        Log.i(TAG, "video hardware decoder output thread exit!");
//            stopThread();
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

    public interface VideoEncoderCallBack {
        void onInputBuffer();

        void onOutputBuffer(ByteBuffer bytes, MediaCodec.BufferInfo bufferInfo);

        void encodeOver();

        void formatChanged();

    }
}
