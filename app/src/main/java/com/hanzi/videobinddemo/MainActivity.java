package com.hanzi.videobinddemo;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.hanzi.videobinddemo.core.MyApplication;
import com.hanzi.videobinddemo.filter.AFilter;
import com.hanzi.videobinddemo.filter.NoFilter;
import com.hanzi.videobinddemo.media.MediaBind;
import com.hanzi.videobinddemo.model.effect.CustomTextDrawingModel;
import com.hanzi.videobinddemo.model.effect.DrawingModel;
import com.hanzi.videobinddemo.model.effect.EffectModel;
import com.hanzi.videobinddemo.model.effect.OverlayEffectDrawingModel;
import com.hanzi.videobinddemo.model.filter.FilterLibrary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private final static String TAG = "MainActivity";
    private Button merge, stop, destory;
    private TextView audioType, audioRate, videoType, videoRate, combineRate;
    private CheckBox cbAudio, cbVideo, cbFilter, cbEffect, cbBgm, cbOrigin, cbNum1, cbNum2, cbTotalLen1, cbTotalLen2;
    private EditText et_startTime1, et_startTime2, et_endTime1, et_endTime2;
    private MediaBind mediaBind;
    private static String PATH = Environment.getExternalStorageDirectory().getAbsolutePath();
    private String finalinputFilePath1 = PATH + "/1/ice.mp4";
    private String finalinputFilePath2 = PATH + "/1/sw12.mp4";
    private String bgmPath = PATH + "/1/Christmas_Story.aac";//Christmas_Story.aac

    private int objectEditWidth = 400, objectEditHeight = 200;

    public int objectEditInitX = 100;
    public int objectEditInitY = 100;

    private DrawingModel drawingModel;

    boolean isAudio = false;
    boolean isVideo = false;
    boolean isFilter = false;
    boolean isEffect = false;
    private boolean isOrigin = false;
    private boolean isBgm = false;
    private boolean isNum1 = false;
    private boolean isNum2 = false;
    private boolean isTotalLen1 = false;
    private boolean isTotalLen2 = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        merge = findViewById(R.id.merger);
        destory = findViewById(R.id.destory);
        stop = findViewById(R.id.stop);

        audioType = findViewById(R.id.audioType);
        audioRate = findViewById(R.id.audioRate);

        videoRate = findViewById(R.id.videoRate);
        videoType = findViewById(R.id.videoType);

        combineRate = findViewById(R.id.combineRate);

        cbAudio = findViewById(R.id.cb_audio);
        cbVideo = findViewById(R.id.cb_video);
        cbBgm = findViewById(R.id.cb_bgm);
        cbOrigin = findViewById(R.id.cb_origin);
        cbEffect = findViewById(R.id.cb_effect);
        cbFilter = findViewById(R.id.cb_filter);
        cbNum1 = findViewById(R.id.cb_num1);
        cbNum2 = findViewById(R.id.cb_num2);
        cbTotalLen1 = findViewById(R.id.cb_totalLen1);
        cbTotalLen2 = findViewById(R.id.cb_totalLen2);
        et_endTime1 = findViewById(R.id.et_endTime1);
        et_endTime2 = findViewById(R.id.et_endTime2);
        et_startTime1 = findViewById(R.id.et_startTime1);
        et_startTime2 = findViewById(R.id.et_startTime2);

        initListener();

        initData();

        addOverlayEffectDrawingModel("20981");
    }

    private void initData() {
        isVideo = cbVideo.isChecked();
        isAudio = cbAudio.isChecked();

        isFilter = cbFilter.isChecked();
        isEffect = cbEffect.isChecked();

        isBgm = cbBgm.isChecked();
        isOrigin = cbOrigin.isChecked();

        isNum1 = cbNum1.isChecked();
        isNum2 = cbNum2.isChecked();

        isTotalLen1 = cbTotalLen1.isChecked();
        isTotalLen2 = cbTotalLen2.isChecked();
    }

    private void initListener() {
        destory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaBind != null) {
                    mediaBind.destory();
                    mediaBind = null;
                }
            }
        });
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
                if (mediaBind != null) {
                    mediaBind.stop();
//                    beStop=true;
//                    mediaBind.destory();
//                    mediaBind = null;
                }
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        merge.setText("合并");
                        audioRate.setText(0 + "");
                        videoRate.setText(0 + "");
                        combineRate.setText(0 + "");
                    }
                });

            }
        });


        cbAudio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    isAudio = true;
                    if (!isBgm)
                    setCbOrigin(true);

                } else {
                    isAudio = false;
                    setCbOrigin(false);
                    setCbBgm(false);
                }
                Log.i(TAG, "onCheckedChanged: isAudio:" + isAudio);
            }
        });

        cbVideo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    isVideo = true;
                } else {
                    isVideo = false;
                    setCbEffect(false);
                    setCbFilter(false);
                }
                Log.i(TAG, "onCheckedChanged: isVideo:" + isVideo);
            }
        });
        cbFilter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    isFilter = true;
                    setCbVideo(true);
                } else {
                    isFilter = false;
                }
            }
        });
        cbEffect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    isEffect = true;
                    setCbVideo(true);
                } else {
                    isEffect = false;
                }
            }
        });
        cbOrigin.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    isOrigin = true;
                    setCbAudio(true);
                } else {
                    isOrigin = false;
                    if (!isBgm) {
                        setCbAudio(false);
                    }
                }
            }
        });
        cbBgm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    isBgm = true;
                    setCbAudio(true);
                } else {
                    isBgm = false;
                    if (!isOrigin) {
                        setCbAudio(false);
                    }
                }
            }
        });
        cbNum1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    isNum1 = true;
                    if (!isNum2 && !isAudio)
                    setCbVideo(true);
                } else {
                    isNum1 = false;
                    if (!isNum2) {
                        setCbVideo(false);
                        setCbAudio(false);
                    }
                    setTotalLen1(false);
                }
            }
        });
        cbNum2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    isNum2 = true;
                    if (!isNum1 && !isAudio)
                        setCbVideo(true);
                } else {
                    isNum2 = false;
                    if (!isNum1) {
                        setCbVideo(false);
                        setCbAudio(false);
                    }
                    setTotalLen2(false);
                }
            }
        });
        cbTotalLen1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    isTotalLen1 = true;
                    setCbNum1(true);
                } else {
                    isTotalLen1 = false;
                }
            }
        });
        cbTotalLen2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    isTotalLen2 = true;
                    setCbNum2(true);
                } else {
                    isTotalLen2 = false;
                }
            }
        });

    }

    private void setCbVideo(boolean isVideo) {
        this.isVideo=isVideo;
        cbVideo.setChecked(isVideo);
    }

    private void setCbNum1(boolean isNum1) {
        this.isNum1 = isNum1;
        cbNum1.setChecked(isNum1);
    }

    private void setCbNum2(boolean isNum2) {
        this.isNum2 = isNum2;
        cbNum2.setChecked(isNum2);
    }

    private void setCbEffect(boolean isEffect) {
        this.isEffect = isEffect;
        cbEffect.setChecked(isEffect);
    }

    private void setCbFilter(boolean isFilter) {
        this.isFilter = isFilter;
        cbFilter.setChecked(isFilter);
    }

    private void setTotalLen1(boolean isTotalLen) {
        this.isTotalLen1 = isTotalLen;
        cbTotalLen1.setChecked(isTotalLen);
    }

    private void setTotalLen2(boolean isTotalLen) {
        this.isTotalLen2 = isTotalLen;
        cbTotalLen2.setChecked(isTotalLen);
    }

    private void setCbAudio(boolean isAudio) {
        this.isAudio = isAudio;
        cbAudio.setChecked(isAudio);
    }

    private void setCbBgm(boolean isBgm) {
        this.isBgm = isBgm;
        cbBgm.setChecked(isBgm);
    }

    private void setCbOrigin(boolean isOrigin) {
        this.isOrigin = isOrigin;
        cbOrigin.setChecked(isOrigin);
    }

    private void startCompose() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int processStragey = MediaBind.BOTH_PROCESS;
                Log.i(TAG, "run: isAudio:" + isAudio);
                Log.i(TAG, "run: isVideo:" + isVideo);
                if (isAudio && !isVideo) processStragey = MediaBind.ONLY_AUDIO_PROCESS;
                if (!isAudio && isVideo) processStragey = MediaBind.ONLY_VIDEO_PROCESS;
                Log.i(TAG, "run: progressStragey:" + processStragey);
                mediaBind = new MediaBind(processStragey);
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
                                Log.i(TAG, "run: content:" + content);
                                audioType.setText(content);
                            }
                        });
                    }

                    @Override
                    public void onVideoRate(final int rate) {
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                videoRate.setText(rate + "");
                            }
                        });
                    }

                    @Override
                    public void onAudioRate(final int rate) {
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.i(TAG, "run: rate:" + rate);
                                audioRate.setText(rate + "");
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
                                combineRate.setText(rate + "");
                            }
                        });

                    }
                });
                MediaBindInfo mediaBindInfo = new MediaBindInfo();

                List<MediaBean> mediaBeans = new ArrayList<>();

                MediaBean mediaBean1 = new MediaBean(finalinputFilePath1, 0);
                if (!isTotalLen1) {
                    Log.i(TAG, "!run: isTotalLen1");
                    long startTime = Long.parseLong(et_startTime1.getText().toString());
                    long endTime = Long.parseLong(et_endTime1.getText().toString());
                    mediaBean1.setTimeUs(startTime * 1000000, endTime * 1000000);
                }

                MediaBean mediaBean2 = new MediaBean(finalinputFilePath2, 0);
                if (!isTotalLen2) {
                    Log.i(TAG, "!run: isTotalLen2");
                    long startTime = Long.parseLong(et_startTime2.getText().toString());
                    long endTime = Long.parseLong(et_endTime2.getText().toString());
                    mediaBean2.setTimeUs(startTime * 1000000, endTime * 1000000);
                }


                if (isNum1) {
                    Log.i(TAG, "run: isNum1");
                    mediaBeans.add(mediaBean1);
                }
                if (isNum2) {
                    Log.i(TAG, "run: isNum2");
                    mediaBeans.add(mediaBean2);
                }
                if (isFilter) {
                    AFilter beatlesFilter = FilterLibrary.getInstance().getFilter("Star");
                    mediaBindInfo.setFilter(beatlesFilter);
                } else {
                    mediaBindInfo.setFilter(new NoFilter(MainActivity.this.getResources()));
                }
                if (isOrigin) {
                    mediaBindInfo.setMute(false);
                } else {
                    mediaBindInfo.setMute(true);
                }

                if (isEffect) {
                    setFrame(drawingModel, mediaBeans);
                }

                mediaBindInfo.setMediaBeans(mediaBeans);


                if (isBgm) {
                    mediaBindInfo.setBgm(new MediaBean(bgmPath, 0));
                }


