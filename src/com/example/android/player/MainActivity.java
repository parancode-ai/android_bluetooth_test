package com.example.android.player;

import android.app.Activity;
import android.content.ComponentName;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Bitmap;

/**
 * Simple Activity that connects to {@link MusicPlaybackService} and mirrors metadata/playback state
 * while offering local transport controls.
 */
public class MainActivity extends Activity {

    private MediaBrowser mediaBrowser;
    private TextView titleView;
    private TextView artistView;
    private ImageView artView;
    private Button playPause;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        titleView = findViewById(R.id.title);
        artistView = findViewById(R.id.artist);
        artView = findViewById(R.id.album_art);
        playPause = findViewById(R.id.play_pause);

        Button previous = findViewById(R.id.previous);
        Button next = findViewById(R.id.next);
        Button fastForward = findViewById(R.id.fast_forward);
        Button rewind = findViewById(R.id.rewind);

        mediaBrowser = new MediaBrowser(this,
                new ComponentName(this, MusicPlaybackService.class),
                connectionCallback,
                null);

        playPause.setOnClickListener(v -> togglePlayback());
        previous.setOnClickListener(v -> getController().getTransportControls().skipToPrevious());
        next.setOnClickListener(v -> getController().getTransportControls().skipToNext());
        fastForward.setOnClickListener(v -> getController().getTransportControls().fastForward());
        rewind.setOnClickListener(v -> getController().getTransportControls().rewind());
    }

    @Override
    protected void onStart() {
        super.onStart();
        mediaBrowser.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        MediaController controller = getController();
        if (controller != null) {
            controller.unregisterCallback(controllerCallback);
        }
        mediaBrowser.disconnect();
    }

    private MediaController getController() {
        return getMediaController();
    }

    private final MediaBrowser.ConnectionCallback connectionCallback = new MediaBrowser.ConnectionCallback() {
        @Override
        public void onConnected() {
            MediaSession.Token token = mediaBrowser.getSessionToken();
            MediaController controller = new MediaController(MainActivity.this, token);
            setMediaController(controller);
            controller.registerCallback(controllerCallback);
            controller.getTransportControls().play();
            if (controller.getMetadata() != null) {
                updateMetadata(controller);
            }
            updatePlaybackState(controller.getPlaybackState());
        }
    };

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(android.media.MediaMetadata metadata) {
            updateMetadata(getController());
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            updatePlaybackState(state);
        }
    };

    private void togglePlayback() {
        MediaController controller = getController();
        if (controller == null) {
            return;
        }
        PlaybackState state = controller.getPlaybackState();
        if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
            controller.getTransportControls().pause();
        } else {
            controller.getTransportControls().play();
        }
    }

    private void updateMetadata(MediaController controller) {
        if (controller == null || controller.getMetadata() == null) {
            titleView.setText(R.string.metadata_unknown);
            artistView.setText(R.string.metadata_unknown);
            artView.setImageResource(R.drawable.ic_album_placeholder);
            return;
        }
        android.media.MediaMetadata metadata = controller.getMetadata();
        titleView.setText(metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE));
        artistView.setText(metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST));
        Bitmap art = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art != null) {
            artView.setImageBitmap(art);
        } else {
            artView.setImageResource(R.drawable.ic_album_placeholder);
        }
    }

    private void updatePlaybackState(PlaybackState state) {
        if (state == null) {
            playPause.setText(R.string.play);
            return;
        }
        if (state.getState() == PlaybackState.STATE_PLAYING) {
            playPause.setText(R.string.pause);
        } else {
            playPause.setText(R.string.play);
        }
    }
}
