package com.hanzi.videobinddemo.media;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
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

import static android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC;

/**
 * Created by gengen on 2018/5/22.
 * 音频合成
 */

public class AudioComposer {
    private final String TAG = "AudioComposer";
    private List<MediaBean> mMediaBeans;
    private long mDuration;
    private List<AudioExtractor> mMediaExtractors = new ArrayList<>();
    private MediaFileMuxer mediaFileMuxer;
    private String outFilePath;

    private int outSampleRate;
    private int outChannelCount;

    private ByteBuffer mReadBuf;

    int mOutAudioTrackIndex = -1;
    private long ptsOffset = 0L;

    private HandlerThread resamplerThread;
    private HandlerThread mergeThread;
    private HandlerThread audioEncoderThread;

    private Handler resamplerHandler;
    private Handler mergeHandler;
    private Handler audioEncoderHandler;

    private boolean beStop = false;

    private boolean isMix = false;

    private AudioComposerCallBack audioComposerCallBack;

    private ByteContainer byteContainer;

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

    private final int RESAMPLE = 0;

    String suffix = "audio";
    private int outMaxInputSize= 500 * 1024;

    public AudioComposer(List<MediaBean> mediaBeans, long duration, String outFilePath, boolean isBgm) {
        this.mMediaBeans = mediaBeans;
        this.mDuration = duration;
        this.outFilePath = outFilePath;
        this.isBgm = isBgm;

        mReadBuf = ByteBuffer.allocate(1048576);

        mediaFileMuxer = new MediaFileMuxer(outFilePath);

        for (MediaBean bean : mMediaBeans) {
            AudioExtractor extractor = new AudioExtractor(bean.getUrl(), bean.getStartTimeUs(), bean.getEndTimeUs());
            mMediaExtractors.add(extractor);
        }

        mergeThread = new HandlerThread("mergeWithoutResample");
        mergeThread.start();
        mergeHandler = new Handler(mergeThread.getLooper());
    }

    public void setAudioComposerCallBack(AudioComposerCallBack audioComposerCallBack) {
        this.audioComposerCallBack = audioComposerCallBack;
    }

