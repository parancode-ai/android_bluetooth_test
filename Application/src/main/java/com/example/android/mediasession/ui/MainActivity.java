package com.example.android.mediasession.ui;

import android.app.Activity;
import android.media.session.MediaController;
import android.os.Bundle;

import com.example.android.mediasession.R;
import com.example.android.mediasession.client.MediaBrowserHelper;
import com.example.android.mediasession.service.MusicService;

/** Basic activity that connects to {@link MusicService} using {@link MediaBrowserHelper}. */
public class MainActivity extends Activity {
    private MediaBrowserHelper browserHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        browserHelper = new MediaBrowserHelper(this, MusicService.class) {
            @Override
            protected void onConnected(MediaController controller) {
                // Update UI here
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        browserHelper.onStart();
    }

    @Override
    protected void onStop() {
        browserHelper.onStop();
        super.onStop();
    }
}
