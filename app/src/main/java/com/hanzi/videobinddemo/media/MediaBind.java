package com.hanzi.videobinddemo.media;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.hanzi.videobinddemo.bean.EffectInfo;
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

    private boolean[] indexPcmMixOk = new boolean[]{false, false};
    private String[] pcmMixPaths = new String[2];

    private boolean[] indexVideoAudioOk = new boolean[]{false, false};

    private Handler audioMixHandler;
    private HandlerThread audioMixHandlerThread;

    private MediaBindInfo mediaBindInfo;


    public MediaBind(int processStragey) {
        this.processStragey = processStragey;
    }

    public int open(MediaBindInfo bindInfo) {
        this.mediaBindInfo = bindInfo;
//        initVideoComposer(bindInfo);
        initAudioComposer(bindInfo);
        initBgmComposer(bindInfo);

        initMediaAudioMix();
        initMediaCombine();
        return 0;
    }

    public int start() {
//        final String mPcmInFilePath = Constants.getPath("audio/", "audio" + "pcmSrc" + 0 + ".pcm");
//        audioComposer.encoderPcm(mPcmInFilePath,44100,0);
        startAudio();
//        startVideo();
        startCombine();
        return 0;
    }

    public int stop() {
        audioComposer.stop();
        bgmComposer.stop();
        audioMix.stop();
        mediaCombine.stop();

//        videoComposer.stop();
        return 0;
    }

    public int destory() {
        return 0;
    }

    private void initMediaCombine() {
        mediaCombine = new MediaCombine();
    }

    private void initMediaAudioMix() {
        audioMixHandlerThread = new HandlerThread("audioMix");
        audioMixHandlerThread.start();
        audioMixHandler = new Handler(audioMixHandlerThread.getLooper());

        audioMix = new AudioMix();
    }

    private void initAudioComposer(MediaBindInfo bindInfo) {
        audioComposer = new AudioComposer("audioComposer",bindInfo.getMediaBeans(), bindInfo.getDuration(), audioOutFilePath, false);
        audioComposer.setAudioComposerCallBack(new AudioComposer.AudioComposerCallBack() {
            @Override
            public void onPcmPath(String path) {
//                indexPcmMixOk[0] = true;
                pcmMixPaths[0] = path;

            }

            @Override
            public void onFinishWithoutMix() {
                indexVideoAudioOk[0] = true;
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
                indexVideoAudioOk[1] = true;
            }
        });
    }

    private void initBgmComposer(MediaBindInfo info) {
        if (info.getBgm() == null) return;
        long mDuration = audioComposer.getDurationUs();
        Log.d(TAG, "initBgmComposer: mDuration:"+mDuration);
        MediaBean bgm = info.getBgm();

        if (mDuration <= 0) {
            mDuration = bgm.getDuration();
        }

        //calculate the mediaBeans's number ,add the repeatBgm file
        List<MediaBean> mediaBeans = new ArrayList<>();
        setMediaBeansCount(mDuration, bgm, mediaBeans);

        bgmComposer = new AudioComposer("bgmComposer",mediaBeans, mDuration, bgmOutFilePath, true);
        bgmComposer.setAudioComposerCallBack(new AudioComposer.AudioComposerCallBack() {
            @Override
            public void onPcmPath(String path) {
//                indexPcmMixOk[1] = true;
                pcmMixPaths[1] = path;
                Log.d(TAG, "onPcmPath: 1");
//                String audioOutFilePath = PATH + "/1/testBgm.aac";
//
//                File file=new File(path);
//                File[]  files=new File[] {file};
//
//                try {
//                    AudioCodec.pcmMix(files, audioOutFilePath, 1, 1, 44100, new AudioCodec.AudioDecodeListener() {
//                        @Override
//                        public void getSampleRate(int sample) {
//
//                        }
//
//                        @Override
//                        public void decodeOver() {
//
//                        }
//
//                        @Override
//                        public void decodeFail() {
//
//                        }
//
//                        @Override
//                        public void onProgress(int progress) {
//
//                        }
//                    });
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
            }

            @Override
            public void onFinishWithoutMix() {

            }
        });
    }

    private void startAudio() {
        //获取音频中最小的采样率
        int audioSampleRate = 44100;
        if (bgmComposer != null) {
            audioSampleRate = audioComposer.getMinSampleRate() > bgmComposer.getMinSampleRate() ?
                    bgmComposer.getMinSampleRate() : audioComposer.getMinSampleRate();

            Log.d(TAG, "start: audioSampleRate bgm:" + audioSampleRate);
            audioComposer.start(audioSampleRate, 2, true);
            bgmComposer.start(audioSampleRate, 2, true);
        } else {
            audioSampleRate = audioComposer.getMinSampleRate();
            audioComposer.start(audioSampleRate, 2, false);
            Log.d(TAG, "start: audioSampleRate:" + audioSampleRate);
        }

        audioMixHandler.post(new Runnable() {
            @Override
            public void run() { 
                while (true) {
                    if (indexPcmMixOk[0] && indexPcmMixOk[1]) {
                        audioMix.open(pcmMixPaths, audioMixFilePath,audioComposer.getFormat(), audioComposer.getMinSampleRate(), audioComposer.getChannelCount(), audioComposer.getMaxInputSize());
                        audioMix.start();
                        audioMix.setOnFinishListener(new AudioMix.FinishListener() {
                            @Override
                            public void onFinish() {
                                indexVideoAudioOk[0] = true;
                                Log.d(TAG, "onFinish: ");
                            }
                        });
                        Log.d(TAG, "audioMixHandler: break");
                        break;
                    }
                }
            }
        });
    }

    private void startVideo() {
        videoComposer.start();
    }

    private void startCombine() {
        while (indexVideoAudioOk[0] && indexVideoAudioOk[1]) {
            mediaCombine.open(videoOutFilePath, audioOutFilePath, finalOutFilePath, new MediaCombine.CombineVideoListener() {
                @Override
                public void onProgress(int progress) {

                }
            });
        }
    }

    private void setMediaBeansCount(long mDuration, MediaBean bgm, List<MediaBean> mediaBeans) {
        long endTime;
        try {

            int count = 3;//(int) (mDuration / bgm.getDuration());
            for (int i = 0; i < count; i++) {
                mediaBeans.add(bgm.clone());
            }
            Log.d(TAG, "setMediaBeansCount: count:"+count);

            endTime =0;// mDuration % bgm.getDuration();
            Log.d(TAG, "setMediaBeansCount: endTime:" + endTime);
            if (endTime != 0) {
                MediaBean mediaBean = bgm.clone();
                mediaBean.setTime(bgm.getDuration(), 0, endTime);
                mediaBeans.add(mediaBean);
            }

        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
    }
}
