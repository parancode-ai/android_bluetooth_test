package com.example.android;

import android.Manifest;
import android.annotation.Nullable;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private MediaController mediaController;
    private PlaybackService playbackService;
    private ImageButton playPauseButton;
    private ImageButton nextButton;
    private ImageButton prevButton;
    private ImageButton ffButton;
    private ImageButton rewButton;
    private TextView titleView;
    private TextView subtitleView;
    private ImageView albumArtView;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlaybackService.LocalBinder binder = (PlaybackService.LocalBinder) service;
            playbackService = binder.getService();
            mediaController = new MediaController(MainActivity.this, binder.getSessionToken());
            mediaController.registerCallback(controllerCallback);
            updateFromController();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (mediaController != null) {
                mediaController.unregisterCallback(controllerCallback);
                mediaController = null;
            }
            playbackService = null;
        }
    };

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            updatePlayPause(state != null && state.getState() == PlaybackState.STATE_PLAYING);
        }

        @Override
        public void onMetadataChanged(android.media.MediaMetadata metadata) {
            updateMetadata(metadata);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        requestNotificationPermissionIfNeeded();
        Intent intent = new Intent(this, PlaybackService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        if (mediaController != null) {
            mediaController.unregisterCallback(controllerCallback);
        }
        unbindService(connection);
        super.onDestroy();
    }

    private void initViews() {
        playPauseButton = findViewById(R.id.button_play_pause);
        nextButton = findViewById(R.id.button_next);
        prevButton = findViewById(R.id.button_prev);
        ffButton = findViewById(R.id.button_ff);
        rewButton = findViewById(R.id.button_rew);
        titleView = findViewById(R.id.text_title);
        subtitleView = findViewById(R.id.text_subtitle);
        albumArtView = findViewById(R.id.image_album_art);

        playPauseButton.setOnClickListener(v -> togglePlayback());
        nextButton.setOnClickListener(v -> sendAction(PlaybackService.ACTION_NEXT));
        prevButton.setOnClickListener(v -> sendAction(PlaybackService.ACTION_PREV));
        ffButton.setOnClickListener(v -> sendAction(PlaybackService.ACTION_FAST_FORWARD));
        rewButton.setOnClickListener(v -> sendAction(PlaybackService.ACTION_REWIND));
    }

    private void togglePlayback() {
        if (mediaController == null) {
            return;
        }
        PlaybackState state = mediaController.getPlaybackState();
        if (state == null || state.getState() != PlaybackState.STATE_PLAYING) {
            sendAction(PlaybackService.ACTION_PLAY);
        } else {
            sendAction(PlaybackService.ACTION_PAUSE);
        }
    }

    private void sendAction(String action) {
        if (mediaController == null) {
            return;
        }
        switch (action) {
            case PlaybackService.ACTION_PLAY:
                mediaController.getTransportControls().play();
                break;
            case PlaybackService.ACTION_PAUSE:
                mediaController.getTransportControls().pause();
                break;
            case PlaybackService.ACTION_NEXT:
                mediaController.getTransportControls().skipToNext();
                break;
            case PlaybackService.ACTION_PREV:
                mediaController.getTransportControls().skipToPrevious();
                break;
            case PlaybackService.ACTION_FAST_FORWARD:
                mediaController.getTransportControls().fastForward();
                break;
            case PlaybackService.ACTION_REWIND:
                mediaController.getTransportControls().rewind();
                break;
            default:
                break;
        }
    }

    private void updateFromController() {
        if (mediaController == null) {
            return;
        }
        PlaybackState state = mediaController.getPlaybackState();
        updatePlayPause(state != null && state.getState() == PlaybackState.STATE_PLAYING);
        updateMetadata(mediaController.getMetadata());
    }

    private void updatePlayPause(boolean playing) {
        playPauseButton.setImageResource(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
    }

    private void updateMetadata(@Nullable android.media.MediaMetadata metadata) {
        if (metadata == null) {
            titleView.setText(R.string.app_name);
            subtitleView.setText(R.string.subtitle_hint);
            albumArtView.setImageResource(R.drawable.ic_album_placeholder);
            return;
        }
        titleView.setText(metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE));
        String subtitle = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                + " • "
                + metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM);
        subtitleView.setText(subtitle);
        albumArtView.setImageBitmap(metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART));
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }
}
