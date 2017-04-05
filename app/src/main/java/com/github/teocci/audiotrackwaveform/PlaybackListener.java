package com.github.teocci.audiotrackwaveform;

/**
 * Created by teocci.
 *
 * @author teocci@yandex.com on 2017/Apr/05
 */

public interface PlaybackListener
{
    void onProgress(int progress);

    void onCompletion();
}
