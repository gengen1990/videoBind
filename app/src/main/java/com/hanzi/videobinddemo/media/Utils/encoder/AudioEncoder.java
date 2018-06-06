package com.hanzi.videobinddemo.media.Utils.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by gengen on 2018/5/21.
 */

public class AudioEncoder {
    private String TAG = "AudioEncoder";
    private MediaCodec encoder;
    final int TIMEOUT_USEC = -1;

    ByteBuffer[] inputBuffers;
    ByteBuffer[] outputBuffers;
//    MediaCodec.BufferInfo inputInfo;
//    private MediaCodec.BufferInfo outputInfo;

    private Thread mOutputThread;

    private boolean mEndOfStream = false;

    private long mCnt = 0;

    private AudioEncoderCallBack audioEncoderCallback;

    private boolean mRunning = false;

    private FileOutputStream fos;
    private BufferedOutputStream bos;

    public int open(String tag, String audioOutPath,String encodeType, int sampleRate, int channelCount, int bitRate, int inputSize, AudioEncoderCallBack audioEncoderCallback) {
        try {
            fos=new FileOutputStream(new File(audioOutPath));
//             bos = new BufferedOutputStream(fos, inputSize);

            MediaFormat encodeFormat = MediaFormat.createAudioFormat(encodeType, sampleRate, channelCount);//mime type 采样率 声道数
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);//比特率
            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);

            encoder = MediaCodec.createEncoderByType(encodeType);
            encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            this.audioEncoderCallback = audioEncoderCallback;

            Log.d(TAG, String.format("AudioEncoder open" +
                            "sampleRate %d, channelCount %s, bitRate %s,inputSize %d"
                    , sampleRate, channelCount, bitRate, inputSize));

            this.TAG = tag;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int start() {
        encoder.start();

        inputBuffers = encoder.getInputBuffers();
        outputBuffers = encoder.getOutputBuffers();
//        inputInfo = new MediaCodec.BufferInfo();

        mOutputThread = new Thread(new CEncoderRunnable());
        mOutputThread.start();
        return 0;
    }

    public boolean encode(byte[] chunkPCM, boolean endOfStream) {
        if (endOfStream) {
            mEndOfStream = endOfStream;
        }
        Log.d(TAG, "encode: ");
        int index = encoder.dequeueInputBuffer(TIMEOUT_USEC);

        Log.d(TAG, "encode: index:" + index);

        if (index >= 0) {
            ByteBuffer inputBuffer = inputBuffers[index];
            inputBuffer.clear();

            if (mEndOfStream) {
                Log.d(TAG, "encode: endOfStream over:" + index);
                encoder.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return false;
            }

//            if (chunkPCM == null) {
//                Log.d(TAG, "encode: null");
////                length = chunkPCM.length;
//                return true;
//            }
//            Log.d(TAG, String.format("AudioEncoder encode" +
//                    "chunkPCM length %d, endOfStream %b", length, endOfStream));

//                if (inputInfo.size < 4096) {//这里看起来应该是16位单声道转16位双声道
//                    //说明是单声道的,需要转换一下
//                    byte[] stereoBytes = new byte[inputInfo.size * 2];
//                    for (int i = 0; i < inputInfo.size; i += 2) {
//                        stereoBytes[i * 2 + 0] = chunkPCM[i];
//                        stereoBytes[i * 2 + 1] = chunkPCM[i + 1];
//                        stereoBytes[i * 2 + 2] = chunkPCM[i];
//                        stereoBytes[i * 2 + 3] = chunkPCM[i + 1];
//                    }
//                    inputBuffer.put(stereoBytes);
//                    encoder.queueInputBuffer(index, 0, stereoBytes.length, inputInfo.presentationTimeUs, 0);
//
//                    Log.d(TAG, String.format("inputInfo.size < 4096"));
//
//                } else {

            inputBuffer.position(0).limit(chunkPCM.length);
            inputBuffer.put(chunkPCM);
            inputBuffer.flip();
            long pts = 1000000 * mCnt++ / 20;
            encoder.queueInputBuffer(index, 0, chunkPCM.length, pts, 0);
            inputBuffer.clear();
//                }
            audioEncoderCallback.onInputBuffer();
            return true;
        }
        return true;
    }

    public int stop() {
        stopEncoder();
        stopThread();
        if (encoder != null) {
            encoder.stop();
            encoder.release();
        }
        return 0;
    }

