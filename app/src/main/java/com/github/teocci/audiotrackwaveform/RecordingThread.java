package com.github.teocci.audiotrackwaveform;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * Created by teocci.
 *
 * @author teocci@yandex.com on 2017/Apr/05
 */

public class RecordingThread
{
    private static final String TAG = RecordingThread.class.getSimpleName();
    private static final int SAMPLE_RATE = 44100;

    public RecordingThread(AudioDataReceivedListener listener)
    {
        audioDataReceivedListener = listener;
    }

    private boolean shouldContinue;
    private AudioDataReceivedListener audioDataReceivedListener;
    private Thread thread;

    public boolean recording()
    {
        return thread != null;
    }

    public void startRecording()
    {
        if (thread != null)
            return;

        shouldContinue = true;
        thread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                record();
            }
        });
        thread.start();
    }

    public void stopRecording()
    {
        if (thread == null)
            return;

        shouldContinue = false;
        thread = null;
    }

    private void record()
    {
        Log.v(TAG, "Start");
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

        // buffer size in bytes
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }

        short[] audioBuffer = new short[bufferSize / 2];

        AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Audio Record can't initialize!");
            return;
        }
        record.startRecording();

        Log.v(TAG, "Start recording");

        long shortsRead = 0;
        while (shouldContinue) {
            int numberOfShort = record.read(audioBuffer, 0, audioBuffer.length);
            shortsRead += numberOfShort;

            // Notify waveform
            audioDataReceivedListener.onAudioDataReceived(audioBuffer);
        }

        record.stop();
        record.release();

        Log.v(TAG, String.format("Recording stopped. Samples read: %d", shortsRead));
    }
}
