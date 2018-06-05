package com.hanzi.videobinddemo.core;

import android.app.Application;
import android.content.Context;


/**
 * Created by qqche_000 on 2017/8/6.
 */

public class MyApplication extends Application {
    private static Context mContext;
//    public static int screenWidth;
//    public static int screenHeight;
    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
//        DisplayMetrics mDisplayMetrics = getApplicationContext().getResources()
//                .getDisplayMetrics();
//        SharedPreferencesUtil.getInstance(this,"VideoEditor");
//        screenWidth = mDisplayMetrics.widthPixels;
//        screenHeight = mDisplayMetrics.heightPixels;
//        MyUncaughtExceptionHandler.getInstance().init(this);

    }

    public static Context getContext() {
        return mContext;
    }
}
