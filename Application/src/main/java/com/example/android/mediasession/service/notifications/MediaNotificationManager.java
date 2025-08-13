package com.example.android.mediasession.service.notifications;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.media.session.MediaSession;

/**
 * Minimal helper for building a foreground service notification for media playback.
 */
public class MediaNotificationManager {
    private final Context context;
    private final MediaSession session;

    public MediaNotificationManager(Context context, MediaSession session) {
        this.context = context;
        this.session = session;
    }

    public Notification buildNotification(PendingIntent contentIntent) {
        Notification.Builder builder = new Notification.Builder(context, "media_playback");
        builder.setContentTitle("Media Playback")
                .setContentText("Playing audio")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(contentIntent)
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()));
        return builder.build();
    }
}
