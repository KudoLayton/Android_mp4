package com.example.leehyoseung.androidmp4player;


import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MovieThread extends Thread {
    private static final String VIDEO = "video/";
    private static final String AUDIO = "audio/";
    private static final String TAG = "VideoDecoder";
    private MediaExtractor mExtractor;
    private MediaCodec mDecoder;
    private MediaCodec aDecoder;
    private int mSampleRate = 0;
    private static final int TIMEOUT_US= 1000;
    private boolean eosReceived;

    public boolean init(Surface surface, String filePath){
        eosReceived = false;
        boolean movie = true;
        boolean audio = true;
        int movietrack = 0;
        int audiotrack = 0;
        int channel = 0;
        try{
            Log.d(TAG, "start init");
            mExtractor = new MediaExtractor();
            Log.d(TAG, "Accessing: "+filePath);
            mExtractor.setDataSource(filePath);
            Log.d(TAG, "I found it");
            MediaFormat format;
            MediaFormat audioformat;
            for(int i = 0; i < mExtractor.getTrackCount()&&(movie||audio); i++){
                format = mExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if(mime.startsWith(VIDEO)){
                    movietrack = i;
                    movie = false;
                }else if(mime.startsWith(AUDIO)){
                    audiotrack = i;
                    audio = false;
                }
            }
            audioformat = mExtractor.getTrackFormat(audiotrack);
            String mime = audioformat.getString(MediaFormat.KEY_MIME);
            mExtractor.selectTrack(audiotrack);
            channel = audioformat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            mSampleRate = audioformat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            aDecoder = MediaCodec.createDecoderByType("audio/mp4a-latm");
            audioformat = makeAACCodecSpecificData(MediaCodecInfo.CodecProfileLevel.AACObjectLC, mSampleRate, channel);
            try{
                Log.d(TAG, "format : "+ audioformat);
                aDecoder.configure(audioformat, null, null, 0);
            }catch(IllegalStateException e){
                Log.e(TAG, "codec '"+mime+"' failed configuration." + e);
                return false;
            }

            format = mExtractor.getTrackFormat(movietrack);
            mime = format.getString(MediaFormat.KEY_MIME);
            mExtractor.selectTrack(movietrack);
            mDecoder = MediaCodec.createDecoderByType(mime);
            try{
                Log.d(TAG, "format : " + format);
                mDecoder.configure(format,surface,null,0);

            }catch(IllegalStateException e){
                Log.e(TAG, "codec '"+mime+"' failed configuration." + e);
                return false;
            }
            aDecoder.start();
            mDecoder.start();
        }catch (IOException e){
            Log.d(TAG, "I cannot find file");
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public void run(){
        Log.d(TAG, "dddd"+mSampleRate);
        MediaCodec.BufferInfo audioinfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean isInput= true;
        boolean first = false;
        long startWhen = 0;
        int buffsize = AudioTrack.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, buffsize, AudioTrack.MODE_STREAM);
        audioTrack.play();
        while (!eosReceived){
            if(isInput){
                int audioInputIndex = aDecoder.dequeueInputBuffer(TIMEOUT_US);
                if(audioInputIndex >= 0){
                    ByteBuffer buffer = aDecoder.getInputBuffer(audioInputIndex);
                    int sampleSize = mExtractor.readSampleData(buffer, 0);
                    if(sampleSize < 0 ){
                        Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM" );
                        aDecoder.queueInputBuffer(audioInputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }else{
                        aDecoder.queueInputBuffer(audioInputIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
                    }
                }

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
            int outAudioIndex = aDecoder.dequeueOutputBuffer(audioinfo, TIMEOUT_US);
            switch(outAudioIndex){
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHAGNED");
                    aDecoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    MediaFormat format =aDecoder.getOutputFormat();
                    Log.d(TAG, "New audio format " + format);

                    audioTrack.setPlaybackRate(format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d(TAG, "dequeueOutputBuffer timed out!");
                    break;
                default:
                    ByteBuffer outBuffer = aDecoder.getOutputBuffer(outAudioIndex);
                    final byte[] chunk = new byte[audioinfo.size];
                    outBuffer.get(chunk);
                    outBuffer.clear();
                    audioTrack.write(chunk, 0, chunk.length);
                    //audioTrack.write(chunk,audioinfo.offset, audioinfo.offset + audioinfo.size);
                    aDecoder.releaseOutputBuffer(outAudioIndex, false);
                    break;
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
        aDecoder.stop();
        aDecoder.release();
        audioTrack.stop();
        audioTrack.release();
        mExtractor.release();

    }
    private MediaFormat makeAACCodecSpecificData(int audioProfile, int sampleRate, int channelConfig) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm"); // AAC의 Decoder type
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate); // sample rate 정의
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelConfig); // channel 정의
        int samplingFreq[] = { // Android 참고 코드상 아래와 같은 samplerate를 지원
                96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
                16000, 12000, 11025, 8000
        };

        // Search the Sampling Frequencies
        // 아래 코드를 통해 0~11 에 맞는 값을 가져와야 합니다.
        // 일반적으로 44100을 사용하고 있으며, 여기에서는 4번에 해당됩니다.
        int sampleIndex = -1;
        for (int i = 0; i < samplingFreq.length; ++i) {
            if (samplingFreq[i] == sampleRate) {
                Log.d(TAG, "kSamplingFreq " + samplingFreq[i] + " i : " + i);
                sampleIndex = i;
            }
        }

        if (sampleIndex == -1) {
            return null;
        }

            /* 디코딩에 필요한 csd-0의 byte를 생성합니다. 이 부분은 Android 4.4.2의 Full source를 참고하여 작성
             * csd-0에서 필요한 byte는 2 byte 입니다. 2byte에 필요한 정보는 audio Profile 정보와
             * sample index, channelConfig 정보가 됩니다.
            */
        ByteBuffer csd = ByteBuffer.allocate(2);
        // 첫 1 byte에는 Audio Profile에 3 bit shift 처리합니다. 그리고 sample index를 1bit shift 합니다.
        csd.put((byte) ((audioProfile << 3) | (sampleIndex >> 1)));

        csd.position(1);
        // 다음 1 byte에는 sample index를 7bit shift 하고, channel 수를 3bit shift 처리합니다.
        csd.put((byte) ((byte) ((sampleIndex << 7) & 0x80) | (channelConfig << 3)));
        csd.flip();
        // MediaCodec에서 필요하는 MediaFormat에 방금 생성한 "csd-0"을 저장합니다.
        format.setByteBuffer("csd-0", csd); // add csd-0

        return format;
    }

    public void close(){
        eosReceived = true;
    }
}