//                mediaBindInfo.setOutputWidth(960);
//                mediaBindInfo.setOutputHeight(720);
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

    private void setDrawingModelLayout(DrawingModel drawingModel, MediaBean.EffectInfo bean) {
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

        float scale = 1;//((float) viewH) / videoH;
        float videoMarginW = 10;//(viewW - videoW * scale) / 2;//缩放多出的左边距
        bean.x = (bean.x - videoMarginW) / scale;
        bean.y = bean.y / scale;
        bean.w = (int) (bean.w / scale);
        bean.h = (int) (bean.h / scale);

        bean.scale = drawingModel.getScale();
    }

    private void setFrame(DrawingModel drawingModel, List<MediaBean> mediaBeans) {
        long drawingInitialTimeUs = drawingModel.getInitialTimeUs();
        long drawingEndTimeUs = drawingModel.getInitialTimeUs() + drawingModel.getDurationUs();

        long cutTotalPre = 0;//前面几个视频的总长度
        for (int i = 0; i < mediaBeans.size(); i++) {

            MediaBean.EffectInfo bean = new MediaBean.EffectInfo();
            bean.effectStartTimeMs = drawingModel.getInitialTimeUs() / 1000;
            bean.effectEndTimeMs = drawingModel.getInitialTimeUs() / 1000 + drawingModel.getDurationUs() / 1000;
            bean.bitmaps.addAll(Arrays.asList(drawingModel.getBitmaps()).subList(0, drawingModel.getBitmapCount()));
            Log.i(TAG, "addEffectInfoData: drawingModel.getBitmapCount():" + drawingModel.getBitmapCount());
            bean.intervalMs = 0;
            if (drawingModel instanceof CustomTextDrawingModel) {
                bean.intervalMs = 1000;
            } else {
                bean.intervalMs = (int) (drawingModel.getFrameTimeUs() / 1000);
            }
            bean.id = drawingModel.getDrawingModelId();
            setDrawingModelLayout(drawingModel, bean);


            long duration = mediaBeans.get(i).getDurationUs();
            long startTimeUs = mediaBeans.get(i).getStartTimeUs();
            long endTimeUs = mediaBeans.get(i).getEndTimeUs();
            long cutDurationUs = 0;

            if (endTimeUs == -1) {
                cutDurationUs = duration - startTimeUs;
            } else {
                cutDurationUs = endTimeUs - startTimeUs;
            }

            Log.i(TAG, "addEffectInfoData: drawingInitialTimeUs:" + drawingInitialTimeUs);
            Log.i(TAG, "addEffectInfoData: drawingEndTimeUs:" + drawingEndTimeUs);
            Log.i(TAG, "addEffectInfoData: cutDurationUs:" + cutDurationUs);
            Log.i(TAG, "addEffectInfoData: cutTotalPre:" + cutTotalPre);

            float frame1 = -1, frame2 = -1;
            if (drawingInitialTimeUs < cutDurationUs + cutTotalPre && drawingInitialTimeUs >= cutTotalPre) {
                frame1 = (float) (drawingInitialTimeUs - cutTotalPre) / 1000000;
            } else if (drawingInitialTimeUs < cutTotalPre && drawingEndTimeUs > cutTotalPre) {
                frame1 = 0;
            }

            if (drawingEndTimeUs > cutTotalPre + cutDurationUs && drawingInitialTimeUs < cutTotalPre + cutDurationUs) {
                frame2 = (float) cutDurationUs / 1000000;
            } else if (drawingEndTimeUs < cutTotalPre + cutDurationUs && drawingEndTimeUs >= cutTotalPre) {
                frame2 = (float) (drawingEndTimeUs - cutTotalPre) / 1000000;
            }

            Log.i(TAG, "addEffectInfoData: frame1:" + frame1);
            Log.i(TAG, "addEffectInfoData: frame2:" + frame2);

            if (frame1 != -1 && frame2 != -1 && frame1 != frame2) {
                bean.videoFrameTimeSList.clear();
                bean.videoFrameTimeSList.add(frame1);
                bean.videoFrameTimeSList.add(frame2);
                mediaBeans.get(i).getEffectInfos().add(bean);
            }

            cutTotalPre += cutDurationUs;
//            bean.videoFrameTimeSList.clear();
        }

    }


