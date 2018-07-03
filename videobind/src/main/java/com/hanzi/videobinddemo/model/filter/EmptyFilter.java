package com.hanzi.videobinddemo.model.filter;

import android.content.Context;

public class EmptyFilter
        implements Filter
{
    public String getFragmentShaderCode(Context context)
    {
//        return RendererUtil.readFromRaw(paramContext, 2131099656);
        return null;
    }

    public void loadTextures(Context context) {}

    public void releaseTextures() {}

    public void setup(Context context, int paramInt) {}

    public void update(long time) {}
}
