package com.hanzi.videobinddemo.media;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.hanzi.videobinddemo.AudioJniUtils;
import com.hanzi.videobinddemo.media.Utils.ByteContainer;
import com.hanzi.videobinddemo.media.Utils.MediaFileMuxer;
import com.hanzi.videobinddemo.media.Utils.encoder.AudioEncoder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by gengen on 2018/5/25.
 * 音频 混合器
 */

public class AudioMix {
    private final static String TAG = "AudioMix";
    //    private String[] inPcmPaths = new String[2];
    private File[] inPcmFiles = new File[2];
    private String outAACPath = "";
    private boolean isEncoding = false;

    private ByteContainer byteContainer;
    private AudioEncoder audioEncoder;

    private int sampleRate = 44100;

    private MediaFileMuxer mediaFileMuxer;

    private HandlerThread encodeHandlerThread;
    private Handler encoderHandler;
    private MediaFormat mediaFormat;

    private AudioMixCallBack audioMixCallBack;

    private static String PATH = Environment.getExternalStorageDirectory().getAbsolutePath();
    private int mOutAudioTrackIndex;

    private boolean beStop = false;

    private long mDurationUs = 0;

    private boolean beStopOver = false;

    private FinishListener finishListener;

    public int open(String[] inPcmPaths, String outAACPath, MediaFormat mediaFormat, int sampleRate, int channelCount, int maxInputSize, long duration) {
        this.outAACPath = outAACPath;
        this.sampleRate = sampleRate;
        this.mediaFormat = mediaFormat;
        this.mDurationUs = duration;
        byteContainer = new ByteContainer();

        for (int i = 0; i < inPcmPaths.length; i++) {
            inPcmFiles[i] = new File(inPcmPaths[i]);
        }
        Log.d(TAG, String.format("open inPcmPaths: %s outAACPath:%s sampleRate %d maxInputSize $d",
                inPcmPaths.toString(), outAACPath, sampleRate, maxInputSize));

        audioEncoder = new AudioEncoder();
        openEncoder(audioEncoder, sampleRate, channelCount, maxInputSize);//maxInputSize ==500 * 1024??

        encodeHandlerThread = new HandlerThread("mixEncoder");
        encodeHandlerThread.start();
        encoderHandler = new Handler(encodeHandlerThread.getLooper());
        mediaFileMuxer = new MediaFileMuxer(outAACPath);
        return 0;
    }

