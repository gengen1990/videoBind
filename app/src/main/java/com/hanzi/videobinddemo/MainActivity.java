package com.hanzi.videobinddemo;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.hanzi.videobinddemo.core.MyApplication;
import com.hanzi.videobinddemo.filter.AFilter;
import com.hanzi.videobinddemo.media.MediaBind;
import com.hanzi.videobinddemo.media.Variable.MediaBean;
import com.hanzi.videobinddemo.media.Variable.MediaBindInfo;
import com.hanzi.videobinddemo.model.effect.DrawingModel;
import com.hanzi.videobinddemo.model.effect.EffectModel;
import com.hanzi.videobinddemo.model.effect.OverlayEffectDrawingModel;
import com.hanzi.videobinddemo.model.filter.FilterLibrary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private final static String TAG="MainActivity";
    private Button merge,stop;
    private TextView audioType ,audioRate, videoType,videoRate, combineRate;

    private MediaBind mediaBind;
    private static String PATH = Environment.getExternalStorageDirectory().getAbsolutePath();
    private String finalinputFilePath1 = PATH + "/1/water11.mp4";
    private String finalinputFilePath2 = PATH + "/1/water12.mp4";
    private String bgmPath = PATH + "/1/Christmas_Story.aac";//Christmas_Story.aac

    private int objectEditWidth = 400, objectEditHeight = 200;

    public int objectEditInitX = 100;
    public int objectEditInitY = 100;

    private DrawingModel drawingModel;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        TextView tv = (TextView) findViewById(R.id.sample_text);
        merge = findViewById(R.id.merger);
        stop = findViewById(R.id.stop);

        audioType=findViewById(R.id.audioType);
        audioRate = findViewById(R.id.audioRate);

        videoRate=findViewById(R.id.videoRate);
        videoType = findViewById(R.id.videoType);

        combineRate=findViewById(R.id.combineRate);

        merge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCompose();
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        merge.setText("开始");
                    }
                });
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaBind!=null) {
                    mediaBind.stop();
//                    mediaBind.destory();
//                    mediaBind = null;
                }
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        merge.setText("合并");
                    }
                });

            }
        });

        addOverlayEffectDrawingModel("20981");
    }

    private void startCompose() {
        new Thread( new Runnable() {
            @Override
            public void run() {
                mediaBind = new MediaBind(MediaBind.BOTH_PROCESS);
                mediaBind.setCallback(new MediaBind.MediaBindCallback() {
                    @Override
                    public void onVideoType(final String content) {
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                videoType.setText(content);
                            }
                        });
                    }

                    @Override
                    public void onAudioType(final String content) {
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.i(TAG, "run: content:"+content);
                                audioType.setText(content);
                            }
                        });
                    }

                    @Override
                    public void onVideoRate(final int rate) {
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                videoRate.setText(rate+"");
                            }
                        });
                    }

                    @Override
                    public void onAudioRate(final int rate) {
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.i(TAG, "run: rate:"+rate);
                                audioRate.setText(rate+"");
                            }
                        });
                    }

                    @Override
                    public void callback(final String content) {
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                merge.setText(content);
                            }
                        });
                    }

                    @Override
                    public void onCombineRate(final int rate) {
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                combineRate.setText(rate+"");
                            }
                        });

                    }
                });
                MediaBindInfo mediaBindInfo = new MediaBindInfo();

                List<MediaBean> mediaBeans = new ArrayList<>();


                MediaBean mediaBean1 = new MediaBean(finalinputFilePath1, 0);
                mediaBean1.setTimeUs(2000000,9000000);
                List<MediaBean.EffectInfo> effectInfos =new ArrayList<>();
                MediaBean.EffectInfo effectInfo=addEffectInfoData(drawingModel);
                effectInfos.add(effectInfo);
                mediaBean1.setEffectInfos(effectInfos);

                MediaBean mediaBean2 =new MediaBean(finalinputFilePath2, 0);

                mediaBeans.add(mediaBean1);
                mediaBeans.add(mediaBean2);
                AFilter beatlesFilter= FilterLibrary.getInstance().getFilter("Beatles");
                mediaBindInfo.setFilter(beatlesFilter);
//                mediaBindInfo.setFilter(new NoFilter(MainActivity.this.getResources()));

