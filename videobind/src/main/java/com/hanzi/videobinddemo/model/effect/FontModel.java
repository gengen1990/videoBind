package com.hanzi.videobinddemo.model.effect;

import android.content.res.AssetManager;
import android.graphics.Typeface;

public class FontModel {
    private String filePath;
    public String id;
    public String name;
    private String orderingKey;
    public boolean supportsOutline;
    public transient Typeface typeface;

    public void init(AssetManager assetManager) {

        if (filePath == null) {
            typeface = Typeface.DEFAULT;
            return;
        }
        this.typeface = Typeface.createFromAsset(assetManager, filePath);
    }

}
