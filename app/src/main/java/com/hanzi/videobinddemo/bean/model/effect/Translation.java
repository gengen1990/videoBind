package com.hanzi.videobinddemo.bean.model.effect;

/**
 * Created by gengen on 2018/3/7.
 */

class Translation {
    private long timeUs;
    private float x;
    private float y;

    public Translation(float paramFloat1, float paramFloat2, long paramLong) {
        this.x = paramFloat1;
        this.y = paramFloat2;
        this.timeUs = paramLong;
    }

    public long getTimeUs() {
        return this.timeUs;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public void setTimeUs(long paramLong) {
        this.timeUs = paramLong;
    }

    public void setX(float paramFloat) {
        this.x = paramFloat;
    }

    public void setY(float paramFloat) {
        this.y = paramFloat;
    }
}
