package com.hanzi.videobinddemo.media;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.hanzi.videobinddemo.Constants;
import com.hanzi.videobinddemo.media.Utils.extractor.AudioExtractor;
import com.hanzi.videobinddemo.bean.MediaBean;
import com.hanzi.videobinddemo.bean.MediaBindInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by gengen on 2018/5/21.
 * mp4音视频文件合成 功能
 */

public class MediaBind {
    public static String TAG = "MediaBind";

    public final static int ONLY_VIDEO_PROCESS = 0;
    public final static int ONLY_AUDIO_PROCESS = 1;
    public final static int BOTH_PROCESS = 2;

    /**
     * 进行音频拼接（非混音），采取该比例
     */
    public final static int NO_AUDIOMIX_RESAMPLE_RATE = 60;
    public final static int NO_AUDIOMIX_MERGE_RATE = 40;
    /**
     * 进行音频拼接（混音），采取该比例
     */
    public final static int AUDIOMIX_RESAMPLE_RATE = 30;
    public final static int AUDIOMIX_MIX_RATE = 40;
    public final static int AUDIOMIX_MERGE_RATE = 30;

    public final static int VIDEO_RECODE_RATE = 60;
    public final static int VIDEO_MERGE_RATE = 40;

    private int processStragey = BOTH_PROCESS;

    private List<String> urls = new ArrayList<>();
    private String bgmPath = "";

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

    private int[] audioProgress = new int[2];

    private Handler audioMixHandler;
    private HandlerThread audioMixHandlerThread;

    private MediaBindInfo mediaBindInfo;

    private MediaBindCallback callback;

    private boolean isAudioMix = false;

    public MediaBind(int processStragey) {
        this.processStragey = processStragey;
    }

    public void setCallback(MediaBindCallback callback) {
        this.callback = callback;
    }

    public int open(MediaBindInfo bindInfo) {

        deleteFile(videoOutFilePath);
        deleteFile(audioMixFilePath);
        deleteFile(finalOutFilePath);
        deleteFile(bgmOutFilePath);
        deleteFile(audioOutFilePath);

        this.mediaBindInfo = bindInfo;
        switch (processStragey) {
            case BOTH_PROCESS:
                initAudioComposer(bindInfo);
                initBgmComposer(bindInfo);
                initMediaAudioMix(bindInfo);

                initVideoComposer(bindInfo);

                initMediaCombine();
                break;
            case ONLY_AUDIO_PROCESS:
                initAudioComposer(bindInfo);
                initBgmComposer(bindInfo);
                initMediaAudioMix(bindInfo);
                break;
            case ONLY_VIDEO_PROCESS:
                initVideoComposer(bindInfo);
            default:
                break;
        }

        return 0;
    }

    private void deleteFile(String path) {
        File file=new File(path);
        if (file.exists()) {
            file.delete();
        }
    }

    public int start() {
        switch (processStragey) {
            case ONLY_AUDIO_PROCESS:
                startAudio();
                break;
            case ONLY_VIDEO_PROCESS:
                startVideo();
                break;
            case BOTH_PROCESS:
                startAudio();
                startVideo();
                startCombine();
                break;
            default:
                break;
        }
        return 0;
    }

    public int stop() {
        switch (processStragey) {
            case ONLY_AUDIO_PROCESS:
                if (audioComposer != null)
                    audioComposer.stop(true);
                if (bgmComposer != null)
                    bgmComposer.stop(true);
                if (audioMix != null)
                    audioMix.stop();
                break;
            case ONLY_VIDEO_PROCESS:
                Log.i(TAG, "stop: videoComposer");
                if (videoComposer != null)
                    videoComposer.stop(true);
                break;
            case BOTH_PROCESS:
                if (audioComposer != null)
                    audioComposer.stop(true);
                if (bgmComposer != null)
                    bgmComposer.stop(true);
                if (audioMix != null)
                    audioMix.stop();
                if (videoComposer != null)
                    videoComposer.stop(true);
                if (mediaCombine != null)
                    mediaCombine.stop();
                break;
            default:
                break;
        }

        videoAudioOkIndex[0]=false;
        videoAudioOkIndex[1]=false;
        return 0;
    }

    public int destory() {
        audioComposer = null;
        bgmComposer = null;
        audioMix = null;
        videoComposer = null;
        mediaCombine = null;
        return 0;
    }

