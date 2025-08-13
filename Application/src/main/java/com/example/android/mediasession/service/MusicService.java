package com.example.android.mediasession.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.service.media.MediaBrowserService;

import com.example.android.mediasession.service.contentcatalogs.MusicLibrary;
import com.example.android.mediasession.service.notifications.MediaNotificationManager;
import com.example.android.mediasession.service.players.MediaPlayerAdapter;

import java.util.List;

/** Service exposing a media browser and handling audio playback. */
public class MusicService extends MediaBrowserService {
    private MediaSession session;
    private MediaPlayerAdapter player;
    private MediaNotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        session = new MediaSession(this, "MusicService");
        setSessionToken(session.getSessionToken());

        player = new MediaPlayerAdapter(this);
        player.setPlaybackInfoListener(new PlaybackInfoListener() {
            @Override
            public void onPlaybackStateChange(PlaybackState state) {
                session.setPlaybackState(state);
            }
        });

        notificationManager = new MediaNotificationManager(this, session);
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        return new BrowserRoot("root", null);
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        result.sendResult(MusicLibrary.getMediaItems(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, getClass()), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = notificationManager.buildNotification(pi);
        startForeground(1, notification);
        return Service.START_STICKY;
    }
}
