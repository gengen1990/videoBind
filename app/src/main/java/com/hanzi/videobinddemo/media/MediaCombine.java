package com.hanzi.videobinddemo.media;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

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

    private HandlerThread audioThread ;
    private HandlerThread videoThread ;
    private HandlerThread isOkThread ;

    private Handler audioHandler;
    private Handler videoHandler;
    private Handler isOkHandler;

    private int videoTrackIndex = 0, audioTrackIndex = 0;

    private MediaFileMuxer mediaFileMuxer;

    private AudioExtractor audioExtractor;
    private VideoExtractor videoExtractor;

    private CombineVideoListener combineVideoListener;

    private boolean[] isWriteOK = new boolean[]{false, false};
    private boolean beStop = false;

    public boolean open(String videoPath, String audioPath, String outPath, CombineVideoListener combineVideoListener) {

        Log.d(TAG, "open: ");

        videoExtractor = new VideoExtractor(videoPath, 0, -1);
        audioExtractor = new AudioExtractor(audioPath, 0, -1);

        MediaFormat videoFormat = videoExtractor.getFormat();
        MediaFormat audioFormat = audioExtractor.getFormat();

        Log.i(TAG, "open: videoFormat:"+videoFormat);
        Log.i(TAG, "open: audioFormat:"+audioFormat);

        mediaFileMuxer = new MediaFileMuxer(outPath);

        videoTrackIndex = mediaFileMuxer.addTrack(videoFormat);
        audioTrackIndex = mediaFileMuxer.addTrack(audioFormat);


        audioThread = new HandlerThread("audio");
        videoThread = new HandlerThread("video");
        isOkThread = new HandlerThread("isOk");
        audioThread.start();
        videoThread.start();
        isOkThread.start();

        audioHandler = new Handler(audioThread.getLooper());
        videoHandler = new Handler(videoThread.getLooper());
        isOkHandler = new Handler(isOkThread.getLooper());

        this.combineVideoListener = combineVideoListener;
        return true;
    }

    public void start() {

        mediaFileMuxer.start();
        Log.d(TAG, "start: ");
        audioHandler.post(new WriteSampleRunnable(0, audioExtractor, audioTrackIndex));
        videoHandler.post(new WriteSampleRunnable(1, videoExtractor, videoTrackIndex));
        isOkHandler.post(new Runnable() {
            @Override
            public void run() {
                while (!beStop) {
                    if (isWriteOK[0] && isWriteOK[1]) {
                        Log.d(TAG, "run: beStop");
                        mediaFileMuxer.stop();
                        mediaFileMuxer.release();
                        audioExtractor.release();
                        videoExtractor.release();
                        if (combineVideoListener != null)
                            combineVideoListener.onProgress(0);
                        break;
                    }
                }
            }
        });
    }

    public void stop() {
        beStop = true;
    }

    private final class WriteSampleRunnable implements Runnable {
        private MediaExtractor extractor;
        private int trackIndex;
        private ByteBuffer byteBuffer;
        MediaCodec.BufferInfo bufferInfo;
        private int index;

        public WriteSampleRunnable(int index, MediaExtractor extractor, int trackIndex) {
            this.index = index;
            this.extractor = extractor;
            this.trackIndex = trackIndex;
            byteBuffer = ByteBuffer.allocate(500 * 1024);
            bufferInfo = new MediaCodec.BufferInfo();
        }


        @Override
        public void run() {
            Log.d(TAG, "run: index");
            while (!beStop) {
                int readVideoSampleSize = extractor.readSampleData(byteBuffer, 0);
                if (readVideoSampleSize < 0) {
                    break;
                }
                bufferInfo.size = readVideoSampleSize;
                bufferInfo.presentationTimeUs = extractor.getSampleTime();
                bufferInfo.offset = 0;
                bufferInfo.flags = extractor.getSampleFlags();
                mediaFileMuxer.writeSampleData(trackIndex, byteBuffer, bufferInfo);
                extractor.advance();
            }
            Log.i(TAG, "run: isWriteOk:"+isWriteOK[index]);
            isWriteOK[index] = true;
        }
    }

    public interface CombineVideoListener {
        public void onProgress(int progress);
    }
}
