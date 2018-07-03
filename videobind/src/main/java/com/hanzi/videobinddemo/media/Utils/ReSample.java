package com.hanzi.videobinddemo.media.Utils;


import com.hanzi.videobinddemo.vavi.sound.pcm.resampling.ssrc.SSRC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by gengen on 2018/5/23.
 */

public class ReSample {
    private int sample;
    private String pathSrc;
    private String pathDst;
    private File file;
    private int outSample;
    private int inSample;

    public ReSample(int inSample, int outSample, String pathSrc, String pathDst) {
        this.inSample = inSample;
        this.outSample = outSample;
        this.pathSrc = pathSrc;
        this.pathDst = pathDst;
    }

    public ReSample invoke() {
        file = null;

        FileInputStream fis;
        try {
            fis = new FileInputStream(pathSrc);
            FileOutputStream fos = new FileOutputStream(pathDst);
            new SSRC(fis, fos, inSample, outSample, 2, 2, 2, Integer.MAX_VALUE, 0, 0, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }
}