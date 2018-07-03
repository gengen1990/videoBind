package com.hanzi.videobinddemo.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Environment;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * ================================================
 * 作    者：顾修忠-guxiuzhong@youku.com/gfj19900401@163.com
 * 版    本：
 * 创建日期：2017/4/4-下午6:56
 * 描    述：
 * 修订历史：
 * ================================================
 */

public class PictureUtils {
    public static final String POSTFIX = ".jpeg";
    private static final String EDIT_PATH = "/EditVideo/";

    public static String saveImageToSD(Bitmap bmp, String dirPath) {
        if (bmp == null) {
            return "";
        }
        File appDir = new File(dirPath);
        if (!appDir.exists()) {
            appDir.mkdir();
        }
        String fileName = System.currentTimeMillis() + ".jpg";
        File file = new File(appDir, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file.getAbsolutePath();
    }


    public static void saveFromAssets(Context context, int i, int pos, int size, String path){
        try {
            File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+i+".png");
            InputStream inputStream = context.getAssets().open(path);
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            inputStream.close();
            Bitmap bitmap = BitmapFactory.decodeByteArray(buffer,pos,size);
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String saveImageToSDForEdit(Bitmap bmp, String dirPath, String fileName) {
        if (bmp == null) {
            return "";
        }
        File appDir = new File(dirPath);
        if (!appDir.exists()) {
            appDir.mkdir();
        }
        File file = new File(appDir, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file.getAbsolutePath();
    }

    public static void deleteFile(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null && files.length > 0) {
                for (int i = 0; i < files.length; ++i) {
                    deleteFile(files[i]);
                }
            }
        }
        f.delete();
    }

    public static String getSaveEditThumbnailDir(Context context) {
        String state = Environment.getExternalStorageState();
        File rootDir = state.equals(Environment.MEDIA_MOUNTED) ? Environment.getExternalStorageDirectory() : context.getCacheDir();
        File folderDir = new File(rootDir.getAbsolutePath() + EDIT_PATH);
        if (!folderDir.exists() && folderDir.mkdirs()) {

        }
        return folderDir.getAbsolutePath();
    }
    public static String getInternalEffectPath(Context context, String resourceName)
    {
        return getInternalPath(context, "effects", resourceName, "effect");
    }

    private static String getInternalPath(Context context, String effects, String resourceName, String effect)
    {
        File file = new File(context.getFilesDir().getAbsolutePath() + File.separator + effects);
        if (!file.exists()) {
            file.mkdirs();
        }
        if (TextUtils.isEmpty(effect)) {
            return file.getPath() + File.separator + resourceName;
        }
        return file.getPath() + File.separator + resourceName + "." + effect;
    }

    public static Bitmap getVideoThumbnail(String videoPath, int width, int height, int kind) {
        Bitmap bitmap = null;
        // 获取视频的缩略图
        bitmap = ThumbnailUtils.createVideoThumbnail(videoPath, kind);
        if(bitmap!= null){
            bitmap = ThumbnailUtils.extractThumbnail(bitmap, width, height,
                    ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
        }
        return bitmap;
    }
}