    private void openEncoder(AudioEncoder audioEncoder, int sampleRate, int channelCount, int maxInputSize) {
        audioEncoder.open("mixAudioEncoder", "audio/mp4a-latm", sampleRate, channelCount, 96000, maxInputSize, new AudioEncoder.AudioEncoderCallBack() {
            @Override
            public void onInputBuffer() {

            }

            @Override
            public void onOutputBuffer(byte[] data, MediaCodec.BufferInfo bufferInfo) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(data.length);
                byteBuffer.put(data);
                byteBuffer.flip();

                mediaFileMuxer.writeSampleData(mOutAudioTrackIndex, byteBuffer, bufferInfo);

                if (audioMixCallBack != null) {
                    float rate = (float) bufferInfo.presentationTimeUs / mDurationUs;
                    if (rate > 1) {
                        rate = 1;
                    }
                    audioMixCallBack.onProgress((int) (MediaBind.AUDIOMIX_MIX_RATE
                            + rate * MediaBind.AUDIOMIX_MERGE_RATE));
                }
            }

            @Override
            public void encodeOver() {
                if (finishListener != null)
                    finishListener.onFinish();
                beStopOver = true;
                stop();
            }
        });
        audioEncoder.start();
    }


    /**
     * 根据采样率重新设置 format
     */
    private void startMuxer() {
        mOutAudioTrackIndex = mediaFileMuxer.addTrack(mediaFormat);
        Log.d(TAG, "startMuxer: mediaFormat:" + mediaFormat.toString());
        Log.d(TAG, "startMuxer: mOutVideoTrackIndex:" + mOutAudioTrackIndex);
        mediaFileMuxer.start();
    }

    private void stopMuxer() {
        Log.d(TAG, "stopMuxer: ");
        if (mediaFileMuxer != null) {
            try {
                mediaFileMuxer.stop();
                mediaFileMuxer.release();
            } catch (Exception e) {
                Log.e(TAG, "Muxer close error. No data was written");
            }
            mediaFileMuxer = null;
        }
    }

    public int start() {
        try {
            startMuxer();
            String mixPath = PATH + "/1/audioMixWithNoMuxer.aac";
            pcmMix(inPcmFiles, mixPath, 1, 1, sampleRate);
            encoderHandler.post(audioMixEncodeInputRunnable);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int stop() {
        beStop = true;
        while (true) {
            if (beStopOver) {
                stopMuxer();
                break;
            }
        }
        return 0;
    }

    private Runnable audioMixEncodeInputRunnable = new Runnable() {
        @Override
        public void run() {
            while (true) {
                if (!byteContainer.isEmpty() && (!beStop)) {
                    audioEncoder.encode(byteContainer.getData(), false);
                } else {
                    audioEncoder.encode(null, true);
                    break;
                }
            }
        }
    };

    /**
     * 音频混合
     */
    private void pcmMix(File[] rawAudioFiles, final String outFile, int firstVol,
                        int secondVol, final int sampleRate) throws IOException {
        File file = new File(outFile);
        if (file.exists()) {
            file.delete();
        }

        final int fileSize = rawAudioFiles.length;
        FileInputStream[] audioFileStreams = new FileInputStream[fileSize];
        File audioFile = null;

        FileInputStream inputStream;
        byte[][] allAudioBytes = new byte[fileSize][];
        boolean[] streamDoneArray = new boolean[fileSize];
        byte[] buffer = new byte[8 * 1024];


        for (int fileIndex = 0; fileIndex < fileSize; ++fileIndex) {
            audioFile = rawAudioFiles[fileIndex];
            audioFileStreams[fileIndex] = new FileInputStream(audioFile);
        }

        int[] availables = new int[audioFileStreams.length];
        for (int i = 0; i < audioFileStreams.length; i++) {
            availables[i] = audioFileStreams[i].available();
        }

        int max = 0;
        for (int i = 0; i < audioFileStreams.length; i++) {
            if (availables[i] > max) {
                max = availables[i];
            }
        }

        while (true) {
            for (int streamIndex = 0; streamIndex < fileSize; ++streamIndex) {
                inputStream = audioFileStreams[streamIndex];
                if (!streamDoneArray[streamIndex] && (inputStream.read(buffer)) != -1) {
                    allAudioBytes[streamIndex] = Arrays.copyOf(buffer, buffer.length);
                } else {
                    streamDoneArray[streamIndex] = true;
                    allAudioBytes[streamIndex] = new byte[8 * 1024];
                }
            }

            byte[] mixBytes = nativeAudioMix(allAudioBytes, firstVol, secondVol);

            byteContainer.putData(mixBytes);
            boolean done = true;
            int isOk = 0;
            for (boolean streamEnd : streamDoneArray) {

                if (!streamEnd) {

                    done = false;
                } else {
                    isOk++;
                }
                if (audioMixCallBack != null) {
                    audioMixCallBack.onProgress((int) ((float) isOk / streamDoneArray.length * MediaBind.AUDIOMIX_MIX_RATE));
                }
            }
            Log.d(TAG, "pcmMix: done：" + done);
            if (done) {
                Log.d(TAG, "pcmMix: isEncoding false");
                break;
            }
        }

    }

    /**
     * 音频的混音,归一算法
     */
    public static byte[] nativeAudioMix(byte[][] allAudioBytes, float firstVol, float secondVol) {
        if (allAudioBytes == null || allAudioBytes.length == 0)
            return null;

        byte[] realMixAudio = allAudioBytes[0];

        //如果只有一个音频的话，就返回这个音频数据
        if (allAudioBytes.length == 1)
            return realMixAudio;

        return AudioJniUtils.audioMix(allAudioBytes[0], allAudioBytes[1], realMixAudio, firstVol, secondVol);
    }

    public void setOnFinishListener(FinishListener finishListener) {
        this.finishListener = finishListener;
    }

    public void setAudioMixCallBack(AudioMixCallBack audioMixCallBack) {
        this.audioMixCallBack = audioMixCallBack;
    }

    public interface FinishListener {
        public void onFinish();
    }

    public interface AudioMixCallBack {

        void onProgress(int rate);
    }
}
