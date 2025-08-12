package com.example.mediabrowserservice;

import android.app.Activity;
import android.content.ComponentName;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Activity that connects to {@link SimpleMediaBrowserService} and displays the current playback
 * state. This demonstrates how a client would interact with the service using a
 * {@link MediaBrowser} and {@link MediaController}.
 */
public class MainActivity extends Activity {
    private MediaBrowser mBrowser;
    private MediaController mController;
    private TextView mState;

    private final MediaBrowser.ConnectionCallback mConnectionCallback =
            new MediaBrowser.ConnectionCallback() {
                @Override
                public void onConnected() {
                    MediaSession.Token token = mBrowser.getSessionToken();
                    mController = new MediaController(MainActivity.this, token);
                    mController.registerCallback(mControllerCallback);
                    updateState(mController.getPlaybackState());
                }
            };

    private final MediaController.Callback mControllerCallback =
            new MediaController.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    updateState(state);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mState = findViewById(R.id.state);
        Button play = findViewById(R.id.play);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController != null) {
                    mController.getTransportControls().play();
                }
            }
        });

        mBrowser = new MediaBrowser(this, new ComponentName(this, SimpleMediaBrowserService.class),
                mConnectionCallback, null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mBrowser.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mController != null) {
            mController.unregisterCallback(mControllerCallback);
            mController = null;
        }
        mBrowser.disconnect();
    }

    private void updateState(PlaybackState state) {
        if (state == null) {
            mState.setText("No state");
        } else {
            mState.setText("State: " + state.getState());
        }
    }
}
