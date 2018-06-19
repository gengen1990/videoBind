package com.hanzi.videobinddemo.media;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.hanzi.videobinddemo.Constants;
import com.hanzi.videobinddemo.bean.EffectInfo;
import com.hanzi.videobinddemo.media.Utils.extractor.AudioExtractor;
import com.hanzi.videobinddemo.media.Variable.MediaBean;
import com.hanzi.videobinddemo.media.Variable.MediaBindInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gengen on 2018/5/21.
 * mp4音视频文件合成 功能
 */

public class MediaBind {
    public static String TAG = "MediaBind";

    public static int ONLY_VIDEO_PROCESS = 0;
    public static int ONLY_AUDIO_PROCESS = 1;
    public static int BOTH_PROCESS = 2;

    private int processStragey = BOTH_PROCESS;

    private List<String> urls = new ArrayList<>();
    private String bgmPath = "";
    private EffectInfo effectInfo = new EffectInfo();

    private VideoComposer videoComposer;
    private AudioComposer bgmComposer, audioComposer;

    private AudioMix audioMix;
    private MediaCombine mediaCombine;


    private static String PATH = Environment.getExternalStorageDirectory().getAbsolutePath();

    private String finalOutFilePath = PATH + "/1/out.mp4";

    private String bgmOutFilePath = PATH + "/1/bgm.aac";
    private String audioOutFilePath = PATH + "/1/audio.aac";
    private String audioMixFilePath = PATH + "/1/audioMix.aac";

    private String videoOutFilePath = PATH + "/1/video.mp4";

    private boolean[] pcmMixOkIndex = new boolean[]{false, false};
    private String[] pcmMixPaths = new String[2];

    private boolean[] videoAudioOkIndex = new boolean[]{false, false};

    private Handler audioMixHandler;
    private HandlerThread audioMixHandlerThread;

    private MediaBindInfo mediaBindInfo;

    private MediaBindCallback callback;

    public MediaBind(int processStragey) {
        this.processStragey = processStragey;
    }

    public void setCallback(MediaBindCallback callback){
        this.callback=callback;
    }

    public int open(MediaBindInfo bindInfo) {
        this.mediaBindInfo = bindInfo;

        initAudioComposer(bindInfo);
        initBgmComposer(bindInfo);
        initMediaAudioMix();

//        initVideoComposer(bindInfo);

//        initMediaCombine();
        return 0;
    }

    public int start() {
        startAudio();

//        startVideo();

//        startCombine();
        return 0;
    }

    public int stop() {
        audioComposer.stop();
        bgmComposer.stop();
        audioMix.stop();

//        videoComposer.stop();

//        mediaCombine.stop();
        return 0;
    }

    public int destory() {
        return 0;
    }

    private void initMediaCombine() {
        mediaCombine = new MediaCombine();
    }

    /**
     * 初始化 音频合成
     */
    private void initMediaAudioMix() {
        audioMixHandlerThread = new HandlerThread("audioMix");
        audioMixHandlerThread.start();
        audioMixHandler = new Handler(audioMixHandlerThread.getLooper());
        audioMix = new AudioMix();
    }

    /**
     * 初始化 音频源播放
     * @param bindInfo
     */
    private void initAudioComposer(MediaBindInfo bindInfo) {
        audioComposer = new AudioComposer("audioComposer", bindInfo.getMediaBeans(), bindInfo.getDuration(), audioOutFilePath, false);
        audioComposer.setAudioComposerCallBack(new AudioComposer.AudioComposerCallBack() {
            @Override
            public void onPcmPath(String path) {
                pcmMixOkIndex[0] = true;
                pcmMixPaths[0] = path;
                Log.d(TAG, "onPcmPath: 0");

            }
            @Override
            public void onFinishWithoutMix() {
                videoAudioOkIndex[0] = true;
                if (callback!=null)
                    callback.callback("音频结束");
            }
        });
    }

    /**
     * 初始化 背景源播放
     * @param info
     */
    private void initBgmComposer(MediaBindInfo info) {
        if (info.getBgm() == null) return;
        long mAudioDuration = audioComposer.getDurationUs();
        Log.d(TAG, "initBgmComposer: mDuration:" + mAudioDuration);

        MediaBean bgm = info.getBgm();

        AudioExtractor extractor = new AudioExtractor(bgm.getUrl(), bgm.getStartTimeUs(), bgm.getEndTimeUs());
        long mBgmDuration =extractor.getDurationUs();
        extractor.release();



        bgm.setDurationUs(mBgmDuration);

        if (mAudioDuration <= 0) {
            mAudioDuration = bgm.getDurationUs();
        }

        //calculate the mediaBeans's number ,add the repeatBgm file
        List<MediaBean> mediaBeans = new ArrayList<>();
        setBgmMediaBeansCount(mAudioDuration, bgm, mediaBeans);

        bgmComposer = new AudioComposer("bgmComposer", mediaBeans, mAudioDuration, bgmOutFilePath, true);
        bgmComposer.setAudioComposerCallBack(new AudioComposer.AudioComposerCallBack() {
            @Override
            public void onPcmPath(String path) {
                pcmMixOkIndex[1] = true;
                pcmMixPaths[1] = path;
                Log.d(TAG, "onPcmPath: 1");

            }

            @Override
            public void onFinishWithoutMix() {

            }
        });
    }

