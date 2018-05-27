package com.hanzi.videobinddemo.utils;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

/**
 * Create by xjs
 * _______date : 18/2/23
 * _______description:
 */
public class VideoComposer {
    private final String TAG = "VideoComposer";
    private List<String> mVideoList;
    private String mOutFilename;

    private MediaMuxer mMuxer;
    private ByteBuffer mReadBuf;
    private int mOutAudioTrackIndex;
    private int mOutVideoTrackIndex;
    private MediaFormat mAudioFormat;
    private MediaFormat mVideoFormat;
    private long mDuration = 0;

    public void setCallBack(VideoComposerCallBack callBack) {
        this.callBack = callBack;
    }

    private VideoComposerCallBack callBack;

    public VideoComposer(List<String> videoList, String outFilename) {
        mVideoList = videoList;
        this.mOutFilename = outFilename;
        mReadBuf = ByteBuffer.allocate(1048576);
    }

    public boolean joinVideo() {
        boolean getAudioFormat = false;
        boolean getVideoFormat = false;
        Iterator videoIterator = mVideoList.iterator();

        while (videoIterator.hasNext()) {
            String videoPath = (String) videoIterator.next();
            MediaExtractor extractor = new MediaExtractor();

            try {
                extractor.setDataSource(videoPath);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            int trackIndex;
            if (!getVideoFormat) {
                trackIndex = this.selectTrack(extractor, "video/");
                if (trackIndex < 0) {
                    Log.e(TAG, "No video track found in " + videoPath);
                } else {
                    extractor.selectTrack(trackIndex);
                    mVideoFormat = extractor.getTrackFormat(trackIndex);
                    Log.d(TAG, "joinVideo: VideoFormat:"+mVideoFormat.toString());
                    getVideoFormat = true;
                }
            }

            if (!getAudioFormat) {
                trackIndex = this.selectTrack(extractor, "audio/");
                if (trackIndex < 0) {
                    Log.e(TAG, "No audio track found in " + videoPath);
                } else {
                    extractor.selectTrack(trackIndex);
                    mAudioFormat = extractor.getTrackFormat(trackIndex);
                    Log.d(TAG, "joinVideo: mAudioFormat:"+mAudioFormat.toString());
                    getAudioFormat = true;
                }
            }

            int trackIndex1;
            trackIndex1 = this.selectTrack(extractor, "video/");
            if (trackIndex1 < 0) {
                Log.e(TAG, "No video track found in " + videoPath);
            } else {
                extractor.selectTrack(trackIndex1);
                mVideoFormat = extractor.getTrackFormat(trackIndex1);
                mDuration = mDuration+ extractor.getTrackFormat(mOutVideoTrackIndex).getLong("durationUs");
            }

            extractor.release();
//            if (getVideoFormat && getAudioFormat) {
//                break;
//            }
        }

//        while (videoIterator.hasNext()) {
//            String videoPath = (String) videoIterator.next();
//            MediaExtractor extractor = new MediaExtractor();
//
//            try {
//                extractor.setDataSource(videoPath);
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }

//        }

        try {
            mMuxer = new MediaMuxer(this.mOutFilename, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (getVideoFormat) {
            mOutVideoTrackIndex = mMuxer.addTrack(mVideoFormat);
        }
        if (getAudioFormat) {
            mOutAudioTrackIndex = mMuxer.addTrack(mAudioFormat);
        }
        mMuxer.start();
        long ptsOffset = 0L;

        int index = 0;


        Iterator trackIterator = mVideoList.iterator();
        while (trackIterator.hasNext()) {
            boolean isAddDuration=false;
            String videoPath = (String) trackIterator.next();
            boolean hasVideo = true;
            boolean hasAudio = true;
            MediaExtractor videoExtractor = new MediaExtractor();


            try {
                videoExtractor.setDataSource(videoPath);
            } catch (Exception var27) {
                var27.printStackTrace();
            }

            int inVideoTrackIndex = this.selectTrack(videoExtractor, "video/");
            if (inVideoTrackIndex < 0) {
                hasVideo = false;
            } else {
                videoExtractor.selectTrack(inVideoTrackIndex);
            }

            MediaExtractor audioExtractor = new MediaExtractor();

            try {
                audioExtractor.setDataSource(videoPath);
            } catch (Exception e) {
                e.printStackTrace();
            }

            int inAudioTrackIndex = this.selectTrack(audioExtractor, "audio/");
            if (inAudioTrackIndex < 0) {
                hasAudio = false;
            } else {
                audioExtractor.selectTrack(inAudioTrackIndex);
            }
            boolean bMediaDone = false;
            long presentationTimeUs = 0L;
            long audioPts = 0L;
            long videoPts = 0L;

            while (!bMediaDone) {
                if (!hasVideo && !hasAudio) {
                    break;
                }

                int outTrackIndex;
                MediaExtractor extractor;
                int currenttrackIndex;
                if ((!hasVideo || audioPts - videoPts <= 50000L) && hasAudio) {
                    currenttrackIndex = inAudioTrackIndex;
                    outTrackIndex = mOutAudioTrackIndex;
                    extractor = audioExtractor;
                } else {
                    currenttrackIndex = inVideoTrackIndex;
                    outTrackIndex = mOutVideoTrackIndex;
                    extractor = videoExtractor;
                }

//                if (outTrackIndex == mOutVideoTrackIndex && !isAddDuration) {
//                    mDuration = mDuration+ extractor.getTrackFormat(mOutVideoTrackIndex).getLong("durationUs");
//                    isAddDuration = true;
//                }

                mReadBuf.rewind();
                int chunkSize = extractor.readSampleData(mReadBuf, 0);//读取帧数据
                if (chunkSize < 0) {
                    if (currenttrackIndex == inVideoTrackIndex) {
                        hasVideo = false;
                    } else if (currenttrackIndex == inAudioTrackIndex) {
                        hasAudio = false;
                    }
                } else {
                    if (extractor.getSampleTrackIndex() != currenttrackIndex) {
                        Log.e(TAG, "WEIRD: got sample from track " + extractor.getSampleTrackIndex() + ", expected " + currenttrackIndex);
                    }

                    presentationTimeUs = extractor.getSampleTime();//读取帧的pts
                    if (currenttrackIndex == inVideoTrackIndex) {
                        videoPts = presentationTimeUs;
                    } else {
                        audioPts = presentationTimeUs;
                    }

                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    info.offset = 0;
                    info.size = chunkSize;
                    info.presentationTimeUs = ptsOffset + presentationTimeUs;//pts重新计算

                    if (outTrackIndex == mOutVideoTrackIndex) {
                        Log.d(TAG, "joinVideo: info.presentationTimeUs:" + info.presentationTimeUs);
                        Log.d(TAG, "joinVideo: mDuration:" + mDuration);
                        int progress = (int) (100 * (float) info.presentationTimeUs / mDuration);
                        if (progress > 100) {
                            progress = 100;
                        }
                        if (callBack != null)
                            callBack.onProgress((int) (progress));
                    }

                    if ((extractor.getSampleFlags() & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                        info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                    }

                    mReadBuf.rewind();
                    Log.i(TAG, String.format("write sample track %d, size %d, pts %d flag %d",
                            outTrackIndex, info.size, info.presentationTimeUs, info.flags));
                    mMuxer.writeSampleData(outTrackIndex, mReadBuf, info);//写入文件
                    extractor.advance();
                }
            }

            ptsOffset += videoPts > audioPts ? videoPts : audioPts;
            ptsOffset += 10000L;

            Log.i(TAG, "finish one file, ptsOffset " + ptsOffset);

            videoExtractor.release();
            audioExtractor.release();
            index++;
        }

        if (mMuxer != null) {
            try {
                mMuxer.stop();
                mMuxer.release();
            } catch (Exception e) {
                Log.e(TAG, "Muxer close error. No data was written");
            }

            mMuxer = null;
        }

        Log.i(TAG, "video join finished");
        if (callBack != null)
            callBack.onProgress(100);
        return true;
    }

    private int selectTrack(MediaExtractor extractor, String mimePrefix) {
        int numTracks = extractor.getTrackCount();

        for (int i = 0; i < numTracks; ++i) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString("mime");
            if (mime.startsWith(mimePrefix)) {
                return i;
            }
        }

        return -1;
    }


    public interface VideoComposerCallBack {
        public void onProgress(int position);
    }
}
