package com.hanzi.videobinddemo.media;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;

import com.hanzi.videobinddemo.Constants;
import com.hanzi.videobinddemo.media.Utils.ByteContainer;
import com.hanzi.videobinddemo.media.Utils.MediaFileMuxer;
import com.hanzi.videobinddemo.media.Utils.ReSample;
import com.hanzi.videobinddemo.media.Utils.decoder.AudioDecoder;
import com.hanzi.videobinddemo.media.Utils.encoder.AudioEncoder;
import com.hanzi.videobinddemo.media.Utils.extractor.AudioExtractor;
import com.hanzi.videobinddemo.media.Variable.MediaBean;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC;

/**
 * Created by gengen on 2018/5/22.
 * 音频合成+添加效果
 */

public class AudioComposer {
    private String TAG = "AudioComposer";
    private List<MediaBean> mMediaBeans;
    private long mDuration;
    private List<AudioExtractor> mMediaExtractors = new ArrayList<>();
    private MediaFileMuxer mediaFileMuxer;
    private String outFilePath;

    private int outSampleRate;
    private int outChannelCount;

    private ByteBuffer mReadBuf;

    private int mOutAudioTrackIndex = -1;
    private long indexPtsOffset = 0L;

    private HandlerThread composerThread;
    private Handler composerHandler;

    private boolean isMix = false;

    private static String PATH = Environment.getExternalStorageDirectory().getAbsolutePath();


    private AudioComposerCallBack audioComposerCallBack;

    private AudioProgressCallBack audioProgressCallBack;

    /**
     * 重采样后，未编码的数据
     */
    private HashMap<Integer, ByteContainer> pcmContainer = new HashMap<>();

    /**
     * 重采样和编码后，需要传给合成器的数据
     */
    private HashMap<Integer, ByteContainer> resampleDataHashMap = new HashMap<>();

    /**
     * 重采样时，需要的编码器
     */
    private HashMap<Integer, Boolean> encoderOverIndex = new HashMap<>();

    /**
     * 重采样时，音频编码输出的pts 可能是间断性递减的，也可能是间断性递增的
     */
    private HashMap<Integer, Boolean> isPtsIncreaseIndex = new HashMap<>();

    /**
     * 重采样时，需要的解码器
     */
    private HashMap<Integer, Boolean> decoderOverIndex = new HashMap<>();

    /**
     * 表示 重采样 的 对象的状态
     * integer表示需要重采样的
     * boolean表示是否已经完成重采样
     */
    private HashMap<Integer, Boolean> resampleIndex = new HashMap<>();

    /**
     * 需要输出的pcm 地址
     */
    private HashMap<Integer, String> pcmPathHashMap = new HashMap<>();

    //计算 在重采样中需要
    private HashMap<Integer, Integer> noMixResampleRateIndex = new HashMap<>();
    private HashMap<Integer, Integer> mixResampleRateIndex = new HashMap<>();

    FileOutputStream mFos;
    BufferedOutputStream mBos;

    /**
     * judge all the audio's url is the same
     */
    private boolean isBgm;

    static ExecutorService executorService = Executors.newFixedThreadPool(4);

    String suffix = "audio";
    private int outMaxInputSize = 1000 * 1024;

    private MediaFormat mediaFormat;
    private boolean beStop = false;

