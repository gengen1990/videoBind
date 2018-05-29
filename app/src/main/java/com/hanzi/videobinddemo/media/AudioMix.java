package com.hanzi.videobinddemo.media;

import android.media.MediaCodec;
import android.media.MediaFormat;
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
 */

public class AudioMix {
    private final static String TAG = "AudioMix";
    private String[] inPcmPaths = new String[2];
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

    private ByteBuffer mReadBuf;

    private int mOutAudioTrackIndex;
//    private FileOutputStream fos;
//    private BufferedOutputStream bos;

    private FinishListener finishListener;

    public int open(String[] inPcmPaths, String outAACPath, MediaFormat mediaFormat, int sampleRate, int channelCount, int maxInputSize) {
        this.inPcmPaths = inPcmPaths;
        this.outAACPath = outAACPath;
        this.sampleRate = sampleRate;
        this.mediaFormat = mediaFormat;
        byteContainer = new ByteContainer();

        mReadBuf = ByteBuffer.allocate(1048576);

        int mOutAudioTrackIndex = -1;

        for (int i = 0; i < inPcmPaths.length; i++) {
            inPcmFiles[i] = new File(inPcmPaths[i]);
        }
        Log.d(TAG, String.format("open inPcmPaths: %s outAACPath:%s sampleRate %d maxInputSize $d", inPcmPaths.toString(), outAACPath, sampleRate, maxInputSize));
        audioEncoder = new AudioEncoder();
        openEncoder(audioEncoder, sampleRate, channelCount, 8192);//maxInputSize ==500 * 1024??

        encodeHandlerThread = new HandlerThread("mixEncoder");
        encodeHandlerThread.start();
        encoderHandler = new Handler(encodeHandlerThread.getLooper());

        mediaFileMuxer = new MediaFileMuxer(outAACPath);
//        try {
//            fos = new FileOutputStream(new File(outAACPath));
//            bos = new BufferedOutputStream(fos, 8192);//500 * 1024
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }

        return 0;
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
            pcmMix(inPcmFiles, outAACPath, 1, 1, sampleRate);
            encoderHandler.post(audioMixEncodeInputRunnable);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int stop() {
        stopMuxer();
        audioEncoder.stop();
        return 0;
    }

    private Runnable audioMixEncodeInputRunnable = new Runnable() {
        @Override
        public void run() {
            isEncoding = true;
            while (true) {
                if (!byteContainer.isEmpty()) {
                    audioEncoder.encode(byteContainer.getData(), false);
                }else {
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
        int index = 0;
        for (int i = 0; i < audioFileStreams.length; i++) {
            if (availables[i] > max) {
                max = availables[i];
                index = i;
            }
        }
        int value = 0;

//        final boolean[] isStartEncode = {false};
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
            for (boolean streamEnd : streamDoneArray) {
                if (!streamEnd) {

                    done = false;
                }
            }
            Log.d(TAG, "pcmMix: done："+done);
            if (done) {
                isEncoding = false;
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

    private void openEncoder(AudioEncoder audioEncoder, int sampleRate, int channelCount, int maxInputSize) {
        audioEncoder.open("mixAudioEncoder","audio/mp4a-latm", sampleRate, channelCount, 96000, maxInputSize, new AudioEncoder.AudioEncoderCallBack() {
            @Override
            public void onInputBuffer() {

            }

            @Override
            public void onOutputBuffer(byte[] data, MediaCodec.BufferInfo bufferInfo) {


                ByteBuffer byteBuffer = ByteBuffer.allocate(data.length);
                byteBuffer.put(data);
                byteBuffer.flip();
////                byteBuffer.get(datas);
//                //添加头信息
//                int outBitSize = bufferInfo.size;
//                int outPacketSize = outBitSize + 7;//头文件长度为7
//                byteBuffer.position(bufferInfo.offset);
//                byteBuffer.limit(bufferInfo.offset + outBitSize);
//                byte[] chunkAudio = new byte[outPacketSize];
//                addADTStoPacket(chunkAudio, outPacketSize);
//                byteBuffer.get(chunkAudio, 7, outBitSize);
//                byteBuffer.position(bufferInfo.offset);
//
//                try {
//                    bos.write(chunkAudio, 0, chunkAudio.length);//BufferOutputStream 将文件保存到内存卡中 *.aac
//                    bos.flush();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                Log.d(TAG, "onOutputBuffer: ");
                mediaFileMuxer.writeSampleData(mOutAudioTrackIndex, byteBuffer, bufferInfo);
            }

            @Override
            public void encodeOver() {
                Log.d(TAG, "encodeOver: finish");
                if (finishListener != null)
                    finishListener.onFinish();
                stop();

//                try {
//                    fos.close();
//                    if (finishListener != null)
//                        finishListener.onFinish();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
            }
        });
        audioEncoder.start();
    }

    /**
     * 写入ADTS头部数据
     */
    public static void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int freqIdx = 4; // 44.1KHz
        int chanCfg = 2; // CPE

        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    public void setOnFinishListener(FinishListener finishListener) {
        this.finishListener = finishListener;
    }

    public interface FinishListener {
        public void onFinish();
    }
}
