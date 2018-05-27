package com.hanzi.videobinddemo.model;

import android.graphics.Matrix;

/**
 * Created by gengen on 2018/3/2.
 */

public class Transform {
    private static final String TAG = Transform.class.getSimpleName();
    private Point center;
    private float degrees;
    private float diagonal;
    private float[] fMatrix = new float[9];
    private Matrix matrix;
    private Point[] points;
    private float scale;
    private float width;
    private float height;

    public void reset() {
        matrix.reset();
        matrix.setTranslate(center.x - width / 2.0F, center.y - height / 2.0F);//位移
        matrix.postScale(scale, scale, center.x, center.y);//放大
        matrix.postRotate(degrees, center.x, center.y);//旋转
        matrix.getValues(fMatrix);
        points[0] = new Point(fMatrix[2], fMatrix[5]);
        points[1] = new Point(fMatrix[1] * height + fMatrix[2], fMatrix[4] * height + fMatrix[5]);
        points[2] = new Point(fMatrix[0] * width + fMatrix[1] * height + fMatrix[2], fMatrix[3] * width + fMatrix[4] * height + fMatrix[5]);
        points[3] = new Point(fMatrix[0] * width + fMatrix[2], fMatrix[3] * width + fMatrix[5]);
        float f1 = center.x - points[2].x;
        float f2 = center.y - points[2].y;
        diagonal = ((float) Math.sqrt(f1 * f1 + f2 * f2));//对角线
    }

    public float calculateDiagonal(float x, float y) {
        x = center.x - x;
        y = center.y - y;
        return (float) Math.sqrt(x * x + y * y);
    }

    public float calculateDiagonalWithRightBottom(float x, float y) {
        x = points[2].x - x;
        y = points[2].y - y;
        return (float) Math.sqrt(x * x + y * y);
    }

    public float calculateDiagonalWithLeftTop(float x, float y) {
        x = points[0].x - x;
        y = points[0].y - y;
        return (float) Math.sqrt(x * x + y * y);
    }

    public float calculateDiagonalWithOtherPoint(float x, float y, int padding) {
        x = points[2].x - padding - x;
        y = points[2].y - padding - y;
        return (float) Math.sqrt(x * x + y * y);
    }

    public Transform copy() {
        Transform localTransform = new Transform();
        localTransform.setTransform(width, height, center.x, center.y, degrees, scale);
        return localTransform;
    }

    public Point getCenterPoint() {
        return center;
    }

    public float getDiagonal() {
        return diagonal;
    }

    public Point getLeftTopPoint() {
        return points[0];
    }

    public Point getRightTopPoint() {
        return points[3];
    }

    public Point getLeftBottomPoint() {
        return points[1];
    }

    public float getMeasuredHeight() {
        return height * scale;
    }

    public float getMeasuredWidth() {
        return width * scale;
    }

    public float getHeight() {
        return height;
    }

    public float getWidth() {
        return width;
    }

    public Point getRightBottomPoint() {
        return points[2];
    }

    public float getRotation() {
        return degrees;
    }

    public float getScale() {
        return scale;
    }

    public float getTranslateX() {
        return center.x - width / 2.0F;
    }

    public float getTranslateY() {
        return center.y - height / 2.0F;
    }

    public void postRotateAndScale(float degree, float scale) {
        degrees += degree;
        degrees = degrees % 360;
        this.scale *= scale;
        reset();
    }

    public void setCenterPoint(float x, float y) {
        center.x = x;
        center.y = y;
        reset();
    }

    public void setScale(float s) {
        scale = s;
        reset();
    }

    public void setTransform(float w, float h) {
        width = w;
        height = h;
        reset();
    }

    public void setTransform(float w, float h, float x, float y, float dg, float s) {
        width = w;
        height = h;
        points = new Point[4];
        matrix = new Matrix();
        center = new Point(x, y);
        degrees = dg;
        scale = s;
        reset();
    }

    public boolean contains(float x, float y) {
        if (this.points == null) {
            return false;
        }

        float[] results = new float[4];
        int k =0;

//        float result0 = (points[1].x - points[0].x)*(y-points[0].y) - (points[1].y-points[0].y)*(x-points[0].x);
//        float result1 = (points[2].x - points[1].x)*(y-points[1].y) - (points[2].y-points[1].y)*(x-points[1].x);
//        float result2 = (points[3].x - points[2].x)*(y-points[2].y) - (points[3].y-points[2].y)*(x-points[2].x);
//        float result3 = (points[0].x - points[3].x)*(y-points[3].y) - (points[0].y-points[3].y)*(x-points[3].x);
        for (int i = 0; i < points.length; i++) {
            results[i] = (points[((i + 1) % points.length)].x - points[i].x) * (y - points[i].y)
                    - (points[((i + 1) % points.length)].y - points[i].y) * (x - points[i].x);

            if (results[i] == 0) {
                return true;
            } else if (results[i]<0){
                k--;
            }else {
                k++;
            }
        }

        if ((k ==4) || (k == -4)) {
            return true;
        }
        return false;
    }


}