    private void initMediaCombine() {
        mediaCombine = new MediaCombine();
    }

    /**
     * 初始化 音频合成
     */
    private void initMediaAudioMix(MediaBindInfo bindInfo) {
        if (bindInfo.getBgm() == null || bindInfo.isMute()) return;
        audioMixHandlerThread = new HandlerThread("audioMix");
        audioMixHandlerThread.start();
        audioMixHandler = new Handler(audioMixHandlerThread.getLooper());
        audioMix = new AudioMix();
        isAudioMix = true;
    }

    /**
     * 初始化 音频源播放
     *
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
                if (callback != null) {
                    callback.callback("音频结束");
                    callback.onAudioRate(100);
                }
            }
        });
        audioComposer.setAudioProgressCallBack(new AudioComposer.AudioProgressCallBack() {
            @Override
            public void onAudioType(String content) {
                Log.i(TAG, "onAudioType: content:" + content);
                if (callback != null)
                    callback.onAudioType(content); //"音频拼接（非混音）："
            }

            @Override
            public void onProgress(int rate) {
                Log.i(TAG, "onProgress: rate:" + rate);
                if (callback != null) {
                    audioProgress[0] = rate / 2;
                    callback.onAudioRate(audioProgress[0] + audioProgress[1]);
                }
            }
        });
    }

    /**
     * 初始化 背景源播放
     *
     * @param info
     */
    private void initBgmComposer(MediaBindInfo info) {
        if (info.getBgm() == null) return;
        long mAudioDuration = audioComposer.getDurationUs();
        Log.d(TAG, "initBgmComposer: mDuration:" + mAudioDuration);

        MediaBean bgm = info.getBgm();

        //计算当个bgm 音频的长度
        AudioExtractor extractor = new AudioExtractor(bgm.getUrl(), bgm.getStartTimeUs(), bgm.getEndTimeUs());
        long mBgmDuration = extractor.getTotalDurationUs();
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
                videoAudioOkIndex[0] = true;
                if (callback != null) {
                    callback.callback("bgm结束");
                    callback.onAudioRate(100);
                }
            }
        });
        bgmComposer.setAudioProgressCallBack(new AudioComposer.AudioProgressCallBack() {
            @Override
            public void onAudioType(String content) {
                Log.i(TAG, "onAudioType: content:" + content);
                if (callback != null)
                    callback.onAudioType(content); //"音频拼接（混音）："
            }

            @Override
            public void onProgress(int rate) {
                Log.i(TAG, "onProgress: rate:" + rate);
                if (callback != null) {
                    audioProgress[1] = rate / 2;
                    callback.onAudioRate(audioProgress[0] + audioProgress[1]);
                }
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
                if (callback != null) {
                    callback.callback("视频结束");
                    callback.onVideoRate(100);
                }

            }
        });
        videoComposer.setVideoProgressCallBack(new VideoComposer.VideoProgressCallBack() {
            @Override
            public void onVideoType(String content) {
                if (callback != null)
                    callback.onVideoType(content); //"视频拼接"
            }

            @Override
            public void onProgress(int rate) {
                if (callback != null) {
                    callback.onVideoRate(rate);
                }
            }
        });
    }

    /**
     * 开始对音频进行处理
     */
    private void startAudio() {
        //获取音频中最小的采样率
        int outSampleRate = getAudioMinSampleRate();

        if (bgmComposer != null && !mediaBindInfo.isMute()) {
            Log.i(TAG, "startAudio: mix");
            audioComposer.start(outSampleRate, 2, true);
            bgmComposer.start(outSampleRate, 2, true);
            audioMixHandler.post(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        if (pcmMixOkIndex[0] && pcmMixOkIndex[1]) {

                            pcmMixPaths[0] = Constants.getPath("audio/", "audio" + "outPcm" + ".pcm");
                            pcmMixPaths[1] = Constants.getPath("audio/", "bgm" + "outPcm" + ".pcm");
                            audioMix.open(pcmMixPaths, audioMixFilePath, audioComposer.getFormat(), 44100, 2, 8192, audioComposer.getDurationUs());//audioComposer.getMinSampleRate(), audioComposer.getChannelCount(), audioComposer.getMaxInputSize());
                            audioMix.start();
                            audioMix.setAudioMixCallBack(new AudioMix.AudioMixCallBack() {
                                @Override
                                public void onProgress(int rate) {
                                    if (callback != null) {
                                        callback.onAudioRate((int) (AUDIOMIX_RESAMPLE_RATE + (float) (AUDIOMIX_MERGE_RATE + AUDIOMIX_MIX_RATE) / 100 * rate));
                                        callback.onAudioType("混音成功");
                                    }
                                }
                            });
                            audioMix.setOnFinishListener(new AudioMix.FinishListener() {
                                @Override
                                public void onFinish() {
                                    videoAudioOkIndex[0] = true;
                                    Log.d(TAG, "audioMixHandler onFinish: ");
                                    if (callback != null) {
                                        callback.onAudioRate(100);
                                    }
                                }
                            });
                            Log.d(TAG, "audioMixHandler: break");
                            break;
                        }
                    }
                }
            });
        } else if (bgmComposer != null) {
            bgmComposer.start(outSampleRate, 2, false);
        } else if (!mediaBindInfo.isMute()) {
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
            if (mediaBindInfo.isMute()) {
                audioSampleRate = bgmComposer.getMinSampleRate();
            } else {
                audioSampleRate = audioComposer.getMinSampleRate() > bgmComposer.getMinSampleRate() ?
                        bgmComposer.getMinSampleRate() : audioComposer.getMinSampleRate();
            }
        } else {
            audioSampleRate = audioComposer.getMinSampleRate();
        }
        Log.d(TAG, "start: audioSampleRate bgm:" + audioSampleRate);
        return audioSampleRate;
    }

    /**
     * 开始对视频进行处理
     */
    private void startVideo() {
        videoComposer.start();
    }

    /**
     * 开始音视频合成
     */
    private void startCombine() {
        while (true) {
            if (videoAudioOkIndex[0] && videoAudioOkIndex[1]) {
                String audioOutFilePath = this.audioOutFilePath;
                if (isAudioMix) {
                    audioOutFilePath = this.audioMixFilePath;
                } else if (!mediaBindInfo.isMute()) {
                    audioOutFilePath = this.audioOutFilePath;
                } else if (mediaBindInfo.getBgm() != null) {
                    audioOutFilePath = this.bgmOutFilePath;
                }
                Log.i(TAG, "startCombine: audioOutFilePath:" + audioOutFilePath);

                mediaCombine.open(videoOutFilePath, audioOutFilePath, finalOutFilePath, videoComposer.getDurationUs(), new MediaCombine.CombineVideoListener() {//videoComposer.getDurationUs()
                    @Override
                    public void onProgress(int progress) {
                        if (callback != null) {
                            callback.onCombineRate(progress);
                        }
                    }

                    @Override
                    public void onFinish() {

                    }

                    @Override
                    public void onCancel(int type) {

                    }
                });

                mediaCombine.start();
                break;
            }

        }
    }

    /**
     * 设置BGM 的数量，同步视频源长度
     *
     * @param mDuration
     * @param bgmBean
     * @param mediaBeans
     */
    private void setBgmMediaBeansCount(long mDuration, MediaBean bgmBean, List<MediaBean> mediaBeans) {
        long endTime;
        try {

            int count = (int) (mDuration / bgmBean.getDurationUs());
            Log.i(TAG, "setBgmMediaBeansCount: mDuration:" + mDuration);
            Log.i(TAG, "setBgmMediaBeansCount: bgmBean.getTotalDurationUs:" + bgmBean.getDurationUs());
            Log.i(TAG, "setBgmMediaBeansCount: count:" + count);
            for (int i = 0; i < count; i++) {
                mediaBeans.add(bgmBean.clone());
            }

            endTime = mDuration % bgmBean.getDurationUs();
            if (endTime != 0) {
                MediaBean mediaBean = bgmBean.clone();
                mediaBean.setTimeUs(bgmBean.getDurationUs(), 0, endTime);
                mediaBeans.add(mediaBean);
            }

        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
    }

    public interface MediaBindCallback {
        public void onVideoType(String content);

        public void onAudioType(String content);

        public void onVideoRate(int rate);

        public void onAudioRate(int rate);

        public void callback(String content);

        public void onCombineRate(int rate);
    }
}
