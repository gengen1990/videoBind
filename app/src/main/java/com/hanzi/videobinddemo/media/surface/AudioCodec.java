package com.hanzi.videobinddemo.media.surface;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.hanzi.videobinddemo.AudioJniUtils;
import com.hanzi.videobinddemo.Constants;
import com.hanzi.videobinddemo.utils.MuxerUtils;
import com.hanzi.videobinddemo.vavi.sound.pcm.resampling.ssrc.SSRC;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Create by xjs
 * _______date : 18/3/26
 * _______description:音频相关操作类
 */
public class AudioCodec {
    private static final String TAG = AudioCodec.class.getSimpleName();
    private static ArrayList<byte[]> chunkPCMDataContainer;

    private static boolean isDecodeOver = false;

    private static Handler handler = new Handler(Looper.getMainLooper());

    /**
     * 混音从视频和音频获取pcm数据
     *
     * @param videoPathOne
     * @param audioPathTwo
     * @param outPath
     * @param listener
     */
    public static void audioMix(final Context context, String videoPathOne, final String audioPathTwo, final String outPath,
                                final AudioDecodeListener listener) {
        final boolean[] isDecoderOver = {false, false};
        final boolean[] isDecoderFailed = {false, false};

        final int[] Sample = {0, 0};


        final String pathSSRC = Constants.getPath("audio/", "pcmSSRC.pcm");
        final String path0 = Constants.getPath("audio/", "video.aac");

        final String path1 = Constants.getPath("audio/", "pcm1.pcm");
        final String path2 = Constants.getPath("audio/", "pcm2.pcm");
        AudioCodec.getAudioFromVideo(videoPathOne, path0, new AudioCodec.AudioDecodeListener() {
            @Override
            public void getSampleRate(int sample) {
            }

            @Override
            public void decodeOver() {
                final long[] mProgress = {0, 0};

                Log.d(TAG, "decodeOver: true");
                AudioCodec.getPCMFromAudio(context, path0, path1, new AudioDecodeListener() {
                    @Override
                    public void getSampleRate(int sample) {
                        Sample[0] = sample;
                    }

                    @Override
                    public void decodeOver() {
                        isDecoderOver[0] = true;

                    }

                    @Override
                    public void decodeFail() {
                        Log.d(TAG, "decodeFail: 1");
                        isDecoderFailed[0] = true;
                    }

                    @Override
                    public void onProgress(int progress) {
                        mProgress[0] = (long) (progress * 0.2);
                        listener.onProgress((int) (10 + mProgress[0] + mProgress[1]));
                        Log.d(TAG, "onProgress: getPCMFromAudio1:" + 10 + mProgress[0] +" "+ mProgress[1]);
                    }
                });

                //final String path2 = Environment.getExternalStorageDirectory().getAbsolutePath()+"/aa.pcm";

                AudioCodec.getPCMFromAudio(context, audioPathTwo, path2, new AudioDecodeListener() {
                    @Override
                    public void getSampleRate(int sample) {
                        Sample[1] = sample;
                    }

                    @Override
                    public void decodeOver() {
                        isDecoderOver[1] = true;
                    }

                    @Override
                    public void decodeFail() {
                        isDecoderFailed[1] = true;
                    }

                    @Override
                    public void onProgress(int progress) {
                        mProgress[1] = (long) (progress * 0.2);
                        listener.onProgress((int) (10 + mProgress[0] + mProgress[1]));
                        Log.d(TAG, "onProgress: getPCMFromAudio2:" + 10 + mProgress[0] + mProgress[1]);
                    }
                });
            }

            @Override
            public void decodeFail() {
                Log.d(TAG, "decodeFail: false");
                isDecoderFailed[0] = true;
            }

            @Override
            public void onProgress(int progress) {
                listener.onProgress((int) (progress * 0.1));
                Log.d(TAG, "onProgress: getAudioFromVideo:" + (int) (progress * 0.1));
            }
        });


        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean isEnd = false;
                while (!isEnd) {
                    Log.e(TAG, "isDecoderOver " + isDecoderOver[0] + "--" + isDecoderOver[1]);
                    if (isDecoderOver[0] && isDecoderOver[1]) {
                        ReSample reSample = new ReSample(Sample, path1, path2, pathSSRC).invoke();
                        File file1 = reSample.getFile1();
                        File file2 = reSample.getFile2();
                        int sample = reSample.getSample();

                        Log.d(TAG, "run: sample0:" + Sample[0]);
                        Log.d(TAG, "run: sample1:" + Sample[1]);
                        if (!file1.exists() || !file2.exists()) {
                            if (listener != null) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.d(TAG, "run: decodeFail");
                                        listener.decodeFail();
                                    }
                                });
                            }
                            break;
                        }
                        File[] files = {file1, file2};
                        listener.onProgress(60);
                        try {
                            AudioCodec.pcmMix(files, outPath, 1, 1, sample, new AudioDecodeListener() {
                                @Override
                                public void getSampleRate(int sample) {
                                }

                                @Override
                                public void decodeOver() {
                                    if (listener != null) {
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                Log.d(TAG, "pcmMix: decodeOver");
                                                listener.decodeOver();
                                            }
                                        });
                                    }
                                }

                                @Override
                                public void decodeFail() {
                                    if (listener != null) {
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                Log.d(TAG, "pcmMix: decodeFail2");
                                                listener.decodeFail();
                                            }
                                        });
                                    }
                                }

                                @Override
                                public void onProgress(int progress) {
                                    listener.onProgress((int) (60 + progress * 0.4));
                                    Log.d(TAG, "onProgress: pcmMix:" + (int) (60 + progress * 0.4));
                                }
                            });

                            break;
                        } catch (IOException e) {
                            e.printStackTrace();
                            if (listener != null) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.d(TAG, "run: decodeFail3");
                                        listener.decodeFail();
                                    }
                                });
                            }
                            break;
                        }
                    }
                    if (isDecoderFailed[0] || isDecoderFailed[1]) {
                        if (listener != null) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Log.d(TAG, "run: decodeFail4");
                                    listener.decodeFail();
                                }
                            });
                        }
                        isEnd = true;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    /**
     * 从视频文件中分离音频
     */
    public static void getAudioFromVideo(String videoPath, final String audioSavePath, final AudioDecodeListener listener) {
        final MediaExtractor extractor = new MediaExtractor();
        int audioTrack = -1;
        boolean hasAudio = false;
        try {
            extractor.setDataSource(videoPath);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat trackFormat = extractor.getTrackFormat(i);
                String mime = trackFormat.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioTrack = i;
                    hasAudio = true;
                    break;
                }
            }
            if (hasAudio) {
                extractor.selectTrack(audioTrack);
                final int finalAudioTrack = audioTrack;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            MediaMuxer mediaMuxer = new MediaMuxer(audioSavePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                            MediaFormat trackFormat = extractor.getTrackFormat(finalAudioTrack);
                            Long mDuration = trackFormat.getLong("durationUs");
                            int writeAudioIndex = mediaMuxer.addTrack(trackFormat);
                            mediaMuxer.start();
                            ByteBuffer byteBuffer = ByteBuffer.allocate(trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                            extractor.readSampleData(byteBuffer, 0);
                            if (extractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
                                extractor.advance();
                            }
                            while (true) {
                                int readSampleSize = extractor.readSampleData(byteBuffer, 0);
                                if (readSampleSize < 0) {
                                    break;
                                }
                                bufferInfo.size = readSampleSize;
                                bufferInfo.flags = extractor.getSampleFlags();
                                bufferInfo.offset = 0;
                                bufferInfo.presentationTimeUs = extractor.getSampleTime();

                                listener.onProgress((int) (100 * (float) bufferInfo.presentationTimeUs / mDuration));
                                mediaMuxer.writeSampleData(writeAudioIndex, byteBuffer, bufferInfo);
                                extractor.advance();//移动到下一帧
                            }
                            mediaMuxer.release();
                            extractor.release();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (listener != null) {
                                        listener.decodeOver();
                                    }
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (listener != null) {
                                        listener.decodeFail();
                                    }
                                }
                            });
                        }
                    }
                }).start();
            } else {
                if (listener != null) {
                    listener.decodeFail();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (listener != null) {
                listener.decodeFail();
            }
        }
    }

    /**
     * 将音频文件解码成原始的PCM数据
     */
    public static void getPCMFromAudio(Context context, String audioPath, String audioSavePath, final AudioDecodeListener listener) {
        MediaExtractor extractor = new MediaExtractor();
        int audioTrack = -1;
        boolean hasAudio = false;
        MuxerUtils.setDataSource(context, extractor, audioPath);
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat trackFormat = extractor.getTrackFormat(i);
            String mime = trackFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                audioTrack = i;
                hasAudio = true;
                break;
            }
        }
        if (hasAudio) {
            extractor.selectTrack(audioTrack);
            //原始音频解码
            new Thread(new AudioDecodeRunnable(extractor, audioTrack, audioSavePath, new DecodeOverListener() {
                @Override
                public void getSampleRate(int sample) {
                    if (listener != null) {
                        listener.getSampleRate(sample);
                    }
                }

                @Override
                public void decodeIsOver() {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.decodeOver();
                            }
                        }
                    });
                }

                @Override
                public void decodeFail() {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.decodeFail();
                            }
                        }
                    });
                }

                @Override
                public void onProgress(final int progress) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onProgress(progress);
                            }
                        }
                    });
                }
            })).start();

        } else {
            if (listener != null) {
                listener.decodeFail();
            }
        }
    }

    /**
     * 音频混合
     */
    public static void pcmMix(File[] rawAudioFiles, final String outFile, int firstVol,
                              int secondVol, final int sampleRate, final AudioDecodeListener listener) throws IOException {
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

        int[] availables=new int[audioFileStreams.length];
        for (int i = 0; i < audioFileStreams.length; i++) {
            availables[i] = audioFileStreams[i].available();
        }

        int max = 0;
        int index =0;
        for (int i=0;i<audioFileStreams.length;i++) {
            if (availables[i]>max) {
                max =availables[i];
                index = i;
            }
        }
        int value =0;

        final boolean[] isStartEncode = {false};
        while (true) {

            for (int streamIndex = 0; streamIndex < fileSize; ++streamIndex) {

                inputStream = audioFileStreams[streamIndex];
                if (!streamDoneArray[streamIndex] && (inputStream.read(buffer)) != -1) {
                    allAudioBytes[streamIndex] = Arrays.copyOf(buffer, buffer.length);
                    if (index== streamIndex) {
                        value = value+buffer.length;
                        int progress = (int) ((float)value/max *100);
                        if (progress>100) {
                            progress=100;
                        }
                        listener.onProgress(progress);
                    }
                } else {
                    streamDoneArray[streamIndex] = true;
                    allAudioBytes[streamIndex] = new byte[8 * 1024];
                }
            }

            byte[] mixBytes = nativeAudioMix(allAudioBytes, firstVol, secondVol);
            putPCMData(mixBytes);
            //mixBytes 就是混合后的数据
            if (!isStartEncode[0]) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        isStartEncode[0] = true;
                        try {
                            Log.e("-------->", "start encode thread.....");
                            Log.d(TAG, "run: PCM2AAC:" + sampleRate);

                            PCM2AAC(sampleRate, "audio/mp4a-latm", outFile);
                            if (listener != null) {
                                listener.decodeOver();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e("-------->", " encode error-----------error------");
                            if (listener != null) {
                                listener.decodeFail();
                            }
                        }
                    }
                }).start();
            }
            boolean done = true;
            for (boolean streamEnd : streamDoneArray) {
                if (!streamEnd) {
                    done = false;
                }
            }

            if (done) {
                isDecodeOver = true;
                break;
            }
        }

    }

    /**
     * 原始pcm数据，转aac音频
     */
    public static void PCM2AAC(int sampleRate, String encodeType, String outputFile) throws IOException {
        int inputIndex;
        ByteBuffer inputBuffer;
        int outputIndex;
        ByteBuffer outputBuffer;
        byte[] chunkAudio;
        int outBitSize;
        int outPacketSize;
        byte[] chunkPCM;
        //初始化编码器
        MediaFormat encodeFormat = MediaFormat.createAudioFormat(encodeType, 44100, 2);//mime type 采样率 声道数
        encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000);//比特率
        encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 500 * 1024);

        MediaCodec mediaEncode = MediaCodec.createEncoderByType(encodeType);
        mediaEncode.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaEncode.start();

        ByteBuffer[] encodeInputBuffers = mediaEncode.getInputBuffers();
        ByteBuffer[] encodeOutputBuffers = mediaEncode.getOutputBuffers();
        MediaCodec.BufferInfo encodeBufferInfo = new MediaCodec.BufferInfo();

        //初始化文件写入流
        FileOutputStream fos = new FileOutputStream(new File(outputFile));
        BufferedOutputStream bos = new BufferedOutputStream(fos, 500 * 1024);
        while (!chunkPCMDataContainer.isEmpty() || !isDecodeOver) {
            Log.d(TAG, "PCM2AAC: chunkPCMDataContainer:" + chunkPCMDataContainer.size());
            for (int i = 0; i < encodeInputBuffers.length - 1; i++) {
                chunkPCM = getPCMData();//获取解码器所在线程输出的数据 代码后边会贴上
                if (chunkPCM == null) {
                    break;
                }
                inputIndex = mediaEncode.dequeueInputBuffer(-1);
                inputBuffer = encodeInputBuffers[inputIndex];
                inputBuffer.clear();//同解码器
                inputBuffer.limit(chunkPCM.length);
                inputBuffer.put(chunkPCM);//PCM数据填充给inputBuffer
                mediaEncode.queueInputBuffer(inputIndex, 0, chunkPCM.length, 0, 0);//通知编码器 编码
            }

            outputIndex = mediaEncode.dequeueOutputBuffer(encodeBufferInfo, 10000);//同解码器
            while (outputIndex >= 0) {//同解码器

                outBitSize = encodeBufferInfo.size;
                outPacketSize = outBitSize + 7;//7为ADTS头部的大小
                outputBuffer = encodeOutputBuffers[outputIndex];//拿到输出Buffer
                outputBuffer.position(encodeBufferInfo.offset);
                outputBuffer.limit(encodeBufferInfo.offset + outBitSize);
                chunkAudio = new byte[outPacketSize];
                addADTStoPacket(chunkAudio, outPacketSize);//添加ADTS 代码后面会贴上
                outputBuffer.get(chunkAudio, 7, outBitSize);//将编码得到的AAC数据 取出到byte[]中 偏移量offset=7 你懂得
                outputBuffer.position(encodeBufferInfo.offset);
                try {
                    bos.write(chunkAudio, 0, chunkAudio.length);//BufferOutputStream 将文件保存到内存卡中 *.aac
                    bos.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                mediaEncode.releaseOutputBuffer(outputIndex, false);
                outputIndex = mediaEncode.dequeueOutputBuffer(encodeBufferInfo, 10000);
            }
        }
        mediaEncode.stop();
        mediaEncode.release();
        fos.close();
    }

    private static void putPCMData(byte[] pcmChunk) {
        synchronized (AudioCodec.class) {//记得加锁
            if (chunkPCMDataContainer == null) {
                chunkPCMDataContainer = new ArrayList<>();
            }
            chunkPCMDataContainer.add(pcmChunk);
        }
    }

    private static byte[] getPCMData() {
        synchronized (AudioCodec.class) {//记得加锁
            if (chunkPCMDataContainer.isEmpty()) {
                return null;
            }

            byte[] pcmChunk = chunkPCMDataContainer.get(0);//每次取出index 0 的数据
            chunkPCMDataContainer.remove(pcmChunk);//取出后将此数据remove掉 既能保证PCM数据块的取出顺序 又能及时释放内存
            return pcmChunk;
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

    interface DecodeOverListener {

        void getSampleRate(int sample);

        void decodeIsOver();

        void decodeFail();

        void onProgress(int progress);
    }

    public interface AudioDecodeListener {
        void getSampleRate(int sample);

        void decodeOver();

        void decodeFail();

        void onProgress(int progress);
    }

    private static class ReSample {
        private int[] sample;
        private String path1;
        private String pathSSRC;
        private String path2;
        private File file1;
        private File file2;
        private int mSample;

        public ReSample(int[] sample, String path1, String path2, String pathSSRC) {
            this.sample = sample;
            this.path1 = path1;
            this.pathSSRC = pathSSRC;
            this.path2 = path2;
        }

        public File getFile1() {
            return file1;
        }

        public File getFile2() {
            return file2;
        }

        public ReSample invoke() {
            file1 = null;
            file2 = null;

            if (sample[0] > sample[1]) {
                mSample = sample[1];
                FileInputStream fis;
                try {
                    fis = new FileInputStream(path1);
                    FileOutputStream fos = new FileOutputStream(pathSSRC);
                    new SSRC(fis, fos, sample[0], sample[1], 2, 2, 2, Integer.MAX_VALUE, 0, 0, true);
                    file1 = new File(pathSSRC);
                    file2 = new File(path2);
                    Log.d(TAG, "invoke: 1");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (sample[0] < sample[1]) {
                mSample = sample[0];
                FileInputStream fis;
                try {
                    fis = new FileInputStream(path2);
                    FileOutputStream fos = new FileOutputStream(pathSSRC);
                    new SSRC(fis, fos, sample[1], sample[0], 2, 2, 2, Integer.MAX_VALUE, 0, 0, true);
                    file1 = new File(path1);
                    file2 = new File(pathSSRC);
                    Log.d(TAG, "invoke: 2");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                mSample = sample[0];
                file1 = new File(path1);
                file2 = new File(path2);
                Log.d(TAG, "invoke: 3");
            }
            return this;
        }

        public int getSample() {
            return mSample;
        }
    }
}
