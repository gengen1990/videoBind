package com.hanzi.videobinddemo;

import android.os.Environment;

import com.hanzi.videobinddemo.core.MyApplication;

import java.io.File;

/**
 * Create by xjs
 * _______date : 18/3/26
 * _______description:
 */
public class Constants {

    public static String getBaseFolder() {
        String baseFolder = Environment.getExternalStorageDirectory().getAbsolutePath() + "/1/Codec/";
        File f = new File(baseFolder);
        if (!f.exists()) {
            boolean b = f.mkdirs();
            if (!b) {
                baseFolder = MyApplication.getContext().getExternalFilesDir(null).getAbsolutePath() + "/";
            }
        }
        return baseFolder;
    }
    //获取VideoPath
    public static String getPath(String path, String fileName) {
        String p = getBaseFolder() + path;
        File f = new File(p);
        if (!f.exists() && !f.mkdirs()) {
            return getBaseFolder() + fileName;
        }
        return p + fileName;
    }

}
