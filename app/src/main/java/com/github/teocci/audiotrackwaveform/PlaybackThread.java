package com.github.teocci.audiotrackwaveform;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.nio.ShortBuffer;

/**
 * Created by teocci.
 *
 * @author teocci@yandex.com on 2017/Apr/05
 */

public class PlaybackThread
{
    static final int SAMPLE_RATE = 44100;
    private static final String LOG_TAG = PlaybackThread.class.getSimpleName();

    private Thread thread;
    private boolean shouldContinue;
    private ShortBuffer sampleBuffer;
    private int numSamples;
    private PlaybackListener playbackListener;

    public PlaybackThread(short[] samples, PlaybackListener listener)
    {
        sampleBuffer = ShortBuffer.wrap(samples);
        numSamples = samples.length;
        playbackListener = listener;
    }

    public boolean playing()
    {
        return thread != null;
    }

    public void startPlayback()
    {
        if (thread != null)
            return;

        // Start streaming in a thread
        shouldContinue = true;
        thread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                play();
            }
        });
        thread.start();
    }

    public void stopPlayback()
    {
        if (thread == null)
            return;

        shouldContinue = false;
        thread = null;
    }

    private void play()
    {
        int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioTrack.ERROR || bufferSize == AudioTrack.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }

        AudioTrack audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM);

        audioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener()
        {
            @Override
            public void onPeriodicNotification(AudioTrack track)
            {
                if (playbackListener != null && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    playbackListener.onProgress((track.getPlaybackHeadPosition() * 1000) / SAMPLE_RATE);
                }
            }

            @Override
            public void onMarkerReached(AudioTrack track)
            {
                Log.v(LOG_TAG, "Audio file end reached");
                track.release();
                if (playbackListener != null) {
                    playbackListener.onCompletion();
                }
            }
        });
        audioTrack.setPositionNotificationPeriod(SAMPLE_RATE / 30); // 30 times per second
        audioTrack.setNotificationMarkerPosition(numSamples);

        audioTrack.play();

        Log.v(LOG_TAG, "Audio streaming started");

        short[] buffer = new short[bufferSize];
        sampleBuffer.rewind();
        int limit = numSamples;
        int totalWritten = 0;
        while (sampleBuffer.position() < limit && shouldContinue) {
            int numSamplesLeft = limit - sampleBuffer.position();
            int samplesToWrite;
            if (numSamplesLeft >= buffer.length) {
                sampleBuffer.get(buffer);
                samplesToWrite = buffer.length;
            } else {
                for (int i = numSamplesLeft; i < buffer.length; i++) {
                    buffer[i] = 0;
                }
                sampleBuffer.get(buffer, 0, numSamplesLeft);
                samplesToWrite = numSamplesLeft;
            }
            totalWritten += samplesToWrite;
            audioTrack.write(buffer, 0, samplesToWrite);
        }

        if (!shouldContinue) {
            audioTrack.release();
        }

        Log.v(LOG_TAG, "Audio streaming finished. Samples written: " + totalWritten);
    }
}
