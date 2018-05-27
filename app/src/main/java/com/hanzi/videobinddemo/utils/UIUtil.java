package com.hanzi.videobinddemo.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

import com.hanzi.videobinddemo.model.Point;

import java.util.Collection;
import java.util.UUID;

import static android.content.Context.WINDOW_SERVICE;

public class UIUtil {
    public static int dip2px(Context context, int dip) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) ((float) dip * scale + 0.5F);
    }

    public static int getScreenWidth(Context context) {
        DisplayMetrics metric = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metric);
        return metric.widthPixels;
    }

    public static int getScreenHeight(Context context) {
        DisplayMetrics metric = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metric);
        return metric.heightPixels;
    }

    public static double calculateAngle(Point centerPoint, Point prePoint, Point cutPoint) {
        prePoint.x -= centerPoint.x;
        prePoint.y -= centerPoint.y;
        cutPoint.x -= centerPoint.x;
        cutPoint.y -= centerPoint.y;
        centerPoint = new Point(0.0F, 0.0F);

        double a = distance4PointF(centerPoint, prePoint);
        double b = distance4PointF(prePoint, cutPoint);
        double c = distance4PointF(centerPoint, cutPoint);

        double cosb = (a * a + c * c - b * b) / (2 * a * c);

        if (cosb >= 1) {
            cosb = 1f;
        }

        double radian = Math.acos(cosb);

        float newDegree = (float) radianToDegree(radian);

        Point centerToProMove = new Point((prePoint.x - centerPoint.x), (prePoint.y - centerPoint.y));

        Point centerToCurMove = new Point((cutPoint.x - centerPoint.x), (cutPoint.y - centerPoint.y));

        float result = centerToProMove.x * centerToCurMove.y - centerToProMove.y * centerToCurMove.x;

        if (result < 0) {
            newDegree = -newDegree;
        }
        return newDegree;
    }

    private static double distance4PointF(Point pf1, Point pf2) {
        float disX = pf2.x - pf1.x;
        float disY = pf2.y - pf1.y;
        return Math.sqrt(disX * disX + disY * disY);
    }

    public static double radianToDegree(double radian) {
        return radian * 180 / Math.PI;
    }

    public static String generateUUID(boolean paramBoolean) {
        String str2 = UUID.randomUUID().toString();
        String str1 = str2;
        if (paramBoolean) {
            str1 = str2.replaceAll("\\-", "");
        }
        return str1;
    }


    public static boolean isEmpty(Collection paramCollection) {
        return (paramCollection == null) || (paramCollection.isEmpty());
    }

    public static Bitmap getViewBitmap(View view, int width, int height)
    {
        if ((width == 0) || (height == 0)) {
            return null;
        }
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }
}
