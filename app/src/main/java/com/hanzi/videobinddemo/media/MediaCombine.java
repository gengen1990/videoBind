package com.hanzi.videobinddemo.media;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;

import com.hanzi.videobinddemo.media.Utils.MediaFileMuxer;
import com.hanzi.videobinddemo.media.Utils.extractor.AudioExtractor;
import com.hanzi.videobinddemo.media.Utils.extractor.MediaExtractor;
import com.hanzi.videobinddemo.media.Utils.extractor.VideoExtractor;

import java.nio.ByteBuffer;

/**
 * Created by gengen on 2018/5/22.
 * 视频和音频合成到同一个文件中
 */

public class MediaCombine {
    private static final String TAG = MediaCombine.class.getSimpleName();

    private HandlerThread audioThread = new HandlerThread("audio");
    private HandlerThread videoThread = new HandlerThread("video");
    private HandlerThread isOkThread = new HandlerThread("isOk");

    private Handler audioHandler;
    private Handler videoHandler;
    private Handler isOkHandler;

    private int videoTrackIndex=0 ,audioTrackIndex =0;

    private MediaFileMuxer mediaFileMuxer;

    private AudioExtractor audioExtractor;
    private VideoExtractor videoExtractor;

    private boolean[] isWriteOK = new boolean[]{false,false};
    private boolean beStop = false;

    public boolean open( String videoPath, String audioPath, String outPath, CombineVideoListener combineVideoListener) {
         videoExtractor = new VideoExtractor(videoPath, 0, -1);
         audioExtractor = new AudioExtractor(audioPath, 0, -1);

        MediaFormat videoFormat = videoExtractor.getFormat();
        MediaFormat audioFormat = audioExtractor.getFormat();

         mediaFileMuxer = new MediaFileMuxer(outPath);

        videoTrackIndex = mediaFileMuxer.addTrack(videoFormat);
        audioTrackIndex = mediaFileMuxer.addTrack(audioFormat);
        mediaFileMuxer.start();

        audioHandler=new Handler(audioThread.getLooper());
        videoHandler = new Handler(videoThread.getLooper());
        isOkHandler = new Handler(isOkThread.getLooper());


        return true;
    }

    public void start(){
        audioHandler.post(new WriteSampleRunnable(0, audioExtractor,audioTrackIndex));
        videoHandler.post(new WriteSampleRunnable(1, videoExtractor,videoTrackIndex));
        isOkHandler.post(new Runnable() {
            @Override
            public void run() {
                while (!beStop) {
                    if (isWriteOK[0] && isWriteOK[1]) {
                        mediaFileMuxer.stop();
                        mediaFileMuxer.release();
                        audioExtractor.release();
                        videoExtractor.release();
                        break;
                    }
                }
            }
        });
    }

    public void stop(){
        beStop = true;
    }

    private final class WriteSampleRunnable implements Runnable {
        private MediaExtractor extractor;
        private int trackIndex;
        private ByteBuffer byteBuffer;
        MediaCodec.BufferInfo bufferInfo ;
        private int index;

        public WriteSampleRunnable(int index ,MediaExtractor extractor, int trackIndex){
            this.index = index;
            this.extractor = extractor;
            this.trackIndex = trackIndex;
             byteBuffer = ByteBuffer.allocate(500 * 1024);
             bufferInfo= new MediaCodec.BufferInfo();
        }


        @Override
        public void run() {
            while (!beStop) {
                int readVideoSampleSize = extractor.readSampleData(byteBuffer, 0);
                if (readVideoSampleSize < 0) {
                    break;
                }
                bufferInfo.size = readVideoSampleSize;
                bufferInfo.presentationTimeUs = extractor.getSampleTime();
                bufferInfo.offset=0;
                bufferInfo.flags=extractor.getSampleFlags();
                mediaFileMuxer.writeSampleData(trackIndex,byteBuffer,bufferInfo);
                extractor.advance();
            }
            isWriteOK[index] = true;
        }
    }

    public interface CombineVideoListener {
        public void onProgress(int progress);
    }
}
