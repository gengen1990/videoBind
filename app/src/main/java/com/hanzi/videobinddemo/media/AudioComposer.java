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
 * 音频合成
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
    private long ptsOffset = 0L;

    private HandlerThread resamplerThread;
    private HandlerThread mergeThread;
    private HandlerThread audioEncoderThread;

    private Handler resamplerHandler;
    private Handler mergeHandler;
    private Handler audioEncoderHandler;

    private boolean beStop = false;

    private boolean isMix = false;

    private static String PATH = Environment.getExternalStorageDirectory().getAbsolutePath();


    private AudioComposerCallBack audioComposerCallBack;

    private HashMap<Integer, ByteContainer> pcmContainer = new HashMap<>();


    private HashMap<Integer, ByteContainer> resampleDataHashMap = new HashMap<>();
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

    /**
     * judge all the audio's url is the same
     */
    private boolean isBgm;

    static ExecutorService executorService = Executors.newFixedThreadPool(4);

    String suffix = "audio";
    private int outMaxInputSize = 1000 * 1024;

    private MediaFormat mediaFormat;
    private boolean bool = true;

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
            mDuration += extractor.getDurationUs();
            if (outMaxInputSize > extractor.getMaxInputSize()) {
                outMaxInputSize = extractor.getMaxInputSize();
            }
        }

        Log.d(TAG, String.format("AudioComposer isBgm:%b, mDuration:%d,outMaxInputSize:%d", isBgm, mDuration, outMaxInputSize));

        mergeThread = new HandlerThread("mergeWithoutResample");
        mergeThread.start();
        mergeHandler = new Handler(mergeThread.getLooper());

        if (isBgm) {
            suffix = "bgm";
        } else {
            suffix = "audio";
        }
    }

    public void start(int outSampleRate, int channelCount, final boolean isMix) {
        this.outSampleRate = outSampleRate;
        this.outChannelCount = channelCount;

        this.isMix = isMix;
        Log.d(TAG, "start: outSampleRate:" + this.outSampleRate);
        Log.d(TAG, "start: channelCount:" + channelCount);
        startMuxer();
        mergeHandler.post(new Runnable() {
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

    public void stop() {
        beStop = true;
        stopMuxer();
        stopExtractor();
    }

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
    }

    /**
     * 开始拼接
     */
    private void startMerge() {
        if (mMediaExtractors == null || mMediaExtractors.size() == 0) {
            return;
        }
        int index = 0;
        //分别对需要进行 重采样的数据重新采样
        for (AudioExtractor audioExtractor : mMediaExtractors) {
            long firstSampleTime = audioExtractor.getSampleTime();
            long durationUs = audioExtractor.getDurationUs();
            long startTimeUs = audioExtractor.getStartTimeUs();
            long endTimeUs = audioExtractor.getEndTimeUs();
            if (endTimeUs != -1 && endTimeUs < durationUs) {
                durationUs = endTimeUs - startTimeUs;
            }

            Log.i(TAG, String.format("startMerge: firstSampleTime:%d, startTimeUs:%d, endtime:%d, durationUs:%d"
                    , firstSampleTime, startTimeUs, endTimeUs, durationUs));

            if (resampleIndex.containsKey(index)) {
                mergeByteBuffer(resampleDataHashMap.get(index));
            } else {
                mergeWithoutResample(audioExtractor, firstSampleTime, durationUs, startTimeUs, endTimeUs);
            }

            index++;
        }
        stop();
        if (audioComposerCallBack != null)
            audioComposerCallBack.onFinishWithoutMix();
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
        while (isRunning) {
            int chunkSize = audioExtractor.readSampleData(mReadBuf, 0);//读取帧数据
            long now = audioExtractor.getSampleTime() - firstSampleTime - startTimeUs;

            if (chunkSize < 0 || (now >= durationUs || now == -1)) {
                isRunning = false;
            } else {
                presentationTimeUs = audioExtractor.getSampleTime();

                audioPts = presentationTimeUs;

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                info.offset = 0;
                info.size = chunkSize;
                info.presentationTimeUs = ptsOffset + presentationTimeUs;//
                if ((audioExtractor.getSampleFlags() & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                    info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                }
                mReadBuf.rewind();

                Log.i(TAG, String.format("write sample track %d, size %d, pts %d flag %d offset %offset",
                        mOutAudioTrackIndex, info.size, info.presentationTimeUs, info.flags, info.offset));
                if (info.size > 0 && info.presentationTimeUs > 0) {
                    mediaFileMuxer.writeSampleData(mOutAudioTrackIndex, mReadBuf, info);//写入文件
                }
                audioExtractor.advance();
            }
        }
        ptsOffset += audioPts;
        ptsOffset += 10000L;//test ，如果不添加，如何
        Log.i(TAG, "finish one file, ptsOffset " + ptsOffset);
    }

    /**
     * 把之前 获取到的缓存  合成进mediaMuxer
     *
     * @param byteContainer
     */
    private void mergeByteBuffer(ByteContainer byteContainer) {
        if (byteContainer == null) {
            return;
        }

        long audioPts = 0;
        while (!byteContainer.isEmpty()) {
            byte[] data = byteContainer.getData();
            ByteBuffer byteBuffer = ByteBuffer.allocate(data.length);
            MediaCodec.BufferInfo bufferInfo = byteContainer.getBufferInfo();
            Log.d(TAG, "mergeByteBuffer: bufferInfo.presentationTimeUs:" + bufferInfo.presentationTimeUs);
            audioPts = bufferInfo.presentationTimeUs;
            byteBuffer.put(data);
            byteBuffer.flip();
            bufferInfo.presentationTimeUs = ptsOffset + bufferInfo.presentationTimeUs;
            mediaFileMuxer.writeSampleData(mOutAudioTrackIndex, byteBuffer, bufferInfo);
        }
        ptsOffset += audioPts;
        ptsOffset += 10000L;//test ，如果不添加，如何
        Log.i(TAG, "finish one file, ptsOffset " + ptsOffset);
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
            long firstSampleTime = audioExtractor.getSampleTime();
            long durationUs = audioExtractor.getDurationUs();
            long startTimeUs = audioExtractor.getStartTimeUs();
            long endTimeUs = audioExtractor.getEndTimeUs();
            if (endTimeUs != -1 && endTimeUs < durationUs) {
                durationUs = endTimeUs - startTimeUs;
            }

            Log.d(TAG, String.format("startResample index %d, firstSampleTime %d,durationUs %d,startTimeUs %d ,endTimeUs %d"
                    , index, firstSampleTime, durationUs, startTimeUs, endTimeUs));

            if (isMix || audioExtractor.isNeedToResample(outSampleRate)) {
                Log.d(TAG, "startResample resampleIndex index:" + index);
                resampleIndex.put(index, false);
                reSamplingOneAudio(index, audioExtractor, firstSampleTime, durationUs, startTimeUs, endTimeUs);
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
                public void onOutputBuffer(byte[] bytes) {
                    try {
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
            long now = audioExtractor.getSampleTime() - firstSampleTime - startTimeUs;

            boolean beEndOfStream = (now >= durationUs || now == -1);
            if (!decoder.decode(beEndOfStream)) {
                isRunning = false;
            }
        }
    }

    /**
     * 从已经进行重采样的文件中获取数据
     */
    private void getOutputPCMData(int index, String outputFilePath) {
        try {
            byte[] buffer = new byte[8 * 1024];
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
                    pcmContainer.put(index, new ByteContainer()); //= new ByteContainer();
//                                    new Thread(new Runnable() {
//                                        @Override
//                                        public void run() {
                    getOutputPCMData(index, path);
//                                        }
//                                    }).start();
                    AudioEncoder audioEncoder = new AudioEncoder();


                    openEncoder(index, audioEncoder, outSampleRate, channelCount, maxInputSize);
                    audioEncoder.start();
                    inputForEncoder(index, audioEncoder);

                } else {
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
    private void openEncoder(final int index, AudioEncoder audioEncoder, int sampleRate, int channelCount, int maxInputSize) {
        String audioOutPath = Constants.getPath("", suffix + "aacDst" + index + ".aac");
        audioEncoder.open(TAG + "Encoder", audioOutPath, "audio/mp4a-latm", sampleRate, channelCount, 96000, maxInputSize, new AudioEncoder.AudioEncoderCallBack() {
            @Override
            public void onInputBuffer() {

            }

            @Override
            public void onOutputBuffer(byte[] data, MediaCodec.BufferInfo bufferInfo) {

                Log.d(TAG, "onOutputBuffer: index:" + index);
                Log.d(TAG, "onOutputBuffer: data.length:" + data.length);

                if (!resampleDataHashMap.containsKey(index)) {
                    Log.d(TAG, "onOutputBuffer: put");
                    ByteContainer byteContainer = new ByteContainer();
                    resampleDataHashMap.put(index, byteContainer);
                }
                resampleDataHashMap.get(index).putData(data);
                resampleDataHashMap.get(index).putBufferInfo(bufferInfo);
//                resampleDataHashMap.get(index).setSize(data.length);
            }

            @Override
            public void encodeOver() {
                Log.d(TAG, "encodeOver: index:" + index);
                resampleIndex.put(index, true);
            }
        });
    }

    private void inputForEncoder(int index, AudioEncoder audioEncoder) {
        while (true) {
            if (pcmContainer.get(index) != null && !pcmContainer.get(index).isStarted()) {
                Log.d(TAG, "inputForEncoder: continue");
                continue;
            }
            if (!pcmContainer.get(index).isEmpty()) {
                byte[] chunkPcm = pcmContainer.get(index).getData();
                Log.d(TAG, "inputForEncoder: chunkPcm size:" + chunkPcm.length);
                audioEncoder.encode(chunkPcm, false);
            } else {
                Log.d(TAG, "inputForEncoder: fail");
                audioEncoder.encode(null, true);
                break;
            }
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

}

