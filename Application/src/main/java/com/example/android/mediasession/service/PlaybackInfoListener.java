package com.example.android.mediasession.service;

import android.media.MediaMetadata;
import android.media.session.PlaybackState;

/** Listener for playback information from the player. */
public abstract class PlaybackInfoListener {
    public void onPlaybackStateChange(PlaybackState state) {}
    public void onMetadataChanged(MediaMetadata metadata) {}
}
