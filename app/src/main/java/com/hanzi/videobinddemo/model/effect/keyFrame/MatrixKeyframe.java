package com.hanzi.videobinddemo.model.effect.keyFrame;

public class MatrixKeyframe {
    public final float interpolatedTime;
    public final float rotation;
    public final float transformX;
    public final float transformY;
    public final float xScale;
    public final float yScale;

    private MatrixKeyframe(float interpolatedTime, float xScale, float yScale, float transformX, float transformY, float rotation) {
        this.interpolatedTime = interpolatedTime;
        this.xScale = xScale;
        this.yScale = yScale;
        this.transformX = transformX;
        this.transformY = transformY;
        this.rotation = rotation;
    }

    public static MatrixKeyframe create(float interpolatedTime, float xScale, float yScale, float transformX, float transformY, float rotation) {
        return new MatrixKeyframe(interpolatedTime, xScale, yScale, transformX, transformY, rotation);
    }
}
