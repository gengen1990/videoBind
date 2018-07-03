package com.hanzi.videobinddemo.bean.model.bgm;

import java.io.File;

public class BgmModel {
    private static final String BGM_EFFECT_FOLDER = "bgm" + File.separator;
    public static final String CUSTOM_BGM = "custom";
    public static final String NORMAL_BGM = "normal";
    public String id;
    public String name;

    public String getSingerName() {
        return singerName;
    }

    public void setSingerName(String singerName) {
        this.singerName = singerName;
    }

    public String singerName = "unknow";
    public String iconImagePath;

    public String getBgmPath() {
        return BGM_EFFECT_FOLDER + this.id + File.separator + this.name + ".aac";
    }

    public String getIconImagePath() {
        return "file:///android_asset/"+BGM_EFFECT_FOLDER + this.id + File.separator + this.name + ".png";
    }
}