    private void stopThread() {
//        mRunning = false;
        if (mOutputThread != null) {
            try {
                Log.d(TAG, "stopThread: before");
                mOutputThread.join();
                Log.d(TAG, "stopThread: after");
            } catch (Exception e) {
                Log.e(TAG, "output thread join failed:" + e);
            }
            mOutputThread = null;
        }

    }

    private int stopEncoder() {
        try {
            int idx = encoder.dequeueInputBuffer(-1);

            encoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        } catch (IllegalStateException e) {
            Log.e(TAG, "dequeueOutputBuffer exception:" + e);
        }
        return 0;
    }

    public int destory() {
        return 0;
    }

    private final class CEncoderRunnable implements Runnable {
        @Override
        public void run() {
            Log.i(TAG, "video hardware decoder output thread running");
//            if (mRunning) {
//                Log.e(TAG, "video hardware decoder start again!");
//                return;
//            }
            mRunning = true;
            long lastStamp = -1;

//            ByteBuffer outputBuffer;
//            byte[] chunkAudio;
//            int outBitSize;
//            int outPacketSize;

            while (mRunning) {
                try {

                    MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
                    int index = encoder.dequeueOutputBuffer(outputInfo, 1000);
//                    Log.d(TAG, "dequeueOutputBuffer: index:"+index);

                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                        mRunning = false;
//                        Log.d(TAG,  "run: INFO_TRY_AGAIN_LATER");
                    } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        outputBuffers = encoder.getOutputBuffers();
                        Log.d(TAG, "run: INFO_OUTPUT_BUFFERS_CHANGED");
                    } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = encoder.getOutputFormat();
                        Log.d(TAG, "run: newFormat:" + newFormat.toString());
                    } else if (index < 0) {
                        Log.d(TAG, "run: index:"+index);
                    } else {

                        ByteBuffer outputData = outputBuffers[index];
                        boolean done = (outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        if (done) {
                            Log.d(TAG, "run: BUFFER_FLAG_END_OF_STREAM:" +  done);
                            mRunning = false;
                            if (audioEncoderCallback!=null)
                                audioEncoderCallback.encodeOver();
                        }

                        Log.d(TAG, "run: presentationTimeUs:" + outputInfo.presentationTimeUs);
//                        if (outputInfo.presentationTimeUs == 0 && !done) {
//                            Log.d(TAG, "run: CEncoderRunnable:continue");
//                            continue;
//                        }
                        Log.d(TAG, "run: outputInfo.size:"+outputInfo.size);

                        if (outputInfo.size != 0 && outputInfo.presentationTimeUs > 0) {
                            Log.d(TAG, "run: outputData.limit():" + outputData.limit());
                            Log.d(TAG, String.format("output: index %d size %d presentationTimeUs:%d", index, outputInfo.size, outputInfo.presentationTimeUs));
//                            if (outputInfo.presentationTimeUs > lastStamp) {//为了避免有问题的数据
                            byte[] data = new byte[outputData.limit()];
                            outputData.get(data);
                            if (audioEncoderCallback != null  )
                                audioEncoderCallback.onOutputBuffer(data, outputInfo);

                            lastStamp = outputInfo.presentationTimeUs;
                            outputData.clear();
//                            }

//                            outBitSize=outputInfo.size;
//                            outPacketSize=outBitSize+7;
//                            outputBuffer = outputBuffers[index];//拿到输出Buffer
//                            outputBuffer.position(outputInfo.offset);
//                            outputBuffer.limit(outputInfo.offset + outBitSize);
//                            chunkAudio = new byte[outPacketSize];
//                            addADTStoPacket(chunkAudio, outPacketSize);//添加ADTS 代码后面会贴上
//                            outputBuffer.get(chunkAudio, 7, outBitSize);//将编码得到的AAC数据 取出到byte[]中 偏移量offset=7 你懂得
//                            outputBuffer.position(outputInfo.offset);
//                            outputBuffer.clear();
//                            try {
//                                Log.i(TAG, "run: chunkAudio.length:"+chunkAudio.length);
//                                fos.write(chunkAudio, 0, chunkAudio.length);//BufferOutputStream 将文件保存到内存卡中 *.aac
//                                fos.flush();
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
                        }
                        Log.i(TAG, "run: releaseOutputBuffer");
                        encoder.releaseOutputBuffer(index, false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "dequeueOutputBuffer exception:" + e);
                    break;
                }
            }
//            stopThread();
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "audio hardware decoder output thread exit!");
        }
    }

