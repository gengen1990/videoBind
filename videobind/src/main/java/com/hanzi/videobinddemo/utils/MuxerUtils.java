package com.hanzi.videobinddemo.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaExtractor;

import java.io.IOException;

/**
 * Created by gengen on 2018/4/16.
 */

public class MuxerUtils {

    public static void setDataSource(Context context, MediaExtractor extractor, String assetsPath) {
        try {
            if (assetsPath.startsWith("/android_asset/")) {

                AssetManager am = context.getAssets();
                String tempPath = assetsPath.replaceFirst("/android_asset/", "");
                AssetFileDescriptor afd = null;

                afd = am.openFd(tempPath);

                extractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            } else {
                extractor.setDataSource(assetsPath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getValue(String s, String name) {
        if (s.contains("{"))
            s = s.replace("{", "");
        if (s.contains("}"))
            s = s.replace("}", "");
        String value = "";
        String[] list = s.split(", ");
        for (int m = 0; m < list.length; m++) {
            if (list[m].contains(name)) {
                value = list[m].replace(name, "");
                if (value.contains("=")) {
                    value = value.replace("=", "");
                }
            }
        }
        return value;
    }

}
