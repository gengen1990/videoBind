package com.hanzi.videobinddemo.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Environment;
import android.util.Log;

import com.hanzi.videobinddemo.media.Variable.MediaBean;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Create by xjs
 * _______date : 18/3/15
 * _______description:
 */
public class BitmapUtils {
    private static final String TAG = BitmapUtils.class.getSimpleName();
    /**
     * 多张bitmap叠加
     *
     * @param infos
     * @param width
     * @param height
     * @return
     */
    public static Bitmap bitmapMix(Context context, List<MediaBean.EffectInfo> infos, int width, int height) {
        boolean isDisplay = false;

        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        for (MediaBean.EffectInfo data : infos) {
            Log.d(TAG, "bitmapMix: data.id:"+data.id+" data.effectPos:"+data.effectPos);
            if (data.effectPos > -1) {
                isDisplay = true;
                //获取bitmap
                Bitmap bitmap = data.bitmaps.get(data.effectPos);//BitmapFactory.decodeResource(context.getResources(), data.path_id.get(data.effectPos));
                Matrix matrix = new Matrix();

                bitmap = BitmapUtils.convert(bitmap, bitmap.getWidth(), bitmap.getHeight());

                //缩放
                matrix.postScale(data.scale,
                        data.scale);

                matrix.postRotate(1.0f * data.angle);
//                matrix.setTranslate(width,height);
                //旋转
//                bitmap_1 = BitmapUtils.rotateBitmap(bitmap_1,1.0f * info.data.get(0).angle);
//                bitmap_2 = BitmapUtils.rotateBitmap(bitmap_2,1.0f * info.data.get(1).angle);

                //创建新的bitmap
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                //绘制画布
                canvas.drawBitmap(bitmap, data.x, data.y, null);

                //回收bitmap
                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        }
        canvas.setMatrix(null);
        canvas.save(Canvas.ALL_SAVE_FLAG);
        canvas.restore();  //抗锯齿
        if (!isDisplay) {
            return null;
        } else {
            return out;
        }
    }

    /**
     * 翻转
     *
     * @param origin
     * @param ww
     * @param wh
     * @return
     */
    public static Bitmap convert(Bitmap origin, int ww, int wh) {
        if (origin == null) {
            return null;
        }
        int w = origin.getWidth();
        int h = origin.getHeight();
        Bitmap newb = Bitmap.createBitmap(ww, wh, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(newb);
        Matrix m = new Matrix();
//        m.postScale(1, -1);   //镜像垂直翻转
//        m.postScale(-1, 1);   //镜像水平翻转
//        m.postRotate(-90);  //旋转-90度
        Bitmap new2 = Bitmap.createBitmap(origin, 0, 0, w, h, m, true);
        cv.drawBitmap(new2, new Rect(0, 0, new2.getWidth(), new2.getHeight()), new Rect(0, 0, ww, wh), null);
//        if (!origin.isRecycled()) {
//            origin.recycle();
//        }
        if (!new2.isRecycled()) {
            new2.recycle();
        }
        return newb;
    }

    /**
     * 旋转
     *
     * @param origin
     * @param angle
     * @return
     */
    public static Bitmap rotateBitmap(Bitmap origin, float angle) {
        if (origin == null) {
            return null;
        }
        int width = origin.getWidth();
        int height = origin.getHeight();
        Matrix matrix = new Matrix();
        matrix.setRotate(angle);
        Bitmap newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        if (newBM.equals(origin)) {
            return newBM;
        }
        origin.recycle();
        return newBM;
    }

    public static void test(Bitmap bitmap, int pos) {
        String name = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + pos + ".png";
        File file = new File(name);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        if (file.exists()) {
            file.delete();
        }

        try {
            FileOutputStream out = new FileOutputStream(name);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }
}
