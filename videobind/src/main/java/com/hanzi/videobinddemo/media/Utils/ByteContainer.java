package com.hanzi.videobinddemo.media.Utils;

import android.media.MediaCodec;

import java.util.ArrayList;

/**
 * Created by gengen on 2018/5/24.
 */

public class ByteContainer {
    private final static String TAG = "ByteContainer";
    private ArrayList<byte[]> chunkDataContainer = new ArrayList<>();
    private ArrayList<MediaCodec.BufferInfo> bufferInfoContainer= new ArrayList<>();
    private final Object object = new Object();
    private boolean isStarted = false;

    public void putData(byte[] pcmChunk) {
        synchronized (object) {
            chunkDataContainer.add(pcmChunk.clone());
            isStarted = true;
        }
    }

    public byte[] getData() {
        synchronized (object) {
        if (chunkDataContainer.isEmpty()) {
            return null;
        }
        byte[] byteChunk = chunkDataContainer.get(0);//每次取出index 0 的数据
        chunkDataContainer.remove(0);//取出后将此数据remove掉 既能保证PCM数据块的取出顺序 又能及时释放内存
        return byteChunk;
        }
    }

    public boolean isEmpty() {
        synchronized (object) {

        boolean isEmpty = chunkDataContainer.isEmpty();

        return isEmpty;
        }
    }

    public void putBufferInfo(MediaCodec.BufferInfo bufferInfo) {
        synchronized (object) {
        if (bufferInfoContainer == null) {
            bufferInfoContainer = new ArrayList<>();
        }
        bufferInfoContainer.add(bufferInfo);
        }
    }

    public MediaCodec.BufferInfo getBufferInfo() {
        synchronized (object) {
        if (bufferInfoContainer.isEmpty()) {
            return new MediaCodec.BufferInfo();
        }

        MediaCodec.BufferInfo bufferInfo = bufferInfoContainer.get(0);//每次取出index 0 的数据
        bufferInfoContainer.remove(bufferInfo);//取出后将此数据remove掉 既能保证PCM数据块的取出顺序 又能及时释放内存
        return bufferInfo;
        }
    }

    public int getSize() {
        synchronized (object) {
        if (chunkDataContainer == null) {
            return 0;
        }
        return chunkDataContainer.size();
        }
    }

    public boolean isStarted() {
        return isStarted;
    }
}
