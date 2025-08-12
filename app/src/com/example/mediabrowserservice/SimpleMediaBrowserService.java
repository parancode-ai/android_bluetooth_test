package com.example.mediabrowserservice;

import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.service.media.MediaBrowserService;

import java.util.Collections;
import java.util.List;

/**
 * A minimal {@link MediaBrowserService} implementation that exposes an empty media catalog.
 * This service sets up a {@link MediaSession} so clients can connect using {@link MediaBrowser}.
 */
public class SimpleMediaBrowserService extends MediaBrowserService {
    private MediaSession mSession;

    @Override
    public void onCreate() {
        super.onCreate();
        mSession = new MediaSession(this, "SimpleMediaBrowserService");
        setSessionToken(mSession.getSessionToken());
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        // Allow all clients to connect and browse the single "root" node.
        return new BrowserRoot("root", /*extras=*/ null);
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaItem>> result) {
        // Return an empty list since this sample does not provide media content.
        result.sendResult(Collections.emptyList());
    }
}
