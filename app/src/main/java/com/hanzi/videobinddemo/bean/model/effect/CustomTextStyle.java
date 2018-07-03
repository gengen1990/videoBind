package com.hanzi.videobinddemo.bean.model.effect;

import android.graphics.Bitmap;
import android.util.Log;

public class CustomTextStyle {
    private static final String TAG = CustomTextStyle.class.getSimpleName();
    public static final String CustomText = "CustomTextStyle";
    private int bgColor;
    private int color;
    private FontModel fontModel = FontLibrary.getInstance().getDefaultFontModel();
    private boolean isOutlineEnabled;
    private boolean isShadowEnabled;
    private String text;
    private int textAlignment;
    private Bitmap textBitmap;

    public CustomTextStyle(String paramString) {
        this.text = paramString;
        this.color = -1;
        this.bgColor = 0;
        this.isShadowEnabled = false;
        this.isOutlineEnabled = false;
        this.textAlignment = 17;
    }

    public int getBgColor() {
        return this.bgColor;
    }

    public int getColor() {
        return this.color;
    }

    public FontModel getFontModel() {
        return this.fontModel;
    }

    public String getText() {
        return this.text;
    }

    public int getTextAlignment() {
        return this.textAlignment;
    }

    public Bitmap getTextBitmap() {
        return this.textBitmap;
    }

    public boolean isOutlineEnabled() {
        return this.isOutlineEnabled;
    }

    public boolean isShadowEnabled() {
        return this.isShadowEnabled;
    }

    public void setBgColor(int bgColor) {
        this.bgColor = bgColor;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setFontModel(FontModel fontModel) {
        this.fontModel = fontModel;
    }

    public void setOutlineEnabled(boolean isOutlineEnabled) {
        this.isOutlineEnabled = isOutlineEnabled;
    }

    public void setShadowEnabled(boolean isShadowEnabled) {
        this.isShadowEnabled = isShadowEnabled;
    }

    public void setText(String text) {
        this.text = text
        ;
    }

    public void setTextAlignment(int textAlignment) {
        this.textAlignment = textAlignment;
    }

    public void setTextBitmap(Bitmap textBitmap) {
        Log.d(TAG, "setTextBitmap: " + textBitmap.toString());
        this.textBitmap = textBitmap;
    }
}
