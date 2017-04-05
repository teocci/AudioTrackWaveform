package com.github.teocci.audiotrackwaveform;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import com.github.teocci.waveform.WaveformView;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Created by teocci.
 *
 * @author teocci@yandex.com on 2017/Apr/05
 */

public class MainActivity extends AppCompatActivity
{
    private WaveformView realtimeWaveformView;
    private RecordingThread recordingThread;
    private PlaybackThread playbackThread;
    private static final int REQUEST_RECORD_AUDIO = 13;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        realtimeWaveformView = (WaveformView) findViewById(R.id.waveformView);
        recordingThread = new RecordingThread(new AudioDataReceivedListener()
        {
            @Override
            public void onAudioDataReceived(short[] data)
            {
                realtimeWaveformView.setSamples(data);
            }
        });

        final WaveformView mPlaybackView = (WaveformView) findViewById(R.id.playbackWaveformView);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if (!recordingThread.recording()) {
                    startAudioRecordingSafe();
                } else {
                    recordingThread.stopRecording();
                }
            }
        });

        short[] samples = null;
        try {
            samples = getAudioSample();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (samples != null) {
            final FloatingActionButton playFab = (FloatingActionButton) findViewById(R.id.playFab);

            playbackThread = new PlaybackThread(samples, new PlaybackListener()
            {
                @Override
                public void onProgress(int progress)
                {
                    mPlaybackView.setMarkerPosition(progress);
                }

                @Override
                public void onCompletion()
                {
                    mPlaybackView.setMarkerPosition(mPlaybackView.getAudioLength());
                    playFab.setImageResource(android.R.drawable.ic_media_play);
                }
            });
            mPlaybackView.setChannels(1);
            mPlaybackView.setSampleRate(PlaybackThread.SAMPLE_RATE);
            mPlaybackView.setSamples(samples);

            playFab.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (!playbackThread.playing()) {
                        playbackThread.startPlayback();
                        playFab.setImageResource(android.R.drawable.ic_media_pause);
                    } else {
                        playbackThread.stopPlayback();
                        playFab.setImageResource(android.R.drawable.ic_media_play);
                    }
                }
            });
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        recordingThread.stopRecording();
        playbackThread.stopPlayback();
    }

    private short[] getAudioSample() throws IOException
    {
        InputStream is = getResources().openRawResource(R.raw.jinglebells);
        byte[] data;
        try {
            data = IOUtils.toByteArray(is);
        } finally {
            if (is != null) {
                is.close();
            }
        }

        ShortBuffer sb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        short[] samples = new short[sb.limit()];
        sb.get(samples);
        return samples;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void startAudioRecordingSafe()
    {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            recordingThread.startRecording();
        } else {
            requestMicrophonePermission();
        }
    }

    private void requestMicrophonePermission()
    {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.RECORD_AUDIO)) {
            // Show dialog explaining why we need record audio
            Snackbar.make(realtimeWaveformView, "Microphone access is required in order to record audio",
                    Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                            android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
                }
            }).show();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults)
    {
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            recordingThread.stopRecording();
        }
    }
}
