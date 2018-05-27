package com.hanzi.videobinddemo.media;


import android.os.Handler;
import android.os.HandlerThread;

import com.hanzi.videobinddemo.filter.AFilter;
import com.hanzi.videobinddemo.media.Utils.MediaFileMuxer;
import com.hanzi.videobinddemo.media.Utils.extractor.VideoExtractor;
import com.hanzi.videobinddemo.media.Variable.MediaBean;
import com.hanzi.videobinddemo.media.Variable.MediaBindInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gengen on 2018/5/22.
 * 视频合成
 */

public class VideoComposer {
    private final String TAG = "VideoComposer";
    private List<MediaBean> mMediaBeans;
    private AFilter mFilter;
    private List<MediaBindInfo.EffectInfo> mEffectInfos;

    private String outFilePath;

    private List<VideoExtractor> mMediaExtractors = new ArrayList<>();

    private MediaFileMuxer mediaFileMuxer;
    private long mDuration, mRate, mWidth, mHeight;

    private HandlerThread mergeThread;
    private Handler mergeHandler;

    private VideoComposerCallBack videoComposerCallBack;

    public VideoComposer(List<MediaBean> mediaBeans, List<MediaBindInfo.EffectInfo> effectInfos, AFilter filter
            , long duration, long rate, int width, int height, String outFilePath) {
        this.mMediaBeans = mediaBeans;
        this.mEffectInfos = effectInfos;
        this.mFilter = filter;
        this.mDuration = duration;
        this.mRate = rate;
        this.mWidth = width;
        this.mHeight = height;
        this.outFilePath = outFilePath;

        mediaFileMuxer = new MediaFileMuxer(outFilePath);

        for (MediaBean bean : mMediaBeans) {
            VideoExtractor extractor = new VideoExtractor(bean.getUrl(), bean.getStartTimeUs(), bean.getEndTimeUs());
            mMediaExtractors.add(extractor);
        }

        mergeThread = new HandlerThread("merge");
        mergeThread.start();
        mergeHandler = new Handler(mergeThread.getLooper());
    }

    public void setVideoComposerCallBack(VideoComposerCallBack videoComposerCallBack) {
        this.videoComposerCallBack = videoComposerCallBack;
    }


    public interface VideoComposerCallBack {
        public void onh264Path(String path);
    }
}
