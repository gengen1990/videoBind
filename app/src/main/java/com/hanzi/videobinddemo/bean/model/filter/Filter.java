package com.hanzi.videobinddemo.bean.model.filter;

import android.content.Context;

interface Filter {
    String getFragmentShaderCode(Context context);

    void loadTextures(Context context);

    void releaseTextures();

    void setup(Context context, int paramInt);

    void update(long time);
}