    public void start(int sampleRate, int channelCount, final boolean isMix) {
        this.outSampleRate = sampleRate;
        this.outChannelCount = channelCount;

        this.isMix = isMix;

        Log.d(TAG, "start: outSampleRate:" + outSampleRate);
        Log.d(TAG, "start: channelCount:" + channelCount);
        startMuxer();
        mergeHandler.post(new Runnable() {
            @Override
            public void run() {
                startResample();
                isResampleOk();
                if (!isMix) {
                    startMerge();
                } else {
                    if (pcmPathHashMap != null) {
                        String outPCMPath = mergeFile((String[]) pcmPathHashMap.values().toArray());
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
            Log.d(TAG, "startResample: audioExtractor.toString():" + audioExtractor.toString());
            Log.d(TAG, "startResample: firstSampleTime:" + firstSampleTime);
            Log.d(TAG, "startResample: startTimeUs:" + startTimeUs);
            Log.d(TAG, "startResample: endtime:" + endTimeUs);
            Log.d(TAG, "startResample: durationUs:" + durationUs);

            if (resampleIndex.containsKey(index)) {
                mergeByteBuffer(resampleDataHashMap.get(index));
            } else {
                Log.d(TAG, "startMerge: mergeWithoutResample");
                mergeWithoutResample(audioExtractor, firstSampleTime, durationUs, startTimeUs, endTimeUs);
            }

            index++;
        }
        stop();
        if (audioComposerCallBack != null)
            audioComposerCallBack.onFinishWithoutMix();
    }

    /**
     * 多个文件放到一个文件中
     * @param paths
     * @return
     */
    private String mergeFile(String[] paths) {
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
     * 把之前 获取到的缓存  合成进mediaMuxer
     *
     * @param byteContainer
     */
    private void mergeByteBuffer(ByteContainer byteContainer) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(byteContainer.getSize());
        long audioPts=0;
        while (!byteContainer.isEmpty()) {
            byte[] data = byteContainer.getData();
            MediaCodec.BufferInfo bufferInfo = byteContainer.getBufferInfo();
            Log.d(TAG, "mergeByteBuffer: bufferInfo.presentationTimeUs:"+bufferInfo.presentationTimeUs);
            audioPts= bufferInfo.presentationTimeUs;
            byteBuffer.put(data);
            byteBuffer.flip();
            bufferInfo.presentationTimeUs = ptsOffset+ bufferInfo.presentationTimeUs;
            mediaFileMuxer.writeSampleData(mOutAudioTrackIndex, byteBuffer, bufferInfo);
        }
        ptsOffset += audioPts;
        ptsOffset += 10000L;//test ，如果不添加，如何
        Log.i(TAG, "finish one file, ptsOffset " + ptsOffset);
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
     *
     */
    private void startMuxer() {
        MediaFormat mediaFormat = mMediaExtractors.get(0).getFormat();
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
            if (isMix || audioExtractor.isNeedToResample(outSampleRate)) {
                resampleIndex.put(index, false);
                ReSampling(index, audioExtractor, firstSampleTime, durationUs, startTimeUs, endTimeUs);
            }
            index++;
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
     * 在拼接时，重新解码+重采样+编码
     *
     * @param audioExtractor
     */
    private void ReSampling(int index, final AudioExtractor audioExtractor, long firstSampleTime, long durationUs,
                            long startTimeUs, long endTimeUs) {

        initResampleHandler(index);

        AudioDecoder decoder = new AudioDecoder();
        openDecoder(index, decoder, audioExtractor);

        audioExtractor.seekTo(firstSampleTime + startTimeUs, SEEK_TO_PREVIOUS_SYNC);
        inputForDecoder(audioExtractor, firstSampleTime, durationUs, startTimeUs, decoder);
    }

    private void initResampleHandler(final int index) {
        resamplerThread = new HandlerThread("resample");
        resamplerThread.start();

        resamplerHandler = new Handler(resamplerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == RESAMPLE) {
                    String inputFilePath = msg.getData().getString("inputFilePath");
                    String outputFilePath = msg.getData().getString("outputFilePath");
                    int inputSampleRate = msg.getData().getInt("inputSampleRate");
                    int outSampleRate = msg.getData().getInt("outSampleRate");
                    int channelCount = msg.getData().getInt("channelCount");
                    int maxInputSize = msg.getData().getInt("maxInputSize");
                    resample(inputSampleRate, outSampleRate, inputFilePath, outputFilePath);

                    if (!isMix) {
                        byteContainer = new ByteContainer();
                        getOutputPCMData(outputFilePath);

                        AudioEncoder audioEncoder = new AudioEncoder();
                        openEncoder(index, audioEncoder, outSampleRate, channelCount, maxInputSize);
                        audioEncoder.start();
                        inputForEncoder(audioEncoder);

                    } else {
                        pcmPathHashMap.put(index, outputFilePath);
                        resampleIndex.put(index, true);
                    }

                }
            }
        };
    }

    private void inputForEncoder(AudioEncoder audioEncoder) {
        while (true) {
            if (!byteContainer.isStarted()) {
                continue;
            }
            if (byteContainer.isEmpty()) {
                byte[] chunkPcm = byteContainer.getData();
                audioEncoder.encode(chunkPcm, false);
            } else {
                audioEncoder.encode(null, true);
                break;
            }
        }
    }

    /**
     * 从已经进行重采样的文件中获取数据
     */
    private void getOutputPCMData(String outputFilePath) {
        try {
            byte[] buffer = new byte[8 * 1024];
            File file = new File(outputFilePath);
            FileInputStream fileInputStream = new FileInputStream(file);
            while (fileInputStream.read(buffer) != -1) {
                byteContainer.putData(buffer);
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
    private void resample(int inputSampleRate, int outSampleRate, String inputFilePath, String outputFilePath) {
        ReSample reSample = new ReSample(inputSampleRate, outSampleRate, inputFilePath, outputFilePath);
        reSample.invoke();
    }

    /**
     * open decoder
     *
     * @param decoder
     * @param audioExtractor
     */
    @NonNull
    private void openDecoder(int index, AudioDecoder decoder, final AudioExtractor audioExtractor) {

        if (isBgm) {
            suffix = "bgm";
        } else {
            suffix = "audio";
        }

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
                        Message message = new Message();
                        message.getData().putString("inputFilePath", mPcmInFilePath);
                        message.getData().putString("outputFilePath", mPcmOutFilePath);
                        message.getData().putInt("inputSampleRate", audioExtractor.getInitSampleRate());
                        message.getData().putInt("outSampleRate", outSampleRate);
                        message.getData().putInt("channelCount", audioExtractor.getChannelCount());
                        message.getData().putInt("maxInputSize", audioExtractor.getMaxInputSize());
                        resamplerHandler.sendMessage(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void openEncoder(final int index, AudioEncoder audioEncoder, int sampleRate, int channelCount, int maxInputSize) {
        audioEncoder.open("audio/mp4a-latm", sampleRate, channelCount, 96000, maxInputSize, new AudioEncoder.AudioEncoderCallBack() {
            @Override
            public void onInputBuffer() {

            }

            @Override
            public void onOutputBuffer(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
                byteBuffer.flip();
                byte[] datas = new byte[byteBuffer.limit()];
                byteBuffer.get(datas);

                if (!resampleDataHashMap.containsKey(index)) {
                    ByteContainer byteContainer = new ByteContainer();
                    resampleDataHashMap.put(index, byteContainer);
                }
                resampleDataHashMap.get(index).putData(datas);
                resampleDataHashMap.get(index).putBufferInfo(bufferInfo);
                resampleDataHashMap.get(index).setSize(byteBuffer.limit());
            }

            @Override
            public void encodeOver() {
                resampleIndex.put(index, true);
            }
        });
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
    private void inputForDecoder(AudioExtractor audioExtractor, long firstSampleTime, long durationUs, long startTimeUs, AudioDecoder decoder) {
        boolean isRunning = true;
        mReadBuf.rewind();
        while (isRunning) {
            int sampleSize = audioExtractor.readSampleData(mReadBuf, 0);
            long sampleTime = audioExtractor.getSampleTime();
            long now = audioExtractor.getSampleTime() - firstSampleTime - startTimeUs;

            if (sampleSize < 0 || (now >= durationUs || now == -1)) {
                isRunning = false;
            } else {
                mReadBuf.rewind();
                if (!decoder.decode(mReadBuf, sampleSize, sampleTime)) {
                    isRunning = false;
                }
            }
        }
    }

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

    private void stopExtractor() {
        for (AudioExtractor extractor : mMediaExtractors) {
            extractor.release();
        }
    }

    public int getChannelCount() {
        return outChannelCount;
    }

    public int getMaxInputSize() {
        return outMaxInputSize;
    }

    public interface AudioComposerCallBack {
        public void onPcmPath(String path);

        public void onFinishWithoutMix();
    }

}

