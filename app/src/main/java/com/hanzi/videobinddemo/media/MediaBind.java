package com.hanzi.videobinddemo.media;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.hanzi.videobinddemo.bean.EffectInfo;
import com.hanzi.videobinddemo.media.Utils.MediaFileMuxer;
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
    private MediaFileMuxer mediaFileMuxer;
    private MediaCombine mediaCombine;


    private static String PATH = Environment.getExternalStorageDirectory().getAbsolutePath();

    private String finalOutFilePath = PATH + "/1/out.mp4";
    private String bgmOutFilePath = PATH + "/1/bgm.aac";
    private String audioOutFilePath = PATH + "/1/audio.aac";
    private String videoOutFilePath = PATH + "/1/video.mp4";

    private boolean[] indexOk = new boolean[] {false,false};
    private String[] pcmMixPath = new String[2];

    private Handler audioMixHandler ;
    private HandlerThread audioMixHandlerThread ;

    private MediaBindInfo mediaBindInfo;


    public MediaBind(int processStragey) {
        this.processStragey = processStragey;
    }

    public int open(MediaBindInfo bindInfo) {
        this.mediaBindInfo = bindInfo;
        initVideoComposer(bindInfo);
        initAudioComposer(bindInfo);
        initBgmComposer(bindInfo);
        initMediaAudioMix();
        initMediaFileMuxer();
        return 0;
    }

    private void initMediaAudioMix() {
        audioMixHandlerThread= new HandlerThread("audioMix");
        audioMixHandlerThread.start();
        audioMixHandler=new Handler(audioMixHandlerThread.getLooper());

        audioMix= new AudioMix();
    }

    public int start() {
        //获取音频中最小的采样率
        int audioSampleRate = 44100;
        if (bgmComposer != null) {
            audioSampleRate = audioComposer.getMinSampleRate() > bgmComposer.getMinSampleRate() ?
                    bgmComposer.getMinSampleRate() : audioComposer.getMinSampleRate();

            Log.d(TAG, "start: audioSampleRate:" + audioSampleRate);
            audioComposer.start(audioSampleRate, 2, true);
            bgmComposer.start(audioSampleRate, 2, true);

        } else {
            audioSampleRate = audioComposer.getMinSampleRate();
            audioComposer.start(audioSampleRate, 2,false);
        }


        audioMixHandler.post(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    if (indexOk [0] && indexOk[1]){
                        audioMix.open(pcmMixPath,audioOutFilePath,audioComposer.getMinSampleRate(),audioComposer.getChannelCount(),audioComposer.getMaxInputSize());
                        audioMix.start();
                    }

                }
            }
        });

        videoComposer.start();

        return 0;
    }

    public int stop() {
        audioComposer.stop();
        bgmComposer.stop();
        audioMix.stop();
        return 0;
    }

    public int destory() {
        return 0;
    }

    private void initAudioComposer(MediaBindInfo bindInfo) {
        audioComposer = new AudioComposer(bindInfo.getMediaBeans(), bindInfo.getDuration(), audioOutFilePath, false);
        audioComposer.setAudioComposerCallBack(new AudioComposer.AudioComposerCallBack() {
            @Override
            public void onPcmPath(String path) {
                indexOk[0]=true;
                pcmMixPath[0] = path;
            }

            @Override
            public void onAccPath(String path) {

            }
        });
    }

    private void initVideoComposer(MediaBindInfo bindInfo) {
        videoComposer = new VideoComposer(bindInfo.getMediaBeans(),
                bindInfo.getFilter(),
                bindInfo.getDuration(),
                bindInfo.getOutputWidth(),
                bindInfo.getOutputHeight(),
                videoOutFilePath);
        videoComposer.setVideoComposerCallBack(new VideoComposer.VideoComposerCallBack() {
            @Override
            public void onh264Path(String path) {

            }
        });
    }

    private void initBgmComposer(MediaBindInfo info) {
        if (info.getBgm()==null)return;
        long mDuration = info.getDuration();
        MediaBean bgm = info.getBgm();

        if (mDuration <= 0) {
            mDuration = bgm.getDuration();
        }

        //calculate the mediaBeans's number ,add the repeatBgm file
        List<MediaBean> mediaBeans = new ArrayList<>();
        setMediaBeansCount(mDuration, bgm, mediaBeans);

        bgmComposer = new AudioComposer(mediaBeans, mDuration, bgmOutFilePath, true);
        bgmComposer.setAudioComposerCallBack(new AudioComposer.AudioComposerCallBack() {
            @Override
            public void onPcmPath(String path) {
                indexOk[1]=true;
                pcmMixPath[1] = path;
            }

            @Override
            public void onAccPath(String path) {

            }
        });
    }

    private void setMediaBeansCount(long mDuration, MediaBean bgm, List<MediaBean> mediaBeans) {
        long endTime;
        try {

            int count = 2;//(int) (mDuration / bgm.getDuration());
            for (int i = 0; i < count; i++) {
                mediaBeans.add(bgm.clone());
            }

            endTime = mDuration % bgm.getDuration();
            Log.d(TAG, "initBgmComposer: endTime:" + endTime);
            if (endTime != 0) {
                MediaBean mediaBean = bgm.clone();
                mediaBean.setTime(bgm.getDuration(), 0, endTime);
                mediaBeans.add(mediaBean);
            }

        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
    }

    private void initMediaFileMuxer() {
        mediaFileMuxer = new MediaFileMuxer(finalOutFilePath);
    }
}
