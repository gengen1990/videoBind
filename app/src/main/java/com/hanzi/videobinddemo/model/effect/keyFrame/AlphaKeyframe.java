package com.hanzi.videobinddemo.model.effect.keyFrame;

public class AlphaKeyframe {
    public final float alphaValue;
    public final float interpolatedTime;

    private AlphaKeyframe(float paramFloat1, float paramFloat2) {
        this.interpolatedTime = paramFloat1;
        this.alphaValue = paramFloat2;
    }

    public static AlphaKeyframe create(float paramFloat1, float paramFloat2) {
        return new AlphaKeyframe(paramFloat1, paramFloat2);
    }
}
