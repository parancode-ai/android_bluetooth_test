package com.example.android.mediasession.service.players;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.util.Log;

import com.example.android.mediasession.service.PlaybackInfoListener;
import com.example.android.mediasession.service.PlayerAdapter;

import java.io.IOException;

/**
 * Very small {@link MediaPlayer}-based implementation of {@link PlayerAdapter}.
 */
public class MediaPlayerAdapter implements PlayerAdapter {
    private static final String TAG = "MediaPlayerAdapter";

    private final Context context;
    private final MediaPlayer player = new MediaPlayer();
    private PlaybackInfoListener listener;

    public MediaPlayerAdapter(Context context) {
        this.context = context.getApplicationContext();
        player.setOnCompletionListener(mp -> notifyState(PlaybackState.STATE_STOPPED));
    }

    @Override
    public void playFromAsset(String assetFileName) {
        try {
            player.reset();
            AssetFileDescriptor afd = context.getAssets().openFd(assetFileName);
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.prepare();
            player.start();
            notifyState(PlaybackState.STATE_PLAYING);
        } catch (IOException e) {
            Log.e(TAG, "Unable to play asset", e);
        }
    }

    @Override
    public void pause() {
        if (player.isPlaying()) {
            player.pause();
            notifyState(PlaybackState.STATE_PAUSED);
        }
    }

    @Override
    public void seekTo(long position) {
        player.seekTo((int) position);
    }

    @Override
    public void setPlaybackInfoListener(PlaybackInfoListener listener) {
        this.listener = listener;
    }

    private void notifyState(int state) {
        if (listener != null) {
            PlaybackState.Builder builder = new PlaybackState.Builder();
            builder.setState(state, player.getCurrentPosition(), 1.0f);
            listener.onPlaybackStateChange(builder.build());
        }
    }
}
