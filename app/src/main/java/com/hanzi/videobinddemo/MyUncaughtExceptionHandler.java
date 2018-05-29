package com.hanzi.videobinddemo;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

/**
 * Create by xjs
 * _______date : 17/11/3
 * _______description:
 */
public class MyUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static MyUncaughtExceptionHandler uncaughtExceptionHandler;
    private static String TAG = "MyUncaughtExceptionHandler";
    private Thread.UncaughtExceptionHandler mDefaultHandler;
    private Context context;
    //用来存储设备信息和异常信息
    private Map<String,String> infos = new HashMap<>();

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        if (!handleException(e) && mDefaultHandler != null){
            mDefaultHandler.uncaughtException(t,e);
        }else {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
                //XLog.e(TAG,e1);
                Log.d(TAG, "uncaughtException: "+e1);
            }
            //退出程序
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }
    }

    /**
     * 初始化
     * @param context
     */
    public void init(Context context){
        this.context = context;
        //获取系统默认的UncaughtExceptionHandler
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        //设置程序的默认处理器 uncaughtExceptionHandler
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    /**
     * 错误信息处理类
     * @param ex
     * @return
     */
    private boolean handleException(Throwable ex){
        if (ex == null){
            return false;
        }
        new Thread(){
            @Override
            public void run() {
                Looper.prepare();
                Toast.makeText(context,"程序出现异常，即将退出", Toast.LENGTH_SHORT).show();
                Looper.loop();
            }
        }.start();
        collectDeviceInfo(context);
        saveCrashInfo(ex);
        return true;
    }

    /**
     * 保存设备信息到文件中
     * @param t
     * @return
     */
    private void saveCrashInfo(Throwable t){
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String,String> entry : infos.entrySet()){
            String key = entry.getKey();
            String value = entry.getValue();
            sb.append(key + "=" + value + "\n");
        }

        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        t.printStackTrace(printWriter);
        Throwable cause = t.getCause();
        while (cause != null){
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        printWriter.close();
        String result = writer.toString();
        sb.append(result);
        //XLog.e(sb);
        Log.d(TAG, "collectDeviceInfo: saveCrashInfo:"+sb);
    }

    /**
     * 收集设备参数信息
     * @param context
     */
    public void collectDeviceInfo(Context context){
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_ACTIVITIES);
            if (pi != null){
                String versionName = pi.versionName == null ? "null" : pi.versionName;
                String versionCode = String.valueOf(pi.versionCode);
                infos.put("versionName",versionName);
                infos.put("versionCode",versionCode);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        java.lang.reflect.Field[] fields = Build.class.getDeclaredFields();
        for (java.lang.reflect.Field field : fields){
            try {
                field.setAccessible(true);
                infos.put(field.getName(),field.get(null).toString());
                //XLog.d(TAG, field.getName() + " : " + field.get(null));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                Log.d(TAG, "collectDeviceInfo: error:"+e);
//                XLog.e(TAG,"an error occured when collect crash info", e);
            }
        }
    }

    public static MyUncaughtExceptionHandler getInstance(){
        if (uncaughtExceptionHandler == null){
            uncaughtExceptionHandler = new MyUncaughtExceptionHandler();
        }
        return uncaughtExceptionHandler;
    }

}