    private void initVideoComposer(MediaBindInfo bindInfo) {
        videoComposer = new VideoComposer(bindInfo.getMediaBeans(),
                bindInfo.getFilter(),
                bindInfo.getOutputWidth(),
                bindInfo.getOutputHeight(),
                videoOutFilePath);
        videoComposer.setVideoComposerCallBack(new VideoComposer.VideoComposerCallBack() {
            @Override
            public void onh264Path() {
                videoAudioOkIndex[1] = true;
                if (callback!=null)
                callback.callback("视频结束");
            }
        });
    }

    /**
     * 开始对音频进行处理
     */
    private void startAudio() {
        //获取音频中最小的采样率
        int outSampleRate = getAudioMinSampleRate();

        if (bgmComposer != null) {
            audioComposer.start(outSampleRate, 2, true);
            bgmComposer.start(outSampleRate, 2, true);

            audioMixHandler.post(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        if (pcmMixOkIndex[0] && pcmMixOkIndex[1]) {

                    pcmMixPaths[0]= Constants.getPath("audio/", "audio" + "outPcm" + ".pcm");
                    pcmMixPaths[1]= Constants.getPath("audio/", "bgm" + "outPcm" + ".pcm");
                            audioMix.open(pcmMixPaths, audioMixFilePath, audioComposer.getFormat(), 44100,2,8192);//audioComposer.getMinSampleRate(), audioComposer.getChannelCount(), audioComposer.getMaxInputSize());
                            audioMix.start();
                            audioMix.setOnFinishListener(new AudioMix.FinishListener() {
                                @Override
                                public void onFinish() {
                                    videoAudioOkIndex[0] = true;
                                    Log.d(TAG, "audioMixHandler onFinish: ");
                                }
                            });
                            Log.d(TAG, "audioMixHandler: break");
                            break;
                        }
                    }
                }
            });
        } else {
            audioComposer.start(outSampleRate, 2, false);
        }


    }

    /**
     * 获取 最小音频的采样率
     *
     * @return
     */
    private int getAudioMinSampleRate() {
        int audioSampleRate = 44100;
        if (bgmComposer != null) {
            audioSampleRate = audioComposer.getMinSampleRate() > bgmComposer.getMinSampleRate() ?
                    bgmComposer.getMinSampleRate() : audioComposer.getMinSampleRate();
        } else {
            audioSampleRate = audioComposer.getMinSampleRate();
        }
        Log.d(TAG, "start: audioSampleRate bgm:" + audioSampleRate);
        return audioSampleRate;
    }

    private void startVideo() {
        videoComposer.start();
    }

    private void startCombine() {
        while (true) {
            if (videoAudioOkIndex[0] && videoAudioOkIndex[1]) {
                mediaCombine.open(videoOutFilePath, audioOutFilePath, finalOutFilePath, new MediaCombine.CombineVideoListener() {
                    @Override
                    public void onProgress(int progress) {
                        if (callback != null)
                            callback.callback("都结束");
                    }
                });
                mediaCombine.start();
                break;
            }

        }
    }

    /**
     * 设置BGM 的数量
     * @param mDuration
     * @param bgmBean
     * @param mediaBeans
     */
    private void setBgmMediaBeansCount(long mDuration, MediaBean bgmBean, List<MediaBean> mediaBeans) {
        long endTime;
        try {

            int count = (int) (mDuration / bgmBean.getDurationUs());

            Log.i(TAG, "setBgmMediaBeansCount: count:"+count);
            for (int i = 0; i < count; i++) {
                mediaBeans.add(bgmBean.clone());
            }

            endTime =  mDuration % bgmBean.getDurationUs();
            if (endTime != 0) {
                MediaBean mediaBean = bgmBean.clone();
                mediaBean.setTime(bgmBean.getDurationUs(), 0, endTime);
                mediaBeans.add(mediaBean);
            }

        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
    }
    public interface MediaBindCallback{
        public void callback(String content);
    }
}
