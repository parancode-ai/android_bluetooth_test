package com.example.android.mediasession.service;

/** Simple abstraction for the underlying player implementation. */
public interface PlayerAdapter {
    void playFromAsset(String assetFileName);
    void pause();
    void seekTo(long position);
    void setPlaybackInfoListener(PlaybackInfoListener listener);
}
