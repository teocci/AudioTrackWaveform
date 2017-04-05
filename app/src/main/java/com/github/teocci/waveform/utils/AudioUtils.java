package com.github.teocci.waveform.utils;

/**
 * Created by teocci.
 *
 * @author teocci@yandex.com on 2017/Apr/05
 */

public final class AudioUtils
{
    public static int calculateAudioLength(int samplesCount, int sampleRate, int channelCount)
    {
        return ((samplesCount / channelCount) * 1000) / sampleRate;
    }
}