//                mediaBindInfo.setMute(false);

                mediaBindInfo.setMediaBeans(mediaBeans);

                mediaBindInfo.setBgm(new MediaBean(bgmPath, 0));

                mediaBind.open(mediaBindInfo);
                mediaBind.start();
            }
        }).start();
    }

    public void addOverlayEffectDrawingModel(String effectId) {

        drawingModel = DrawingModel.createOverlayEffectDrawingModel(MyApplication.getContext(),
                objectEditWidth, objectEditHeight,
                objectEditInitX, objectEditInitY, effectId, new OverlayEffectDrawingModel.OnEffectDrawingModelListener() {
                    @Override
                    public void onLoadEffectModelError(EffectModel paramEffectModel) {
                    }

                    @Override
                    public void onLoadEffectBitmapSuccess() {
                        drawingModel.setTransform(objectEditInitX, objectEditInitY, 1, 0);
                        drawingModel.setSegmentTime(1 * 1000,
                                1 * 1000, 5000000);
//                        drawingModelLiveData.setValue(drawingModel);
//                        canEditDrawingModelId.add(drawingModel.getDrawingModelId());
                    }
                });
    }

    private MediaBean.EffectInfo addEffectInfoData(DrawingModel drawingModel) {
        MediaBean.EffectInfo bean = new MediaBean.EffectInfo();
        if (drawingModel != null) {
            bean.effectStartTimeMs = drawingModel.getInitialTimeUs() / 1000;
            bean.effectEndTimeMs = drawingModel.getInitialTimeUs() / 1000 + drawingModel.getDurationUs() / 1000;
            bean.bitmaps.addAll(Arrays.asList(drawingModel.getBitmaps()).subList(0, drawingModel.getBitmapCount()));
            Log.i(TAG, "addEffectInfoData: drawingModel.getBitmapCount():"+drawingModel.getBitmapCount());
            bean.intervalMs = (int) (drawingModel.getFrameTimeUs() / 1000);
            bean.id = drawingModel.getDrawingModelId();
//            bean.x = drawingModel.getLeftTopX();
//            bean.y = drawingModel.getLeftTopY();

            bean.angle = drawingModel.getRotation();
            if (bean.angle >= 0 && bean.angle <= 90) {
                bean.x = drawingModel.getLeftBottomX();
                bean.y = drawingModel.getLeftTopY();
            } else if (bean.angle < 0 && bean.angle > -90) {
                bean.x = drawingModel.getLeftTopX();
                bean.y = drawingModel.getRightTopY();
            } else if (bean.angle <= -90 && bean.angle > -180) {
                bean.x = drawingModel.getRightTopX();
                bean.y = drawingModel.getRightBottomY();
            } else if (bean.angle > 90 && bean.angle <= 180) {
                bean.x = drawingModel.getRightBottomX();
                bean.y = drawingModel.getLeftBottomY();
            }
            bean.w = drawingModel.getWidth();
            bean.h = drawingModel.getHeight();

            //根据视频缩放比例对贴图的位置及大小进行调整
//            com.google.android.exoplayer2.Format format = binding.videoView.getFormat();
//            int videoW = format.width;
//            int videoH = format.height;
//
//            int viewW = binding.videoView.getWidth();
//            int viewH = binding.videoView.getHeight();

            float scale = 1;//((float) viewH) / videoH;
            float videoMarginW = 10;//(viewW - videoW * scale) / 2;//缩放多出的左边距
            bean.x = (bean.x - videoMarginW) / scale;
            bean.y = bean.y / scale;
            bean.w = (int) (bean.w / scale);
            bean.h = (int) (bean.h / scale);

            bean.scale = drawingModel.getScale();
            int frameTime1 = (int)  (bean.effectStartTimeMs / 1000);
            int frameTime2 = (int)  (bean.effectEndTimeMs / 1000);
//            if (bean.effectStartTimeMs == 0) {
//                frame2 = (int) (bean.effectEndTimeMs);
//            } else {
//                frame2 = (int) (frame1 * bean.effectEndTimeMs / );// (int)(bean.effectEndTimeMs* mVideoView.getFrameRate(0));
//            }
            bean.videoFrameTimeList.add(frameTime1);
            bean.videoFrameTimeList.add(frameTime2);
            Log.d(TAG, "addEffectInfoData: angle:" + bean.angle
                    + ",x:" + bean.x + ",y:" + bean.y
                    + ",w:" + bean.w + ",h:" + bean.h
                    + ",frameTime1:" + frameTime1
                    + ",frameTime2:" + frameTime2
                    + ",scale:" + bean.scale);
        }
        return bean;
    }
}
