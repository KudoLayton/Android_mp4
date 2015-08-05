package com.example.leehyoseung.androidmp4player;

import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;


public class MovieActivity extends AppCompatActivity implements SurfaceHolder.Callback{
    private MovieThread mVideoDecoder;
    private static final String FILE_PATH = "/storage/extSdCard/video.mp4";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SurfaceView surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        setContentView(surfaceView);
        mVideoDecoder = new MovieThread();

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,	int height) {
        if (mVideoDecoder != null){
            if(mVideoDecoder.init(holder.getSurface(), FILE_PATH)){
                mVideoDecoder.start();
            }else{
                mVideoDecoder = null;
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if(mVideoDecoder != null){
            mVideoDecoder.close();
        }
    }
}
