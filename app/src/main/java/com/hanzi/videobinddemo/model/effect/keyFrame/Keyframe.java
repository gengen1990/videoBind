package com.hanzi.videobinddemo.model.effect.keyFrame;

import java.util.List;

public abstract class Keyframe
{
    public final List<AlphaKeyframe> alphaKeyframes;
    public final List<MatrixKeyframe> matrixKeyframes;

    protected Keyframe(List<AlphaKeyframe> alphaKeyFrames, List<MatrixKeyframe> matrixKeyFrames)
    {
        this.alphaKeyframes = alphaKeyFrames;
        this.matrixKeyframes = matrixKeyFrames;
    }
}
