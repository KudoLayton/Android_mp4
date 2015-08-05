package com.example.leehyoseung.androidmp4player;


import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MovieThread extends Thread {
    private static final String VIDEO = "video/";
    private static final String TAG = "VideoDecoder";
    private MediaExtractor mExtractor;
    private MediaCodec mDecoder;

    private boolean eosReceived;

    public boolean init(Surface surface, String filePath){
        eosReceived = false;
        try{
            Log.d(TAG, "start init");
            mExtractor = new MediaExtractor();
            Log.d(TAG, "Accessing: "+filePath);
            mExtractor.setDataSource(filePath);
            Log.d(TAG, "I found it");
            MediaFormat format;
            for(int i = 0; i < mExtractor.getTrackCount(); i++){
                format = mExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if(mime.startsWith(VIDEO)){
                    mExtractor.selectTrack(i);
                    mDecoder = MediaCodec.createDecoderByType(mime);
                    try{
                        Log.d(TAG, "format : " + format);
                        mDecoder.configure(format,surface,null,0);

                    }catch(IllegalStateException e){
                        Log.e(TAG, "codec '"+mime+"' failed configuration." + e);
                        return false;
                    }
                    mDecoder.start();
                    break;
                }
            }
        }catch (IOException e){
            Log.d(TAG, "I cannot find file");
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public void run(){
        Log.d(TAG, "dddd");
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean isInput= true;
        boolean first = false;
        long startWhen = 0;

        while (!eosReceived){
            if(isInput){
                int inputIndex = mDecoder.dequeueInputBuffer(10000);
                if(inputIndex >= 0){
                    ByteBuffer inputBuffer = mDecoder.getInputBuffer(inputIndex);
                    int sampleSize = mExtractor.readSampleData(inputBuffer, 0);
                    if(mExtractor.advance() && sampleSize > 0){
                        mDecoder.queueInputBuffer(inputIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
                    }else{
                        Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                        mDecoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isInput=false;
                    }
                }
            }
            int outIndex = mDecoder.dequeueOutputBuffer(info, 10000);
            switch(outIndex){
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    mDecoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED format : "+mDecoder.getOutputFormat());
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d(TAG, "INFO_TRY_AGAIN_LATER");
                    break;
                default:
                    if(!first){
                        startWhen = System.currentTimeMillis();
                        first = true;
                    }
                    try {
                        long sleepTime = (info.presentationTimeUs/10000) - (System.currentTimeMillis() - startWhen);
                        Log.d(TAG, "info.presentationTimeUs : " + (info.presentationTimeUs / 10000)+" playTime: " + (System.currentTimeMillis()-startWhen)+" sleepTime : " + sleepTime);
                        if(sleepTime > 0)
                            Thread.sleep(sleepTime);
                    }catch(InterruptedException e){
                        e.printStackTrace();
                    }
                    mDecoder.releaseOutputBuffer(outIndex, true);
                    break;
            }
            if((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0){
                Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                break;
            }
        }
        mDecoder.stop();
        mDecoder.release();
        mExtractor.release();

    }

    public void close(){
        eosReceived = true;
    }
}
