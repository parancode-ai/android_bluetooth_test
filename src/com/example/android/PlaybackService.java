package com.example.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Locale;

public class PlaybackService extends Service implements PlaybackEngine.Listener {

    public static final String ACTION_PLAY = "com.example.android.action.PLAY";
    public static final String ACTION_PAUSE = "com.example.android.action.PAUSE";
    public static final String ACTION_NEXT = "com.example.android.action.NEXT";
    public static final String ACTION_PREV = "com.example.android.action.PREV";
    public static final String ACTION_FAST_FORWARD = "com.example.android.action.FAST_FORWARD";
    public static final String ACTION_REWIND = "com.example.android.action.REWIND";

    private static final String CHANNEL_ID = "bluetooth_player_channel";
    private static final int NOTIFICATION_ID = 1001;

    private final IBinder binder = new LocalBinder();
    private MediaSession mediaSession;
    private MediaController mediaController;
    private PlaybackEngine playbackEngine;
    private Handler progressHandler;

    public class LocalBinder extends Binder {
        PlaybackService getService() {
            return PlaybackService.this;
        }

        MediaSession.Token getSessionToken() {
            return mediaSession.getSessionToken();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        playbackEngine = new PlaybackEngine(this);
        progressHandler = new Handler(Looper.getMainLooper());
        setupMediaSession();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            handleIntent(intent.getAction());
        }
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        progressHandler.removeCallbacksAndMessages(null);
        playbackEngine.release();
        mediaSession.release();
        super.onDestroy();
    }

    private void setupMediaSession() {
        mediaSession = new MediaSession(this, "BluetoothPlayerSession");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                playbackEngine.play();
                updateSessionState(PlaybackState.STATE_PLAYING);
                startForeground(NOTIFICATION_ID, buildNotification());
            }

            @Override
            public void onPause() {
                playbackEngine.pause();
                updateSessionState(PlaybackState.STATE_PAUSED);
                stopForeground(false);
            }

            @Override
            public void onSkipToNext() {
                playbackEngine.skipToNext();
                updateMetadata();
                updateSessionState(playbackEngine.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
            }

            @Override
            public void onSkipToPrevious() {
                playbackEngine.skipToPrevious();
                updateMetadata();
                updateSessionState(playbackEngine.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
            }

            @Override
            public void onFastForward() {
                playbackEngine.fastForward();
                updateSessionState(playbackEngine.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
            }

            @Override
            public void onRewind() {
                playbackEngine.rewind();
                updateSessionState(playbackEngine.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
            }

            @Override
            public void onSeekTo(long pos) {
                playbackEngine.seekTo(pos);
                updateSessionState(playbackEngine.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
            }
        });
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);
        mediaController = new MediaController(this, mediaSession.getSessionToken());
        updateMetadata();
        updateSessionState(PlaybackState.STATE_PAUSED);
        scheduleProgressUpdate();
    }

    private void handleIntent(String action) {
        if (ACTION_PLAY.equals(action)) {
            mediaController.getTransportControls().play();
        } else if (ACTION_PAUSE.equals(action)) {
            mediaController.getTransportControls().pause();
        } else if (ACTION_NEXT.equals(action)) {
            mediaController.getTransportControls().skipToNext();
        } else if (ACTION_PREV.equals(action)) {
            mediaController.getTransportControls().skipToPrevious();
        } else if (ACTION_FAST_FORWARD.equals(action)) {
            mediaController.getTransportControls().fastForward();
        } else if (ACTION_REWIND.equals(action)) {
            mediaController.getTransportControls().rewind();
        }
    }

    private PendingIntent buildPendingIntent(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, action.hashCode(), intent, flags);
    }

    private Notification buildNotification() {
        PlaybackEngine.TrackInfo trackInfo = playbackEngine.currentTrack();
        boolean playing = playbackEngine.isPlaying();

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(trackInfo.title)
                .setContentText(String.format(Locale.getDefault(), "%s • %s", trackInfo.artist, trackInfo.album))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setLargeIcon(createAlbumArt(trackInfo))
                .setOngoing(playing)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));

        builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_previous,
                getString(R.string.action_prev),
                buildPendingIntent(ACTION_PREV)).build());

        builder.addAction(new Notification.Action.Builder(
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? getString(R.string.action_pause) : getString(R.string.action_play),
                buildPendingIntent(playing ? ACTION_PAUSE : ACTION_PLAY)).build());

        builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_next,
                getString(R.string.action_next),
                buildPendingIntent(ACTION_NEXT)).build());

        return builder.build();
    }

    private void updateMetadata() {
        PlaybackEngine.TrackInfo trackInfo = playbackEngine.currentTrack();
        mediaSession.setMetadata(new android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, trackInfo.title)
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, trackInfo.artist)
                .putString(android.media.MediaMetadata.METADATA_KEY_ALBUM, trackInfo.album)
                .putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID, trackInfo.fileName)
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, trackInfo.durationMs)
                .putBitmap(android.media.MediaMetadata.METADATA_KEY_ART, createAlbumArt(trackInfo))
                .build());
    }

    private void updateSessionState(int state) {
        PlaybackState playbackState = new PlaybackState.Builder()
                .setActions(
                        PlaybackState.ACTION_PLAY
                                | PlaybackState.ACTION_PAUSE
                                | PlaybackState.ACTION_PLAY_PAUSE
                                | PlaybackState.ACTION_SKIP_TO_NEXT
                                | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                                | PlaybackState.ACTION_FAST_FORWARD
                                | PlaybackState.ACTION_REWIND
                                | PlaybackState.ACTION_SEEK_TO)
                .setState(state, playbackEngine.getPosition(), 1.0f, SystemClock.elapsedRealtime())
                .build();
        mediaSession.setPlaybackState(playbackState);
        mediaSession.setActive(true);
    }

    private void scheduleProgressUpdate() {
        progressHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateSessionState(playbackEngine.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
                scheduleProgressUpdate();
            }
        }, 1000);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notification_channel_description));
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    private Bitmap createAlbumArt(PlaybackEngine.TrackInfo trackInfo) {
        int size = getResources().getDimensionPixelSize(R.dimen.album_art_size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.parseColor("#3F51B5"));
        canvas.drawRoundRect(new RectF(0, 0, size, size), 24f, 24f, paint);

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(size / 6f);
        float x = size / 2f;
        float y = size / 2f - ((paint.descent() + paint.ascent()) / 2);
        canvas.drawText(trackInfo.title, x, y, paint);
        return bitmap;
    }

    @Override
    public void onTrackChanged(PlaybackEngine.TrackInfo trackInfo) {
        updateMetadata();
        startForeground(NOTIFICATION_ID, buildNotification());
    }
}
