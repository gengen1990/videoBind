package com.hanzi.videobinddemo.model.effect.keyFrame;

import java.util.ArrayList;
import java.util.List;

public class EmptyKeyframe
        extends Keyframe {
    public static final Keyframe INSTANCE = new EmptyKeyframe(new ArrayList(0), new ArrayList(0));

    public EmptyKeyframe(List<AlphaKeyframe> paramList, List<MatrixKeyframe> paramList1) {
        super(paramList, paramList1);
    }
}