//    /**
//     * 写入ADTS头部数据
//     */
//    public static void addADTStoPacket(byte[] packet, int packetLen) {
//        int profile = 2; // AAC LC
//        int freqIdx = 4; // 44.1KHz
//        int chanCfg = 2; // CPE
//
//        packet[0] = (byte) 0xFF;
//        packet[1] = (byte) 0xF9;
//        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
//        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
//        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
//        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
//        packet[6] = (byte) 0xFC;
//    }


    public interface AudioEncoderCallBack {
        void onInputBuffer();

        void onOutputBuffer(byte[] bytes, MediaCodec.BufferInfo bufferInfo);

        void encodeOver();
    }

//    public void PCM2AAC(int sampleRate, String encodeType, String outputFile, ByteContainer container) throws IOException {
//        int inputIndex;
//        ByteBuffer inputBuffer;
//        int outputIndex;
//        ByteBuffer outputBuffer;
//        byte[] chunkAudio;
//        int outBitSize;
//        int outPacketSize;
//        byte[] chunkPCM;
//        //初始化编码器
//        MediaFormat encodeFormat = MediaFormat.createAudioFormat(encodeType, sampleRate, 2);//mime type 采样率 声道数
//        encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000);//比特率
//        encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
//        encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 500*1024);
//
//        MediaCodec mediaEncode = MediaCodec.createEncoderByType(encodeType);
//        mediaEncode.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//        mediaEncode.start();
//
//        ByteBuffer[] encodeInputBuffers = mediaEncode.getInputBuffers();
//        ByteBuffer[] encodeOutputBuffers = mediaEncode.getOutputBuffers();
//        MediaCodec.BufferInfo encodeBufferInfo = new MediaCodec.BufferInfo();
//
//        //初始化文件写入流
//        FileOutputStream fos = new FileOutputStream(new File(outputFile));
//        BufferedOutputStream bos = new BufferedOutputStream(fos, 500*1024);
//        Log.i(TAG, "PCM2AAC: container0:"+container.getSize());
//        while (!container.isEmpty() ) {//|| !isDecodeOver
//
//            Log.d(TAG, "PCM2AAC: chunkPCMDataContainer:" + container.getSize());
//            for (int i = 0; i < encodeInputBuffers.length - 1; i++) {
//                chunkPCM = container.getData();//获取解码器所在线程输出的数据 代码后边会贴上
//                if (chunkPCM == null) {
//                    break;
//                }
//                inputIndex = mediaEncode.dequeueInputBuffer(-1);
//                inputBuffer = encodeInputBuffers[inputIndex];
//                inputBuffer.clear();//同解码器
//                inputBuffer.limit(chunkPCM.length);
//                inputBuffer.put(chunkPCM);//PCM数据填充给inputBuffer
//                mediaEncode.queueInputBuffer(inputIndex, 0, chunkPCM.length, 0, 0);//通知编码器 编码
//            }
//
//            outputIndex = mediaEncode.dequeueOutputBuffer(encodeBufferInfo, 10000);//同解码器
//            while (outputIndex >= 0) {//同解码器
//                outBitSize = encodeBufferInfo.size;
//                outPacketSize = outBitSize + 7;//7为ADTS头部的大小
//                outputBuffer = encodeOutputBuffers[outputIndex];//拿到输出Buffer
//                outputBuffer.position(encodeBufferInfo.offset);
//                outputBuffer.limit(encodeBufferInfo.offset + outBitSize);
//                chunkAudio = new byte[outPacketSize];
//                addADTStoPacket(chunkAudio, outPacketSize);//添加ADTS 代码后面会贴上
//                outputBuffer.get(chunkAudio, 7, outBitSize);//将编码得到的AAC数据 取出到byte[]中 偏移量offset=7 你懂得
//                outputBuffer.position(encodeBufferInfo.offset);
//                try {
//                    Log.i(TAG, "PCM2AAC: length:"+chunkAudio.length);
//                    bos.write(chunkAudio, 0, chunkAudio.length);//BufferOutputStream 将文件保存到内存卡中 *.aac
//                    bos.flush();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//
//                mediaEncode.releaseOutputBuffer(outputIndex, false);
//                outputIndex = mediaEncode.dequeueOutputBuffer(encodeBufferInfo, 10000);
//            }
//        }
//
//
//
//        Log.i(TAG, "PCM2AAC: end");
//        mediaEncode.stop();
//        mediaEncode.release();
//        bos.close();
//        fos.close();
//    }
}
