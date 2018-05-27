package com.hanzi.videobinddemo.media.Utils;

import android.media.MediaCodec;

import java.util.ArrayList;

/**
 * Created by gengen on 2018/5/24.
 */

public class ByteContainer {
    private ArrayList<byte[]> chunkDataContainer;
    private ArrayList<MediaCodec.BufferInfo> bufferInfoContainer;
    Object object=new Object();
    private boolean isStarted = false;

    private int size = 0;

    public void putData(byte[] pcmChunk) {
        synchronized (object) {//记得加锁
            if (chunkDataContainer == null) {
                chunkDataContainer = new ArrayList<>();
            }
            chunkDataContainer.add(pcmChunk);
            isStarted = true;
        }
    }

    public byte[] getData() {
        synchronized (object) {//记得加锁
            if (chunkDataContainer.isEmpty()) {
                return null;
            }

            byte[] byteChunk = chunkDataContainer.get(0);//每次取出index 0 的数据
            chunkDataContainer.remove(byteChunk);//取出后将此数据remove掉 既能保证PCM数据块的取出顺序 又能及时释放内存
            return byteChunk;
        }
    }

    public boolean isEmpty(){
        if (chunkDataContainer ==null) {
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

    public void setSize(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }

    public boolean isStarted() {
        return isStarted;
    }
}