//    private MediaBean.EffectInfo addEffectInfoData(DrawingModel drawingModel) {
//        MediaBean.EffectInfo bean = new MediaBean.EffectInfo();
//        if (drawingModel != null) {
//            bean.effectStartTimeMs = drawingModel.getInitialTimeUs() / 1000;
//            bean.effectEndTimeMs = drawingModel.getInitialTimeUs() / 1000 + drawingModel.getDurationUs() / 1000;
//            bean.bitmaps.addAll(Arrays.asList(drawingModel.getBitmaps()).subList(0, drawingModel.getBitmapCount()));
//            Log.i(TAG, "addEffectInfoData: drawingModel.getBitmapCount():" + drawingModel.getBitmapCount());
//            bean.intervalMs = (int) (drawingModel.getFrameTimeUs() / 1000);
//            bean.id = drawingModel.getDrawingModelId();
////            bean.x = drawingModel.getLeftTopX();
////            bean.y = drawingModel.getLeftTopY();
//
//            bean.angle = drawingModel.getRotation();
//            if (bean.angle >= 0 && bean.angle <= 90) {
//                bean.x = drawingModel.getLeftBottomX();
//                bean.y = drawingModel.getLeftTopY();
//            } else if (bean.angle < 0 && bean.angle > -90) {
//                bean.x = drawingModel.getLeftTopX();
//                bean.y = drawingModel.getRightTopY();
//            } else if (bean.angle <= -90 && bean.angle > -180) {
//                bean.x = drawingModel.getRightTopX();
//                bean.y = drawingModel.getRightBottomY();
//            } else if (bean.angle > 90 && bean.angle <= 180) {
//                bean.x = drawingModel.getRightBottomX();
//                bean.y = drawingModel.getLeftBottomY();
//            }
//            bean.w = drawingModel.getWidth();
//            bean.h = drawingModel.getHeight();
//
//            //根据视频缩放比例对贴图的位置及大小进行调整
////            com.google.android.exoplayer2.Format format = binding.videoView.getFormat();
////            int videoW = format.width;
////            int videoH = format.height;
////
////            int viewW = binding.videoView.getWidth();
////            int viewH = binding.videoView.getHeight();
//
//            float scale = 1;//((float) viewH) / videoH;
//            float videoMarginW = 10;//(viewW - videoW * scale) / 2;//缩放多出的左边距
//            bean.x = (bean.x - videoMarginW) / scale;
//            bean.y = bean.y / scale;
//            bean.w = (int) (bean.w / scale);
//            bean.h = (int) (bean.h / scale);
//
//            bean.scale = drawingModel.getScale();
//            int frameTime1 = (int) (bean.effectStartTimeMs / 1000);
//            int frameTime2 = (int) (bean.effectEndTimeMs / 1000);
////            if (bean.effectStartTimeMs == 0) {
////                frame2 = (int) (bean.effectEndTimeMs);
////            } else {
////                frame2 = (int) (frame1 * bean.effectEndTimeMs / );// (int)(bean.effectEndTimeMs* mVideoView.getFrameRate(0));
////            }
//            bean.videoFrameTimeSList.add(frameTime1);
//            bean.videoFrameTimeSList.add(frameTime2);
//            Log.d(TAG, "addEffectInfoData: angle:" + bean.angle
//                    + ",x:" + bean.x + ",y:" + bean.y
//                    + ",w:" + bean.w + ",h:" + bean.h
//                    + ",frameTime1:" + frameTime1
//                    + ",frameTime2:" + frameTime2
//                    + ",scale:" + bean.scale);
//        }
//        return bean;
//    }
}