    public AudioComposer(String tag, List<MediaBean> mediaBeans, long duration, String outFilePath, boolean isBgm) {
        this.mMediaBeans = mediaBeans;
        this.mDuration = duration;
        this.outFilePath = outFilePath;
        this.isBgm = isBgm;
        this.TAG = tag;

        mReadBuf = ByteBuffer.allocate(1048576);

        mediaFileMuxer = new MediaFileMuxer(outFilePath);

        for (MediaBean bean : mMediaBeans) {
            AudioExtractor extractor = new AudioExtractor(bean.getUrl(), bean.getStartTimeUs(), bean.getEndTimeUs());
            mMediaExtractors.add(extractor);
            mDuration += extractor.getCutDurationUs();
            if (outMaxInputSize > extractor.getMaxInputSize()) {
                outMaxInputSize = extractor.getMaxInputSize();
            }
        }

        Log.d(TAG, String.format("AudioComposer isBgm:%b, mDuration:%d,outMaxInputSize:%d", isBgm, mDuration, outMaxInputSize));

        composerThread = new HandlerThread("audioComposer");
        composerThread.start();
        composerHandler = new Handler(composerThread.getLooper());

        if (isBgm) {
            suffix = "bgm";
        } else {
            suffix = "audio";
        }

        try {
            mFos = new FileOutputStream(new File(outFilePath));
            mBos = new BufferedOutputStream(mFos, 500 * 1024);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    public void start(int outSampleRate, int channelCount, final boolean isMix) {
        this.outSampleRate = outSampleRate;
        this.outChannelCount = channelCount;

        this.isMix = isMix;

        if (audioProgressCallBack != null) {
            if (isMix) {
                audioProgressCallBack.onAudioType("音频拼接（混音）");
            } else {
                audioProgressCallBack.onAudioType("音频拼接（不混音）");
            }
        }

        Log.d(TAG, "start: outSampleRate:" + this.outSampleRate);
        Log.d(TAG, "start: channelCount:" + channelCount);
        startMuxer();
        composerHandler.post(new Runnable() {
            @Override
            public void run() {
                //进行重采样
                startResample();
                //判断是否重采样完成
                isResampleOk();
                if (!isMix) {
                    //开始合并
                    startMerge();
                } else {
                    if (beStop) {
                        return;
                    }
                    //如果是混音，将pcm 合并到一个文件，传出
                    if (pcmPathHashMap != null) {
                        ArrayList<String> pcmPaths = new ArrayList<>();

                        for (int i = 0; i < pcmPathHashMap.size(); i++) {
                            pcmPaths.add(pcmPathHashMap.get(i));
                        }
                        String outPCMPath = mergeFile(pcmPaths);
                        audioComposerCallBack.onPcmPath(outPCMPath);
                    }
                }
            }
        });
    }

    public void stop(boolean beStopThread) {
        Log.i(TAG, "stop: ");
        beStop = true;
        isResampleStopOk();
        if (beStopThread) {
            stopMergeThread();
        }
        stopMuxer();
        stopExtractor();
        try {
            mFos.close();
            mBos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopMergeThread() {
        if (composerThread != null) {
            composerThread.quitSafely();
            try {
                composerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            composerThread = null;
        }
        composerThread = null;
    }

    /**
     * 判断是否重采样中的编解码已经结束
     */
    private void isResampleStopOk() {
        boolean isResampleStop = false;
        while (!isResampleStop) {
            int i = 0;
            for (Integer key : encoderOverIndex.keySet()) {
                if (!encoderOverIndex.get(key)) {
                    break;
                }
                i++;
            }
            int j = 0;
            for (Integer key : decoderOverIndex.keySet()) {
                if (!decoderOverIndex.get(key)) {
                    break;
                }
                j++;
            }
            if (i == encoderOverIndex.size() && j == decoderOverIndex.size()) {
                isResampleStop = true;
            }
        }
    }

    /**
     * 判断是否重采样已经完成
     */
    private void isResampleOk() {
        boolean isResampleOk = false;
        while ((!isResampleOk) && (!beStop)) {
            int i = 0;
            for (Integer key : resampleIndex.keySet()) {
                if (!resampleIndex.get(key)) {
                    break;
                }
                i++;
            }
            if (i == resampleIndex.size()) {
                isResampleOk = true;
            }
        }
        Log.i(TAG, "isResampleOk: break");
    }

    /**
     * 开始拼接
     */
    private void startMerge() {
        if (beStop && mMediaExtractors == null || mMediaExtractors.size() == 0) {
            return;
        }
        int index = 0;
        //分别对需要进行 重采样的数据重新采样
        for (AudioExtractor audioExtractor : mMediaExtractors) {
            long firstSampleTime = audioExtractor.getSampleTime();
            long totalDurationUs = audioExtractor.getTotalDurationUs();
            long startTimeUs = audioExtractor.getStartTimeUs();
            long endTimeUs = audioExtractor.getEndTimeUs();
            long cutDurationUs = audioExtractor.getCutDurationUs();

            Log.i(TAG, String.format("startMerge: firstSampleTime:%d, startTimeUs:%d, endtime:%d, cutDurationUs:%d"
                    , firstSampleTime, startTimeUs, endTimeUs, cutDurationUs));

            if (resampleIndex.containsKey(index)) {
//                mergeByteBufferByMuxer(index, resampleDataHashMap.get(index));
                mergeByteBuffer(resampleDataHashMap.get(index));
            } else {
                mergeWithoutResample(audioExtractor, firstSampleTime, cutDurationUs, startTimeUs, endTimeUs);
            }

            index++;
        }
        stop(false);
        if (audioComposerCallBack != null)
            audioComposerCallBack.onFinishWithoutMix();
    }

    private void mergeByteBuffer(ByteContainer byteContainer) {

        if (byteContainer==null) {
            return;
        }
        byte[] chunkAudio;

        while (!byteContainer.isEmpty() && !beStop) {

            MediaCodec.BufferInfo bufferInfo = byteContainer.getBufferInfo();
            byte[] buffer = byteContainer.getData();

            int offset = bufferInfo.size+7;

            chunkAudio = new byte[offset];
            addADTStoPacket(chunkAudio, offset);

            System.arraycopy(buffer,0,chunkAudio,7,buffer.length);
            Log.i(TAG, "mergeByteBuffer: size:"+buffer.length);
            try {
                mBos.write(chunkAudio, 0, chunkAudio.length);
//                mBos.flush();
//                mBos.write(buffer, 0, buffer.length);
                mBos.flush();

            } catch (IOException e) {
                e.printStackTrace();
            }


        }


    }

    /**
     * 在拼接时，不需要重新解码+采样+编码
     *
     * @param audioExtractor
     */
    private void mergeWithoutResample(AudioExtractor audioExtractor, long firstSampleTime, long durationUs,
                                      long startTimeUs, long endTimeUs) {
        long presentationTimeUs = 0L;
        long audioPts = 0L;

        audioExtractor.seekTo(firstSampleTime + startTimeUs, SEEK_TO_PREVIOUS_SYNC);

        mReadBuf.rewind();
        boolean isRunning = true;
        while (isRunning && !beStop) {
            int chunkSize = audioExtractor.readSampleData(mReadBuf, 0);//读取帧数据

            long now = audioExtractor.getSampleTime() - firstSampleTime - startTimeUs;
            Log.i(TAG, "mergeWithoutResample: getSampleTime:" + audioExtractor.getSampleTime());
            Log.i(TAG, "mergeWithoutResample: now:" + now);
            if (chunkSize < 0 || (now >= durationUs || now == -1)) {
                isRunning = false;
            } else {
                presentationTimeUs = now;

                audioPts = presentationTimeUs;

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                info.offset = 0;
                info.size = chunkSize;
                info.presentationTimeUs = indexPtsOffset + presentationTimeUs;//
                if ((audioExtractor.getSampleFlags() & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                    info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                }
                mReadBuf.rewind();

                Log.i(TAG, String.format("write sample track %d, size %d, pts %d flag %d offset %offset",
                        mOutAudioTrackIndex, info.size, info.presentationTimeUs, info.flags, info.offset));
                if (info.size > 0 && presentationTimeUs > 0 && !beStop) {
                    mediaFileMuxer.writeSampleData(mOutAudioTrackIndex, mReadBuf, info);//写入文件
                    noMixMergeProgress(info);
                }
                if (!beStop)
                    audioExtractor.advance();
            }
        }
        indexPtsOffset += audioPts;
        indexPtsOffset += 10000L;//test ，如果不添加，如何
        Log.i(TAG, "finish one file, indexPtsOffset " + indexPtsOffset);
    }

    /**
     * 把之前 获取到的缓存  合成进mediaMuxer
     *
     * @param byteContainer
     */
    private void mergeByteBufferByMuxer(int index, ByteContainer byteContainer) {
        if (byteContainer == null) {
            return;
        }
        long increasePts = 0;
        long prePts = 0;

        long audioPts = 0;
        while (!byteContainer.isEmpty() && !beStop) {


            byte[] data = byteContainer.getData();
            ByteBuffer byteBuffer = ByteBuffer.allocate(data.length);
            MediaCodec.BufferInfo bufferInfo = byteContainer.getBufferInfo();
            byteBuffer.put(data);
            byteBuffer.flip();

            boolean isIncrease = isPtsIncreaseIndex.get(index);
//            if (isIncrease) {
//                if (bufferInfo.presentationTimeUs >= prePts) {
//                    prePts = bufferInfo.presentationTimeUs;
//                } else {
//                    increasePts += prePts;
//                    prePts = 0;
//                }
//            } else {
//                if (bufferInfo.presentationTimeUs>prePts) {
//                    increasePts+=bufferInfo.presentationTimeUs;
//                }
//                prePts=bufferInfo.presentationTimeUs;
//            }

            Log.i(TAG, "mergeByteBufferByMuxer: isIncrease:" + isIncrease);
            Log.i(TAG, "mergeByteBufferByMuxer: incresePts:" + increasePts);
//            Log.d(TAG, "mergeByteBufferByMuxer: bufferInfo.presentationTimeUs:" + bufferInfo.presentationTimeUs);
            bufferInfo.presentationTimeUs = indexPtsOffset + bufferInfo.presentationTimeUs + increasePts;

            Log.i(TAG, "mergeByteBufferByMuxer: bufferInfo.presentationTimeUs:" + bufferInfo.presentationTimeUs);

            audioPts = bufferInfo.presentationTimeUs;

            if (!beStop)
                mediaFileMuxer.writeSampleData(mOutAudioTrackIndex, byteBuffer, bufferInfo);
            noMixMergeProgress(bufferInfo);
        }
        indexPtsOffset += audioPts;
        indexPtsOffset += 10000L;//test ，如果不添加，如何
        Log.i(TAG, "finish one file, indexPtsOffset " + indexPtsOffset);
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

    /**
     * 多个文件放到一个文件中
     *
     * @param paths
     * @return
     */
    private String mergeFile(ArrayList<String> paths) {
        for (String path : paths) {
            Log.d(TAG, "mergeFile: path:" + path);
        }

        String outPcmPath = Constants.getPath("audio/", suffix + "outPcm" + ".pcm");
        try {
            File outFile = new File(outPcmPath);
            FileOutputStream fos = new FileOutputStream(outFile);

            for (int i = 0; i < pcmPathHashMap.size(); i++) {
                File inFile = new File(pcmPathHashMap.get(i));
                FileInputStream fis = new FileInputStream(inFile);
                byte[] tmpBytes = new byte[fis.available()];
                int length = tmpBytes.length;

                while (fis.read(tmpBytes) != -1) {
                    fos.write(tmpBytes, 0, length);
                }
                fos.flush();
                fis.close();
            }
            fos.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return outPcmPath;
    }

    /**
     * 获取 所有音频中最小的采样率
     *
     * @return
     */
    public int getMinSampleRate() {
        int sampleRate = 441000;
        if (!isBgm) {
            for (AudioExtractor extractor : mMediaExtractors) {
                if (sampleRate > extractor.getInitSampleRate()) {
                    sampleRate = extractor.getInitSampleRate();
                }
            }
        } else {
            if (mMediaExtractors.size() > 0)
                sampleRate = mMediaExtractors.get(0).getInitSampleRate();
        }
        return sampleRate;
    }

    /**
     * 根据采样率重新设置 format
     */
    private void startMuxer() {
        mediaFormat = mMediaExtractors.get(0).getFormat();
        if (mediaFormat != null) {
            mediaFormat.setInteger("sample-rate", outSampleRate);
            mediaFormat.setInteger("channel-count", outChannelCount);
        }

        mOutAudioTrackIndex = mediaFileMuxer.addTrack(mediaFormat);
        Log.d(TAG, "startMuxer: mediaFormat:" + mediaFormat.toString());
        Log.d(TAG, "startMuxer: mOutVideoTrackIndex:" + mOutAudioTrackIndex);
        mediaFileMuxer.start();
    }

    /**
     * 开始 重采样
     */
    private void startResample() {
        if (mMediaExtractors == null || mMediaExtractors.size() == 0) {
            return;
        }
        int index = 0;
        //分别对需要进行 重采样的数据重新采样

        for (AudioExtractor audioExtractor : mMediaExtractors) {
            if (beStop) {
                return;
            }
            long firstSampleTime = audioExtractor.getSampleTime();
            long totalDurationUs = audioExtractor.getTotalDurationUs();
            long startTimeUs = audioExtractor.getStartTimeUs();
            long endTimeUs = audioExtractor.getEndTimeUs();
            long cutDurationUs = audioExtractor.getCutDurationUs();

            Log.d(TAG, String.format("startResample index %d, firstSampleTime %d,cutDurationUs %d,startTimeUs %d ,endTimeUs %d"
                    , index, firstSampleTime, cutDurationUs, startTimeUs, endTimeUs));

            if (isMix || audioExtractor.isNeedToResample(outSampleRate)) {
                Log.d(TAG, "startResample resampleIndex index:" + index);
                resampleIndex.put(index, false);
                reSamplingOneAudio(index, audioExtractor, firstSampleTime, cutDurationUs, startTimeUs, endTimeUs);
            }
            index++;
        }

    }

    /**
     * 在拼接时，重新解码+重采样+编码
     *
     * @param audioExtractor
     */
    private void reSamplingOneAudio(int index, final AudioExtractor audioExtractor, long firstSampleTime, long durationUs,
                                    long startTimeUs, long endTimeUs) {

        audioExtractor.seekTo(firstSampleTime + startTimeUs, SEEK_TO_PREVIOUS_SYNC);

        AudioDecoder decoder = new AudioDecoder();
        openDecoder(index, decoder, audioExtractor);
        inputForDecode(audioExtractor, firstSampleTime, durationUs, startTimeUs, decoder);
    }

    /**
     * open decoder
     *
     * @param decoder
     * @param audioExtractor
     */
    @NonNull
    private void openDecoder(final int index, AudioDecoder decoder, final AudioExtractor audioExtractor) {

        final String mPcmInFilePath = Constants.getPath("audio/", suffix + "pcmSrc" + index + ".pcm");
        final String mPcmOutFilePath = Constants.getPath("audio/", suffix + "pcmDst" + index + ".pcm");
        try {
            final FileOutputStream fos = new FileOutputStream(mPcmInFilePath);
            MediaFormat format = audioExtractor.getFormat();
            decoder.open(format, new AudioDecoder.AudioDecodeCallBack() {
                @Override
                public void onInputBuffer() {
                    audioExtractor.advance();
                }

                @Override
                public void onOutputBuffer(byte[] bytes, long pts) {
                    try {
                        if (isMix) {
                            mixDecodeProgress(index, pts);
                        }
                        fos.write(bytes);
                        fos.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void decodeOver() {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    encodePcm(audioExtractor, mPcmInFilePath, mPcmOutFilePath, 44100, index);
                    decoderOverIndex.put(index, true);
                }

                @Override
                public int putInputData(ByteBuffer byteBuffer) {
                    return audioExtractor.readSampleData(byteBuffer, 0);
                }

                @Override
                public long getPresentationTimeUs() {
                    return audioExtractor.getSampleTime();
                }
            });
            decoderOverIndex.put(index, false);
            decoder.start();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * input to  decoder from Media Extractor
     *
     * @param audioExtractor
     * @param firstSampleTime
     * @param durationUs
     * @param startTimeUs
     * @param decoder
     */
    private void inputForDecode(AudioExtractor audioExtractor, long firstSampleTime, long durationUs,
                                long startTimeUs, AudioDecoder decoder) {
        boolean isRunning = true;
        while (isRunning) {
            long now;
            if (!beStop) {
                now = audioExtractor.getSampleTime() - firstSampleTime - startTimeUs;
            } else {
                now = -1;
            }
            boolean beEndOfStream = (now >= durationUs || now == -1);
            if (!decoder.decode(now, beEndOfStream)) {
                isRunning = false;
            }
        }
    }

    /**
     * 从已经进行重采样的文件中获取数据
     */
    private void getOutputPCMData(int index, int maxInputSize, String outputFilePath) {
        try {
            byte[] buffer = new byte[maxInputSize];
            File file = new File(outputFilePath);
            FileInputStream fileInputStream = new FileInputStream(file);
            while (fileInputStream.read(buffer) != -1) {
                pcmContainer.get(index).putData(buffer);
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "getOutputPCMData: FileNotFoundException");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "getOutputPCMData: FileNotFoundException");
        }
    }

    /**
     * 重采样
     */
    private boolean resample(int inputSampleRate, int outSampleRate, String inputFilePath, String outputFilePath) {
        if (inputSampleRate == outSampleRate) {
            return false;
        }
        ReSample reSample = new ReSample(inputSampleRate, outSampleRate, inputFilePath, outputFilePath);
        reSample.invoke();
        return true;
    }

    public void encodePcm(final AudioExtractor audioExtractor, final String mPcmInFilePath,
                          final String mPcmOutFilePath, final int outSampleRate, final int index) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                int inputSampleRate = audioExtractor.getInitSampleRate();
                int channelCount = audioExtractor.getChannelCount();
                int maxInputSize = audioExtractor.getMaxInputSize();
                long cutDurationUs = audioExtractor.getCutDurationUs();
                Log.d(TAG, String.format("decodeOver " +
                                "index %d, inputFilePath %s, outputFilePath %s,inputSampleRate %d,outSampleRate %d ,channelCount %d, maxInputSize %d"
                        , index, mPcmInFilePath, mPcmOutFilePath, inputSampleRate
                        , outSampleRate, channelCount, maxInputSize
                ));

                final boolean isOk = resample(inputSampleRate, outSampleRate, mPcmInFilePath, mPcmOutFilePath);
                Log.d(TAG, "run: isOk:" + isOk);

                String path;
                if (isOk) {
                    path = mPcmOutFilePath;
                } else {
                    path = mPcmInFilePath;
                }
                if (!isMix) {
                    //如果非混音，直接 编码输出
                    pcmContainer.put(index, new ByteContainer()); //= new ByteContainer();
//                                    new Thread(new Runnable() {
//                                        @Override
//                                        public void run() {
                    getOutputPCMData(index, maxInputSize, path);
//                                        }
//                                    }).start();
                    AudioEncoder audioEncoder = new AudioEncoder();


                    openEncoder(index, audioEncoder, outSampleRate, channelCount, maxInputSize, cutDurationUs);

                    inputForEncoder(index, audioEncoder);
                } else {
                    //如果混音，先放到某个文件中
                    pcmPathHashMap.put(index, path);
                    resampleIndex.put(index, true);
                }
            }
        });
    }

    /**
     * 开启音频编码器
     *
     * @param index
     * @param audioEncoder
     * @param sampleRate
     * @param channelCount
     * @param maxInputSize
     */
    private void openEncoder(final int index, final AudioEncoder audioEncoder, int sampleRate, int channelCount, int maxInputSize, final long durationUs) {
        audioEncoder.open(TAG + "Encoder", "audio/mp4a-latm", sampleRate, channelCount, 96000, maxInputSize, new AudioEncoder.AudioEncoderCallBack() {
            @Override
            public void onInputBuffer() {
            }

            @Override
            public void onOutputBuffer(byte[] data, MediaCodec.BufferInfo bufferInfo) {
                if (!resampleDataHashMap.containsKey(index)) {

                    ByteContainer byteContainer = new ByteContainer();
                    resampleDataHashMap.put(index, byteContainer);
                }
                resampleDataHashMap.get(index).putData(data);
                resampleDataHashMap.get(index).putBufferInfo(bufferInfo);
                Log.i(TAG, "onOutputBuffer: putBufferInfo:" + bufferInfo.presentationTimeUs);
                noMixResampleProgress(bufferInfo.presentationTimeUs, index);
            }

            @Override
            public void encodeOver(boolean isIncrease) {
                isPtsIncreaseIndex.put(index, isIncrease);
                resampleIndex.put(index, true);
                audioEncoder.stop();
                encoderOverIndex.put(index, true);

            }

            @Override
            public void setFormat(MediaFormat newFormat) {

            }
        });
        encoderOverIndex.put(index, false);
        audioEncoder.start();
    }

    private void inputForEncoder(int index, AudioEncoder audioEncoder) {
        while (true) {
            if (pcmContainer.get(index) != null && !pcmContainer.get(index).isStarted()) {
                continue;
            }

            if (!pcmContainer.get(index).isEmpty() && (!beStop)) {
                byte[] chunkPcm = pcmContainer.get(index).getData();
                audioEncoder.encode(chunkPcm, false);
            } else {
                audioEncoder.encode(null, true);
                break;
            }
        }
    }

    private void noMixMergeProgress(MediaCodec.BufferInfo bufferInfo) {
        if (audioProgressCallBack != null) {
            int RATE, exRate;
            if (resampleIndex.size() == 0) {
                RATE = MediaBind.NO_AUDIOMIX_MERGE_RATE + MediaBind.NO_AUDIOMIX_RESAMPLE_RATE;
                exRate = 0;
            } else {
                RATE = MediaBind.NO_AUDIOMIX_MERGE_RATE;
                exRate = MediaBind.NO_AUDIOMIX_RESAMPLE_RATE;
            }

            float rate = (float) bufferInfo.presentationTimeUs / mDuration;
            if (rate > 1) {
                rate = 1;
            }
            Log.i(TAG, "noMixMergeProgress: rate:" + rate);
            Log.i(TAG, "noMixMergeProgress: merge:" + (exRate + RATE * rate));
            audioProgressCallBack.onProgress((int) (exRate + RATE * rate));
        }
    }

    private void mixDecodeProgress(int index, long pts) {
        if (audioProgressCallBack != null) {
            mixResampleRateIndex.put(index, (int) pts);
            int RATE;

            RATE = MediaBind.AUDIOMIX_RESAMPLE_RATE;
            long sum = 0;
            for (Integer integer : mixResampleRateIndex.keySet()) {
                int t = mixResampleRateIndex.get(integer);
                sum += t;
            }
            Log.i(TAG, "onOutputBuffer: sum:" + sum);
            Log.i(TAG, "onOutputBuffer: duration:" + mDuration);

            float rate = (float) sum / mDuration;
            if (rate > 1) {
                rate = 1;
            }
            audioProgressCallBack.onProgress((int) (RATE * rate));
        }
    }

    /**
     * 回调重采样进度
     *
     * @param pts
     * @param index
     */
    private void noMixResampleProgress(long pts, int index) {
        Log.d(TAG, "noMixResampleProgress: pts:" + pts);
        if (audioProgressCallBack != null) {
            noMixResampleRateIndex.put(index, (int) pts);
            int sum = 0;
            for (Integer integer : noMixResampleRateIndex.keySet()) {
                if (noMixResampleRateIndex.containsKey(integer))
                    sum += noMixResampleRateIndex.get(integer);
            }
            Log.i(TAG, "onOutputBuffer: sum:" + sum);
            Log.i(TAG, "onOutputBuffer: duration:" + mDuration);

            float rate = ((float) sum / mDuration);
            if (rate > 1) {
                rate = 1;
            }
            audioProgressCallBack.onProgress((int) (MediaBind.NO_AUDIOMIX_RESAMPLE_RATE * rate));
        }
    }


    /**
     * 停止合成
     */
    private void stopMuxer() {
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

    /**
     * 关闭所有的音频抽取器
     */
    private void stopExtractor() {
        for (AudioExtractor extractor : mMediaExtractors) {
            extractor.release();
        }
    }

    public void setAudioComposerCallBack(AudioComposerCallBack audioComposerCallBack) {
        this.audioComposerCallBack = audioComposerCallBack;
    }

    public void setAudioProgressCallBack(AudioProgressCallBack audioProgressCallBack) {
        this.audioProgressCallBack = audioProgressCallBack;
    }

    public int getChannelCount() {
        return outChannelCount;
    }

    public int getMaxInputSize() {
        return outMaxInputSize;
    }

    public long getDurationUs() {
        return mDuration;
    }

    public MediaFormat getFormat() {
        return mediaFormat;
    }

    public interface AudioComposerCallBack {
        void onPcmPath(String path);

        void onFinishWithoutMix();
    }

    public interface AudioProgressCallBack {
        void onAudioType(String content);

        void onProgress(int rate);
    }

}

