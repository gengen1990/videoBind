package com.hanzi.videobinddemo.media.Utils;

import android.media.MediaCodec;

import java.util.ArrayList;

/**
 * Created by gengen on 2018/5/24.
 */

public class ByteContainer {
    private final String TAG = "ByteContainer";
    private ArrayList<byte[]> chunkDataContainer = new ArrayList<>();
    private ArrayList<MediaCodec.BufferInfo> bufferInfoContainer;
    final Object object = new Object();
    private boolean isStarted = false;

    public void putData(byte[] pcmChunk) {
        synchronized (object) {//记得加锁
            chunkDataContainer.add(pcmChunk.clone());
            isStarted = true;
        }
    }

    public byte[] getData() {
        synchronized (object) {//记得加锁
            if (chunkDataContainer.isEmpty()) {
                return null;
            }
            byte[] byteChunk = chunkDataContainer.get(0);//每次取出index 0 的数据
            chunkDataContainer.remove(0);//取出后将此数据remove掉 既能保证PCM数据块的取出顺序 又能及时释放内存
            return byteChunk;
        }
    }

    public boolean isEmpty() {
        if (chunkDataContainer == null) {
            return true;
        }
        return chunkDataContainer.isEmpty();
    }

    public void putBufferInfo(MediaCodec.BufferInfo bufferInfo) {
        synchronized (object) {//记得加锁
            if (bufferInfoContainer == null) {
                bufferInfoContainer = new ArrayList<>();
            }
            bufferInfoContainer.add(bufferInfo);
        }
    }

    public MediaCodec.BufferInfo getBufferInfo() {
        synchronized (object) {//记得加锁
            if (bufferInfoContainer.isEmpty()) {
                return null;
            }

            MediaCodec.BufferInfo bufferInfo = bufferInfoContainer.get(0);//每次取出index 0 的数据
            bufferInfoContainer.remove(bufferInfo);//取出后将此数据remove掉 既能保证PCM数据块的取出顺序 又能及时释放内存
            return bufferInfo;
        }
    }

    public int getSize() {
        if (chunkDataContainer == null) {
            return 0;
        }
        return chunkDataContainer.size();
    }

    public boolean isStarted() {
        return isStarted;
    }
}
