package com.example.android.mediasession.client;

import android.content.ComponentName;
import android.content.Context;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;

/**
 * Helper class for managing connection to {@link android.service.media.MediaBrowserService}.
 * This is a greatly simplified version of the helper used in the original sample project.
 */
public class MediaBrowserHelper {
    private final Context context;
    private final ComponentName serviceComponent;
    private MediaBrowser mediaBrowser;
    private MediaController mediaController;

    public MediaBrowserHelper(Context context, Class<?> serviceClass) {
        this.context = context.getApplicationContext();
        this.serviceComponent = new ComponentName(context, serviceClass);
    }

    /** Connect to the MediaBrowserService. */
    public void onStart() {
        if (mediaBrowser == null) {
            mediaBrowser = new MediaBrowser(context, serviceComponent, connectionCallback, null);
        }
        if (!mediaBrowser.isConnected()) {
            mediaBrowser.connect();
        }
    }

    /** Disconnect from the MediaBrowserService. */
    public void onStop() {
        if (mediaController != null) {
            mediaController.unregisterCallback(controllerCallback);
            mediaController = null;
        }
        if (mediaBrowser != null && mediaBrowser.isConnected()) {
            mediaBrowser.disconnect();
        }
    }

    protected void onConnected(MediaController controller) {
        // subclasses may override
    }

    private final MediaBrowser.ConnectionCallback connectionCallback = new MediaBrowser.ConnectionCallback() {
        @Override
        public void onConnected() {
            mediaController = new MediaController(context, mediaBrowser.getSessionToken());
            mediaController.registerCallback(controllerCallback);
            onConnected(mediaController);
        }
    };

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
    };
}
