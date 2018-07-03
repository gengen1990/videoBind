package com.hanzi.videobinddemo;

/**
 * desc 混音jni调用
 */

public class AudioJniUtils {

    static {
        System.loadLibrary("native-lib");
    }
    public static native byte[] audioMix(byte[] sourceA,byte[] sourceB,byte[] dst,float firstVol , float secondVol);
}
